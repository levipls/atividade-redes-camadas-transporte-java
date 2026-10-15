package br.edu.redes.simulador.fsm;

import br.edu.redes.simulador.core.SimulationTrace;
import br.edu.redes.simulador.core.TransitionLog;
import java.util.List;

/** Generic FSM interpreter. Transition behavior is loaded from a definition file. */
public final class GenericFsm {
    private final String protocol;
    private final String host;
    private final FsmDefinition definition;
    private final SimulationTrace trace;
    private String state;

    public GenericFsm(String protocol, String host, String initialState, FsmDefinition definition, SimulationTrace trace) {
        this.protocol = protocol;
        this.host = host;
        this.state = initialState;
        this.definition = definition;
        this.trace = trace;
        if (!definition.states().contains(initialState)) throw new IllegalArgumentException("Estado inicial ausente da definição: " + initialState);
    }

    public boolean fire(String event, long nowMs) {
        FsmTransition transition = definition.transitions().stream()
                .filter(t -> t.state().equals(state) && t.event().equals(event)).findFirst().orElse(null);
        if (transition == null) {
            trace.transitions().add(new TransitionLog(nowMs, protocol, host, state, event,
                    "REJEITADO", state, false, "Evento não permitido no estado " + state));
            return false;
        }
        String previous = state;
        state = transition.nextState();
        trace.transitions().add(new TransitionLog(nowMs, protocol, host, previous, event,
                transition.action(), state, true, ""));
        return true;
    }

    public String state() { return state; }
    public String host() { return host; }
    public List<FsmTransition> transitions() { return definition.transitions(); }
    public List<String> stateHistory() {
        return trace.transitions().stream().filter(t -> t.protocol().equals(protocol) && t.host().equals(host) && t.accepted())
                .map(TransitionLog::nextState).toList();
    }
}
