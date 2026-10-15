package br.edu.redes.simulador.core;

import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicLong;

/** Discrete event clock: timers advance simulated milliseconds, not wall-clock time. */
public final class VirtualClock {
    private final PriorityQueue<Scheduled> queue = new PriorityQueue<>();
    private final AtomicLong sequence = new AtomicLong();
    private long nowMs;

    public long nowMs() { return nowMs; }

    public Ticket schedule(long delayMs, Runnable action) {
        if (delayMs < 0) throw new IllegalArgumentException("Atraso negativo.");
        Scheduled scheduled = new Scheduled(nowMs + delayMs, sequence.getAndIncrement(), action);
        queue.add(scheduled);
        return () -> scheduled.cancelled = true;
    }

    public boolean runNext() {
        Scheduled next;
        do {
            next = queue.poll();
        } while (next != null && next.cancelled);
        if (next == null) return false;
        nowMs = next.whenMs;
        next.action.run();
        return true;
    }

    public int runUntilIdle(int eventLimit) {
        int count = 0;
        while (count < eventLimit && runNext()) count++;
        if (count == eventLimit && !queue.isEmpty()) throw new IllegalStateException("Limite de eventos virtuais atingido.");
        return count;
    }

    public interface Ticket { void cancel(); }

    private static final class Scheduled implements Comparable<Scheduled> {
        private final long whenMs;
        private final long sequence;
        private final Runnable action;
        private boolean cancelled;
        private Scheduled(long whenMs, long sequence, Runnable action) {
            this.whenMs = whenMs; this.sequence = sequence; this.action = action;
        }
        @Override public int compareTo(Scheduled other) {
            int byTime = Long.compare(whenMs, other.whenMs);
            return byTime != 0 ? byTime : Long.compare(sequence, other.sequence);
        }
    }
}
