//
// The OSS Proxy does not belong to any package.
//
import ngat.net.*;
import ngat.util.*;
import ngat.util.logging.*;
import ngat.message.base.*;
import ngat.message.OSS.*;
import ngat.message.POS_RCS.*;
import ngat.message.GUI_RCS.*;

import java.io.*;
import java.net.*;
import javax.net.ssl.*;
import javax.security.cert.*;
import java.util.*;
import java.text.*;

/** Acts as a proxy for any remote clients which wish to access the OSS
 * and PCA Servers at the Telescope LAN. 
 * The Proxy performs authentication (via SSL) and could be used to do 
 * load-control if required. 
 * This could be done via automatic monitoring of response times and client 
 * connection rates.
 * The OAR acts as launcher for this application.
 */ 
public class ProxyServer extends ControlThread implements Logging {

    /** Error code: Indicates that the OSS Server would not accept connections.*/
    public static final int REMOTE_CONNECTION_REFUSED = 710801;

    /** Error code: Indicates that the OSS Server cannot be reached (config or DNS problem).*/
    public static final int REMOTE_HOST_UNREACHABLE = 710802;
    
    /** Error code: Indicates a DNS or config problem typically.*/
    public static final int REMOTE_HOST_UNKNOWN = 710803;
    
    /** Error code: Indicates a problem opening a TCP socket connection to the OSS .*/
    public static final int REMOTE_SOCKET_ERROR = 710804;

    /** Error code: Indicates a general TCP problem.*/
    public static final int REMOTE_IO_ERROR = 710805;
    
    /** Error code: Indicates some very bad i/o problem.*/
    public static final int REMOTE_SOCKET_NO_SOCKET = 710806;
    
    /** Error code: Indicates a problem opening the output stream to the OSS.*/
    public static final int REMOTE_SOCKET_OSTREAM_ERROR = 710807;
    
    /** Error code: Indicates a serialization version problem.*/
    public static final int REMOTE_SOCKET_ISTREAM_SERIALIZATION_ERROR = 710808;
    
    /** Error code: Indicates a problem opening the input stream from the OSS.*/
    public static final int REMOTE_SOCKET_ISTREAM_ERROR = 710809;
    
    /** Error code: Indicates that the request from the client was not read.*/
    public static final int NO_READ_COMMAND = 710810;
    
    /** Error code: Indicates that the request could not be forwarded.*/
    public static final int NO_SEND_COMMAND = 710820;

    /** Error code: Indicates a surprising response class from the OSS.*/
    public static final int UNEXPECTED_RESPONSE_CLASS = 710830;
    
    /** Error code: Indicates no valid response class was forthcoming from the OSS.*/
    public static final int NO_READ_RESPONSE = 710840;
    
    /** Error code: Indicates an unknown class of command.*/
    public static final int UNKNOWN_COMMAND_CLASS = 710850;
    

    static final String CLASS = "ProxyServer";
    
    /** Counts no. of ConnectionThreads made.*/
    static int connectCount;
    
    /** IP Address of primary client.*/
    protected String primaryClient;

    /** Name of the forwarding host.*/
    protected String realHost;

    /** Connection port at forwarding server.*/
    protected int realPort;

    /** Name of the Primary OSS host.*/
    protected String ossHost1;

    /** Name of the Secondary OSS host.*/
    protected String ossHost2;

    /** Name of the POS host.*/
    protected String posHost;

    /** Name of the GLShost.*/
    protected String glsHost;

    /** Connection port at Primary OSS server.*/
    protected int ossPort1;

    /** Connection port at Secondary OSS server.*/
    protected int ossPort2;

    /** Connection port at POS server.*/
    protected int posPort;

    /** Connection port at proxy server.*/
    protected int proxyPort;

    /** GLS port.*/
    protected int glsPort;

    /** Socket for proxy connection.*/
    protected ServerSocket proxySocket;

    protected boolean secure;

