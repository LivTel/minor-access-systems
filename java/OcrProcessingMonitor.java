import java.rmi.Remote;
import java.rmi.RemoteException;

public interface OcrProcessingMonitor extends Remote {

    public void addOcrProcessingListener(OcrProcessingListener ol) throws RemoteException;

    public void removeOcrProcessingListener(OcrProcessingListener ol) throws RemoteException;

}
