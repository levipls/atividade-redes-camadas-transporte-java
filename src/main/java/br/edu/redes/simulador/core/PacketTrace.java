package br.edu.redes.simulador.core;

public record PacketTrace(long timeMs, String protocol, String from, String to, String label,
                          int sequence, int acknowledgement, boolean lost, boolean corrupted,
                          boolean retransmission, boolean duplicate) { }
