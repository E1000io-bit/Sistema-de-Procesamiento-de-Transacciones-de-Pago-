package tp;

public class AdmissionWorker extends Worker{
    //Antes del conflicto en P1

    private static final int[] SEQUENCE = {0};

    public AdmissionWorker(MonitorInterface monitor){
        super(monitor);
    }

    @Override
    protected int[] getSequence(){
        return SEQUENCE;
    }
}
