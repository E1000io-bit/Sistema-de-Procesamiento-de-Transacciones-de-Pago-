package tp;

public class AdmissionWorker extends Worker{

    private static final int[] SEQUENCE = {0};

    public AdmissionWorker(MonitorInterface monitor){
        super(monitor);
    }

    @Override
    protected int[] getSequence(){
        return SEQUENCE.clone(); // copia: nadie puede cambiar la secuencia desde afuera
    }
}
