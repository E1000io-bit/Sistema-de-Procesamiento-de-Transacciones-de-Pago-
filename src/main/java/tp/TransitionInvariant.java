package tp;

import java.util.StringJoiner;

/*
 Invariante de transición: secuencia de disparos que devuelve la red al marcado de partida.
 Ej.: Tarjeta = T0 T1 T2 T3 T9.
*/

public class TransitionInvariant {

    private final String name;
    private final int[] sequence;

    public TransitionInvariant(String name, int[] sequence) {
        if (sequence.length == 0) {
            throw new IllegalArgumentException("El invariante " + name + " no tiene transiciones");
        }
        this.name = name;
        this.sequence = sequence.clone();
    }

    public String getName() {
        return name;
    }

    public int[] getSequence() {
        return sequence.clone();
    }

    /*
     Regex de la secuencia sobre el log aplanado ("T0T1T4..."): cada transición en orden, con
     cualquier cosa (lazy) en el medio, porque los invariantes se intercalan entre hilos.
    */
    public String toRegex() {
        StringJoiner sj = new StringJoiner("(.*?)");
        for (int t : sequence) {
            sj.add("T" + t + "(?!\\d)");
        }
        return sj.toString();
    }

    @Override
    public String toString() {
        StringJoiner sj = new StringJoiner(" ", name + ": ", "");
        for (int t : sequence) {
            sj.add("T" + t);
        }
        return sj.toString();
    }
}
