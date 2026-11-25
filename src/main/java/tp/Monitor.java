package tp;

import java.io.IOException;
import java.io.UncheckedIOException;


public class Monitor implements MonitorInterface {

    private final Mutex mutex;
    private final Queues queues;
    private final PetriNet rdp;
    private final Policy policy;

    /**
     * Lo pone en true finish(): cuando se completaron los invariantes pedidos o cuando se cortó
     * la simulación por un error. Lo leen los hilos que despiertan de una cola.
     */
    private volatile boolean finished = false;

    public Monitor(PetriNet rdp, Policy policy) {
        this.rdp = rdp;
        this.policy = policy;
        this.mutex = new Mutex();
        this.queues = new Queues(rdp.getTransitionCount());
    }

    @Override
    public boolean fireTransition(int transition) {
        mutex.acquire();
        boolean k = true;

        while (k) {
            // break [simulación terminada]: se completaron los invariantes, o se cortó por un error
            if (finished || rdp.isFinished()) {
                finish();
                return false;
            }

            k = rdp.isEnable(transition);

            if (k) {
                // [k == true]: t se puede disparar
                try {
                    rdp.fire(transition);
                    rdp.checkPlaceInvariants();
                    TransitionLogger.record(transition, Thread.currentThread().getName());

                } catch (PlaceInvariantException e) {
                    abort("se violo un invariante de plaza despues de disparar T" + transition);
                    throw e;

                } catch (IOException e) {
                    abort("no se pudo escribir el log despues de disparar T" + transition);
                    throw new UncheckedIOException(e);
                }

                boolean[] vc = queues.someOneThere();  // con hilos esperando
                boolean[] vs = rdp.vectorEnable();
                boolean[] m = and(vs, vc);

                if (!isEmpty(m)) {
                    // [m != 0]: la política elige a quién despertar.
                    // Signal and exit: el hilo despertado hereda el mutex, por eso acá no hay release().
                    int next = policy.choose(m);
                    queues.awake(next);
                    TransitionLogger.writeDecision(m, next);

                } else {
                    // [m == 0]: nadie para despertar
                    mutex.release();
                }
                return true;
            }

            // [k == false]: t no se puede disparar
            if (rdp.timeToWait(transition) > 0) {
                // Tiene marcado pero todavía no pasó α: duerme fuera del mutex y reintenta.
                waitTimeWindow(transition);
            } else {
                // register(t) va antes del release(): así el próximo hilo que entre ya lo ve
                // en someOneThere(), aunque todavía no se haya bloqueado en la cola.
                queues.register(transition);
                mutex.release();
                queues.await(transition);

                if (finished) {
                    // Lo despertó finish() con awakeAll(): no tiene el mutex, así que no lo libera.
                    return false;
                }
                // Lo despertó awake(t): heredó el mutex.
            }
            k = true; // reintenta
        }
        return false; // no se llega nunca: el loop siempre sale con return
    }

    /** CÓDIGO ERRÓNEO, LA POLITICA NO DEBERÍA FILTRAR
     * SIMPLEMENTE HACER EL CHOOSE LUEGO DE LA TRANSICION DISPARADA
     * true si la política deja disparar t en este momento. Si la frena, el hilo va a la cola
     * como si t no estuviera sensibilizada, y lo despiertan cuando la política lo permita.

    private boolean allowedNow(int t) {
        return rdp.vectorEnable()[t] && queues.someOneThere()[t];
    } */

    /** m = Vs AND Vc, posición por posición. */
    private boolean[] and(boolean[] vs, boolean[] vc) {
        boolean[] m = new boolean[vs.length];
        for (int i = 0; i < vs.length; i++) {
            m[i] = vs[i] && vc[i];
        }
        return m;
    }

    /** true si m == 0, es decir, si no hay ninguna transición para despertar. */
    private boolean isEmpty(boolean[] m) {
        for (boolean b : m) {
            if (b) {
                return false;
            }
        }
        return true;
    }

    /**
     * Semántica temporal: suelta el mutex, duerme lo que le falta a t para entrar en su
     * ventana de tiempo y vuelve a tomar el mutex.
     */
    private void waitTimeWindow(int t) {
        long ms = rdp.timeToWait(t);
        mutex.release();
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        mutex.acquire();
    }

    /**
     * Corta la simulación por un error. Se llama con el mutex tomado, antes de volver a tirar
     * la excepción: avisa por consola y hace finish() para que el resto de los hilos termine.
     */
    private void abort(String reason) {
        System.err.println("Simulacion cortada por " + Thread.currentThread().getName() + ": " + reason);
        finish();
    }

    /**
     * Fin de la simulación: despierta a todos los hilos que estaban en las colas y suelta el
     * mutex. Se llama con el mutex tomado.
     */
    private void finish() {
        finished = true;
        queues.awakeAll();
        mutex.release();
    }
}
