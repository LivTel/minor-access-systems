import java.rmi.Remote;
import java.rmi.RemoteException;

public interface OcrProcessingListener extends Remote {
    
    public void ocrRequestReceived(long time, long id, int type) throws RemoteException;

    public void ocrRequestCompleted(long time, long id) throws RemoteException;

    public void ocrRequestFailed(long time, long id, int code, String message) throws RemoteException;

}