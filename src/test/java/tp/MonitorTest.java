package tp;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/*
 Tests del Monitor: lo que pide la consigna sobre la interfaz, que sea agnóstico a la red,
 que termine siempre (req. 3) y que la exclusión mutua y la semántica temporal funcionen.
 Usan redes chicas o la red de la Figura 1 sin tiempos, así corren rápido.
*/
class MonitorTest {

    @TempDir
    Path tmp;

    // Red mínima de prueba: un token que va y viene  P0 -T0-> P1 -T1-> P0
    private static NetConfig pingPong(long alphaT1, int target) {
        int[][] pre  = { {1, 0}, {0, 1} };
        int[][] post = { {0, 1}, {1, 0} };
        return new NetConfig(pre, post, new int[]{1, 0}, new long[]{0, alphaT1},
                List.of(new PlaceInvariant("Token", new int[]{1, 1}, 1)),
                List.of(new TransitionInvariant("Vuelta", new int[]{0, 1})),
                1, target, new int[0]);
    }

    // ------------------------------------------------------------------ interfaz de la consigna

    @Test
    void implementaLaInterfazDeLaConsigna() throws Exception {
        assertTrue(MonitorInterface.class.isInterface());
        Method[] methods = MonitorInterface.class.getDeclaredMethods();
        assertEquals(1, methods.length, "MonitorInterface tiene que tener un solo método");
        Method m = MonitorInterface.class.getDeclaredMethod("fireTransition", int.class);
        assertEquals(boolean.class, m.getReturnType());
        assertTrue(MonitorInterface.class.isAssignableFrom(Monitor.class), "Monitor implementa MonitorInterface");
    }

    @Test
    void fireTransitionEsElUnicoMetodoPublico() {
        List<String> publicMethods = new ArrayList<>();
        for (Method m : Monitor.class.getDeclaredMethods()) {
            if (Modifier.isPublic(m.getModifiers()) && !m.isSynthetic()) {
                publicMethods.add(m.getName());
            }
        }
        assertEquals(List.of("fireTransition"), publicMethods);
        assertTrue(Arrays.stream(Monitor.class.getDeclaredFields()).noneMatch(f -> Modifier.isPublic(f.getModifiers())),
                "El Monitor no expone atributos públicos");
    }

    @Test
    void elCodigoDelMonitorNoNombraTransicionesDeLaRed() throws IOException {
        Path source = Path.of("src/main/java/tp/Monitor.java");
        Assumptions.assumeTrue(Files.exists(source), "Hay que correr los tests desde la raíz del proyecto");
        String code = Files.readString(source)
                .replaceAll("(?s)/\\*.*?\\*/", "")      // sin comentarios de bloque
                .replaceAll("//[^\\n]*", "")             // sin comentarios de línea
                .replaceAll("\"[^\"]*\"", "\"\"");       // sin textos entre comillas
        assertFalse(code.matches("(?s).*\\bT\\d.*"), "El Monitor no puede nombrar transiciones (T0, T1, ...)");
        assertFalse(code.matches("(?s).*[^\\w.]([2-9]|\\d{2,})[^\\w.].*"),
                "El Monitor no debería tener números que dependan de la red");
    }

    // ------------------------------------------------------------------ agnóstico a la red

    @Test
    void funcionaConOtraRedSinCambiarElMonitor() throws Exception {
        SimulationRunner.Result r = SimulationRunner.run(pingPong(0, 50), new RandomPolicy(), tmp, m -> List.of(
                new Thread(SimulationRunner.worker(m, 0), "Ida"),
                new Thread(SimulationRunner.worker(m, 1), "Vuelta")));

        assertTrue(r.stillAlive.isEmpty(), "Quedaron hilos vivos: " + r.stillAlive);
        assertTrue(r.errors.isEmpty(), "Errores: " + r.errors);
        assertEquals(50, r.net.getCompletedInvariants());
        assertArrayEquals(new int[]{50, 50}, r.net.getFirings());
        assertEquals(100, r.logLines().size());
    }

    // ------------------------------------------------------------------ colas y despertar

    @Test
    void elHiloQueNoPuedeDispararEsperaEnSuColaYDespuesLoDespiertan() throws Exception {
        NetConfig cfg = pingPong(0, 5);
        PetriNet net = new PetriNet(cfg);
        TransitionLogger.init(tmp, "transiciones.log");
        Monitor monitor = new Monitor(net, new RandomPolicy());
        try {
            // Arranca primero el que dispara T1, que todavía no tiene token: tiene que quedar bloqueado
            Thread vuelta = new Thread(SimulationRunner.worker(monitor, 1), "Vuelta");
            vuelta.start();
            waitUntilBlocked(vuelta);
            assertEquals(0, net.getFirings()[1], "No pudo haber disparado T1 sin token");

            // Cuando arranca el otro, el primero se despierta y la simulación sigue hasta el final
            Thread ida = new Thread(SimulationRunner.worker(monitor, 0), "Ida");
            ida.start();
            ida.join(10_000);
            vuelta.join(10_000);

            assertFalse(ida.isAlive());
            assertFalse(vuelta.isAlive());
            assertEquals(5, net.getCompletedInvariants());
        } finally {
            TransitionLogger.close();
        }
    }

