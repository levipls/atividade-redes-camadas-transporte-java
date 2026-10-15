package br.edu.redes.simulador.protocol;

import br.edu.redes.simulador.core.ChannelConfig;
import br.edu.redes.simulador.core.NetworkPacket;
import br.edu.redes.simulador.core.SimulatedChannel;
import br.edu.redes.simulador.core.SimulationTrace;
import br.edu.redes.simulador.core.VirtualClock;
import br.edu.redes.simulador.fsm.FsmDefinition;
import br.edu.redes.simulador.fsm.GenericFsm;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Connectionless UDP endpoint pair: the receiver validates checksum and silently drops invalid data. */
public final class UdpProtocol {
    private static final int HOST_A = 0x0a000001;
    private static final int HOST_B = 0x0a000002;
    private final VirtualClock clock;
    private final SimulationTrace trace;
    private final SimulatedChannel channel;
    private final GenericFsm sender;
    private final GenericFsm receiver;
    private final List<Integer> receivedSequences = new ArrayList<>();
    private int checksumDrops;

    public UdpProtocol(ChannelConfig config, VirtualClock clock, SimulationTrace trace) {
        this.clock = clock;
        this.trace = trace;
        this.channel = new SimulatedChannel(config, clock, trace);
        FsmDefinition definition = FsmDefinition.loadResource("/fsm/udp.fsm");
        this.sender = new GenericFsm("UDP", "A", "UDP_READY", definition, trace);
        this.receiver = new GenericFsm("UDP", "B", "UDP_READY", definition, trace);
    }

    public void sendDatagrams(int count) {
        for (int sequence = 1; sequence <= count; sequence++) sendOne(sequence, false);
    }

    public void sendOne(int sequence, boolean forceCorruption) {
        String message = "D#" + sequence;
        sender.fire("APP_SEND_DATAGRAM", clock.nowMs());
        UdpDatagram datagram = UdpDatagram.create(HOST_A, HOST_B, 49152, 9000, message);
        NetworkPacket packet = NetworkPacket.udp("A", "B", sequence, datagram);
        if (forceCorruption) channel.corruptNextPacket();
        channel.send(packet, this::receive);
    }

    private void receive(NetworkPacket packet) {
        UdpDatagram datagram = packet.datagram();
        if (packet.corrupted() || datagram == null || !datagram.checksumValid()) {
            checksumDrops++;
            receiver.fire("RCV_INVALID_CHECKSUM", clock.nowMs());
            return;
        }
        receiver.fire("RCV_VALID_DATAGRAM", clock.nowMs());
        receivedSequences.add(packet.sequence());
    }

    public int sentCount() {
        return (int) trace.transitions().stream().filter(t -> t.protocol().equals("UDP") && t.host().equals("A")
                && t.event().equals("APP_SEND_DATAGRAM") && t.accepted()).count();
    }
    public List<Integer> receivedSequences() { return List.copyOf(receivedSequences); }
    public int uniqueDeliveredCount() { return new HashSet<>(receivedSequences).size(); }
    public int duplicates() { return receivedSequences.size() - uniqueDeliveredCount(); }
    public int lostCount() { return sentCount() - uniqueDeliveredCount(); }
    public int checksumDrops() { return checksumDrops; }
    public boolean inOrder() {
        for (int i = 0; i < receivedSequences.size(); i++) if (receivedSequences.get(i) != i + 1) return false;
        return receivedSequences.size() == uniqueDeliveredCount();
    }
    public long elapsedMs() { return clock.nowMs(); }
}