    /** Error log.*/
    Logger errorLog;

    /** Operational trace log.*/       
    Logger traceLog;     

    /** Network traffic log.*/
    Logger trafficLog;    

    /** Create a Proxy server using the specified parameters.
     * @param ossHost Name of the OSS host.
     * @param proxyHost Name of the Proxy host.
     * @param ossPort Connection port at OSS server.
     * @param proxyPort Connection port at proxy server.
     */
    public ProxyServer(int proxyPort, boolean secure) {
	super("PROXY-SERVER", true);

	this.proxyPort = proxyPort;
	this.secure    = secure;

	// Logging.
	errorLog       = LogManager.getLogger("ERROR");
	traceLog       = LogManager.getLogger("TRACE");
	trafficLog     = LogManager.getLogger("TRAFFIC");

	errorLog.setLogLevel(3);
	traceLog.setLogLevel(3);
	trafficLog.setLogLevel(3);

	errorLog.setChannelID("OAR_ERROR");
	traceLog.setChannelID("OAR_TRACE");
	trafficLog.setChannelID("OAR_TRAFFIC");
	
	SimpleLogFormatter slf = new SimpleLogFormatter();
	ConsoleLogHandler console = new ConsoleLogHandler(slf);
	console.setLogLevel(3);
	errorLog.addHandler(console);
	traceLog.addHandler(console);
	trafficLog.addHandler(console);

    }
    
    protected void setOssHost1(String h) { this.ossHost1 = h;}
    protected void setOssPort1(int    p) { this.ossPort1 = p; }

    protected void setOssHost2(String h) { this.ossHost2 = h;}
    protected void setOssPort2(int    p) { this.ossPort2 = p; }

    protected void setPosHost(String  h) { this.posHost  = h;}
    protected void setPosPort(int     p) { this.posPort  = p; }

    protected void setGlsHost(String g) {this.glsHost = g;}
    protected void setGlsPort(int p) { this.glsPort = p;}

    protected void setPrimaryClient(String c) { this.primaryClient = c; }

    /** Initialize the Proxy - load configuration settings and start running.*/
    protected void initialise() {

	
	    try {
		DatagramLogHandler dlh = new DatagramLogHandler(glsHost, glsPort);
		dlh.setLogLevel(3);
		traceLog.addHandler(dlh);
		errorLog.addHandler(dlh);
		trafficLog.addHandler(dlh);
	    } catch (Exception e) {
		//throw new IllegalArgumentException("Creating Datagram handler: "+e);
	    }

	
	if (secure) {
	    SSLServerSocketFactory ssf = (SSLServerSocketFactory)SSLServerSocketFactory.getDefault();
	    try {
		proxySocket = ssf.createServerSocket(proxyPort);
		((SSLServerSocket)proxySocket).setNeedClientAuth(true);
		traceLog.log(INFO, 1, CLASS, getName(), "init", 
			     "Started ProxyServer on port: "+proxyPort+
			     " ** SECURE CONNECTION - CLIENT MUST AUTHENTICATE **");
	    } catch (IOException iox) {
		errorLog.log(ERROR, 1, CLASS, getName(), "init", 
			     "Error creating SECURE server socket: "+iox); 
		terminate(); 
		return;
	    }	    
	} else {
	    try {
		proxySocket = new ServerSocket(proxyPort); 
		traceLog.log(INFO, 1, CLASS, getName(), "init", 
			     "Started ProxyServer on port: "+proxyPort+
			     " ** WARNING - NON-SECURE CONNECTION **");
	    } catch (IOException iox) {
		errorLog.log(ERROR, 1, CLASS, getName(), "init", 
			     "Error creating NON-SECURE server socket: "+iox); 
		terminate(); 
		return;
	    }	 
	}
	
	// Set SO-Timeout to check whether Launcher has been terminated() yet.
	try {
	    proxySocket.setSoTimeout(600000);
	} catch (SocketException soe1) {
	    errorLog.log(ERROR, 1, "Error setting Server Socket timeout: ", soe1.getMessage());
	    errorLog.dumpStack(2, soe1);
	}
    }
    