    // ------------------------------------------------------------------ exclusión mutua

    @Test
    void exclusionMutuaConMilesDeDisparos() throws Exception {
        // Red de la Figura 1 sin tiempos y con un objetivo grande: si dos hilos entraran a la red a
        // la vez, se rompería el marcado y saltaría un invariante de plaza o un disparo inválido.
        SimulationRunner.Result r = SimulationRunner.runPaymentNet(
                SimulationRunner.paymentNet(new long[10], 3000), new RandomPolicy(), tmp);

        assertTrue(r.stillAlive.isEmpty(), "Quedaron hilos vivos: " + r.stillAlive);
        assertTrue(r.errors.isEmpty(), "Errores: " + r.errors);
        assertEquals(3000, r.net.getFirings()[9], "T9 se dispara exactamente el objetivo, ni uno más");
        assertDoesNotThrow(r.net::checkPlaceInvariants);
        assertEquals(Arrays.stream(r.net.getFirings()).sum(), r.logLines().size(), "Una línea de log por disparo");
    }

    // ------------------------------------------------------------------ errores (req. 3 y 12)

    @Test
    void siSeRompeUnInvarianteDePlazaLaSimulacionTerminaSinColgarse() throws Exception {
        NetConfig c = NetConfig.paymentServiceProvider();
        // Invariante falso: P0 = 3 siempre. Se rompe en el primer T0.
        NetConfig cfg = new NetConfig(c.getPre(), c.getPost(), c.getInitialMarking(), new long[10],
                List.of(new PlaceInvariant("Mal", new int[]{1, 0, 0, 0, 0, 0, 0, 0, 0, 0}, 3)),
                c.getTransitionInvariants(), 9, 200, new int[]{4});

        SimulationRunner.Result r = SimulationRunner.runPaymentNet(cfg, new RandomPolicy(), tmp);

        assertTrue(r.stillAlive.isEmpty(), "Quedaron hilos vivos (el programa no terminaría): " + r.stillAlive);
        assertEquals(1, r.errors.size(), "Solo el hilo que disparó tira la excepción: " + r.errors);
        assertInstanceOf(PlaceInvariantException.class, r.errors.get(0));
    }

    // ------------------------------------------------------------------ semántica temporal

    @Test
    void respetaElAlfaDeLasTransicionesTemporales() throws Exception {
        SimulationRunner.Result r = SimulationRunner.run(pingPong(40, 5), new RandomPolicy(), tmp, m -> List.of(
                new Thread(SimulationRunner.worker(m, 0), "Ida"),
                new Thread(SimulationRunner.worker(m, 1), "Vuelta")));

        assertTrue(r.errors.isEmpty(), "Errores: " + r.errors);
        assertEquals(5, r.net.getCompletedInvariants());
        assertTrue(r.elapsedMs >= 5 * 40, "5 disparos de T1 con α = 40 ms no pueden tardar " + r.elapsedMs + " ms");
    }

    @Test
    void mientrasUnHiloEsperaSuAlfaLosDemasSiguenDisparando() throws Exception {
        // Dos ciclos independientes: A (P0 <-> P1, T1 con α = 150 ms) y B (P2 <-> P3, inmediato).
        // Si el hilo de A durmiera con el mutex tomado, B no podría disparar mientras tanto.
        int[][] pre  = { {1, 0, 0, 0}, {0, 1, 0, 0}, {0, 0, 1, 0}, {0, 0, 0, 1} };
        int[][] post = { {0, 1, 0, 0}, {1, 0, 0, 0}, {0, 0, 0, 1}, {0, 0, 1, 0} };
        NetConfig cfg = new NetConfig(pre, post, new int[]{1, 0, 1, 0}, new long[]{0, 150, 0, 0},
                List.of(new PlaceInvariant("A", new int[]{1, 1, 0, 0}, 1),
                        new PlaceInvariant("B", new int[]{0, 0, 1, 1}, 1)),
                List.of(new TransitionInvariant("A", new int[]{0, 1}),
                        new TransitionInvariant("B", new int[]{2, 3})),
                1, 3, new int[0]);

        SimulationRunner.Result r = SimulationRunner.run(cfg, new RandomPolicy(), tmp, m -> List.of(
                new Thread(SimulationRunner.worker(m, 0), "A-ida"),
                new Thread(SimulationRunner.worker(m, 1), "A-vuelta"),
                new Thread(SimulationRunner.worker(m, 2, 3), "B")));

        assertTrue(r.stillAlive.isEmpty() && r.errors.isEmpty(), "vivos " + r.stillAlive + " errores " + r.errors);
        assertTrue(r.elapsedMs >= 3 * 150, "Tardó " + r.elapsedMs + " ms");
        assertTrue(r.net.getFirings()[2] > 10,
                "B tendría que haber disparado muchas veces mientras A dormía; disparó " + r.net.getFirings()[2]);
    }

    // ------------------------------------------------------------------

    private static void waitUntilBlocked(Thread t) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2_000;
        while (t.getState() != Thread.State.WAITING && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(Thread.State.WAITING, t.getState(), "El hilo tendría que estar bloqueado en su cola");
    }
}
