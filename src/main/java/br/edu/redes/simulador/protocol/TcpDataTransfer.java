package br.edu.redes.simulador.protocol;

import br.edu.redes.simulador.core.ChannelConfig;
import br.edu.redes.simulador.core.NetworkPacket;
import br.edu.redes.simulador.core.SimulatedChannel;
import br.edu.redes.simulador.core.SimulationTrace;
import br.edu.redes.simulador.core.VirtualClock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Cumulative ACK transfer with a retransmission timer and fast retransmit after 3 duplicate ACKs. */
public final class TcpDataTransfer {
    private final TcpConnection connection;
    private final VirtualClock clock;
    private final SimulationTrace trace;
    private final SimulatedChannel channel;
    private final int count;
    private final int initialRtoMs = 100;
    private final int maxRetries = 5;
    private final Map<Integer, Long> sentAt = new HashMap<>();
    private final Map<Integer, Integer> attempts = new HashMap<>();
    private final Map<Integer, Boolean> retransmitted = new HashMap<>();
    private final TreeMap<Integer, String> outOfOrderBuffer = new TreeMap<>();
    private final List<Integer> delivered = new ArrayList<>();
    private int expectedSequence = 1;
    private int baseAck = 1;
    private int duplicateAcks;
    private int retransmissions;
    private int fastRetransmissions;
    private int timeouts;
    private int outOfOrderPackets;
    private int currentRtoMs = initialRtoMs;
    private double estimatedRttMs;
    private double devRttMs;
    private boolean hasRttSample;
    private boolean failed;
    private long fastRetransmitAtMs = -1;
    private VirtualClock.Ticket timeoutTicket;
    private long timeoutGeneration;
    private long startedAtMs;

    public TcpDataTransfer(TcpConnection connection, ChannelConfig config, SimulationTrace trace, int count) {
        this.connection = connection;
        this.clock = connection.clock();
        this.trace = trace;
        this.channel = connection.channel();
        this.count = count;
    }

    public TransferMetrics run(int forcedDropSequence) {
        if (!connection.connected()) throw new IllegalStateException("A conexão TCP precisa estar ESTABLISHED antes dos dados.");
        startedAtMs = clock.nowMs();
        for (int sequence = 1; sequence <= count; sequence++) {
            connection.fsmA().fire("APP_SEND_DATA", clock.nowMs());
            if (sequence == forcedDropSequence) channel.dropNextPacket();
            sendSegment(sequence, false);
        }
        scheduleTimeout();
        clock.runUntilIdle(50_000);
        return new TransferMetrics(count, delivered.size(), retransmissions, fastRetransmissions, timeouts,
                outOfOrderPackets, estimatedRttMs, devRttMs, currentRtoMs, fastRetransmitAtMs,
                clock.nowMs() - startedAtMs, List.copyOf(delivered));
    }

    private void sendSegment(int sequence, boolean retry) {
        if (retry) {
            retransmissions++;
            retransmitted.put(sequence, true);
        } else {
            attempts.putIfAbsent(sequence, 0);
            sentAt.put(sequence, clock.nowMs());
        }
        attempts.merge(sequence, 1, Integer::sum);
        NetworkPacket packet = NetworkPacket.tcp("DATA", "A", "B", sequence, 0,
                "segmento-" + sequence, retry);
        connection.sendPacket(packet, this::receiveData);
    }

    private void receiveData(NetworkPacket packet) {
        if (packet.corrupted()) return;
        int sequence = packet.sequence();
        connection.fsmB().fire("RCV_DATA", clock.nowMs());
        if (sequence == expectedSequence) {
            delivered.add(sequence);
            expectedSequence++;
            while (outOfOrderBuffer.containsKey(expectedSequence)) {
                outOfOrderBuffer.remove(expectedSequence);
                delivered.add(expectedSequence++);
            }
        } else if (sequence > expectedSequence) {
            outOfOrderBuffer.putIfAbsent(sequence, packet.payload());
            outOfOrderPackets++;
        }
        sendDataAck(expectedSequence);
    }

    private void sendDataAck(int acknowledgement) {
        NetworkPacket packet = NetworkPacket.tcp("DATA_ACK", "B", "A", 0, acknowledgement, "", false);
        channel.send(packet, this::receiveAck);
    }

    private void receiveAck(NetworkPacket packet) {
        if (packet.corrupted()) return;
        int acknowledgement = packet.acknowledgement();
        if (acknowledgement > baseAck) {
            int newlyAcknowledged = acknowledgement - baseAck;
            baseAck = acknowledgement;
            duplicateAcks = 0;
            connection.fsmA().fire("RCV_CUMULATIVE_ACK", clock.nowMs());
            int sampleSequence = acknowledgement - 1;
            if (newlyAcknowledged > 0 && sentAt.containsKey(sampleSequence) && !retransmitted.getOrDefault(sampleSequence, false))
                updateRtt(clock.nowMs() - sentAt.get(sampleSequence));
            cancelTimeout();
            if (baseAck <= count) scheduleTimeout();
        } else if (acknowledgement == baseAck) {
            duplicateAcks++;
            connection.fsmA().fire("RCV_DUP_ACK", clock.nowMs());
            if (duplicateAcks == 3 && baseAck <= count && attempts.getOrDefault(baseAck, 0) <= maxRetries) {
                connection.fsmA().fire("FAST_RETRANSMIT", clock.nowMs());
                fastRetransmissions++;
                fastRetransmitAtMs = clock.nowMs() - startedAtMs;
                cancelTimeout();
                sendSegment(baseAck, true);
                scheduleTimeout();
            }
        }
    }

    private void updateRtt(long sampleMs) {
        if (!hasRttSample) {
            estimatedRttMs = sampleMs;
            devRttMs = sampleMs / 2.0;
            hasRttSample = true;
        } else {
            estimatedRttMs = 0.875 * estimatedRttMs + 0.125 * sampleMs;
            devRttMs = 0.75 * devRttMs + 0.25 * Math.abs(sampleMs - estimatedRttMs);
        }
        currentRtoMs = (int) Math.max(1, Math.round(estimatedRttMs + 4 * devRttMs));
    }

    private void scheduleTimeout() {
        if (baseAck > count) return;
        long generation = ++timeoutGeneration;
        int delay = currentRtoMs;
        timeoutTicket = clock.schedule(delay, () -> {
            if (generation != timeoutGeneration || baseAck > count) return;
            timeouts++;
            if (connection.fsmA().fire("TIMEOUT_DATA", clock.nowMs())) {
                int sequence = baseAck;
                if (attempts.getOrDefault(sequence, 0) <= maxRetries) sendSegment(sequence, true);
                else failed = true;
            }
            if (!failed) currentRtoMs = Math.min(1_000, currentRtoMs * 2);
            if (!failed) scheduleTimeout();
        });
    }

    private void cancelTimeout() {
        timeoutGeneration++;
        if (timeoutTicket != null) timeoutTicket.cancel();
    }
}