    /** Listen for client connections and forward to oss host.*/ 
    protected void mainTask() {

	Socket clientSocket = null;
		
	// Listen for Client connections. Timeout regularly to check for termination signal.
	while (canRun() && !isInterrupted()) {  
	    try {
		clientSocket = proxySocket.accept();
		trafficLog.log(INFO, 1, "ProxyServer", getName(), "mainTask",
			       "Client attached from: " + clientSocket.getInetAddress().getHostAddress()+
			       " port: " + clientSocket.getPort());	   
	    } catch (InterruptedIOException iie1) {
		// Socket timed-out so try again
		errorLog.log(ERROR, 1, "Server socket timeout: "+iie1);
	    } catch (IOException ie3) {
		errorLog.log(ERROR, 1, "Error connecting to client");
		clientSocket = null;
	    }
	    if (clientSocket != null) break; // got a connection.
	}
	// 
	if (clientSocket != null) {
	    trafficLog.log(INFO, 1, "ProxyServer", getName(), "main",
			   "Creating Connection Thread for Client at: ["+
			   clientSocket.getInetAddress().getHostAddress() + "] on port: [" + 
			   proxySocket.getLocalPort() + "]");
	    ProxyConnectionThread proxyConnectionThread = new ProxyConnectionThread(clientSocket, connectCount++);
	    
	    if (proxyConnectionThread == null) {
		errorLog.log(ERROR, 1, "Error generating Proxy Connection Thread for client: ");
	    } else {
		trafficLog.log(INFO, 2, "Starting Proxy Connection Thread for client connection: ");
		proxyConnectionThread.start();
	    }
	}
    }
    
    /** Shutdown the Proxy.*/
    protected void shutdown() {
	traceLog.log(INFO, 1, CLASS, getName(), "shutdown", "Shutting down now.");
	// Close the ServerConnection
	if (proxySocket != null) {
	    try {		
		proxySocket.close();	
		traceLog.log(INFO, 1, CLASS, getName(), "shutdown",
			     "Closed down server:");
	    } catch (IOException iox) {
		errorLog.log(ERROR, 1, CLASS, getName(), "shutdown",
			     "Error closing down server: "+iox);
	    }
	}
    }
    
    /** Launch the Proxy settings from configuration file and set running.
     * The command line to start up is:<br>
     * java ProxyServer <config-file>.*/
    public static void main(String args[]) {
	if (args.length < 1) {
	    System.err.println("Error starting Proxy:");
	    usage();
	    return;
	}

	FileInputStream in = null;
	File file = new File(args[0]);
	try {
	    in = new FileInputStream(file);
	} catch (IOException iox) {
	    System.err.println("Error opening config file:");
	    return;
	}

	ConfigurationProperties config = new ConfigurationProperties();
	try {
	    config.load(in);
	} catch (IOException iox) {
	    System.err.println("Error loading Proxy server config: "+iox);
	    return;
	}
	
	String ossHost1  = config.getProperty("oss.host.1",  "localhost");
	String ossHost2  = config.getProperty("oss.host.2",  "localhost");
	String posHost   = config.getProperty("pos.host",  "localhost");

	int    ossPort1  = config.getIntValue("oss.port.1",   6870);
	int    ossPort2  = config.getIntValue("oss.port.2",   6870);
	int    posPort   = config.getIntValue("pos.port",   6870);
	
	int    proxyPort = config.getIntValue("proxy.port",  8990);
	boolean secure   = (config.getProperty("secure") != null);

	String pc = config.getProperty("primary.client", "localhost");

	String glsHost = config.getProperty("gls.host", "localhost");
	int glsPort    = config.getIntValue("gls.port", 2371); 
	
	System.err.println("Starting "+(secure ? "SECURE" : "NON_SECURE")+" Proxy on port: "+proxyPort+
			   " Relaying to OSS: "+ossHost1+" : "+ossPort1+" (Primary:"+pc+") "+
			   ossHost2+" : "+ossPort2+   " (Secondary) and POS: "+posHost+" : "+posPort);
	
	ProxyServer server = new ProxyServer(proxyPort, secure);
	
	server.setOssHost1(ossHost1);
	server.setOssPort1(ossPort1);
	server.setOssHost2(ossHost2);
	server.setOssPort2(ossPort2);
	server.setPrimaryClient(pc);
	server.setPosHost(posHost);
	server.setPosPort(posPort);
	server.setGlsHost(glsHost);
	server.setGlsPort(glsPort);
	server.start();
    
    }

