package tp;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/*
 Req. 4: las dos políticas. Se prueba qué eligen con distintos vectores m = Vs AND Vc,
 y qué transiciones deja disparar cada una (filter).
 Las transiciones del conflicto en P1 son T1 (tarjeta), T4 (alto riesgo) y T6 (transferencia).
*/
class PolicyTest {

    private static final int DRAWS = 30_000;
    private static final int[][] PRE = NetConfig.paymentServiceProvider().getPre();

    private static PriorityPolicy priorizada(int... order) {
        return new PriorityPolicy(order, PRE);
    }

    private static boolean[] m(int... enabled) {
        boolean[] m = new boolean[10];
        for (int t : enabled) {
            m[t] = true;
        }
        return m;
    }

    @Test
    void aleatoriaSoloEligeTransicionesDeM() {
        Policy p = new RandomPolicy();
        boolean[] m = m(1, 4, 6);
        for (int i = 0; i < 1000; i++) {
            int t = p.choose(m);
            assertTrue(m[t], "Eligió T" + t + ", que no está en m");
        }
    }

    @Test
    void aleatoriaRepartePareja() {
        Policy p = new RandomPolicy();
        int[] count = new int[10];
        for (int i = 0; i < DRAWS; i++) {
            count[p.choose(m(1, 4, 6))]++;
        }
        for (int t : new int[]{1, 4, 6}) {
            double share = (double) count[t] / DRAWS;
            assertEquals(1.0 / 3, share, 0.03, "T" + t + " salió " + count[t] + " de " + DRAWS);
        }
    }

    @Test
    void priorizadaEligeAltoRiesgoSiempreQuePuede() {
        Policy p = priorizada(NetConfig.paymentServiceProvider().getPriorityOrder());
        for (int i = 0; i < 1000; i++) {
            assertEquals(4, p.choose(m(1, 4, 6)));
            assertEquals(4, p.choose(m(4, 9)));
        }
    }

    @Test
    void priorizadaSinAltoRiesgoEligeAlAzarEntreLasDemas() {
        Policy p = priorizada(4);
        int[] count = new int[10];
        for (int i = 0; i < DRAWS; i++) {
            count[p.choose(m(1, 6))]++;
        }
        assertEquals(0, count[4]);
        assertEquals(DRAWS, count[1] + count[6]);
        assertEquals(0.5, (double) count[1] / DRAWS, 0.03, "T1 vs T6 tendría que ser 50/50");
    }

    @Test
    void sinNadaParaElegirFalla() {
        assertThrows(IllegalArgumentException.class, () -> new RandomPolicy().choose(new boolean[10]));
        assertThrows(IllegalArgumentException.class, () -> priorizada(4).choose(new boolean[10]));
    }

    @Test
    void elOrdenDePrioridadNoSePuedeCambiarDesdeAfuera() {
        int[] order = {4};
        Policy p = priorizada(order);
        order[0] = 1;
        assertEquals(4, p.choose(m(1, 4)));
    }

    // ---- filter: quién puede disparar en este momento ----

    @Test
    void aleatoriaNoFrenaNada() {
        boolean[] enabled = m(0, 1, 6, 9);
        assertArrayEquals(enabled, new RandomPolicy().filter(enabled, m(4)));
    }

    @Test
    void priorizadaFrenaATarjetaYTransferenciaMientrasEsperaAltoRiesgo() {
        // El hilo de alto riesgo espera en la cola de T4: T1 y T6 no pueden tomar los recursos
        boolean[] allowed = priorizada(4).filter(m(0, 1, 6, 9), m(4));
        assertFalse(allowed[1], "T1 tendría que estar frenada");
        assertFalse(allowed[6], "T6 tendría que estar frenada");
        assertTrue(allowed[0], "T0 no compite con T4");
        assertTrue(allowed[9], "T9 no compite con T4");
    }

    @Test
    void priorizadaNoFrenaSiNadieEsperaAltoRiesgo() {
        // T4 sensibilizada pero sin hilo esperando: no se frena a nadie (si no, habría inanición)
        boolean[] enabled = m(1, 4, 6);
        assertArrayEquals(enabled, priorizada(4).filter(enabled, m(1, 6)));
    }

    @Test
    void lasCompetidorasSalenDeLaRed() {
        // Con alto riesgo esperando y todo sensibilizado, solo se frenan las que comparten plaza de entrada con T4
        boolean[] all = new boolean[10];
        java.util.Arrays.fill(all, true);
        boolean[] allowed = priorizada(4).filter(all, m(4));
        for (int t = 0; t < 10; t++) {
            assertEquals(t != 1 && t != 6, allowed[t], "T" + t);
        }
    }
}
