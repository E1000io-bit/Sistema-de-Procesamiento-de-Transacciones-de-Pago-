package tp;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class PetriNetTest {

    // Misma red de la Figura 1 pero sin tiempos y con objetivo chico, para tests rápidos
    private static NetConfig untimed(int target) {
        NetConfig c = NetConfig.paymentServiceProvider();
        return new NetConfig(c.getPre(), c.getPost(), c.getInitialMarking(), new long[10],
                c.getPlaceInvariants(), c.getTransitionInvariants(),
                c.getCompletionTransition(), target, c.getPriorityOrder());
    }

    private static void fireAll(PetriNet net, int... ts) {
        for (int t : ts) {
            net.fire(t);
            net.checkPlaceInvariants();
        }
    }

    @Test
    void marcadoInicialYSensibilizadas() {
        PetriNet net = new PetriNet(NetConfig.paymentServiceProvider());
        assertArrayEquals(new int[]{3, 0, 0, 0, 0, 0, 0, 1, 1, 0}, net.getMarking());
        assertEquals(10, net.getTransitionCount());

        boolean[] vs = net.vectorEnable();
        for (int t = 0; t < 10; t++) {
            assertEquals(t == 0, vs[t], "T" + t);
        }
    }

    @Test
    void losTresInvariantesDeTransicionVuelvenAlMarcadoInicial() {
        PetriNet net = new PetriNet(untimed(200));
        int[] m0 = net.getMarking();
        for (TransitionInvariant inv : NetConfig.paymentServiceProvider().getTransitionInvariants()) {
            fireAll(net, inv.getSequence());
            assertArrayEquals(m0, net.getMarking(), inv.getName());
        }
        assertEquals(3, net.getCompletedInvariants());
        assertArrayEquals(new int[]{3, 1, 1, 1, 1, 1, 1, 1, 1, 3}, net.getFirings());
    }

    @Test
    void recursosCompartidosSeExcluyen() {
        PetriNet net = new PetriNet(untimed(200));
        fireAll(net, 0, 0, 0, 1);              // tarjeta tomó el gateway (P7)
        assertFalse(net.isEnable(1));
        assertFalse(net.isEnable(4));          // alto riesgo necesita P7 y P8
        assertTrue(net.isEnable(6));           // transferencia solo necesita P8
        fireAll(net, 6);
        assertFalse(net.isEnable(4));
        assertFalse(net.isEnable(6));
    }

    @Test
    void dispararNoHabilitadaFalla() {
        PetriNet net = new PetriNet(untimed(200));
        int[] before = net.getMarking();
        assertThrows(IllegalStateException.class, () -> net.fire(1));
        assertArrayEquals(before, net.getMarking());
        assertThrows(IllegalArgumentException.class, () -> net.isEnable(10));
    }

    @Test
    void terminaAlCompletarElObjetivo() {
        PetriNet net = new PetriNet(untimed(2));
        fireAll(net, 0, 4, 5, 9);
        assertFalse(net.isFinished());
        fireAll(net, 0, 6, 7, 8, 9);
        assertTrue(net.isFinished());
    }

    @Test
    void invarianteDePlazaRotoTiraExcepcion() {
        NetConfig c = NetConfig.paymentServiceProvider();
        List<PlaceInvariant> wrong = List.of(new PlaceInvariant("Mal", new int[]{1, 0, 0, 0, 0, 0, 0, 0, 0, 0}, 3));
        NetConfig cfg = new NetConfig(c.getPre(), c.getPost(), c.getInitialMarking(), new long[10],
                wrong, c.getTransitionInvariants(), 9, 200, new int[]{4});
        PetriNet net = new PetriNet(cfg);
        net.fire(0);                            // P0 baja a 2
        assertThrows(PlaceInvariantException.class, net::checkPlaceInvariants);
    }

    @Test
    void transicionTemporalEsperaSuAlfa() throws InterruptedException {
        NetConfig c = NetConfig.paymentServiceProvider();
        long[] alpha = new long[10];
        alpha[2] = 80;
        NetConfig cfg = new NetConfig(c.getPre(), c.getPost(), c.getInitialMarking(), alpha,
                c.getPlaceInvariants(), c.getTransitionInvariants(), 9, 200, new int[]{4});
        PetriNet net = new PetriNet(cfg);

        assertEquals(0, net.timeToWait(2));     // sin marcado: no hay nada que esperar
        fireAll(net, 0, 1);                     // T2 queda sensibilizada por marcado

        assertTrue(net.vectorEnable()[2]);      // Vs no mira el tiempo
        assertFalse(net.isEnable(2));           // todavía no pasó α
        long wait = net.timeToWait(2);
        assertTrue(wait > 0 && wait <= 80, "timeToWait = " + wait);
        assertThrows(IllegalStateException.class, () -> net.fire(2));

        Thread.sleep(wait + 5);
        assertTrue(net.isEnable(2));
        assertEquals(0, net.timeToWait(2));
        net.fire(2);
    }

    @Test
    void netConfigNoSeModificaDesdeAfuera() {
        NetConfig c = NetConfig.paymentServiceProvider();
        c.getPre()[0][0] = 99;
        c.getInitialMarking()[0] = 99;
        assertEquals(1, c.getPre()[0][0]);
        assertEquals(3, c.getInitialMarking()[0]);
        assertThrows(UnsupportedOperationException.class, () -> c.getPlaceInvariants().clear());
    }

    @Test
    void netConfigValidaDimensiones() {
        NetConfig c = NetConfig.paymentServiceProvider();
        assertThrows(IllegalArgumentException.class, () -> new NetConfig(c.getPre(), c.getPost(),
                new int[]{3, 0, 0}, c.getAlpha(), c.getPlaceInvariants(), c.getTransitionInvariants(),
                9, 200, new int[]{4}));
    }

    @Test
    void regexDelInvariante() {
        TransitionInvariant inv = new TransitionInvariant("AltoRiesgo", new int[]{0, 4, 5, 9});
        assertTrue("T0T1T4T2T5T3T9".matches(".*" + inv.toRegex() + ".*"));
        assertFalse("T0T10T5T9".matches(".*" + inv.toRegex() + ".*"));
    }
}
