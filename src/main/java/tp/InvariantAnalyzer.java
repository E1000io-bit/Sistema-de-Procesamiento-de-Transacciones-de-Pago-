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

/*
 Verifica los invariantes de transición con expresiones regulares sobre el log (req. 13).

 Pasos:
 1. readSequence(): lee el log y lo aplana en una sola cadena con las transiciones en el orden
    en que se dispararon, por ejemplo "T0T1T0T4T2T3T9...".
 2. buildPattern(): arma UNA regex con todos los invariantes. Lo que tienen en común al principio
    y al final (en esta red T0 y T9) se escribe una sola vez, y lo del medio va como alternativas:
        T0(.*?)(?:(T1(.*?)T2(.*?)T3)|(T4(.*?)T5)|(T6(.*?)T7(.*?)T8))(.*?)T9
    Los (.*?) permiten que en el medio haya transiciones de otros hilos, porque se intercalan.
    Factorizar el principio importa: así cada T0 se empareja con la PRIMERA transición de
    conflicto (T1, T4 o T6) que aparece después. Si cada invariante fuera una alternativa completa,
    la regex probaría siempre primero "Tarjeta" y le robaría el T0 a transacciones de otros flujos,
    que después quedarían sin T0 adelante y no se contarían.
 3. analyze(): busca la primera coincidencia, la cuenta para el invariante que coincidió y la
    reemplaza por lo que había en el medio (los grupos (.*?)): saca del log solo las transiciones
    de ese invariante y deja las de los demás. Repite hasta que no haya más coincidencias. Lo que
    queda es el sobrante: transacciones que no llegaron a cerrarse.
*/

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
