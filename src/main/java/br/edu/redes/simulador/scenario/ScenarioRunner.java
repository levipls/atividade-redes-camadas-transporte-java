package br.edu.redes.simulador.scenario;

import br.edu.redes.simulador.core.ChannelConfig;
import br.edu.redes.simulador.core.PacketTrace;
import br.edu.redes.simulador.core.SimulationTrace;
import br.edu.redes.simulador.core.VirtualClock;
import br.edu.redes.simulador.protocol.TcpConnection;
import br.edu.redes.simulador.protocol.TcpDataTransfer;
import br.edu.redes.simulador.protocol.TransferMetrics;
import br.edu.redes.simulador.protocol.UdpProtocol;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Executes the ten reproducible scenarios listed in the assignment. */
public final class ScenarioRunner {
    private static final List<String> NAMES = List.of(
            "Handshake sem falhas", "Perda do SYN", "Perda do SYN-ACK", "Encerramento normal",
            "Fechamento simultâneo", "Perda de dados / fast retransmit", "Porta fechada / RST",
            "UDP: 20 datagramas e 30% de perda", "UDP: checksum inválido", "UDP × TCP: canal ruidoso");

    public List<ScenarioResult> runAll(ChannelConfig config) {
        List<ScenarioResult> results = new ArrayList<>();
        for (int id = 1; id <= 10; id++) results.add(run(id, config));
        return List.copyOf(results);
    }

    public ScenarioResult run(int id, ChannelConfig requested) {
        if (id < 1 || id > 10) throw new IllegalArgumentException("Cenário deve estar entre 1 e 10.");
        ChannelConfig clean = requested;
        return switch (id) {
            case 1 -> handshake(id, clean, false);
            case 2 -> handshake(id, clean, true);
            case 3 -> lostSynAck(clean);
            case 4 -> normalClose(clean);
            case 5 -> simultaneousClose(clean);
            case 6 -> fastRetransmit(clean);
            case 7 -> closedPort(clean);
            case 8 -> udpLoss(requested);
            case 9 -> udpCorruption(requested);
            case 10 -> compare(requested);
            default -> throw new AssertionError();
        };
    }

    private ScenarioResult handshake(int id, ChannelConfig config, boolean loseSyn) {
        SimulationTrace trace = new SimulationTrace();
        TcpConnection tcp = new TcpConnection(config, trace);
        if (loseSyn) tcp.dropNextPacket();
        tcp.startHandshake(true);
        tcp.runUntilIdle();
        boolean pass = tcp.connected() && (!loseSyn || tcp.synAttempts() >= 2)
                && has(tcp.historyA(), "SYN_SENT", "ESTABLISHED")
                && has(tcp.historyB(), "LISTEN", "SYN_RCVD", "ESTABLISHED");
        trace.addMetric("connected", tcp.connected());
        trace.addMetric("synRetransmissions", Math.max(0, tcp.synAttempts() - 1));
        trace.addMetric("totalTimeMs", tcp.nowMs());
        String summary = loseSyn ? "Timeout retransmitiu SYN; cliente e servidor chegaram a ESTABLISHED."
                : "A: CLOSED → SYN_SENT → ESTABLISHED; B: LISTEN → SYN_RCVD → ESTABLISHED.";
        return result(id, pass, summary, trace);
    }

    private ScenarioResult lostSynAck(ChannelConfig config) {
        int id = 3;
        SimulationTrace trace = new SimulationTrace();
        TcpConnection tcp = new TcpConnection(config, trace);
        tcp.dropNextType("SYN_ACK");
        tcp.startHandshake(true);
        tcp.runUntilIdle();
        long repeats = trace.transitions().stream().filter(t -> t.action().equals("RESEND_SYN_ACK")).count();
        boolean pass = tcp.connected() && tcp.synAttempts() >= 2 && repeats >= 1;
        trace.addMetric("connected", tcp.connected());
        trace.addMetric("synRetransmissions", Math.max(0, tcp.synAttempts() - 1));
        trace.addMetric("serverRepeatedSynAck", repeats);
        trace.addMetric("totalTimeMs", tcp.nowMs());
        return result(id, pass, "SYN-ACK perdido; SYN retransmitido e SYN-ACK reenviado pelo servidor.", trace);
    }

    private ScenarioResult normalClose(ChannelConfig config) {
        int id = 4;
        SimulationTrace trace = new SimulationTrace();
        TcpConnection tcp = new TcpConnection(config, trace);
        tcp.startHandshake(true);
        tcp.runUntilIdle();
        tcp.closeNormally();
        tcp.runUntilIdle();
        boolean pass = tcp.stateA().equals("CLOSED") && tcp.stateB().equals("CLOSED")
                && has(tcp.historyA(), "FIN_WAIT_1", "FIN_WAIT_2", "TIME_WAIT", "CLOSED")
                && has(tcp.historyB(), "CLOSE_WAIT", "LAST_ACK", "CLOSED");
        trace.addMetric("clientStates", tcp.historyA());
        trace.addMetric("serverStates", tcp.historyB());
        trace.addMetric("timeWaitExpirations", tcp.timeWaitExpirations());
        trace.addMetric("totalTimeMs", tcp.nowMs());
        return result(id, pass, "Encerramento de quatro vias concluiu após TIME_WAIT.", trace);
    }

