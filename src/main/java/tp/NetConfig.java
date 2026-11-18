package tp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/*
 Todo lo específico de la red de la Figura 1: matrices pre/post, marcado inicial, tiempos α,
 invariantes de plaza y de transición, transición de cierre, objetivo y prioridades.

 Cambiar de red = escribir otra fábrica como paymentServiceProvider() (y sus Worker).
 Monitor, PetriNet y Queues no cambian.

 Convención de las matrices: pre[p][t] / post[p][t] (fila = plaza, columna = transición).
   pre[p][t]  = tokens que t saca de p al dispararse
   post[p][t] = tokens que t pone en p al dispararse
 La matriz de incidencia es W = post - pre.

 Los getters devuelven copias: nadie puede cambiar la red desde afuera.
*/

public class NetConfig {

    private final int[][] pre;
    private final int[][] post;
    private final int[] initialMarking;
    private final long[] alpha;                       // ms; 0 = transición inmediata
    private final List<PlaceInvariant> placeInvariants;
    private final List<TransitionInvariant> transitionInvariants;
    private final int completionTransition;           // transición que cierra un invariante (T9)
    private final int targetInvariants;               // cuántos invariantes completar (200)
    private final int[] priorityOrder;                // para PriorityPolicy, de mayor a menor

    // Package-private (no private) para que los tests puedan armar redes chicas o con otros tiempos
    NetConfig(int[][] pre, int[][] post, int[] initialMarking, long[] alpha,
              List<PlaceInvariant> placeInvariants, List<TransitionInvariant> transitionInvariants,
              int completionTransition, int targetInvariants, int[] priorityOrder) {

        int places = initialMarking.length;
        int transitions = alpha.length;
        checkMatrix("pre", pre, places, transitions);
        checkMatrix("post", post, places, transitions);
        if (completionTransition < 0 || completionTransition >= transitions) {
            throw new IllegalArgumentException("Transición de cierre fuera de rango: " + completionTransition);
        }
        if (targetInvariants <= 0) {
            throw new IllegalArgumentException("El objetivo de invariantes tiene que ser > 0");
        }
        for (int t : priorityOrder) {
            if (t < 0 || t >= transitions) {
                throw new IllegalArgumentException("Transición prioritaria fuera de rango: " + t);
            }
        }

        this.pre = copy(pre);
        this.post = copy(post);
        this.initialMarking = initialMarking.clone();
        this.alpha = alpha.clone();
        this.placeInvariants = Collections.unmodifiableList(new ArrayList<>(placeInvariants));
        this.transitionInvariants = Collections.unmodifiableList(new ArrayList<>(transitionInvariants));
        this.completionTransition = completionTransition;
        this.targetInvariants = targetInvariants;
        this.priorityOrder = priorityOrder.clone();
    }

