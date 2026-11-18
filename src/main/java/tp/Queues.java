package tp;

import java.util.concurrent.Semaphore;


/*
 Colas de espera del monitor: una cola (semáforo en 0) por transición.
 Contrato: register(), awake(), someOneThere() y awakeAll() se llaman siempre con el mutex del monitor tomado,
           por eso waiting[] no necesita sincronización propia.
 Solo await() se llama sin el mutex (el hilo ya lo soltó y se bloquea aquí).
*/

public class Queues {

    private final Semaphore[] queues;
    private final int[] waiting;

    public Queues(int transitions) {
        this.queues = new Semaphore[transitions];
        this.waiting = new int[transitions];
        for (int t = 0; t < transitions; t++) {
            queues[t] = new Semaphore(0, true); // FIFO dentro de cada transición
        }
    }

    // Anota al hilo como esperando en t. Se hace con el mutex tomado, antes de soltarlo
    public void register(int t) {
        waiting[t]++;
    }

    /* Bloquea el hilo en la cola de t. Se llama después de soltar el mutex.
        Si awake(t) ya ocurrió, el permiso quedó guardado en el semáforo y no se bloquea (no hay wakeup perdido) */
    public void await(int t) {
        queues[t].acquireUninterruptibly();
    }

    // Despierta a 1 hilo de la cola de t y lo saca de waiting
    public void awake(int t) {
        if (waiting[t] <= 0) {
            throw new IllegalStateException("awake(" + t + ") sin hilos esperando");
        }
        waiting[t]--;
        queues[t].release();
    }

    // vc[t] da true si hay al menos un hilo esperando en la cola de t
    public boolean[] someOneThere() {
        boolean[] vc = new boolean[waiting.length];
        for (int t = 0; t < waiting.length; t++) {
            vc[t] = waiting[t] > 0;
        }
        return vc;
    }

    // Despierta a todos los hilos de todas las colas (se usa al terminar la ejecución)
    public void awakeAll() {
        for (int t = 0; t < waiting.length; t++) {
            while (waiting[t] > 0) {
                awake(t);
            }
        }
    }
}