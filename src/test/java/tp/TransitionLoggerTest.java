package tp;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/*
 Req. 9: el log de transiciones. Una línea por disparo, en orden, con "Hilo;Tn".
*/
class TransitionLoggerTest {

    @TempDir
    Path tmp;

    @Test
    void escribeUnaLineaPorDisparoEnOrden() throws Exception {
        TransitionLogger.init(tmp, "log.txt");
        TransitionLogger.record(0, "Admision");
        TransitionLogger.record(1, "Tarjeta");
        TransitionLogger.record(9, "Liquidacion-2");
        TransitionLogger.close();

        assertEquals(List.of("Admision;T0", "Tarjeta;T1", "Liquidacion-2;T9"), Files.readAllLines(tmp.resolve("log.txt")));
    }

    @Test
    void cadaCorridaEmpiezaConElLogVacio() throws Exception {
        TransitionLogger.init(tmp, "log.txt");
        TransitionLogger.record(0, "Admision");
        TransitionLogger.close();
        TransitionLogger.init(tmp, "log.txt");
        TransitionLogger.close();

        assertEquals(List.of(), Files.readAllLines(tmp.resolve("log.txt")));
    }

    @Test
    void creaLaCarpetaSiNoExiste() throws Exception {
        Path dir = tmp.resolve("logs/nueva");
        TransitionLogger.init(dir, "log.txt");
        TransitionLogger.close();
        assertTrue(Files.exists(dir.resolve("log.txt")));
    }

    @Test
    void registrarSinInicializarFalla() throws Exception {
        TransitionLogger.close();
        assertThrows(IllegalStateException.class, () -> TransitionLogger.record(0, "Admision"));
    }
}
