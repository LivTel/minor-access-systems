
import ngat.net.*;
import ngat.util.*;

import java.io.*;
import java.util.*;
import java.text.*;
import javax.net.*;
import javax.net.ssl.*;
import java.security.*;
import java.security.cert.*;

import ngat.rcs.gui.*;
import ngat.message.base.*;
import ngat.message.POS_RCS.*;

public class ScheduleTool {

    private static SimpleDateFormat rdf = new SimpleDateFormat("yyyyMMddHHmmss");
    private static SimpleDateFormat odf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss z");
    
    private static SimpleTimeZone UTC = new SimpleTimeZone(0, "UTC");

    private static final String DEFAULT_HOST = "localhost";
    
    private static final int    DEFAULT_PORT = 7900;

    private static final long   DEFAULT_TIMEOUT = 30000L;

    /** OSS Host.*/
    String host;

    /** OSS Port.*/
    int port;


    long id = 1L;

    public ScheduleTool() {

    }
    
    public static void main(String args[]) {
	
	rdf.setTimeZone(UTC);
	rdf.setLenient(false);
	odf.setTimeZone(UTC);
	odf.setLenient(false);

	ScheduleTool tool = new ScheduleTool();

	// Parse args.
	CommandParser parser = new CommandParser();

	try {
	    parser.parse(args);
	} catch (Exception e) {
	    System.err.println("Error parsing args: "+e);
	    return;
	}

	ConfigurationProperties config = parser.getMap();

	try {
	    tool.configure(config);
	} catch (IllegalArgumentException iax) {
	    System.err.println("Error configuring: "+iax);
	    return;
	}

        boolean check = (config.getProperty("check") != null);
	if (check) {
	  
	    try {
		tool.check();
	    } catch (Exception e) {
		System.err.println("Error during check: "+e);	
		e.printStackTrace();
	    }	
	    return;
	}

	String fileName = config.getProperty("upload");
	if (fileName != null) {
	    File file = new File(fileName);
	    try {
		tool.upload(file);
	    } catch (Exception e) {
		System.err.println("Error during upload: "+e);	
		e.printStackTrace();
	    }	
	    return;
	}

	System.err.println("Error: No command option specified");

    }

    private void configure(ConfigurationProperties config) throws IllegalArgumentException {
	host = config.getProperty("host", DEFAULT_HOST);
	port = config.getIntValue("port", DEFAULT_PORT);
    }
    
    /** Setup the SSL SocketFactory using keyinfo passed in system properties.*/ 
    private SSLSocketFactory setupSSL() throws IOException {	
	SSLSocketFactory secureSocketFactory = (SSLSocketFactory)SSLSocketFactory.getDefault();
	return secureSocketFactory;
    }

    /** Check the specified file conforms to current OCC schedule.*/
    private void check() throws IOException, IllegalArgumentException {

	// Load Local windows.
	//TreeSet local = loadWindows(file);
	
	// Download the schedule.
	TreeSet remote = null;
	try {
	    remote = downloadRemoteSchedule();
	} catch (Exception e) {
	    System.err.println("Error during check: "+e);
	    return;
	}

	// Check with local.
	WindowSchedule.TimeWindow win = null;
	Iterator it = remote.iterator();
	while (it.hasNext()) {
	    win = (WindowSchedule.TimeWindow)it.next();
	    System.out.println("Remote: "+win);
	}
	
	System.out.flush();

    }

    /** Upload the specified file to OCC.*/
    private void upload(File file) throws IOException, IllegalArgumentException {

	// Load Local windows.
	TreeSet local = loadWindows(file);

	// Upload the schedule.
	try {
	    uploadSchedule(local);
	} catch (Exception e) {

	}

    }

    private WindowSchedule.TimeWindow createWindow(String line) throws ParseException {

	StringTokenizer t = new StringTokenizer(line);

	if (t.countTokens() < 3)
	    throw new ParseException("Missing tokens",0);
	
	String st1 = t.nextToken();
	String st2 = t.nextToken();
	String rtoc= t.nextToken();
	
	if (st1.length() < 14)
	    throw new ParseException("Missing digits: Only "+st1.length()+" not 14 in start-time", 0);
	
	if (st2.length() < 14)
	    throw new ParseException("Missing digits: Only "+st2.length()+" not 14 in end-time", 0);
	
	Date d1 = rdf.parse(st1);
	Date d2 = rdf.parse(st2);

	return new WindowSchedule.TimeWindow(id++, "PCA", d1.getTime(), d2.getTime(), rtoc);

    }

