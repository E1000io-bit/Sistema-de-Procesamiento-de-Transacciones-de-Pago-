package tp;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TransitionLogger {

    private static BufferedWriter invariantsWriter;
    private static List<Integer> vecesCandidata = new ArrayList<>();  // en decisiones con 2 o mas candidatas
    private static List<Integer> vecesElegida = new ArrayList<>();  // en decisiones con 2 o mas candidatas
    private static final List<String> decisiones = new ArrayList<>();

    private TransitionLogger() {}

    // Crea (o pisa) el archivo del log
    public static void init(Path outputDirectory, String fileName) throws IOException {
        Files.createDirectories(outputDirectory);

        Path parent = outputDirectory.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        // Sin opciones = CREATE + TRUNCATE_EXISTING: cada corrida empieza de cero
        invariantsWriter = Files.newBufferedWriter(outputDirectory.resolve(fileName), StandardCharsets.UTF_8);
    }

    /** Escribe la línea de los invariantes */
    public static void record(int transition, String threadName) throws IOException {

        if (invariantsWriter == null) {
            throw new IllegalStateException("TransitionLogger no inicializado: llamar a TransitionLogger.init(...) primero");
        }

        writeInvariant(transition, threadName);
    }

    /** Vuelca y cierra el archivo. Llamar despues de que todos los hilos terminaron. */
    public static void close() throws IOException {

        if (invariantsWriter != null) {
            try {
                invariantsWriter.close();

            } finally {
                invariantsWriter = null;
            }
        }
    }

    public static void writeDecition(boolean[] wakeable, int next) {
        List<Integer> candidates = new ArrayList<>();
        for (int t = 0; t < wakeable.length; t++) {
            if (wakeable[t]) {
                candidates.add(t);
            }
        }
        if (candidates.size() < 2) {
            return;
        }
        candidates.forEach( t -> (vecesCandidata.get(t))++ );
        vecesElegida[next]++;

        decisiones.add("candidatas=" + candidates.stream().map(t -> "T" + t)
                .collect(Collectors.joining(",")) + " elegida=T" + next);

    }

    // ----------------------------------------------------------------------------------------
    /* Privados: siempre se ejecutan con el monitor Logger.class ya tomado */
    private static void writeInvariant(int transition, String threadName) {

        String line = String.join(";",
                threadName,
                "T" + Integer.toString(transition));

        write(invariantsWriter, line);
    }

    private static void write(BufferedWriter writer, String line) {

        if (writer == null) {
            throw new IllegalStateException("Logger no inicializado: llamar a TransitionLogger.init(...) primero");
        }

        try {
            writer.write(line);
            writer.newLine();

        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo escribir en el archivo de rastreo", e);
        }
    }




    /** private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    } */
}
