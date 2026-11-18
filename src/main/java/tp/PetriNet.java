package tp;

import java.util.Arrays;
import java.util.List;

/*
 Red de Petri temporal genérica: no sabe nada de la red de pagos, todo sale de NetConfig.

 Contrato con el Monitor: todos los métodos se llaman con el mutex del monitor tomado,
 por eso no hay sincronización propia (igual que Queues).

   isEnable(t)     t tiene marcado suficiente y, si es temporal, ya está en su ventana [α, ∞)
   vectorEnable()  sensibilizadas solo por marcado (sin mirar el tiempo)
   timeToWait(t)   ms que faltan para que t entre en su ventana; > 0 solo si t tiene marcado
                   y todavía no pasó α, 0 en cualquier otro caso
   isFinished()    ya se disparó targetInvariants veces la transición de cierre
*/

public final class PetriNet {

    private final int[][] pre;
    private final int[][] post;
    private final int[] marking;
    private final List<PlaceInvariant> placeInvariants;
    private final TimedTransitions timed;
    private final int[] firings;                 // disparos por transición
    private final int completionTransition;
    private final int targetInvariants;
    private int completedInvariants;

    public PetriNet(NetConfig cfg) {
        this.pre = cfg.getPre();                 // los getters ya devuelven copias
        this.post = cfg.getPost();
        this.marking = cfg.getInitialMarking();
        this.placeInvariants = cfg.getPlaceInvariants();
        this.timed = new TimedTransitions(cfg.getAlpha());
        this.firings = new int[getTransitionCount()];
        this.completionTransition = cfg.getCompletionTransition();
        this.targetInvariants = cfg.getTargetInvariants();
        this.completedInvariants = 0;

        checkPlaceInvariants();                  // el marcado inicial también tiene que cumplirlos

        // Las que arrancan sensibilizadas empiezan a contar su α desde ahora
        updateEnabledSince(new boolean[getTransitionCount()], -1);
    }

    // t se puede disparar ya: marcado suficiente y, si es temporal, dentro de la ventana
    public boolean isEnable(int t) {
        checkTransition(t);
        return isMarkingEnabled(t) && timed.inWindow(t, now());
    }

    // Dispara t: M' = M - pre[.][t] + post[.][t]
    public void fire(int t) {
        if (!isEnable(t)) {
            throw new IllegalStateException("T" + t + " no está habilitada (marcado " + Arrays.toString(marking) + ")");
        }
        boolean[] before = vectorEnable();

        for (int p = 0; p < marking.length; p++) {
            marking[p] += post[p][t] - pre[p][t];
        }
        firings[t]++;
        if (t == completionTransition) {
            completedInvariants++;
        }

        updateEnabledSince(before, t);
    }

    // Requisito 12: se llama después de cada disparo
    public void checkPlaceInvariants() {
        for (PlaceInvariant inv : placeInvariants) {
            if (!inv.holds(marking)) {
                throw new PlaceInvariantException("No se cumple " + inv + " con el marcado "
                        + Arrays.toString(marking));
            }
        }
    }

    // Vs: vs[t] == true si t tiene marcado suficiente (no mira el tiempo)
    public boolean[] vectorEnable() {
        boolean[] vs = new boolean[getTransitionCount()];
        for (int t = 0; t < vs.length; t++) {
            vs[t] = isMarkingEnabled(t);
        }
        return vs;
    }

    // ms que le faltan a t para entrar en su ventana de tiempo (0 si no tiene marcado o ya está en la ventana)
    public long timeToWait(int t) {
        checkTransition(t);
        if (!isMarkingEnabled(t)) {
            return 0;
        }
        return timed.timeToWait(t, now());
    }

    public boolean isFinished() {
        return completedInvariants >= targetInvariants;
    }

    public int getTransitionCount() {
        return pre[0].length;
    }

    public int[] getMarking() {
        return marking.clone();
    }

    public int[] getFirings() {
        return firings.clone();
    }

    public int getCompletedInvariants() {
        return completedInvariants;
    }

    // ----------------------------------------------------------------------------------------

    private boolean isMarkingEnabled(int t) {
        for (int p = 0; p < marking.length; p++) {
            if (marking[p] < pre[p][t]) {
                return false;
            }
        }
        return true;
    }

    /*
     Actualiza desde cuándo está sensibilizada cada transición, comparando con antes del disparo:
       - recién sensibilizada                          -> arranca a contar α ahora
       - la que se acaba de disparar y sigue sensibilizada -> nueva sensibilización, reinicia α
       - dejó de estar sensibilizada                   -> se borra la marca de tiempo
       - seguía sensibilizada y no se disparó          -> no se toca (conserva lo que ya esperó)
    */
    private void updateEnabledSince(boolean[] before, int fired) {
        long now = now();
        boolean[] after = vectorEnable();
        for (int t = 0; t < after.length; t++) {
            if (!after[t]) {
                timed.clear(t);
            } else if (!before[t] || t == fired) {
                timed.setEnabledSince(t, now);
            }
        }
    }

    private void checkTransition(int t) {
        if (t < 0 || t >= getTransitionCount()) {
            throw new IllegalArgumentException("Transición inexistente: T" + t);
        }
    }

    // Reloj monotónico en ms (no salta si cambia la hora del sistema)
    private static long now() {
        return System.nanoTime() / 1_000_000L;
    }
}
