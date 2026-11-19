package tp;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/*
 Las piezas de sincronización del monitor: Mutex (exclusión mutua) y Queues (una cola por transición).
*/
class SynchronizationTest {

    // ------------------------------------------------------------------ Mutex

    @Test
    void elMutexDejaEntrarDeAUno() throws Exception {
        Mutex mutex = new Mutex();
        mutex.acquire();
        Thread other = new Thread(() -> {
            mutex.acquire();
            mutex.release();
        });
        other.start();
        waitUntilBlocked(other);              // no puede entrar mientras lo tengo yo
        mutex.release();
        other.join(2_000);
        assertFalse(other.isAlive(), "Después del release el otro hilo tendría que haber entrado");
    }

    @Test
    void liberarElMutexSinTenerloFalla() {
        Mutex mutex = new Mutex();
        assertThrows(IllegalStateException.class, mutex::release);
        mutex.acquire();
        mutex.release();
        assertThrows(IllegalStateException.class, mutex::release);
    }

    // ------------------------------------------------------------------ Queues

    @Test
    void someOneThereMuestraQuienEstaEsperando() {
        Queues q = new Queues(10);
        assertArrayEquals(new boolean[10], q.someOneThere());
        q.register(4);
        q.register(9);
        q.register(9);
        boolean[] vc = q.someOneThere();
        for (int t = 0; t < 10; t++) {
            assertEquals(t == 4 || t == 9, vc[t], "T" + t);
        }
        q.awake(9);
        assertTrue(q.someOneThere()[9], "Queda otro hilo en la cola de T9");
        q.awake(9);
        assertFalse(q.someOneThere()[9]);
    }

    @Test
    void unAwakeAntesDelAwaitNoSePierde() throws Exception {
        // El hilo se anota y suelta el mutex, pero el awake llega antes de que se bloquee:
        // el permiso queda en el semáforo y el await pasa de largo
        Queues q = new Queues(10);
        q.register(2);
        q.awake(2);
        Thread t = new Thread(() -> q.await(2));
        t.start();
        t.join(2_000);
        assertFalse(t.isAlive(), "El await se quedó bloqueado: se perdió el awake");
    }

    @Test
    void awakeDespiertaAUnSoloHilo() throws Exception {
        Queues q = new Queues(10);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            q.register(9);
            Thread t = new Thread(() -> q.await(9));
            threads.add(t);
            t.start();
        }
        for (Thread t : threads) {
            waitUntilBlocked(t);
        }
        q.awake(9);
        Thread.sleep(200);
        long awake = threads.stream().filter(t -> !t.isAlive()).count();
        assertEquals(1, awake, "awake(t) tiene que despertar a un solo hilo");
        q.awakeAll();
        for (Thread t : threads) {
            t.join(2_000);
            assertFalse(t.isAlive());
        }
    }

    @Test
    void awakeAllDespiertaATodasLasColas() throws Exception {
        Queues q = new Queues(10);
        List<Thread> threads = new ArrayList<>();
        for (int t : new int[]{0, 1, 4, 6, 9, 9, 9}) {
            q.register(t);
            Thread th = new Thread(() -> q.await(t));
            threads.add(th);
            th.start();
        }
        q.awakeAll();
        for (Thread th : threads) {
            th.join(2_000);
            assertFalse(th.isAlive(), "awakeAll dejó un hilo dormido");
        }
        assertArrayEquals(new boolean[10], q.someOneThere());
    }

    @Test
    void despertarUnaColaVaciaFalla() {
        assertThrows(IllegalStateException.class, () -> new Queues(10).awake(3));
    }

    static void waitUntilBlocked(Thread t) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2_000;
        while (t.getState() != Thread.State.WAITING && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(Thread.State.WAITING, t.getState());
    }
}
