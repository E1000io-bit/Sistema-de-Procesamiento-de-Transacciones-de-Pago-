package tp;

/*
 Hilo de la simulación. run() es igual para todos (template method): repite la secuencia
 de transiciones que da getSequence() hasta que fireTransition devuelve false, que significa
 que la simulación terminó (req. 3). Cada subclase solo define su secuencia.
*/
public abstract class Worker implements Runnable{

    private final MonitorInterface monitor;

    protected Worker(MonitorInterface monitor){
        this.monitor = monitor;
    }

    protected abstract int[] getSequence();

    @Override
    public final void run() {   // final: las subclases no cambian el ciclo, solo la secuencia
        int[] sequence = getSequence();
        while (true) {
            for (int transicion : sequence) {
                boolean disparo = monitor.fireTransition(transicion);
                if (!disparo) {
                    return;
                }
            }
        }
    }
}
