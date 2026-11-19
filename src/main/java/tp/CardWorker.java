package tp;

public class CardWorker extends Worker{
    //Es la tarjeta de credito/debito
    private static final int[] SEQUENCE = {1, 2, 3};
    public CardWorker(MonitorInterface monitor){
        super(monitor);
    }

    @Override
    protected int[] getSequence(){
        return SEQUENCE;
    }
}
