package tp;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/*
 Política priorizada (req. 4): prioriza el flujo de alto riesgo (T4).

 Hace dos cosas:
 1. choose(m): si alguna transición priorizada está en m, despierta a la primera según el orden;
    si no, elige al azar.
 2. filter(...): mientras haya un hilo esperando la transición priorizada, frena a las
    transiciones que compiten con ella por alguna plaza de entrada (en esta red, T4 compite con
    T1 y T6 por P1, P7 y P8). Así los recursos que se liberan no los vuelve a tomar otro flujo:
    quedan reservados hasta que T4 los pueda tomar juntos. Sin esto, T4 casi nunca tiene P7 y P8
    libres a la vez y la política priorizada da lo mismo que la aleatoria.

 El freno dura solo mientras el hilo de alto riesgo espera: apenas dispara T4, T1 y T6 vuelven a
 poder disparar. Por eso no hay inanición: cuando el alto riesgo termina, tarjeta y transferencia
 toman los recursos. No hay ningún porcentaje ni probabilidad elegidos: el reparto sale de la red.

 Las competidoras se calculan con la matriz pre: la política no nombra transiciones fijas, más
 allá de priorityOrder, que viene de NetConfig.
*/
public class PriorityPolicy implements Policy {

    private final int[] priorityOrder;   // transiciones priorizadas de mayor a menor prioridad
    private final int[][] competitors;   // competitors[i]: transiciones que comparten plaza de entrada con priorityOrder[i]
    private final Random random;

    public PriorityPolicy(int[] priorityOrder, int[][] pre) {
        this.priorityOrder = priorityOrder.clone();
        this.competitors = new int[priorityOrder.length][];
        for (int i = 0; i < priorityOrder.length; i++) {
            competitors[i] = competitorsOf(priorityOrder[i], pre);
        }
        this.random = new Random();
    }

    // Transiciones distintas de p que necesitan tokens de alguna plaza de entrada de p
    private static int[] competitorsOf(int p, int[][] pre) {
        List<Integer> list = new ArrayList<>();
        for (int t = 0; t < pre[0].length; t++) {
            if (t == p) {
                continue;
            }
            for (int[] place : pre) {
                if (place[p] > 0 && place[t] > 0) {
                    list.add(t);
                    break;
                }
            }
        }
        return list.stream().mapToInt(Integer::intValue).toArray();
    }

    @Override
    public boolean[] filter(boolean[] enabled, boolean[] waiting) {
        boolean[] allowed = enabled.clone();
        for (int i = 0; i < priorityOrder.length; i++) {
            // Si hay un hilo esperando la priorizada, sus competidoras no pueden tomar los recursos
            if (waiting[priorityOrder[i]]) {
                for (int c : competitors[i]) {
                    allowed[c] = false;
                }
            }
        }
        return allowed;
    }

    @Override
    public int choose(boolean[] m) {

        // Si alguna transición priorizada está en m, se elige la primera según el orden
        for (int t : priorityOrder) {
            if (t >= 0 && t < m.length && m[t]) {
                return t;
            }
        }

        // Si ninguna prioritaria está en m, se elige al azar
        return chooseRandom(m);
    }

    private int chooseRandom(boolean[] m) {

        int count = 0;
        for (boolean b : m) {
            if (b) count++;
        }
        if (count == 0) {
            throw new IllegalArgumentException("m no tiene transiciones habilitadas");
        }

        int n = random.nextInt(count);
        for (int t = 0; t < m.length; t++) {
            if (m[t] && n-- == 0) {
                return t;
            }
        }
        throw new IllegalStateException("Inalcanzable");
    }
}
