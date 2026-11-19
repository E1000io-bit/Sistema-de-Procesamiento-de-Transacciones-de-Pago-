package tp;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Punto de entrada del programa (req. 2).
 *
 * <p>Uso, desde la raíz del proyecto:
 * <pre>
 *   mvn compile exec:java -Dexec.args="aleatoria"
 *   mvn compile exec:java -Dexec.args="priorizada"
 * </pre>
 * Sin argumento usa la política aleatoria.
 */
public class Main {

    private static final Path LOG_DIR = Path.of("logs");
    private static final String LOG_NAME = "transiciones.log";

    public static void main(String[] args) throws IOException {
        // Si no se pasa ningún argumento, se usa la política aleatoria
        String policyName = "priorizada";
        if (args.length > 0) {
            policyName = args[0];
        }

        // 1. Red, política, log y monitor
        NetConfig cfg = NetConfig.paymentServiceProvider();
        Policy policy = createPolicy(policyName, cfg); //Check
        PetriNet rdp = new PetriNet(cfg);
        TransitionLogger.init(LOG_DIR, LOG_NAME);
        Monitor monitor = new Monitor(rdp, policy);

        // 2. Lanzar los 7 hilos y esperar a que terminen todos (req. 3)
        System.out.println("Politica: " + policyName);
        long start = System.nanoTime();
        List<Thread> threads = startWorkers(monitor);
        joinAll(threads);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        TransitionLogger.close();
        System.out.println("Terminaron los " + threads.size() + " hilos.");

        // 3. Análisis de los invariantes de transición sobre el log, con regex (req. 8 y 13)
        InvariantAnalyzer analyzer = new InvariantAnalyzer(cfg.getTransitionInvariants());
        InvariantReport report = analyzer.analyze(LOG_DIR.resolve(LOG_NAME));
        printResults(report, elapsedMs);
    }

    /** Crea la política pedida por argumento: aleatoria o priorizada (req. 4). */
    private static Policy createPolicy(String name, NetConfig cfg) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "aleatoria", "random" -> new RandomPolicy();
            case "priorizada", "priority" -> new PriorityPolicy(cfg.getPriorityOrder());
            default -> throw new IllegalArgumentException(
                    "Politica desconocida: " + name + " (usar aleatoria o priorizada)");
        };
    }

    /**
     * Crea y arranca los 7 hilos: uno antes del conflicto, uno por cada flujo y tres después
     * del join en P9. El nombre de cada Thread es el que queda registrado en el log.
     */
    private static List<Thread> startWorkers(MonitorInterface m) {
        List<Thread> threads = new ArrayList<>();
        threads.add(new Thread(new SettlementWorker(m), "Liquidacion-"));
        threads.add(new Thread(new CardWorker(m), "Tarjeta"));
        threads.add(new Thread(new HighRiskWorker(m), "AltoRiesgo"));
        threads.add(new Thread(new TransferWorker(m), "Transferencia"));
        for (int i = 1; i <= 3; i++) {
            threads.add(new Thread(new AdmissionWorker(m), "Admision" + i));
        }
        for (Thread t : threads) {
            t.start();
        }
        return threads;
    }

    /** Espera a que terminen todos los hilos. */
    private static void joinAll(List<Thread> threads) {
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** Muestra el tiempo de ejecución (req. 10) y la cantidad de cada invariante (req. 8). */
    private static void printResults(InvariantReport report, long elapsedMs) {
        System.out.printf("Tiempo de ejecucion: %.2f s%n", elapsedMs / 1000.0);
        if (elapsedMs < 20_000 || elapsedMs > 40_000) {
            System.out.println("  Atencion: el req. 10 pide entre 20 y 40 s.");
        }
        System.out.println("Invariantes de transicion completos: " + report.getTotal());
        System.out.println(report);
        String leftover = report.getLeftover();
        System.out.println("Sobrante del log: " + (leftover.isEmpty() ? "(vacio)" : leftover));
    }
}