    private class ProxyConnectionThread extends ControlThread {

	/** Socket associated with current client connection. */
	protected Socket clientSocket;

	/** Socket associated with current oss server connection. */
	protected Socket realSocket;
	
	/** Input stream from real server.*/
	ObjectInputStream rin;

	/** Input stream from client.*/
	ObjectInputStream cin;
	
	/** Output stream to client.*/
	ObjectOutputStream cout;
	
	/** Output stream to real server.*/
	ObjectOutputStream rout;

	/** The command.*/
	COMMAND command = null;

	/** Tha ACK.*/
	ACK ack = null;

	/** Counts the ACKs.*/
	int acks = 0;

	Object obj = null;

	/** The response.*/
	COMMAND_DONE done = null;

	ProxyConnectionThread(Socket clientSocket, int cc) {	    
	    super("PROXY_CONNECT#"+(cc), false);	    
	    this.clientSocket = clientSocket;	
	} // (Constructor).



	/** Set up I/O streams between here and client and real server. */
	protected void initialise() {

	    // Make connections to Client.
	    try {
		//clientSocket.setSoTimeout(20000);
		clientSocket.setTcpNoDelay(true);   // send small packets immediately.
		clientSocket.setSoLinger(true, 600); // give up and close after 5mins.
		cin = new ObjectInputStream(clientSocket.getInputStream());
		trafficLog.log(INFO, 1, 
			       "Opened INPUT stream from Client: " + clientSocket.getInetAddress()+":"+
			       clientSocket.getPort()+" timeout: "+clientSocket.getSoTimeout()+" secs.");
	    } catch (StreamCorruptedException sce) {
		errorLog.log(ERROR, 1, 
			     "Fatal Error opening input stream from client - corrupt header: ", sce);
		errorLog.dumpStack(2, sce);
		terminate();
		return;
	    } catch (IOException ie1) {
		errorLog.log(ERROR, 1, 
			     "Error opening input stream from Client: " + clientSocket.getInetAddress()+
			     ":"+clientSocket.getPort());
		errorLog.dumpStack(2, ie1);
		terminate();
		return;
		// cant send Error message as connect failed. Client will see IOException.
	    }
	    
	    try {
		cout = new ObjectOutputStream(clientSocket.getOutputStream());
		cout.flush();
		trafficLog.log(INFO, 1, 
			       "Opened OUTPUT stream to Client: " + clientSocket.getInetAddress()+":"+
			       clientSocket.getPort()+" and flushed header:");
	    } catch (IOException ie2) {
		errorLog.log(ERROR, 1, 
			     "Error opening output stream to Client: " + clientSocket.getInetAddress() +
			     ":" + clientSocket.getPort());
		errorLog.dumpStack(2, ie2);
		terminate();
		return;
		// cant send Error message as connect failed. Client will see IOException.
	    }	
	    

	    traceLog.log(INFO, 1, CLASS, getName(), "initialise",
			 "Starting special authentication using mechanism: NOCHECK");
	    
	    if (secure) {
		String clientInfo = null;
		SSLSession session = ((SSLSocket)clientSocket).getSession();
		X509Certificate[] chain = null;
		try {
		    chain = session.getPeerCertificateChain();
		    X509Certificate         cert = chain[0];
		    java.security.Principal cp   = cert.getSubjectDN();
		    String                  cn   = cp.getName();
		    
		    StringTokenizer tok  = new StringTokenizer(cn, ",");
		    StringBuffer    buff = new StringBuffer();
		    String          item = null;
		    while (tok.hasMoreTokens()) {
			item = tok.nextToken();
			if (item.indexOf("=") != -1) {
			    String key = item.substring(0, item.indexOf("=")).trim();
			    String val = item.substring(item.indexOf("=")+1).trim();
			    buff.append("\n\t"+key+"\t=\t"+val);
			    if (key.equals("O"))
				clientInfo = val; 
			}
		    }
		    
		    traceLog.log(INFO, 1, CLASS, getName(), "initialise",
				 "Special authentication: Client Verified:"+buff.toString());
		} catch (SSLPeerUnverifiedException px) {
		    errorLog.log(WARNING, 1, CLASS, getName(), "initialise",
				 "Special authentication: Could not verify peer: "+px);
		}
	    }
	  
	    //  Read the data from the Client.
	    try {
		command = readCommand();
	    } catch (IOException iox) {
		autoAck("Automatic-Response#0");
		sendError("Automatic-Response#0", NO_READ_COMMAND, "Failed to read command: "+iox);
		return;
	    }
	    
	    if (command instanceof TRANSACTION) {


		// Match client to ongoing OSS connection -
		// Currently we only have a single primary address and default secondary.
		String cip = clientSocket.getInetAddress().getHostAddress();

		if (cip.equals(primaryClient)) {
		    realHost = ossHost1;
		    realPort = ossPort1; 
		    traceLog.log(INFO, 1, CLASS, getName(), "initialise",
				 "Ongoing to Primary OSS");
		} else {
		    realHost = ossHost2;
		    realPort = ossPort2;
		    traceLog.log(INFO, 1, CLASS, getName(), "initialise",
				 "Ongoing to Secondary OSS");
		}
	    } else if
		(command instanceof GUI_TO_RCS) {
		realHost = posHost;
		realPort = posPort;
		traceLog.log(INFO, 1, CLASS, getName(), "initialise",
			     "Ongoing to Secondary POS");
	    } else 
		sendError("Automatic-Response#0", 
			  UNKNOWN_COMMAND_CLASS, 
			  "Unknown command class: "+
			  (command != null ? command.getClass().getName() :  "NULL"));
	    
	    // Make connections to Real Server.

	    try {
		realSocket = new Socket(realHost, realPort);
		realSocket.setTcpNoDelay(true);    // small packets so send immediately.
		realSocket.setSoLinger(true, 600); // give up and close after 5mins.
		trafficLog.log(INFO, 1, 
			       "Connection established to Real-Server: host: "+realHost+" port: "+realPort+" IP: "+
			       realSocket.getInetAddress().getHostAddress());
	    } catch (ConnectException cx) {
		errorLog.log(ERROR, 1, "Relay Connection Refused: The server may be down or busy: ", cx);	     
		autoAck("Automatic_response#0");
		sendError("Automatic-Response#0", REMOTE_CONNECTION_REFUSED, "Connect-Error: "+cx);
		terminate();
		return;
	    } catch (NoRouteToHostException nx) {
		errorLog.log(ERROR, 1, "Relay Connection Refused: No route to host:", nx);		
		autoAck("Automatic_response#0");
		sendError("Automatic-Response#0", REMOTE_HOST_UNREACHABLE, "Connect-Error: "+nx);
		terminate();
		return;
	    } catch(UnknownHostException ux) {
		errorLog.log(ERROR, 1, "Relay Connection Refused: Unknown host:", ux);		
		autoAck("Automatic_response#0");
		sendError("Automatic-Response#0", REMOTE_HOST_UNKNOWN, "Connect-Error: "+ux);
		terminate();
		return;
	    } catch (SocketException sx) {
		errorLog.log(ERROR, 1, "Relay Socket Error: ", sx);		
		autoAck("Automatic_response#0");
		sendError("Automatic-Response#0", REMOTE_SOCKET_ERROR, "Connect-Error: "+sx);
		terminate();
		return;
	    } catch (IOException iox) {
		errorLog.log(ERROR, 1, "Relay IO Error: ", iox);		
		autoAck("Automatic_response#0");
		sendError("Automatic-Response#0", REMOTE_IO_ERROR, "Connect-Error: "+iox);
		terminate();
		return;
	    }
	    
	    if (realSocket == null) {
		autoAck("Automatic_response#0");
		sendError("Automatic-Response#0", REMOTE_SOCKET_NO_SOCKET, "Connect-Error: ");
		terminate();
		return;
	    }
	    
	    try {
		rout = new ObjectOutputStream(realSocket.getOutputStream());
		rout.flush();
		trafficLog.log(INFO, 1, 
			       "Opened output stream to Remote-Server: " + realSocket.getInetAddress()+":"+
			       realSocket.getPort()+" and flushed header:");
	    } catch (IOException iox) {
		errorLog.log(ERROR, 1, 
			     "Error opening output stream to Remote-Server: " + realSocket.getInetAddress() +
			     ":" + realSocket.getPort());
		errorLog.dumpStack(2, iox);
		autoAck("Automatic_response#0");
		sendError("Automatic-Response#0", REMOTE_SOCKET_OSTREAM_ERROR, "Connect-Error: "+iox); 
		terminate();
		return;
	    }		   
	    
	    try {		
		rin = new ObjectInputStream(realSocket.getInputStream());
		trafficLog.log(INFO, 1, 
			       "Opened input stream from Remote-Server: " + realSocket.getInetAddress()+":"+
			       realSocket.getPort()+" timeout: "+realSocket.getSoTimeout()+" secs.");
	    } catch (StreamCorruptedException scx) {
		errorLog.log(ERROR, 1,
			     "Error opening input stream from Remote-Server (corrupt header?): ", scx);
		errorLog.dumpStack(2, scx);
		autoAck("Automatic_response#0");
		sendError("Automatic-Response#0", REMOTE_SOCKET_ISTREAM_SERIALIZATION_ERROR, "Connect-Error: "+scx);
		terminate();
		return;
	    } catch (IOException iox) {
		errorLog.log(ERROR, 1, 
			     "Error opening input stream from Remote-Server: " + realSocket.getInetAddress()+
			     ":"+realSocket.getPort());
		errorLog.dumpStack(2, iox);
		autoAck("Automatic_response#0");
		sendError("Automatic-Response#0", REMOTE_SOCKET_ISTREAM_ERROR, "Connect-Error: "+iox); 
		terminate();
		return;
	    }
	    
	} // (initialise).
	
