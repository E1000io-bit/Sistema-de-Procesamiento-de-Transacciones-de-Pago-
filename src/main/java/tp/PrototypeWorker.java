package tp;

import java.util.ArrayList;
import java.util.List;

/**
 * PROTOTIPO TEMPORAL - NO VA EN LA VERSIÓN FINAL.
 *
 * Worker genérico para probar el Monitor y el resto del sistema mientras se terminan las
 * clases reales (Worker abstracta + AdmissionWorker, CardWorker, HighRiskWorker,
 * TransferWorker y SettlementWorker). Un solo PrototypeWorker sirve para cualquier hilo:
 * la secuencia de transiciones se le pasa por constructor.
 *
 * Qué hace cada hilo:
 * Repite su secuencia en loop, llamando a monitor.fireTransition(t) para cada transición.
 * Cuando fireTransition devuelve false (la simulación terminó), el hilo sale del loop y
 * termina (req. 3). Al terminar imprime cuántas transiciones disparó, y si el hilo muere
 * por una excepción la imprime con su nombre, así se ve enseguida qué falló.
 *
 * Cómo se usa (desde Main.startWorkers):
 *   return PrototypeWorker.startDefaultThreads(m);
 *
 * Cómo sacarlo cuando estén los Worker reales:
 * 1. En Main.startWorkers, volver al código con las subclases (está comentado ahí mismo).
 * 2. Borrar este archivo.
 *
 * Cosas fáciles de modificar para probar:
 * - THREADS: qué hilos se crean, cuántos de cada uno y con qué secuencia.
 * - VERBOSE: en true imprime cada disparo por consola (sirve para ver el orden, pero son
 *   unas 1000 líneas por corrida).
 * - doTask(t): hoy no hace nada. Si quieren probar la opción de "la tarea se ejecuta en el
 *   Worker, afuera del monitor", acá va el sleep con la duración de la tarea. Ojo: si lo
 *   usan, pongan los α de la red en 0, porque si no el tiempo se cuenta dos veces.
 */
public class PrototypeWorker implements Runnable {

    /** En true imprime cada disparo por consola. */
    private static final boolean VERBOSE = false;

    /**
     * Hilos que crea startDefaultThreads(): nombre, cantidad y secuencia de transiciones.
     * Son los 7 hilos del análisis de la red: T0 x1 antes del conflicto, un hilo por flujo y
     * T9 x3 después del join en P9. Los nombres son los mismos que usa Main, así el log
     * queda igual que con los Worker reales.
     */
    private static final Object[][] THREADS = {
        // nombre           cantidad  secuencia
        {"Admision",        1,        new int[] {0}},
        {"Tarjeta",         1,        new int[] {1, 2, 3}},
        {"AltoRiesgo",      1,        new int[] {4, 5}},
        {"Transferencia",   1,        new int[] {6, 7, 8}},
        {"Liquidacion",     3,        new int[] {9}},
    };

    private final MonitorInterface monitor;
    private final int[] sequence;

    public PrototypeWorker(MonitorInterface monitor, int[] sequence) {
        this.monitor = monitor;
        this.sequence = sequence;
    }

    @Override
    public void run() {
        String name = Thread.currentThread().getName();
        int fired = 0;
        try {
            while (true) {
                for (int t : sequence) {
                    if (!monitor.fireTransition(t)) {
                        // La simulación terminó: el hilo sale y termina.
                        System.out.println("[prototipo] " + name + " termino despues de " + fired + " disparos");
                        return;
                    }
                    fired++;
                    if (VERBOSE) {
                        System.out.println("[prototipo] " + name + " disparo T" + t);
                    }
                    doTask(t);
                }
            }
        } catch (RuntimeException e) {
            // Si un hilo muere, que se vea cuál y por qué (por ejemplo, un invariante de plaza violado).
            System.out.println("[prototipo] " + name + " murio con una excepcion despues de " + fired + " disparos:");
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * Tarea que se hace después de disparar t, afuera del monitor (sin el mutex).
     * Hoy no hace nada: la duración de las tareas la da el α de las transiciones temporales.
     * Para probar la otra opción, por ejemplo: if (t == 1) sleep(100);
     */
    private void doTask(int t) {
        // vacío a propósito
    }

    /**
     * Crea y arranca los hilos definidos en THREADS. Si hay más de uno con el mismo nombre,
     * les agrega un número: Liquidacion-1, Liquidacion-2, Liquidacion-3.
     */
    public static List<Thread> startDefaultThreads(MonitorInterface m) {
        List<Thread> threads = new ArrayList<>();
        for (Object[] def : THREADS) {
            String name = (String) def[0];
            int count = (Integer) def[1];
            int[] sequence = (int[]) def[2];
            for (int i = 1; i <= count; i++) {
                String threadName = count > 1 ? name + "-" + i : name;
                threads.add(new Thread(new PrototypeWorker(m, sequence), threadName));
            }
        }
        for (Thread t : threads) {
            t.start();
        }
        return threads;
    }
}
