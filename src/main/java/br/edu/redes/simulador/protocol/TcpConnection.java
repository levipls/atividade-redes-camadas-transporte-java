package br.edu.redes.simulador.protocol;

import br.edu.redes.simulador.core.ChannelConfig;
import br.edu.redes.simulador.core.NetworkPacket;
import br.edu.redes.simulador.core.SimulatedChannel;
import br.edu.redes.simulador.core.SimulationTrace;
import br.edu.redes.simulador.core.VirtualClock;
import br.edu.redes.simulador.fsm.FsmDefinition;
import br.edu.redes.simulador.fsm.GenericFsm;
import java.util.List;

/** Pair of TCP endpoints with the connection FSM, handshake, close and TIME_WAIT timer. */
public final class TcpConnection {
    private static final int SYN_TIMEOUT_MS = 40;
    private final ChannelConfig config;
    private final VirtualClock clock = new VirtualClock();
    private final SimulationTrace trace;
    private final SimulatedChannel channel;
    private final Endpoint a;
    private final Endpoint b;
    private VirtualClock.Ticket synTimer;
    private int synAttempts;
    private int timeWaitExpirations;
    private boolean normalClose;

    public TcpConnection(ChannelConfig config, SimulationTrace trace) {
        this.config = config;
        this.trace = trace;
        this.channel = new SimulatedChannel(config, clock, trace);
        FsmDefinition definition = FsmDefinition.loadResource("/fsm/tcp_connection.fsm");
        this.a = new Endpoint("A", new GenericFsm("TCP", "A", "CLOSED", definition, trace));
        this.b = new Endpoint("B", new GenericFsm("TCP", "B", "CLOSED", definition, trace));
    }

    public void dropNextPacket() { channel.dropNextPacket(); }
    public void dropNextType(String packetType) { channel.dropNextType(packetType); }

    public void startHandshake(boolean listen) {
        if (listen) b.fsm.fire("APP_LISTEN", clock.nowMs());
        a.fsm.fire("APP_CONNECT", clock.nowMs());
        sendSyn(false);
        scheduleSynTimeout();
    }

    private void sendSyn(boolean retransmission) {
        synAttempts++;
        send(NetworkPacket.tcp("SYN", "A", "B", 0, 0, "", retransmission), this::receiveConnectionPacket);
    }

    private void scheduleSynTimeout() {
        if (synTimer != null) synTimer.cancel();
        if (synAttempts >= 4) return;
        synTimer = clock.schedule(SYN_TIMEOUT_MS, () -> {
            if (!a.fsm.state().equals("SYN_SENT")) return;
            if (a.fsm.fire("TIMEOUT_SYN", clock.nowMs())) {
                sendSyn(true);
                scheduleSynTimeout();
            }
        });
    }

    private void receiveConnectionPacket(NetworkPacket packet) {
        Endpoint target = packet.to().equals("A") ? a : b;
        if (packet.corrupted()) return;
        switch (packet.type()) {
            case "SYN" -> {
                if (target.fsm.state().equals("LISTEN")) {
                    if (target.fsm.fire("RCV_SYN", clock.nowMs())) sendSynAck(target);
                } else if (target.fsm.state().equals("SYN_RCVD")) {
                    if (target.fsm.fire("RCV_SYN", clock.nowMs())) sendSynAck(target);
                } else if (target.fsm.state().equals("CLOSED")) {
                    if (target.fsm.fire("RCV_SYN", clock.nowMs()))
                        send(NetworkPacket.tcp("RST", target.id, packet.from(), 0, 0, "", false), this::receiveConnectionPacket);
                }
            }
            case "SYN_ACK" -> {
                if (target.fsm.state().equals("SYN_SENT")) {
                    if (target.fsm.fire("RCV_SYN_ACK", clock.nowMs())) {
                        if (synTimer != null) synTimer.cancel();
                        send(NetworkPacket.tcp("ACK", target.id, packet.from(), 0, 0, "", false), this::receiveConnectionPacket);
                    }
                } else if (target.fsm.state().equals("ESTABLISHED")) {
                    if (target.fsm.fire("DUP_SYN_ACK", clock.nowMs()))
                        send(NetworkPacket.tcp("ACK", target.id, packet.from(), 0, 0, "", false), this::receiveConnectionPacket);
                }
            }
            case "ACK" -> {
                if (target.fsm.state().equals("SYN_RCVD")) target.fsm.fire("RCV_ACK", clock.nowMs());
            }
            case "RST" -> {
                if (target.fsm.state().equals("SYN_SENT")) {
                    if (synTimer != null) synTimer.cancel();
                    target.fsm.fire("RCV_RST", clock.nowMs());
                }
            }
            case "FIN" -> receiveFin(target, packet.from());
            case "FIN_ACK" -> receiveFinAck(target);
            default -> { }
        }
    }

