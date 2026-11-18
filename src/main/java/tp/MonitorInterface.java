package tp;

/**
 * Interfaz del monitor de concurrencia, tal como la pide la consigna.
 */
public interface MonitorInterface {

    /**
     * Intenta disparar la transición indicada. Si no está sensibilizada, el hilo
     * se bloquea hasta que pueda dispararla.
     *
     * @param transition índice de la transición a disparar
     * @return true si la transición se disparó; false si la simulación ya terminó
     *         y el hilo tiene que dejar de pedir disparos
     */
    boolean fireTransition(int transition);
}
