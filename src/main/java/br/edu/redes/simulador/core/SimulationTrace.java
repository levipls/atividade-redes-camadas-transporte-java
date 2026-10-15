package br.edu.redes.simulador.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SimulationTrace {
    private final List<TransitionLog> transitions = new ArrayList<>();
    private final List<PacketTrace> packets = new ArrayList<>();
    private final Map<String, Object> metrics = new LinkedHashMap<>();

    public List<TransitionLog> transitions() { return transitions; }
    public List<PacketTrace> packets() { return packets; }
    public Map<String, Object> metrics() { return metrics; }
    public void addMetric(String key, Object value) { metrics.put(key, value); }
}
