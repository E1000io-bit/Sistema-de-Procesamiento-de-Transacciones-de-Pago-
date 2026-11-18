package tp;
import java.util.Arrays;

// Invariante de plaza: sum(weights[i] * marking[i]) == value, para para todo marcado alcanzable

public class PlaceInvariant {

    private final String name;
    private final int[] weights; // un peso por plaza (índice i = plaza Pi)
    private final int value;

    public PlaceInvariant(String name, int[] weights, int value) {
        this.name = name;
        this.weights = Arrays.copyOf(weights, weights.length);  // copia defensiva
        this.value = value;
    }

    // Devuelve true si el marcado cumple el invariante
    public boolean holds(int[] marking) {
        if (marking.length != weights.length) {
            throw new IllegalArgumentException("El marcado tiene " + marking.length + " plazas y el invariante " + name + " espera " + weights.length);
        }
        int sum = 0;
        for (int p = 0; p < weights.length; p++) {
            sum += weights[p] * marking[p];
        }
        return sum == value;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(name).append(": ");
        boolean first = true;
        for (int p = 0; p < weights.length; p++) {
            if (weights[p] == 0) continue;
            if (!first) sb.append(" + ");
            if (weights[p] != 1) sb.append(weights[p]).append("*");
            sb.append("M(P").append(p).append(")");
            first = false;
        }
        return sb.append(" = ").append(value).toString();
    }
}