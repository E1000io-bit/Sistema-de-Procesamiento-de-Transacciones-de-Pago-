package tp;

public class HighRiskWorker extends Worker{

    private static final int[] SEQUENCE = {4, 5};
    public HighRiskWorker(MonitorInterface monitor){
        super(monitor);
    }

    @Override
    protected int[] getSequence(){
        return SEQUENCE.clone(); // copia: nadie puede cambiar la secuencia desde afuera
    }
}
