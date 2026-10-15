package br.edu.redes.simulador.core;

public record TransitionLog(long timeMs, String protocol, String host, String previousState,
                            String event, String action, String nextState, boolean accepted, String detail) { }
