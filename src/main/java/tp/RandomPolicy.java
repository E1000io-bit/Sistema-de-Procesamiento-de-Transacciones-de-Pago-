package tp;
import java.util.Random;

public class RandomPolicy implements Policy {

    private final Random random;

    public RandomPolicy() {
        this.random = new Random();
    }

    @Override
    public int choose(boolean[] m) {
        int count = 0;
        for (boolean b : m) {
            if (b) count++;
        }
        if (count == 0) {
            throw new IllegalArgumentException("m no tiene transiciones habilitadas");
        }

        int n = random.nextInt(count);   // n-ésima transición habilitada
        for (int t = 0; t < m.length; t++) {
            if (m[t] && n-- == 0) {
                return t;
            }
        }
        throw new IllegalStateException("Inalcanzable");
    }
}