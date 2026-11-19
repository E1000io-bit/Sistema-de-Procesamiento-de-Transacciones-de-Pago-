package tp;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class InvariantReport {

    private final Map<String, Integer> countByInvariant;   // en el orden de NetConfig
    private final String leftover;

    public InvariantReport(Map<String, Integer> counts, String leftover) {
        this.countByInvariant = Collections.unmodifiableMap(new LinkedHashMap<>(counts));
        this.leftover = leftover;
    }

    // Cantidad de veces que se completó el invariante con ese nombre (0 si no existe)
    public int getCount(String name) {
        return countByInvariant.getOrDefault(name, 0);
    }

    // Total de invariantes completos, de todos los tipos
    public int getTotal() {
        int total = 0;
        for (int c : countByInvariant.values()) {
            total += c;
        }
        return total;
    }

    // Lo que quedó del log sin formar invariantes completos (ej. "T0T0T1T2")
    public String getLeftover() {
        return leftover;
    }

    // Una línea por invariante, con cantidad y porcentaje sobre el total (para el req. 8)
    @Override
    public String toString() {
        int total = getTotal();
        StringBuilder sb = new StringBuilder("Invariantes por tipo:");
        for (Map.Entry<String, Integer> e : countByInvariant.entrySet()) {
            double pct = total == 0 ? 0 : 100.0 * e.getValue() / total;
            sb.append(String.format("%n  %-15s %4d  (%5.1f %%)", e.getKey(), e.getValue(), pct));
        }
        return sb.toString();
    }
}