	protected void mainTask() {
	    
	  
	    // 1. Forward to Real-Server. 
	    try {
		sendCommand(command);
	    } catch (IOException iox) {
		autoAck("Automatic-Response#2");
		sendError("Automatic-Response#2", NO_SEND_COMMAND, "Failed to forward command to Remote-Server: "+iox);
		return;
	    }
	    
	    boolean complete = false;

	    // 2. Read stuff from Real-Server. 
	    while (! complete) {
		try {
		    obj = readReal();
		    if (obj instanceof ACK) {
			ack = readAck(obj);
			sendAck(ack);
		    } else if
			(obj instanceof COMMAND_DONE) {
			done = readDone(obj);
			complete = true;
			sendDone(done);
		    } else {
			autoAck("Automatic-Response#3");
			sendError("Automatic-Response#3", UNEXPECTED_RESPONSE_CLASS, 
				  "Unexpected response type ["+obj.getClass().getName()+
				  "] from Remote-Server: ");
		    }
		} catch (IOException iox) {
		    autoAck("Automatic-Response#4");
		    sendError("Automatic-Response#4", NO_READ_RESPONSE, 
			      "Failed to read valid response from Remote-Server: "+iox);
		    return;
		}
	    }
	    
	}
	
	private COMMAND readCommand() throws IOException {
	    COMMAND command = null;
	    try {
		command = (COMMAND)cin.readObject();
		trafficLog.log(INFO, 1, "Read Command from Client: "+(command != null ? command.getClass().getName() : "null"));
	    } catch (ClassNotFoundException cx) {
		errorLog.log(ERROR, 1, "Error reading request from Client - Unknown class:");
		errorLog.dumpStack(2, cx);
		throw new IOException("Error reading COMMAND from client: "+cx);
	    } 
	    return command;
	}

