package tp;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/*
 Los hilos (req. 7): qué secuencia dispara cada Worker y que cortan cuando la simulación termina.
*/
class WorkerTest {

    // Monitor de mentira: anota lo que le piden y dice "terminó" después de n disparos
    private static final class FakeMonitor implements MonitorInterface {
        final List<Integer> calls = new ArrayList<>();
        final int limit;

        FakeMonitor(int limit) {
            this.limit = limit;
        }

        @Override
        public boolean fireTransition(int transition) {
            calls.add(transition);
            return calls.size() <= limit;
        }
    }

    @Test
    void cadaWorkerTieneLaSecuenciaDeSuParteDeLaRed() {
        MonitorInterface m = new FakeMonitor(0);
        Map<String, int[]> expected = Map.of(
                "AdmissionWorker", new int[]{0},
                "CardWorker", new int[]{1, 2, 3},
                "HighRiskWorker", new int[]{4, 5},
                "TransferWorker", new int[]{6, 7, 8},
                "SettlementWorker", new int[]{9});
        for (Worker w : List.of(new AdmissionWorker(m), new CardWorker(m), new HighRiskWorker(m),
                new TransferWorker(m), new SettlementWorker(m))) {
            assertArrayEquals(expected.get(w.getClass().getSimpleName()), w.getSequence(), w.getClass().getSimpleName());
        }
    }

    @Test
    void entreTodosLosWorkerCubrenCadaTransicionUnaSolaVez() {
        MonitorInterface m = new FakeMonitor(0);
        int[] owners = new int[10];
        for (Worker w : List.of(new AdmissionWorker(m), new CardWorker(m), new HighRiskWorker(m),
                new TransferWorker(m), new SettlementWorker(m))) {
            for (int t : w.getSequence()) {
                owners[t]++;
            }
        }
        int[] once = new int[10];
        Arrays.fill(once, 1);
        assertArrayEquals(once, owners, "Cada transición tiene que ser responsabilidad de un solo tipo de hilo");
    }

    @Test
    void repiteSuSecuenciaHastaQueFireTransitionDevuelveFalse() {
        FakeMonitor m = new FakeMonitor(7);
        new CardWorker(m).run();
        // 7 disparos que salen bien y el octavo devuelve false: ahí el hilo termina
        assertEquals(List.of(1, 2, 3, 1, 2, 3, 1, 2), m.calls);
    }

    @Test
    void laSecuenciaNoSePuedeCambiarDesdeAfuera() {
        Worker w = new TransferWorker(new FakeMonitor(0));
        w.getSequence()[0] = 99;
        assertArrayEquals(new int[]{6, 7, 8}, w.getSequence());
    }
}
