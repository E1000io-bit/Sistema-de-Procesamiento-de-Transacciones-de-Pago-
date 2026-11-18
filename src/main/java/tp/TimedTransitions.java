package tp;

import java.util.Arrays;

/*
 Semántica temporal de la red: guarda el α de cada transición y desde cuándo está sensibilizada.
 Ventana de cada transición: [α, ∞) contado desde que quedó sensibilizada (β = ∞).
 α = 0 significa transición inmediata: siempre está en su ventana.

 Los tiempos van en ms, con el mismo reloj que usa PetriNet (now()).
 No tiene sincronización propia: la usa PetriNet, siempre con el mutex del monitor tomado.
*/

public class TimedTransitions {

    private static final long NOT_ENABLED = -1;

    private final long[] alpha;          // ms; 0 = inmediata
    private final long[] enabledSince;   // ms en que quedó sensibilizada; NOT_ENABLED si no lo está

    public TimedTransitions(long[] alpha) {
        for (long a : alpha) {
            if (a < 0) {
                throw new IllegalArgumentException("Los α no pueden ser negativos");
            }
        }
        this.alpha = alpha.clone();
        this.enabledSince = new long[alpha.length];
        Arrays.fill(enabledSince, NOT_ENABLED);
    }

    // true si t tiene α > 0
    public boolean isTimed(int t) {
        return alpha[t] > 0;
    }

    // true si t ya está en su ventana de tiempo (las inmediatas siempre lo están)
    public boolean inWindow(int t, long now) {
        return timeToWait(t, now) == 0;
    }

    /*
     ms que le faltan a t para entrar en su ventana. 0 si es inmediata o si ya pasó α.
     Es > 0 siempre que falte tiempo: como todo está en ms enteros, no hay redondeo que lo deje en 0
     antes de tiempo (si quedara en 0, el Monitor mandaría el hilo a la cola y nadie lo despertaría).
    */
    public long timeToWait(int t, long now) {
        if (!isTimed(t) || enabledSince[t] == NOT_ENABLED) {
            return 0;
        }
        long remaining = alpha[t] - (now - enabledSince[t]);
        return Math.max(0, remaining);
    }

    // Marca que t quedó sensibilizada en el instante now: empieza a contar su α
    public void setEnabledSince(int t, long now) {
        enabledSince[t] = now;
    }

    // t dejó de estar sensibilizada: se borra la marca de tiempo
    public void clear(int t) {
        enabledSince[t] = NOT_ENABLED;
    }
}
