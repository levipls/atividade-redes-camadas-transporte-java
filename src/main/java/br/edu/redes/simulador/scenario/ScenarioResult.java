package br.edu.redes.simulador.scenario;

import br.edu.redes.simulador.core.SimulationTrace;
import java.util.Map;

public record ScenarioResult(int id, String name, boolean passed, String summary,
                             Map<String, Object> metrics, SimulationTrace trace) { }
