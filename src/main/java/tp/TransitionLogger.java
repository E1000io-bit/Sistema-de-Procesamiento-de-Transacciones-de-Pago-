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
import java.util.Map;
import java.util.stream.Collectors;

public class TransitionLogger {

    private static BufferedWriter invariantsWriter;
    private static BufferedWriter decisionsWriter;
    private static int[] vecesCandidata;  // en decisiones con 2 o mas candidatas
    private static int[] vecesElegida;  // en decisiones con 2 o mas candidatas
    private static final List<String> decisiones = new ArrayList<>();

    private TransitionLogger() {}

    // Crea (o pisa) el archivo del log
    public static void init(Path outputDirectory, String resultsLog, String decisionsLog, int transitionsAmount) throws IOException {
        Files.createDirectories(outputDirectory);

        vecesCandidata = new int[transitionsAmount];
        vecesElegida = new int[transitionsAmount];

        Path parent = outputDirectory.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        // Sin opciones = CREATE + TRUNCATE_EXISTING: cada corrida empieza de cero
        invariantsWriter = Files.newBufferedWriter(outputDirectory.resolve(resultsLog), StandardCharsets.UTF_8);
        decisionsWriter = Files.newBufferedWriter(outputDirectory.resolve(decisionsLog), StandardCharsets.UTF_8);
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

        if (invariantsWriter != null && decisionsWriter != null) {
            try {
                invariantsWriter.close();
                decisionsWriter.close();

            } finally {
                invariantsWriter = null;
                decisionsWriter = null;
            }
        }
    }

    public static void writeDecision(boolean[] wakeable, int next) {

        List<Integer> candidates = new ArrayList<>();
        for (int t = 0; t < wakeable.length; t++) {
            if (wakeable[t]) {
                candidates.add(t);
            }
        }
        if (candidates.size() < 2) {
            return;
        }

        candidates.forEach( t -> vecesCandidata[t]++ );
        vecesElegida[next]++;

        decisiones.add("candidatas=" + candidates.stream().map(t -> "T" + t)
                .collect(Collectors.joining(",")) + " elegida=T" + next);

    }

    public static void decisionsResult(NetConfig netConfig) {

        List<TransitionInvariant> invariants = netConfig.getTransitionInvariants();

        for (int i = 0; i < invariants.size(); i++) {
            write(decisionsWriter, formatLine(invariants.get(i).getName(), invariants.get(i).getSequence()[1]));
        }
    }

    private static String formatLine(String nombre, int transicion) {
        return nombre + " candidato: " + vecesCandidata[transicion] + ", elegido: " + vecesElegida[transicion];
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
