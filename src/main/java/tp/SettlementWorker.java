package tp;

public class SettlementWorker extends Worker{

    private static final int[] SEQUENCE = {9};
    public SettlementWorker(MonitorInterface monitor){
        super(monitor);
    }

    @Override
    protected int[] getSequence(){

    }
}
