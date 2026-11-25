package tp;
import java.util.Random;

public class PriorityPolicy implements Policy {

    private final int[] priorityOrder;   // transiciones priorizadas, de mayor a menor prioridad (ej: {4})
    private final Random random;

    public PriorityPolicy(int[] priorityOrder) {
        this.priorityOrder = priorityOrder.clone();
        this.random = new Random();
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