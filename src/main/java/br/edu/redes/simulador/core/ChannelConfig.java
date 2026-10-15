package br.edu.redes.simulador.core;

/** Immutable parameters for the virtual network channel. */
public record ChannelConfig(
        int lossPercent,
        int minDelayMs,
        int maxDelayMs,
        int corruptionPercent,
        int duplicationPercent,
        int reorderingPercent,
        int timeWaitMs,
        long seed,
        boolean dropNextPacket) {

    public ChannelConfig {
        checkPercent("lossPercent", lossPercent);
        checkPercent("corruptionPercent", corruptionPercent);
        checkPercent("duplicationPercent", duplicationPercent);
        checkPercent("reorderingPercent", reorderingPercent);
        if (minDelayMs < 0 || maxDelayMs < minDelayMs) {
            throw new IllegalArgumentException("O atraso máximo deve ser maior ou igual ao mínimo.");
        }
        if (timeWaitMs < 1) throw new IllegalArgumentException("TIME_WAIT deve durar ao menos 1 ms.");
    }

    private static void checkPercent(String name, int value) {
        if (value < 0 || value > 100) throw new IllegalArgumentException(name + " deve estar entre 0 e 100.");
    }

    public static ChannelConfig defaults() {
        return new ChannelConfig(0, 1, 1, 0, 0, 0, 80, 2025L, false);
    }

    public ChannelConfig withDropNextPacket(boolean value) {
        return new ChannelConfig(lossPercent, minDelayMs, maxDelayMs, corruptionPercent,
                duplicationPercent, reorderingPercent, timeWaitMs, seed, value);
    }
}
