package tp;

public class HighRiskWorker extends Worker{
    //Alto riesgo
    private static final int[] SEQUENCE = {4, 5};
    public HighRiskWorker(MonitorInterface monitor){
        super(monitor);
    }

    @Override
    protected int[] getSequence(){
        return SEQUENCE;
    }
}
