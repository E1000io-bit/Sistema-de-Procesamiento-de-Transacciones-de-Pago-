package tp;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/*
 Req. 13: verificación de los invariantes de transición con expresiones regulares sobre el log.
 Cada test arma un log a mano con un caso que el análisis tiene que resolver bien.
*/
class InvariantAnalyzerTest {

    @TempDir
    Path tmp;

    private InvariantReport analyze(String... lines) throws IOException {
        Path log = tmp.resolve("log.txt");
        Files.write(log, Arrays.asList(lines));
        return new InvariantAnalyzer(NetConfig.paymentServiceProvider().getTransitionInvariants()).analyze(log);
    }

    private static void assertCounts(InvariantReport r, int tarjeta, int altoRiesgo, int transferencia, String leftover) {
        assertEquals(tarjeta, r.getCount("Tarjeta"), "Tarjeta");
        assertEquals(altoRiesgo, r.getCount("AltoRiesgo"), "AltoRiesgo");
        assertEquals(transferencia, r.getCount("Transferencia"), "Transferencia");
        assertEquals(tarjeta + altoRiesgo + transferencia, r.getTotal(), "Total");
        assertEquals(leftover, r.getLeftover(), "Sobrante");
    }

    @Test
    void unoDeCadaTipoSinIntercalar() throws IOException {
        InvariantReport r = analyze(
                "Admision;T0", "Tarjeta;T1", "Tarjeta;T2", "Tarjeta;T3", "Liquidacion-1;T9",
                "Admision;T0", "AltoRiesgo;T4", "AltoRiesgo;T5", "Liquidacion-2;T9",
                "Admision;T0", "Transferencia;T6", "Transferencia;T7", "Transferencia;T8", "Liquidacion-3;T9");
        assertCounts(r, 1, 1, 1, "");
    }

    @Test
    void invariantesIntercalados() throws IOException {
        // Dos transacciones a la vez: alto riesgo entra primero, pero tarjeta termina antes
        InvariantReport r = analyze(
                "Admision;T0", "Admision;T0", "AltoRiesgo;T4", "Tarjeta;T1", "Tarjeta;T2",
                "Tarjeta;T3", "Liquidacion-1;T9", "AltoRiesgo;T5", "Liquidacion-2;T9");
        assertCounts(r, 1, 1, 0, "");
    }

    @Test
    void elT0DeUnaTransferenciaNoSeLoRobaUnaTarjetaPosterior() throws IOException {
        // Caso que rompía la primera versión de la regex: el primer T0 es de una transferencia,
        // y la alternativa "Tarjeta" no se lo puede quedar dejando a la transferencia sin T0
        InvariantReport r = analyze(
                "Admision;T0", "Transferencia;T6", "Admision;T0", "Tarjeta;T1", "Tarjeta;T2",
                "Tarjeta;T3", "Liquidacion-1;T9", "Transferencia;T7", "Transferencia;T8", "Liquidacion-2;T9");
        assertCounts(r, 1, 0, 1, "");
    }

    @Test
    void lasTransaccionesQueNoTerminaronQuedanEnElSobrante() throws IOException {
        InvariantReport r = analyze(
                "Admision;T0", "Transferencia;T6", "Transferencia;T7", "Transferencia;T8", "Liquidacion-1;T9",
                "Admision;T0", "Admision;T0", "Tarjeta;T1", "Tarjeta;T2");
        assertCounts(r, 0, 0, 1, "T0T0T1T2");
    }

    @Test
    void logVacio() throws IOException {
        assertCounts(analyze(), 0, 0, 0, "");
    }

    @Test
    void noDependeDeDondeEsteElNombreDelHilo() throws IOException {
        // Si el logger cambiara de formato ("T1 Tarjeta" o solo "T1"), el análisis sigue andando
        InvariantReport r = analyze("T0 Admision", "T4 AltoRiesgo", "T5", "T9");
        assertCounts(r, 0, 1, 0, "");
    }

    @Test
    void elReporteMuestraCadaTipoConSuPorcentaje() throws IOException {
        InvariantReport r = analyze("A;T0", "R;T4", "R;T5", "L;T9", "A;T0", "R;T4", "R;T5", "L;T9");
        String text = r.toString();
        assertTrue(text.contains("AltoRiesgo"), text);
        assertTrue(text.contains("100.0") || text.contains("100,0"), text);
    }

    @Test
    void sinInvariantesNoSePuedeAnalizar() {
        assertThrows(IllegalArgumentException.class, () -> new InvariantAnalyzer(java.util.List.of()));
    }
}
