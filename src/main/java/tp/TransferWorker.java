package tp;

public class TransferWorker extends Worker {
    //Transferencia bancaria
    private static final int[] SEQUENCE = {6, 7 ,8};
    public TransferWorker(MonitorInterface monitor){
        super(monitor);
    }

    @Override
    protected int[] getSequence(){
        return SEQUENCE.clone(); // copia: nadie puede cambiar la secuencia desde afuera
    }
}