	private Object readReal() throws IOException {
	    Object obj = null;
	    try {
		obj = rin.readObject();
		trafficLog.log(INFO, 1, "Read Response from Real-Server:");
	    } catch (ClassNotFoundException cx) {
		errorLog.log(ERROR, 1, "Error reading response from Remote-Server - Unknown class:");
		errorLog.dumpStack(2, cx);
		throw new IOException("Error reading Response from Remote-Server: "+cx);
	    } 
	    return obj;
	}

	
	private ACK  readAck(Object obj) {
	    ACK ack = (ACK)obj;
	    return ack;
	}
	
	private COMMAND_DONE readDone(Object obj) {
	    COMMAND_DONE done = (COMMAND_DONE)obj;		
	    return done;
	}
	
	private void sendCommand(COMMAND command) throws IOException {
	    rout.writeObject(command);
	    rout.flush();
	    trafficLog.log(INFO, 1, "Forwarded Command to Real-Server:");
	}

	private void autoAck(String ackid) {
	    ACK ack = new ACK(ackid);
	    ack.setTimeToComplete(30000);
	    sendAck(ack);
	}
	
	private void sendAck(ACK ack) {	  
	    try {
		cout.writeObject(ack);
		cout.flush();
		trafficLog.log(INFO, 1, "Forwarded ACK "+(++acks)+" to Client:");
	    } catch (IOException iox) {
		errorLog.log(ERROR, 1, "Error sending ACK to client:", iox);
		errorLog.dumpStack(2, iox);
		terminate();
	    }
	}
	