    private void sendSynAck(Endpoint from) {
        send(NetworkPacket.tcp("SYN_ACK", from.id, other(from).id, 0, 0, "", false), this::receiveConnectionPacket);
    }

    private void receiveFin(Endpoint target, String source) {
        String state = target.fsm.state();
        if (state.equals("ESTABLISHED") || state.equals("FIN_WAIT_2") || state.equals("FIN_WAIT_1")) {
            if (target.fsm.fire("RCV_FIN", clock.nowMs())) {
                send(NetworkPacket.tcp("FIN_ACK", target.id, source, 0, 0, "", false), this::receiveConnectionPacket);
                if (target.fsm.state().equals("TIME_WAIT")) enterTimeWait(target);
            }
        }
    }

    private void receiveFinAck(Endpoint target) {
        String state = target.fsm.state();
        if (state.equals("FIN_WAIT_1") || state.equals("CLOSING") || state.equals("LAST_ACK")) {
            if (target.fsm.fire("RCV_ACK", clock.nowMs())) {
                if (target.fsm.state().equals("TIME_WAIT")) enterTimeWait(target);
                if (normalClose && target == a && target.fsm.state().equals("FIN_WAIT_2")) closeEndpoint(b);
            }
        }
    }

    private void enterTimeWait(Endpoint endpoint) {
        clock.schedule(config.timeWaitMs(), () -> {
            if (endpoint.fsm.fire("TIME_WAIT_EXPIRE", clock.nowMs())) timeWaitExpirations++;
        });
    }

    public void closeNormally() {
        normalClose = true;
        closeEndpoint(a);
    }

    public void closeSimultaneously() {
        closeEndpoint(a);
        closeEndpoint(b);
    }

    private void closeEndpoint(Endpoint endpoint) {
        if (endpoint.fsm.fire("APP_CLOSE", clock.nowMs())) {
            send(NetworkPacket.tcp("FIN", endpoint.id, other(endpoint).id, 0, 0, "", false), this::receiveConnectionPacket);
        }
    }

    public void runUntilIdle() { clock.runUntilIdle(50_000); }

    public boolean connected() { return a.fsm.state().equals("ESTABLISHED") && b.fsm.state().equals("ESTABLISHED"); }
    public long nowMs() { return clock.nowMs(); }
    public String stateA() { return a.fsm.state(); }
    public String stateB() { return b.fsm.state(); }
    public List<String> historyA() { return a.fsm.stateHistory(); }
    public List<String> historyB() { return b.fsm.stateHistory(); }
    public int synAttempts() { return synAttempts; }
    public int timeWaitExpirations() { return timeWaitExpirations; }
    public GenericFsm fsmA() { return a.fsm; }
    public GenericFsm fsmB() { return b.fsm; }
    public SimulationTrace trace() { return trace; }
    public SimulatedChannel channel() { return channel; }
    public void sendPacket(NetworkPacket packet, java.util.function.Consumer<NetworkPacket> receiver) { send(packet, receiver); }
    public void advanceAll() { runUntilIdle(); }
    public VirtualClock clock() { return clock; }

    private void send(NetworkPacket packet, java.util.function.Consumer<NetworkPacket> receiver) { channel.send(packet, receiver); }
    private Endpoint other(Endpoint endpoint) { return endpoint == a ? b : a; }

    private record Endpoint(String id, GenericFsm fsm) { }
}