    private ScenarioResult simultaneousClose(ChannelConfig config) {
        int id = 5;
        SimulationTrace trace = new SimulationTrace();
        TcpConnection tcp = new TcpConnection(config, trace);
        tcp.startHandshake(true);
        tcp.runUntilIdle();
        tcp.closeSimultaneously();
        tcp.runUntilIdle();
        boolean pass = tcp.stateA().equals("CLOSED") && tcp.stateB().equals("CLOSED")
                && has(tcp.historyA(), "FIN_WAIT_1", "CLOSING", "TIME_WAIT", "CLOSED")
                && has(tcp.historyB(), "FIN_WAIT_1", "CLOSING", "TIME_WAIT", "CLOSED");
        trace.addMetric("clientStates", tcp.historyA());
        trace.addMetric("serverStates", tcp.historyB());
        trace.addMetric("timeWaitExpirations", tcp.timeWaitExpirations());
        trace.addMetric("totalTimeMs", tcp.nowMs());
        return result(id, pass, "As duas pontas passaram por CLOSING e TIME_WAIT.", trace);
    }

    private ScenarioResult fastRetransmit(ChannelConfig config) {
        int id = 6;
        SimulationTrace trace = new SimulationTrace();
        TcpConnection tcp = new TcpConnection(config, trace);
        tcp.startHandshake(true);
        tcp.runUntilIdle();
        long transferStart = tcp.nowMs();
        TransferMetrics m = new TcpDataTransfer(tcp, config, trace, 10).run(2);
        boolean pass = m.delivered() == 10 && m.fastRetransmissions() == 1 && m.timeouts() == 0
                && m.fastRetransmitAtMs() < 100 && m.deliveredSequence().equals(java.util.stream.IntStream.rangeClosed(1, 10).boxed().toList());
        trace.addMetric("sent", m.sent());
        trace.addMetric("delivered", m.delivered());
        trace.addMetric("fastRetransmissions", m.fastRetransmissions());
        trace.addMetric("timeouts", m.timeouts());
        trace.addMetric("estimatedRttMs", m.estimatedRttMs());
        trace.addMetric("devRttMs", m.devRttMs());
        trace.addMetric("timeoutIntervalMs", m.timeoutIntervalMs());
        trace.addMetric("initialTimeoutMs", 100);
        trace.addMetric("timeoutThresholdMs", 100);
        trace.addMetric("fastRetransmitAtMs", m.fastRetransmitAtMs());
        trace.addMetric("savedMs", Math.max(0, 100 - m.fastRetransmitAtMs()));
        trace.addMetric("totalTimeMs", tcp.nowMs() - transferStart);
        return result(id, pass, "O segmento 2 foi recuperado por fast retransmit, sem esperar o timeout.", trace);
    }

    private ScenarioResult closedPort(ChannelConfig config) {
        int id = 7;
        SimulationTrace trace = new SimulationTrace();
        TcpConnection tcp = new TcpConnection(config, trace);
        tcp.startHandshake(false);
        tcp.runUntilIdle();
        boolean rst = trace.packets().stream().anyMatch(p -> p.protocol().equals("TCP") && p.label().equals("RST"));
        boolean pass = rst && tcp.stateA().equals("CLOSED") && tcp.stateB().equals("CLOSED");
        trace.addMetric("rstReceived", rst);
        trace.addMetric("clientState", tcp.stateA());
        trace.addMetric("totalTimeMs", tcp.nowMs());
        return result(id, pass, "Porta fechada respondeu com RST e encerrou a tentativa.", trace);
    }

    private ScenarioResult udpLoss(ChannelConfig requested) {
        int id = 8;
        ChannelConfig config = new ChannelConfig(30, Math.max(1, requested.minDelayMs()),
                Math.max(Math.max(1, requested.minDelayMs()), requested.maxDelayMs()), 0, 0, 0,
                requested.timeWaitMs(), 31415L, requested.dropNextPacket());
        SimulationTrace trace = new SimulationTrace();
        VirtualClock clock = new VirtualClock();
        UdpProtocol udp = new UdpProtocol(config, clock, trace);
        udp.sendDatagrams(20);
        clock.runUntilIdle(10_000);
        boolean pass = udp.sentCount() == 20 && udp.lostCount() > 0 && udp.duplicates() == 0
                && trace.packets().stream().noneMatch(p -> p.protocol().equals("UDP") && p.retransmission());
        trace.addMetric("sent", udp.sentCount());
        trace.addMetric("delivered", udp.uniqueDeliveredCount());
        trace.addMetric("lost", udp.lostCount());
        trace.addMetric("duplicates", udp.duplicates());
        trace.addMetric("inOrder", udp.inOrder());
        trace.addMetric("retransmissions", 0);
        trace.addMetric("totalTimeMs", udp.elapsedMs());
        return result(id, pass, "Vinte datagramas com 30% de perda; UDP não retransmite os perdidos.", trace);
    }

