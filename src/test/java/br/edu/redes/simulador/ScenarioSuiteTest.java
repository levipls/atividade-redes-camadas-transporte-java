package br.edu.redes.simulador;

import br.edu.redes.simulador.core.ChannelConfig;
import br.edu.redes.simulador.protocol.UdpDatagram;
import br.edu.redes.simulador.fsm.FsmDefinition;
import br.edu.redes.simulador.fsm.GenericFsm;
import br.edu.redes.simulador.core.SimulationTrace;
import br.edu.redes.simulador.scenario.ScenarioResult;
import br.edu.redes.simulador.scenario.ScenarioRunner;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScenarioSuiteTest {
    private final ScenarioRunner runner = new ScenarioRunner();
    private final ChannelConfig config = ChannelConfig.defaults();

    @Test void cenario01HandshakeSemFalhas() {
        ScenarioResult r = runner.run(1, config);
        assertTrue(r.passed(), r.summary());
        assertTrue(r.metrics().get("connected").equals(true));
    }

    @Test void cenario02PerdaDoSynRetransmite() {
        ScenarioResult r = runner.run(2, config);
        assertTrue(r.passed(), r.summary());
        assertTrue(((Number) r.metrics().get("synRetransmissions")).intValue() >= 1);
    }

    @Test void cenario03PerdaDoSynAckRecupera() {
        ScenarioResult r = runner.run(3, config);
        assertTrue(r.passed(), r.summary());
        assertTrue(((Number) r.metrics().get("serverRepeatedSynAck")).intValue() >= 1);
    }

    @Test void cenario04EncerramentoNormalPassaPorTimeWait() {
        ScenarioResult r = runner.run(4, config);
        assertTrue(r.passed(), r.summary());
        assertEquals(1, r.metrics().get("timeWaitExpirations"));
    }

    @Test void cenario05FechamentoSimultaneoUsaClosing() {
        ScenarioResult r = runner.run(5, config);
        assertTrue(r.passed(), r.summary());
        assertEquals(2, r.metrics().get("timeWaitExpirations"));
    }

    @Test void cenario06PerdaDeDadosAcionaFastRetransmit() {
        ScenarioResult r = runner.run(6, config);
        assertTrue(r.passed(), r.summary());
        assertEquals(10, r.metrics().get("delivered"));
        assertEquals(1, r.metrics().get("fastRetransmissions"));
        assertEquals(0, r.metrics().get("timeouts"));
        assertEquals(98L, r.metrics().get("savedMs"));
    }

    @Test void cenario07PortaFechadaRespondeRst() {
        ScenarioResult r = runner.run(7, config);
        assertTrue(r.passed(), r.summary());
        assertEquals(true, r.metrics().get("rstReceived"));
    }

    @Test void cenario08UdpPerdeDatagramasSemRetransmitir() {
        ScenarioResult r = runner.run(8, config);
        assertTrue(r.passed(), r.summary());
        assertEquals(20, r.metrics().get("sent"));
        assertEquals(0, r.metrics().get("retransmissions"));
    }

    @Test void cenario09UdpDescartaChecksumCorrompido() {
        ScenarioResult r = runner.run(9, config);
        assertTrue(r.passed(), r.summary());
        assertEquals(1, r.metrics().get("checksumDrops"));
        assertEquals(0, r.metrics().get("delivered"));
    }

    @Test void cenario10ComparaUdpETcpComMesmoCanal() {
        ScenarioResult r = runner.run(10, config);
        assertTrue(r.passed(), r.summary());
        @SuppressWarnings("unchecked") var tcp = (java.util.Map<String, Object>) r.metrics().get("tcp");
        assertEquals(10, tcp.get("delivered"));
    }

    @Test void checksumUdpDetectaAlteracaoDoPayload() {
        UdpDatagram datagram = UdpDatagram.create(0x0a000001, 0x0a000002, 5000, 9000, "rede");
        assertTrue(datagram.checksumValid());
        assertFalse(datagram.corruptPayload().checksumValid());
        assertEquals(12, datagram.length());
    }

    @Test void motorRejeitaEventoInvalidoERegistraMotivo() {
        SimulationTrace trace = new SimulationTrace();
        FsmDefinition definition = FsmDefinition.loadResource("/fsm/udp.fsm");
        GenericFsm fsm = new GenericFsm("UDP", "A", "UDP_READY", definition, trace);
        assertFalse(fsm.fire("APP_CLOSE_TCP", 0));
        assertEquals("UDP_READY", fsm.state());
        assertFalse(trace.transitions().getFirst().accepted());
        assertTrue(trace.transitions().getFirst().detail().contains("Evento não permitido"));
    }

    @Test void cenarioUdpComSeedEReprodutivel() {
        ScenarioResult first = runner.run(8, config);
        ScenarioResult second = runner.run(8, config);
        assertEquals(first.metrics(), second.metrics());
        assertEquals(first.passed(), second.passed());
    }
}
