package tp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/*
 Ayuda para los tests (no es un test). Arma la simulación igual que Main, pero deja a mano
 todo lo que los tests necesitan revisar: la red, el log, el tiempo, los hilos que quedaron
 vivos y las excepciones que tiraron los hilos.
*/
final class SimulationRunner {

    // Cuánto esperamos a que terminen los hilos antes de declarar que la simulación se colgó
    static final long JOIN_TIMEOUT_MS = 60_000;

    private SimulationRunner() {
    }

    // Resultado de una corrida
    static final class Result {
        final PetriNet net;
        final Path log;
        final long elapsedMs;
        final List<Thread> threads;
        final List<Thread> stillAlive;
        final List<Throwable> errors;

        Result(PetriNet net, Path log, long elapsedMs, List<Thread> threads,
               List<Thread> stillAlive, List<Throwable> errors) {
            this.net = net;
            this.log = log;
            this.elapsedMs = elapsedMs;
            this.threads = threads;
            this.stillAlive = stillAlive;
            this.errors = errors;
        }

        List<String> logLines() throws IOException {
            return Files.readAllLines(log);
        }
    }

    /*
     3 antes del conflicto (P1 llega a 3), 1 por flujo y 3 después del join (P9 llega a 3).
    */
    static Result runPaymentNet(NetConfig cfg, Policy policy, Path logDir) throws Exception {
        return run(cfg, policy, logDir, m -> List.of(
                new Thread(new AdmissionWorker(m), "Admision-1"),
                new Thread(new AdmissionWorker(m), "Admision-2"),
                new Thread(new AdmissionWorker(m), "Admision-3"),
                new Thread(new CardWorker(m), "Tarjeta"),
                new Thread(new HighRiskWorker(m), "AltoRiesgo"),
                new Thread(new TransferWorker(m), "Transferencia"),
                new Thread(new SettlementWorker(m), "Liquidacion-1"),
                new Thread(new SettlementWorker(m), "Liquidacion-2"),
                new Thread(new SettlementWorker(m), "Liquidacion-3")));
    }

    interface ThreadFactory {
        List<Thread> create(MonitorInterface m);
    }

    /*
     Corre cualquier red con los hilos que arme la fábrica. Inicializa y cierra el
     TransitionLogger (es estático, por eso los tests que lo usan no corren en paralelo).
    */
    static Result run(NetConfig cfg, Policy policy, Path logDir, ThreadFactory factory) throws Exception {
        PetriNet net = new PetriNet(cfg);
        TransitionLogger.init(logDir, "transiciones.log");
        Monitor monitor = new Monitor(net, policy);

        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        List<Thread> threads = factory.create(monitor);
        for (Thread t : threads) {
            t.setUncaughtExceptionHandler((th, e) -> errors.add(e));
        }

        long start = System.nanoTime();
        for (Thread t : threads) {
            t.start();
        }
        long deadline = System.currentTimeMillis() + JOIN_TIMEOUT_MS;
        for (Thread t : threads) {
            t.join(Math.max(1, deadline - System.currentTimeMillis()));
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        List<Thread> alive = new ArrayList<>();
        for (Thread t : threads) {
            if (t.isAlive()) {
                alive.add(t);
            }
        }
        TransitionLogger.close();
        return new Result(net, logDir.resolve("transiciones.log"), elapsedMs, threads, alive, errors);
    }

    /*
     La misma red de la Figura 1 pero con otros tiempos y otro objetivo, para tests rápidos.
    */
    static NetConfig paymentNet(long[] alpha, int target) {
        NetConfig c = NetConfig.paymentServiceProvider();
        return new NetConfig(c.getPre(), c.getPost(), c.getInitialMarking(), alpha,
                c.getPlaceInvariants(), c.getTransitionInvariants(),
                c.getCompletionTransition(), target, c.getPriorityOrder());
    }

    // Un Worker cualquiera con la secuencia que se le pase (para redes de prueba)
    static Worker worker(MonitorInterface m, int... sequence) {
        return new Worker(m) {
            @Override
            protected int[] getSequence() {
                return sequence.clone();
            }
        };
    }
}
