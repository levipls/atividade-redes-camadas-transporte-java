package br.edu.redes.simulador.scenario;

import br.edu.redes.simulador.core.ChannelConfig;
import java.util.List;

/** Executa os cenários sem iniciar a interface gráfica. */
public final class ScenarioCli {
    private ScenarioCli() { }
    public static void main(String[] args) {
        List<ScenarioResult> results = new ScenarioRunner().runAll(ChannelConfig.defaults());
        results.forEach(r -> System.out.printf("%02d | %-7s | %s%n", r.id(), r.passed() ? "APROVADO" : "REPROVADO", r.name()));
        long passed = results.stream().filter(ScenarioResult::passed).count();
        System.out.printf("Resultado: %d/10 cenários aprovados.%n", passed);
        if (passed != 10) System.exit(1);
    }

}