	private void sendDone(COMMAND_DONE done) {
	    try {
		cout.writeObject(done);
		cout.flush();
		trafficLog.log(INFO, 1, "Forwarded DONE to Client: "+(done != null ? done.getClass().getName() : "null"));
	    } catch (IOException iox) {
		errorLog.log(ERROR, 1, "Error sending DONE to client:", iox);
		//error = true;
		errorLog.dumpStack(2, iox);
		terminate();
	    }
	}
	
	private void sendError(String id, int errorCode, String errorMessage) {
	    COMMAND_DONE error = 
		new COMMAND_DONE(id);
	    error.setSuccessful(false);
	    error.setErrorNum(errorCode);
	    error.setErrorString(errorMessage);
	    try {
		cout.writeObject(error);
		cout.flush();
		trafficLog.log(INFO, 1, "Forwarded ERROR to Client: "+errorCode+" : "+errorMessage);
	    } catch (IOException ie4) {
		errorLog.log(ERROR, 1, "Error sending ERROR to client:", ie4);
		//error = true;
		errorLog.dumpStack(2, ie4);
		terminate();
	    }
	}
	
	protected void shutdown() {
	    traceLog.log(INFO, 1, "ProxyServer", getName(), "main", "Shutting down now.");
	}
		
    } // Inner Class Def. [ProxyConnectionThread].

    static void usage() {
	System.err.println("Usage: java ProxyServer <config_file>");
    }

} // Class Def. [ProxyServer].
