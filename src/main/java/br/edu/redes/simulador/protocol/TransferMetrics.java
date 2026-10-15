package br.edu.redes.simulador.protocol;

import java.util.List;

public record TransferMetrics(int sent, int delivered, int retransmissions, int fastRetransmissions,
                              int timeouts, int outOfOrderPackets, double estimatedRttMs,
                              double devRttMs, double timeoutIntervalMs, long fastRetransmitAtMs,
                              long totalTimeMs, List<Integer> deliveredSequence) { }