    /*
     Red de la Figura 1 (10 plazas, 10 transiciones):

       T0: P0           -> P1             admisión
       T1: P1 + P7      -> P2             tarjeta: toma el gateway
       T2: P2           -> P3             tarjeta: autorización        (temporal)
       T3: P3           -> P7 + P9        tarjeta: captura, libera     (temporal)
       T4: P1 + P7 + P8 -> P4             alto riesgo: toma gateway y antifraude
       T5: P4           -> P7 + P8 + P9   alto riesgo: scoring, libera (temporal)
       T6: P1 + P8      -> P5             transferencia: toma antifraude
       T7: P5           -> P6             transferencia: valida cuenta (temporal)
       T8: P6           -> P8 + P9        transferencia: ejecuta, libera (temporal)
       T9: P9           -> P0             liquidación / cierre
    */
    public static NetConfig paymentServiceProvider() {
        //                T0 T1 T2 T3 T4 T5 T6 T7 T8 T9
        int[][] pre = {
                /* P0 */ { 1, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
                /* P1 */ { 0, 1, 0, 0, 1, 0, 1, 0, 0, 0 },
                /* P2 */ { 0, 0, 1, 0, 0, 0, 0, 0, 0, 0 },
                /* P3 */ { 0, 0, 0, 1, 0, 0, 0, 0, 0, 0 },
                /* P4 */ { 0, 0, 0, 0, 0, 1, 0, 0, 0, 0 },
                /* P5 */ { 0, 0, 0, 0, 0, 0, 0, 1, 0, 0 },
                /* P6 */ { 0, 0, 0, 0, 0, 0, 0, 0, 1, 0 },
                /* P7 */ { 0, 1, 0, 0, 1, 0, 0, 0, 0, 0 },
                /* P8 */ { 0, 0, 0, 0, 1, 0, 1, 0, 0, 0 },
                /* P9 */ { 0, 0, 0, 0, 0, 0, 0, 0, 0, 1 },
        };
        //                T0 T1 T2 T3 T4 T5 T6 T7 T8 T9
        int[][] post = {
                /* P0 */ { 0, 0, 0, 0, 0, 0, 0, 0, 0, 1 },
                /* P1 */ { 1, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
                /* P2 */ { 0, 1, 0, 0, 0, 0, 0, 0, 0, 0 },
                /* P3 */ { 0, 0, 1, 0, 0, 0, 0, 0, 0, 0 },
                /* P4 */ { 0, 0, 0, 0, 1, 0, 0, 0, 0, 0 },
                /* P5 */ { 0, 0, 0, 0, 0, 0, 1, 0, 0, 0 },
                /* P6 */ { 0, 0, 0, 0, 0, 0, 0, 1, 0, 0 },
                /* P7 */ { 0, 0, 0, 1, 0, 1, 0, 0, 0, 0 },
                /* P8 */ { 0, 0, 0, 0, 0, 1, 0, 0, 1, 0 },
                /* P9 */ { 0, 0, 0, 1, 0, 1, 0, 0, 1, 0 },
        };

        //                     P0 P1 P2 P3 P4 P5 P6 P7 P8 P9
        int[] initialMarking = { 3, 0, 0, 0, 0, 0, 0, 1, 1, 0 };

        // α en ms. Temporales: T2, T3, T5, T7, T8 (los valores los elige el grupo)
        //              T0 T1   T2   T3 T4   T5 T6   T7   T8 T9
        long[] alpha = { 0, 0, 100, 100, 0, 150, 0, 100, 100, 0 };

        List<PlaceInvariant> placeInvariants = List.of(
                // Transacciones: las 3 están en la cola de arribo, en algún flujo o en el buffer de salida
                new PlaceInvariant("Transacciones", new int[]{ 1, 1, 1, 1, 1, 1, 1, 0, 0, 1 }, 3),
                // Gateway de tarjetas (P7): libre o en uso por el flujo de tarjeta (P2, P3) o alto riesgo (P4)
                new PlaceInvariant("Gateway",       new int[]{ 0, 0, 1, 1, 1, 0, 0, 1, 0, 0 }, 1),
                // Motor antifraude (P8): libre o en uso por alto riesgo (P4) o transferencia (P5, P6)
                new PlaceInvariant("Antifraude",    new int[]{ 0, 0, 0, 0, 1, 1, 1, 0, 1, 0 }, 1)
        );

        List<TransitionInvariant> transitionInvariants = List.of(
                new TransitionInvariant("Tarjeta",       new int[]{ 0, 1, 2, 3, 9 }),
                new TransitionInvariant("AltoRiesgo",    new int[]{ 0, 4, 5, 9 }),
                new TransitionInvariant("Transferencia", new int[]{ 0, 6, 7, 8, 9 })
        );

        int completionTransition = 9;
        int targetInvariants = 200;
        int[] priorityOrder = { 4 };   // PriorityPolicy: primero alto riesgo (T4)

        return new NetConfig(pre, post, initialMarking, alpha, placeInvariants, transitionInvariants,
                completionTransition, targetInvariants, priorityOrder);
    }

    public int[][] getPre() {
        return copy(pre);
    }

    public int[][] getPost() {
        return copy(post);
    }

    public int[] getInitialMarking() {
        return initialMarking.clone();
    }

    public long[] getAlpha() {
        return alpha.clone();
    }

    public List<PlaceInvariant> getPlaceInvariants() {
        return placeInvariants;      // ya es inmodificable
    }

    public List<TransitionInvariant> getTransitionInvariants() {
        return transitionInvariants; // ya es inmodificable
    }

    public int getCompletionTransition() {
        return completionTransition;
    }

    public int getTargetInvariants() {
        return targetInvariants;
    }

    public int[] getPriorityOrder() {
        return priorityOrder.clone();
    }

    // ----------------------------------------------------------------------------------------

    private static void checkMatrix(String name, int[][] m, int places, int transitions) {
        if (m.length != places) {
            throw new IllegalArgumentException(name + " tiene " + m.length + " filas y hay " + places + " plazas");
        }
        for (int p = 0; p < places; p++) {
            if (m[p].length != transitions) {
                throw new IllegalArgumentException(name + "[P" + p + "] tiene " + m[p].length
                        + " columnas y hay " + transitions + " transiciones");
            }
            for (int v : m[p]) {
                if (v < 0) {
                    throw new IllegalArgumentException(name + " tiene pesos negativos en P" + p);
                }
            }
        }
    }

    private static int[][] copy(int[][] m) {
        int[][] c = new int[m.length][];
        for (int i = 0; i < m.length; i++) {
            c[i] = Arrays.copyOf(m[i], m[i].length);
        }
        return c;
    }
}
