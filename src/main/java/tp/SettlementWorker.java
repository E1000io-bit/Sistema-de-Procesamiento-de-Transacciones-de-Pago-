package tp;

public class SettlementWorker extends Worker{
    //Despues del join
    private static final int[] SEQUENCE = {9};
    public SettlementWorker(MonitorInterface monitor){
        super(monitor);
    }

    @Override
    protected int[] getSequence(){
        return SEQUENCE.clone(); // copia: nadie puede cambiar la secuencia desde afuera
    }
}
