package tp;

public interface Policy {

    /** Elige UNA transición entre las habilitadas.
     * @param m vector m = Vs AND Vc (m[t] == true si t está sensibilizada, la política la deja disparar y tiene hilos esperando)
     * @return índice de la transición elegida */

    int choose(boolean[] m);

    /**
     * Decide qué transiciones sensibilizadas se pueden disparar en este momento.
     * El monitor lo usa antes de disparar y al armar m: una transición que la política frena
     * se trata como no sensibilizada (su hilo espera en la cola hasta que lo despierten).
     * @param enabled transiciones sensibilizadas por marcado (Vs)
     * @param waiting transiciones con al menos un hilo esperando en su cola (Vc)
     * @return copia de enabled sin las transiciones que la política frena. Por defecto no frena ninguna.
     */
    default boolean[] filter(boolean[] enabled, boolean[] waiting) {
        return enabled.clone();
    }
}
