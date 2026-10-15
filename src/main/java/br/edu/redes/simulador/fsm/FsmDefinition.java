package br.edu.redes.simulador.fsm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Reads the compact, data-driven FSM table: state|event|action|next-state. */
public record FsmDefinition(List<FsmTransition> transitions, Set<String> states) {
    public static FsmDefinition loadResource(String resourcePath) {
        InputStream input = FsmDefinition.class.getResourceAsStream(resourcePath);
        if (input == null) throw new IllegalArgumentException("Definição FSM não encontrada: " + resourcePath);
        return parse(input);
    }

    public static FsmDefinition parse(InputStream input) {
        List<FsmTransition> transitions = new ArrayList<>();
        Set<String> states = new LinkedHashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                String[] fields = trimmed.split("\\|", -1);
                if (fields.length != 4) throw new IllegalArgumentException("Linha FSM inválida " + lineNumber + ": " + line);
                FsmTransition transition = new FsmTransition(fields[0].trim(), fields[1].trim(), fields[2].trim(), fields[3].trim());
                if (transitions.stream().anyMatch(t -> t.state().equals(transition.state()) && t.event().equals(transition.event()))) {
                    throw new IllegalArgumentException("Transição duplicada em " + transition.state() + " para " + transition.event());
                }
                transitions.add(transition);
                states.add(transition.state());
                states.add(transition.nextState());
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao ler a definição da FSM.", ex);
        }
        if (transitions.isEmpty()) throw new IllegalArgumentException("Definição FSM sem transições.");
        return new FsmDefinition(List.copyOf(transitions), Set.copyOf(states));
    }
}
