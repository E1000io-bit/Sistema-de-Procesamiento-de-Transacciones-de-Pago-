package tp;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/*
 Test de punta a punta: corre el sistema completo como Main, con la red de la Figura 1, los
 tiempos reales y las dos políticas, y verifica los requisitos de la consigna sobre esas corridas.

 Tarda unos 45 s (una corrida de ~21 s por política). Las dos corridas se hacen una sola vez,
 en @BeforeAll, y cada test revisa un requisito distinto sobre los mismos resultados.
*/
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SimulationTest {

    private final Map<String, SimulationRunner.Result> runs = new LinkedHashMap<>();
    private final Map<String, InvariantReport> reports = new LinkedHashMap<>();
    private Path tmp;

    @BeforeAll
    void correrLasDosPoliticas() throws Exception {
        tmp = Files.createTempDirectory("simulacion");
        NetConfig cfg = NetConfig.paymentServiceProvider();
        Map<String, Policy> policies = new LinkedHashMap<>();
        policies.put("aleatoria", new RandomPolicy());
        policies.put("priorizada", new PriorityPolicy(cfg.getPriorityOrder()));

        for (Map.Entry<String, Policy> e : policies.entrySet()) {
            Path dir = Files.createDirectories(tmp.resolve(e.getKey()));
            SimulationRunner.Result r = SimulationRunner.runPaymentNet(cfg, e.getValue(), dir);
            runs.put(e.getKey(), r);
            reports.put(e.getKey(), new InvariantAnalyzer(cfg.getTransitionInvariants()).analyze(r.log));
        }
    }

    @AfterAll
    void borrarArchivos() throws Exception {
        try (var files = Files.walk(tmp)) {
            files.sorted((a, b) -> b.compareTo(a)).forEach(p -> p.toFile().delete());
        }
    }

    // req. 3: el programa termina, no quedan hilos activos
    @Test
    void req3_terminanLosNueveHilos() {
        runs.forEach((policy, r) -> {
            assertEquals(9, r.threads.size(), policy);
            assertTrue(r.stillAlive.isEmpty(), policy + ": quedaron hilos vivos " + r.stillAlive);
            assertTrue(r.errors.isEmpty(), policy + ": algún hilo tiró una excepción " + r.errors);
        });
    }

    // req. 8: cada ejecución completa 200 invariantes (200 disparos de la transición de cierre)
    @Test
    void req8_seCompletanExactamente200Invariantes() {
        runs.forEach((policy, r) -> {
            assertEquals(200, r.net.getCompletedInvariants(), policy);
            assertEquals(200, r.net.getFirings()[9], policy + ": T9 se dispara 200 veces, ni una más");
        });
    }

    // req. 10: el programa tiene que demorar entre 20 y 40 segundos
    @Test
    void req10_duraEntre20y40Segundos() {
        runs.forEach((policy, r) -> {
            double s = r.elapsedMs / 1000.0;
            assertTrue(s >= 20 && s <= 40, policy + ": tardó " + s + " s");
        });
    }

    // req. 12: los invariantes de plaza se cumplen después de cada disparo (si no, un hilo
    // habría tirado PlaceInvariantException) y también en el marcado final
    @Test
    void req12_seCumplenLosInvariantesDePlaza() {
        runs.forEach((policy, r) -> {
            assertTrue(r.errors.stream().noneMatch(e -> e instanceof PlaceInvariantException), policy);
            assertDoesNotThrow(r.net::checkPlaceInvariants, policy);
        });
    }

    // req. 9: queda registrado cada disparo en el log, en orden y con el formato "Hilo;Tn"
    @Test
    void req9_elLogTieneUnaLineaPorDisparo() throws Exception {
        Pattern format = Pattern.compile("([A-Za-z]+(?:-\\d)?);T(\\d)");
        for (Map.Entry<String, SimulationRunner.Result> e : runs.entrySet()) {
            List<String> lines = e.getValue().logLines();
            int total = 0;
            for (int f : e.getValue().net.getFirings()) {
                total += f;
            }
            assertEquals(total, lines.size(), e.getKey() + ": una línea por disparo");
            for (String line : lines) {
                assertTrue(format.matcher(line).matches(), e.getKey() + ": línea con formato raro: " + line);
            }
        }
    }

    // req. 7: cada hilo solo dispara las transiciones de las que es responsable
    @Test
    void req7_cadaHiloDisparaSoloSusTransiciones() throws Exception {
        Map<String, Set<Integer>> responsible = Map.of(
                "Admision", Set.of(0),
                "Tarjeta", Set.of(1, 2, 3),
                "AltoRiesgo", Set.of(4, 5),
                "Transferencia", Set.of(6, 7, 8),
                "Liquidacion", Set.of(9));
        for (Map.Entry<String, SimulationRunner.Result> e : runs.entrySet()) {
            for (String line : e.getValue().logLines()) {
                String[] parts = line.split(";");
                String thread = parts[0].replaceAll("-\\d$", "");
                int t = Integer.parseInt(parts[1].substring(1));
                assertTrue(responsible.get(thread).contains(t), e.getKey() + ": " + parts[0] + " disparó T" + t);
            }
        }
    }

    // req. 13: el análisis con regex encuentra los 200 invariantes y cuenta bien cada tipo
    @Test
    void req13_laRegexEncuentraLos200InvariantesYCuentaBienCadaTipo() {
        for (String policy : runs.keySet()) {
            InvariantReport report = reports.get(policy);
            int[] f = runs.get(policy).net.getFirings();
            String leftover = report.getLeftover();

            assertEquals(200, report.getTotal(), policy);
            // Cada tipo cuenta las transacciones que llegaron al final de su flujo, menos las que
            // todavía estaban en curso (esas quedan en el sobrante)
            assertEquals(f[3] - occurrences(leftover, "T3"), report.getCount("Tarjeta"), policy + " Tarjeta");
            assertEquals(f[5] - occurrences(leftover, "T5"), report.getCount("AltoRiesgo"), policy + " AltoRiesgo");
            assertEquals(f[8] - occurrences(leftover, "T8"), report.getCount("Transferencia"), policy + " Transferencia");
            // Lo que sobra son, como mucho, las 3 transacciones de P0 que estaban en curso
            assertTrue(occurrences(leftover, "T0") <= 3, policy + ": sobrante raro " + leftover);
            assertEquals(0, occurrences(leftover, "T9"), policy + ": no puede sobrar un cierre " + leftover);
        }
    }

    private static int occurrences(String s, String token) {
        Matcher m = Pattern.compile(token + "(?!\\d)").matcher(s);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }
}
