package tp;

public interface Policy {

    /** Elige UNA transición entre las habilitadas.
     * @param m vector m = Vs AND Vc (m[t] == true si t está sensibilizada, la política la deja disparar y tiene hilos esperando)
     * @return índice de la transición elegida */

    int choose(boolean[] m);
}
