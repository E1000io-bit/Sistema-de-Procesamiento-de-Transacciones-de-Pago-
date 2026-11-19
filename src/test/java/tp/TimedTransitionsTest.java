package tp;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/*
 Semántica temporal (apartado Tiempo): ventana [α, ∞) desde que la transición quedó sensibilizada.
 Se usa un reloj inventado (now) para que el test no dependa del tiempo real.
*/
class TimedTransitionsTest {

    private final TimedTransitions timed = new TimedTransitions(new long[]{0, 100});

    @Test
    void lasInmediatasSiempreEstanEnSuVentana() {
        timed.setEnabledSince(0, 1_000);
        assertFalse(timed.isTimed(0));
        assertTrue(timed.inWindow(0, 1_000));
        assertEquals(0, timed.timeToWait(0, 1_000));
    }

    @Test
    void unaTemporalEsperaSuAlfaDesdeQueQuedoSensibilizada() {
        timed.setEnabledSince(1, 1_000);
        assertTrue(timed.isTimed(1));

        assertFalse(timed.inWindow(1, 1_000));
        assertEquals(100, timed.timeToWait(1, 1_000));

        assertFalse(timed.inWindow(1, 1_099));
        assertEquals(1, timed.timeToWait(1, 1_099), "A 1 ms del final tiene que esperar 1, no 0");

        assertTrue(timed.inWindow(1, 1_100));
        assertEquals(0, timed.timeToWait(1, 1_100));
        assertTrue(timed.inWindow(1, 5_000), "β = ∞: después de α siempre se puede");
    }

    @Test
    void siSeVuelveASensibilizarEmpiezaACuentaDeNuevo() {
        timed.setEnabledSince(1, 1_000);
        timed.setEnabledSince(1, 2_000);
        assertEquals(100, timed.timeToWait(1, 2_000));
    }

    @Test
    void clearBorraLaMarcaDeTiempo() {
        timed.setEnabledSince(1, 1_000);
        timed.clear(1);
        assertEquals(0, timed.timeToWait(1, 1_000));
    }

    @Test
    void losTiemposDeLaRedSonLosDeLaConsigna() {
        long[] alpha = NetConfig.paymentServiceProvider().getAlpha();
        for (int t = 0; t < alpha.length; t++) {
            boolean timedInTp = t == 2 || t == 3 || t == 5 || t == 7 || t == 8;
            assertEquals(timedInTp, alpha[t] > 0, "La consigna dice que T2, T3, T5, T7 y T8 son temporales (T" + t + ")");
        }
    }

    @Test
    void alfaNegativoNoSePuede() {
        assertThrows(IllegalArgumentException.class, () -> new TimedTransitions(new long[]{-1}));
    }
}
