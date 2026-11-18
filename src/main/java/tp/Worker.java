package tp;

public abstract class Worker implements Runnable{

    private final MonitorInterface monitor;

    protected Worker(MonitorInterface monitor){
        this.monitor = monitor;
    }

    protected abstract int[] getSequence();

    @Override
    public void run() {
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
