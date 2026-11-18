package tp;

/**
 * Monitor de concurrencia que guía la ejecución de la red de Petri.
 *
 * Sigue el diagrama de secuencia de fireTransition(t):
 *
 * El hilo toma el mutex.
 * Si la simulación terminó, sale con false.
 * Si t está sensibilizada: la dispara, verifica los invariantes de plaza, registra el
 * disparo en el log y calcula m = Vs AND Vc. Si hay alguien para despertar, la política
 * elige a quién y se le pasa el mutex (signal and exit). Si no, se libera el mutex.
 * Si t no está sensibilizada: el hilo se anota en la cola de t, suelta el mutex y se
 * bloquea. Cuando lo despiertan hereda el mutex y vuelve a intentar.

 * El monitor no nombra ninguna transición concreta: todo lo saca de la PetriNet, las
 * colas y la política, así que cambiar la red no lo cambia. fireTransition es su único
 * método público.
 *
 * Lo que el monitor espera de PetriNet:
 * isEnable(t): t tiene marcado suficiente y, si es temporal, ya pasó su α.
 * vectorEnable(): transiciones sensibilizadas por marcado (sin mirar el tiempo).
 * timeToWait(t): milisegundos que faltan para que t entre en su ventana de tiempo.
 * Tiene que ser mayor que 0 siempre que t tenga marcado y todavía no haya pasado α
 * (redondeando para arriba): si devolviera 0 por redondeo, el hilo se iría a la cola
 * y nadie lo despertaría.
 * isFinished(): ya se completó la cantidad de invariantes pedida.
 *
 */
public class Monitor implements MonitorInterface {

    private final Mutex mutex;
    private final Queues queues;
    private final PetriNet rdp;
    private final Policy policy;

    /** Lo pone en true finish(). Lo leen los hilos que despiertan de una cola. */
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
            // break [simulación terminada]
            if (rdp.isFinished()) {
                finish();
                return false;
            }

            k = rdp.isEnable(transition);

            if (k) {
                // [k == true]: t se puede disparar
                rdp.fire(transition);
                rdp.checkPlaceInvariants();
                TransitionLogger.getInstance().record(transition, Thread.currentThread().getName());

                boolean[] vs = rdp.vectorEnable();    // sensibilizadas
                boolean[] vc = queues.someOneThere();  // con hilos esperando
                boolean[] m = and(vs, vc);

                if (!isEmpty(m)) {
                    // [m != 0]: la política elige a quién despertar.
                    // Signal and exit: el hilo despertado hereda el mutex, por eso acá no hay release().
                    int next = policy.choose(m);
                    queues.awake(next);
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
                queues.wait(transition);

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
     * Fin de la simulación: despierta a todos los hilos que estaban en las colas y suelta el
     * mutex. Se llama con el mutex tomado.
     */
    private void finish() {
        finished = true;
        queues.awakeAll();
        mutex.release();
    }
}
