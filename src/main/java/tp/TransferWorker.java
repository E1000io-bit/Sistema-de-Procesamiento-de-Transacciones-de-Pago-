package tp;

public class TransferWorker extends Worker {

    private static final int[] SEQUENCE = {6, 7 ,8};
    public TransferWorker(MonitorInterface monitor){
        super(monitor);
    }

    @Override
    protected int[] getSequence(){

    }
}
