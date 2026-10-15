package br.edu.redes.simulador.core;

import java.util.HashSet;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.function.Consumer;

/** In-memory channel with reproducible loss, delay, corruption, duplication and reordering. */
public final class SimulatedChannel {
    private final ChannelConfig config;
    private final VirtualClock clock;
    private final SimulationTrace trace;
    private final SplittableRandom random;
    private final Set<String> dropTypesOnce = new HashSet<>();
    private boolean dropNext;
    private boolean corruptNext;

    public SimulatedChannel(ChannelConfig config, VirtualClock clock, SimulationTrace trace) {
        this.config = config;
        this.clock = clock;
        this.trace = trace;
        this.random = new SplittableRandom(config.seed());
        this.dropNext = config.dropNextPacket();
    }

    public void dropNextPacket() { dropNext = true; }
    public void dropNextType(String packetType) { dropTypesOnce.add(packetType); }
    public void corruptNextPacket() { corruptNext = true; }

    public void send(NetworkPacket packet, Consumer<NetworkPacket> receiver) {
        boolean manualDrop = dropNext || dropTypesOnce.remove(packet.type());
        if (dropNext) dropNext = false;
        boolean lost = manualDrop || chance(config.lossPercent());
        if (lost) {
            trace.packets().add(toTrace(packet, true, false, false));
            return;
        }
        boolean corrupted = corruptNext || chance(config.corruptionPercent());
        if (corruptNext) corruptNext = false;
        NetworkPacket delivered = corrupted ? packet.asCorrupted() : packet;
        long delay = randomDelay();
        if (chance(config.reorderingPercent())) delay += Math.max(1, config.maxDelayMs());
        trace.packets().add(toTrace(packet, false, corrupted, false));
        clock.schedule(delay, () -> receiver.accept(delivered));
        if (chance(config.duplicationPercent())) {
            long duplicateDelay = delay + Math.max(1, config.minDelayMs());
            trace.packets().add(toTrace(packet, false, corrupted, true));
            clock.schedule(duplicateDelay, () -> receiver.accept(delivered));
        }
    }

    private PacketTrace toTrace(NetworkPacket p, boolean lost, boolean corrupted, boolean duplicate) {
        return new PacketTrace(clock.nowMs(), p.protocol(), p.from(), p.to(), p.label(), p.sequence(),
                p.acknowledgement(), lost, corrupted, p.retransmission(), duplicate);
    }

    private long randomDelay() {
        if (config.minDelayMs() == config.maxDelayMs()) return config.minDelayMs();
        return random.nextInt(config.minDelayMs(), config.maxDelayMs() + 1);
    }

    private boolean chance(int percent) { return percent > 0 && random.nextInt(100) < percent; }
}
