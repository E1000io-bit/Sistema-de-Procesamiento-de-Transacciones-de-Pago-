package tp;

public class CardWorker extends Worker{

    private static final int[] SEQUENCE = {1, 2, 3};
    public CardWorker(MonitorInterface monitor){
        super(monitor);
    }

    @Override
    protected int[] getSequence(){
        return SEQUENCE.clone(); // copia: nadie puede cambiar la secuencia desde afuera
    }
}
