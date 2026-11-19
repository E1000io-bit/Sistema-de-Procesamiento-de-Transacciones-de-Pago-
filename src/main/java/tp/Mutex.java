package tp;

import java.util.concurrent.Semaphore;

/*
 Exclusión mutua del monitor: un semáforo binario justo (FIFO).
 Quien llama a acquire() es el único dueño hasta que llame a release().
*/

public class Mutex {

    private final Semaphore semaphore;

    public Mutex() {
        this.semaphore = new Semaphore(1, true); // fair = true: los hilos obtienen el mutex en orden de llegada (evita inanición)
    }

    // Bloquea hasta obtener el mutex. Ininterrumpible: no se pierde el interrupt, el hilo no sale sin el lock. */
    public void acquire() {
        semaphore.acquireUninterruptibly();
    }

    /* Libera el mutex. Si el semáforo ya tenía un permiso disponible significa que
        alguien lo está liberando sin tenerlo: se corta ahí en vez de romper la exclusión mutua en silencio. */
    public void release() {
        if (semaphore.availablePermits() > 0) {
            throw new IllegalStateException("Mutex liberado sin  estar tomado (release de más)");
        }
        semaphore.release();
    }
}