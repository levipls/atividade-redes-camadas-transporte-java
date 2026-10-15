package br.edu.redes.simulador.fsm;

public record FsmTransition(String state, String event, String action, String nextState) { }
