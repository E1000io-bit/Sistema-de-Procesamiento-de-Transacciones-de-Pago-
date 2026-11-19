package tp;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InvariantAnalyzer {

    private static final Pattern TRANSITION = Pattern.compile("T\\d+");
    private static final String GAP = "(.*?)";

    private final List<TransitionInvariant> invariants;

    // Los llena buildPattern(): qué grupo de la regex envuelve a cada invariante y cuáles son huecos (.*?)
    private int[] wrapperGroup;
    private final List<Integer> gapGroups = new ArrayList<>();

    public InvariantAnalyzer(List<TransitionInvariant> invariants) {
        if (invariants.isEmpty()) {
            throw new IllegalArgumentException("No hay invariantes de transición para analizar");
        }
        this.invariants = List.copyOf(invariants);
    }

    public InvariantReport analyze(Path logFile) {
        String sequence = readSequence(logFile);
        Pattern pattern = buildPattern();

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (TransitionInvariant inv : invariants) {
            counts.put(inv.getName(), 0);
        }

        Matcher m = pattern.matcher(sequence);
        while (m.find()) {
            // Qué invariante coincidió: el único grupo envolvente que no es null
            int i = 0;
            while (m.group(wrapperGroup[i]) == null) {
                i++;
            }
            counts.merge(invariants.get(i).getName(), 1, Integer::sum);

            // Se sacan las transiciones del invariante y se deja lo que estaba en los huecos
            StringBuilder between = new StringBuilder();
            for (int g : gapGroups) {
                if (m.group(g) != null) {        // los huecos de las otras alternativas quedan en null
                    between.append(m.group(g));
                }
            }
            sequence = sequence.substring(0, m.start()) + between + sequence.substring(m.end());
            m = pattern.matcher(sequence);
        }

        return new InvariantReport(counts, sequence);
    }

    /*
     Lee el log y devuelve las transiciones en orden, pegadas: "T0T1T0T4...".
     De cada línea toma solo la transición (por ejemplo, de "Tarjeta;T1" toma "T1"), así no
     depende de dónde esté el nombre del hilo.
    */
    private String readSequence(Path logFile) {
        StringBuilder sb = new StringBuilder();
        try {
            for (String line : Files.readAllLines(logFile)) {
                for (String field : line.split("[;\\s]+")) {
                    if (TRANSITION.matcher(field).matches()) {
                        sb.append(field);
                        break;
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el log " + logFile, e);
        }
        return sb.toString();
    }

    /*
     Arma la regex: principio común + (?:(medio 1)|(medio 2)|...) + final común, con huecos (.*?)
     entre cada par de transiciones. Va anotando el número de cada grupo a medida que lo escribe.
    */
    private Pattern buildPattern() {
        List<int[]> seqs = new ArrayList<>();
        for (TransitionInvariant inv : invariants) {
            seqs.add(inv.getSequence());
        }
        int shortest = Integer.MAX_VALUE;
        for (int[] seq : seqs) {
            shortest = Math.min(shortest, seq.length);
        }

        // Largo del principio y del final que comparten todos (sin que se pisen entre sí)
        int prefix = 0;
        while (prefix < shortest && sameAt(seqs, prefix, false)) {
            prefix++;
        }
        int suffix = 0;
        while (prefix + suffix < shortest && sameAt(seqs, suffix, true)) {
            suffix++;
        }

        StringBuilder regex = new StringBuilder();
        int group = 0;
        gapGroups.clear();
        wrapperGroup = new int[invariants.size()];
        int[] first = seqs.get(0);

        // Principio común: T0
        for (int k = 0; k < prefix; k++) {
            if (k > 0) {
                regex.append(GAP);
                gapGroups.add(++group);
            }
            regex.append(token(first[k]));
        }
        if (prefix > 0) {
            regex.append(GAP);
            gapGroups.add(++group);
        }

        // Medio: una alternativa por invariante, cada una envuelta en su grupo
        regex.append("(?:");
        for (int i = 0; i < seqs.size(); i++) {
            int[] seq = seqs.get(i);
            if (i > 0) {
                regex.append('|');
            }
            regex.append('(');
            wrapperGroup[i] = ++group;
            for (int k = prefix; k < seq.length - suffix; k++) {
                if (k > prefix) {
                    regex.append(GAP);
                    gapGroups.add(++group);
                }
                regex.append(token(seq[k]));
            }
            regex.append(')');
        }
        regex.append(')');

        // Final común: T9
        int[] last = seqs.get(0);
        for (int k = last.length - suffix; k < last.length; k++) {
            regex.append(GAP);
            gapGroups.add(++group);
            regex.append(token(last[k]));
        }

        return Pattern.compile(regex.toString());
    }

    // true si todas las secuencias tienen la misma transición en la posición pos (contando desde el final si fromEnd)
    private static boolean sameAt(List<int[]> seqs, int pos, boolean fromEnd) {
        int[] ref = seqs.get(0);
        int value = fromEnd ? ref[ref.length - 1 - pos] : ref[pos];
        for (int[] seq : seqs) {
            int v = fromEnd ? seq[seq.length - 1 - pos] : seq[pos];
            if (v != value) {
                return false;
            }
        }
        return true;
    }

    // "T1" seguido de algo que no sea dígito, para que T1 no coincida con el principio de T10
    private static String token(int t) {
        return "T" + t + "(?!\\d)";
    }
}