    private TreeSet loadWindows(File file) throws IOException, IllegalArgumentException {

	// Load the windows vector from file.
	BufferedReader bin = new BufferedReader(new FileReader(file));
	
	TreeSet windows = new TreeSet(new WindowSchedule.WindowComparator());
	String line    = null;
	int    lino    = 0;
	WindowSchedule.TimeWindow win = null;
	
	while ( (line = bin.readLine()) != null) {
	    lino++;

	    if (line.trim().startsWith("#") ||
		line.trim().length() == 0)
		continue;

	    try {		
		win = createWindow(line);
		System.err.println("Adding a window: "+(win != null ? win.getClass().getName() : "NULL"));
		windows.add(win);
		System.err.println("Read: "+win);
	    } catch (ParseException px) {
		throw new IllegalArgumentException("CreateWindows: Line: "+lino+" : "+px+
						   " Index: "+px.getErrorOffset()+
						   "\n Detail ["+line+"]");	       
	    }
	}
	
	return windows;

    }

    /** Downloads and returns the currently scheduled windows.*/
    private TreeSet downloadRemoteSchedule() throws IOException {

	READ_WINDOWS rw = new READ_WINDOWS("schedule-tool");

	Handler handler = sendCommand(rw);
	    
	if (handler.isDone()) {

	    // Check its a READ_WINDOWS_DONE and extract

	    COMMAND_DONE done = handler.getResponse();
	    
	    if (done instanceof READ_WINDOWS_DONE) {

		TreeSet windows = ((READ_WINDOWS_DONE)done).getWindows();
		return windows;
	    } 
		
	}

	return null;

    }

    private void uploadSchedule(TreeSet local) throws IOException {
	
	SET_WINDOWS sw = new SET_WINDOWS("schedule-tool");
	sw.setWindows(local);

	Handler handler = sendCommand(sw);

    }

    private Handler sendCommand(COMMAND command) throws IOException {

	Handler handler = new Handler(command);

	SSLSocketFactory sf = setupSSL();
	
	JMSMA_ProtocolClientImpl protocol = 
	    new JMSMA_ProtocolClientImpl((JMSMA_Client)handler, 
					 new SocketConnection(host, 
							      port, 
							      sf));
	
	protocol.implement();
	
	// Wait while handler is not completed.
	long delay = DEFAULT_TIMEOUT;

	while (! handler.isDone() && ! handler.isFailed()) {
	    try {
		handler.waitFor(delay);
	    } catch (InterruptedException ix) {}
	}

	return handler;
	
    }
    
    
    private class  Handler extends JMSMA_ClientImpl {
	
	volatile boolean done;
	volatile boolean failed;

	COMMAND_DONE response;

	Handler(COMMAND command){
	    super();
	    timeout = 600000L;
	    this.command = command;
	    done   = false;
	    failed = false;
	}
	
	public void failedConnect(Exception e) {	   	  
	    System.err.println("Failed connect: "+e); 	  
	    failed = true;
	}
	
	public void failedDespatch(Exception e) {	   
	    System.err.println("Failed despatch: "+e);	 
	    failed = true;
	}
	
	public void failedResponse(Exception e) {	   
	    System.err.println("Failed response: "+e); 	 
	    failed = true;
	}
	
	public void exceptionOccurred(Object source, Exception e) {	   
	    System.err.println("Exception: Source: "+source+" Exc: "+e);  
	    failed = true;	   
	}

	public void handleAck(ACK ack) {	  
	    System.err.println("Received Ack: "+ack.getTimeToComplete()+" millis.");
	}
	
	public void handleDone(COMMAND_DONE response) {	   
	    System.err.println("Received Response: "+
			       "\n\tClass:   "+response.getClass().getName()+
			       "\n\tSuccess: "+response.getSuccessful()+
			       "\n\tError:   "+response.getErrorNum()+
			       "\n\tString:  "+response.getErrorString());	 
	    done = true;
	    this.response = response;
	}
	
	public void sendCommand(COMMAND command) {}

	public synchronized boolean isDone() { return done; }
	
	public synchronized boolean isFailed() { return failed; }
	
	public COMMAND_DONE  getResponse() {  return response; }

    }
        
}