    private ScenarioResult udpCorruption(ChannelConfig requested) {
        int id = 9;
        ChannelConfig config = new ChannelConfig(0, 1, 1, 100, 0, 0, requested.timeWaitMs(), 9L, false);
        SimulationTrace trace = new SimulationTrace();
        VirtualClock clock = new VirtualClock();
        UdpProtocol udp = new UdpProtocol(config, clock, trace);
        udp.sendOne(1, false);
        clock.runUntilIdle(100);
        boolean pass = udp.checksumDrops() == 1 && udp.uniqueDeliveredCount() == 0
                && trace.packets().stream().noneMatch(p -> p.protocol().equals("UDP") && p.label().startsWith("ACK"));
        trace.addMetric("sent", udp.sentCount());
        trace.addMetric("delivered", udp.uniqueDeliveredCount());
        trace.addMetric("checksumDrops", udp.checksumDrops());
        trace.addMetric("acknowledgements", 0);
        return result(id, pass, "Checksum inválido descartado sem notificação ao remetente.", trace);
    }

    private ScenarioResult compare(ChannelConfig requested) {
        int id = 10;
        ChannelConfig noisy = new ChannelConfig(Math.max(10, requested.lossPercent()),
                Math.max(1, requested.minDelayMs()), Math.max(Math.max(1, requested.minDelayMs()), requested.maxDelayMs()),
                0, Math.max(5, requested.duplicationPercent()), Math.max(15, requested.reorderingPercent()),
                requested.timeWaitMs(), 2025L, false);
        SimulationTrace combined = new SimulationTrace();
        SimulationTrace udpTrace = new SimulationTrace();
        VirtualClock udpClock = new VirtualClock();
        UdpProtocol udp = new UdpProtocol(noisy, udpClock, udpTrace);
        udp.sendDatagrams(10);
        udpClock.runUntilIdle(10_000);
        combined.transitions().addAll(udpTrace.transitions());
        combined.packets().addAll(udpTrace.packets());

        SimulationTrace tcpTrace = new SimulationTrace();
        TcpConnection tcp = new TcpConnection(noisy, tcpTrace);
        tcp.startHandshake(true);
        tcp.runUntilIdle();
        TransferMetrics tm = new TcpDataTransfer(tcp, noisy, tcpTrace, 10).run(0);
        tcp.closeNormally();
        tcp.runUntilIdle();
        combined.transitions().addAll(tcpTrace.transitions());
        combined.packets().addAll(tcpTrace.packets());

        Map<String, Object> udpMetrics = new LinkedHashMap<>();
        udpMetrics.put("sent", udp.sentCount()); udpMetrics.put("delivered", udp.uniqueDeliveredCount());
        udpMetrics.put("lost", udp.lostCount()); udpMetrics.put("duplicates", udp.duplicates());
        udpMetrics.put("inOrder", udp.inOrder()); udpMetrics.put("retransmissions", 0);
        udpMetrics.put("totalTimeMs", udp.elapsedMs());
        Map<String, Object> tcpMetrics = new LinkedHashMap<>();
        tcpMetrics.put("sent", tm.sent()); tcpMetrics.put("delivered", tm.delivered());
        tcpMetrics.put("lost", 0); tcpMetrics.put("duplicates", 0); tcpMetrics.put("inOrder", true);
        tcpMetrics.put("retransmissions", tm.retransmissions()); tcpMetrics.put("fastRetransmissions", tm.fastRetransmissions());
        tcpMetrics.put("timeouts", tm.timeouts()); tcpMetrics.put("totalTimeMs", tcp.nowMs());
        boolean pass = tm.delivered() == 10 && udp.sentCount() == 10 && udp.lostCount() + udp.uniqueDeliveredCount() == 10;
        combined.addMetric("udp", udpMetrics); combined.addMetric("tcp", tcpMetrics);
        combined.addMetric("seed", noisy.seed()); combined.addMetric("lossPercent", noisy.lossPercent());
        combined.addMetric("duplicatedPackets", udp.duplicates());
        combined.addMetric("udpElapsedMs", udp.elapsedMs());
        return result(id, pass, "Mesma carga, canal e seed; UDP expõe perdas/ordem, TCP recupera a carga.", combined);
    }

    private static boolean has(List<String> actual, String... required) {
        int index = 0;
        for (String state : actual) if (state.equals(required[index]) && ++index == required.length) return true;
        return false;
    }

    private static ScenarioResult result(int id, boolean passed, String summary, SimulationTrace trace) {
        return new ScenarioResult(id, NAMES.get(id - 1), passed, summary, Map.copyOf(trace.metrics()), trace);
    }
}
