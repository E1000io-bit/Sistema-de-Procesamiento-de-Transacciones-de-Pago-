package tp;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class TransitionLogger {

    private static BufferedWriter invariantsWriter;
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
