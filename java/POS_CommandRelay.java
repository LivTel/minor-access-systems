import ngat.astrometry.*;
import ngat.math.*;
import ngat.util.*;
import ngat.util.logging.*;
import ngat.net.*;
import ngat.message.base.*;
import ngat.message.POS_RCS.*;

import java.io.*;
import java.net.*;
import javax.net.ssl.*;
import javax.security.cert.*;
import java.util.*;
import java.text.*;

/** Temporary Text interface POS Command Relayserver. Users may telnet to the
 * speciified port and type in commands to control the POS. 
 *
 * NOTE THE KEEPALIVE TIMEOUT IS TEMP OVERRIDDEN FOR NOW !!!
 *
 *
 * <dl>
 * <dt><b>RCS:</b>
 * <dd>$Id: POS_CommandRelay.java,v 1.1 2006/11/17 09:53:45 snf Exp snf $
 * <dt><b>Source:</b>
 * <dd>$Source: /home/dev/src/planetarium/java/RCS/POS_CommandRelay.java,v $
 * </dl>
 * @author $Author: snf $
 * @version $Revision: 1.1 $
 */
public class POS_CommandRelay extends ControlThread implements Logging {

    /** Indicates that system should shutdown and not reboot.*/
    public static final int    SYSTEM_OFF = 0;
    
    /** Indicates that system should shutdown and reboot.*/
    public static final int    SYSTEM_REBOOT = 2;

    /**  Indicates the default ID of this server.*/
    public static final String DEFAULT_ID = "POS_GENERIC";
    
    /**  Indicates the default port to bind to.*/
    public static final int    DEFAULT_SERVER_PORT = 6563; 

    /** Indicates the default remote host address. (DNS Name OR IP quads).*/
    public static final String DEFAULT_REMOTE_HOST = "localhost";
    
    /**  Indicates the default remote host's server port.*/
    public static final int    DEFAULT_REMOTE_PORT = 6567; 
    
    /** Indicates the number of connection requests to queue - LOW for antihacking.*/
    public static final int    DEFAULT_BACKLOG = 3; 
    
    /**  Indicates the default reconnection delay after a client breaks connection.*/
    public static final long   DEFAULT_DELAY = -1L; 
   
    /**  Indicates the default special authentication method.*/
    public static final String DEFAULT_SPECIAL_AUTH = "XXX";

    /**  Indicates that NO special authentication is required.*/
    public static final String NO_SPECIAL_AUTHENTICATION = "NONE";
    
    /**  Indicates the default interactive prompt.*/
    public static final String DEFAULT_PROMPT = "\nPOS>>";
  
    /** Indicates the default socket keep-alive timeout (millis).*/
    public static final long   DEFAULT_KEEPALIVE_TIMEOUT = 8*3600*1000L;
  
    /** Indicates the default timeout period for waiting ACK and DONE.*/
    public static final long   DEFAULT_TIMEOUT = 30*1000L;

    /** Indicates the default line terminator.*/
    public static final String DEFAULT_TERMINATOR = "\n";

    /** Indicates the default Instrument-configuration file.*/
    public static final String DEFAULT_INST_CONFIG_FILE = "instrument.properties";

    /** Indicates the default request-counter file.*/
    public static final String DEFAULT_REQUEST_COUNTER_FILE = "request.uid";

    /** Indicates how many requests can go through before the lock file needs updating.*/
    public static final int    MAX_REQUEST_COUNT_INC = 50;

    /** Indicates the default camera control URL.*/
    public static final String DEFAULT_CAMERA_CONTROL_URL ="http://192.168.4.32/axis-cgi/io/output.cgi";

    /** Indicates the default camera control passphrase.*/
    public static final String DEFAULT_CAMERA_PASSPHRASE = "public:ftn";

    /** Indicates the default camera control ON action.*/
    public static final String DEFAULT_CAMERA_ON_ACTION = "action=1:\\";
    
    /** Indicates the default camera control OFF action.*/
    public static final String DEFAULT_CAMERA_OFF_ACTION = "action=1:/";

    /** Indicates the default log level for command responses.*/
    public static final int DEFAULT_COMMAND_LOG_LEVEL = 5;
	    
    /** CR line terminator.*/
    protected static String CR = "\n";
    
    /** (Long ISO8601) date formatter.*/
    static SimpleDateFormat iso8601 = new SimpleDateFormat("yyyy-MM-dd 'T' HH:mm:ss z");

    /** Date formatter (Long ISO8601).*/
    static SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");

    /** Date formatter (Medium SOD).*/
    static SimpleDateFormat mdf = new SimpleDateFormat("ddHHmmss");

    /** Date formatter (Day in year).*/
    static SimpleDateFormat adf = new SimpleDateFormat("yyyyMMdd");
    
    /** UTC Timezone.*/
    static final SimpleTimeZone UTC = new SimpleTimeZone(0, "UTC");

    /** Numberformatter.*/
    static NumberFormat nf2 = NumberFormat.getInstance();

    /** Numberformatter.*/
    static NumberFormat nf12 = NumberFormat.getInstance();

    static final String CLASS = "POS_CommandRelay";

    CommandParser parser;

    Map commandLogLevels;

    /** Holds the instruemntation config properties.*/
    ConfigurationProperties instConfig;

    /** Server Socket.*/
    ServerSocket serverSocket;

    /** Socket for client communication.*/
    Socket clientSocket;

    /** Current setting for keep-alive timeout (millis).*/
    long keepAliveTimeout;

   
    /** Stores details of each received command string against its 
     * assigned request number for use by GETQUEUE commands to allow
     * the original command text to be retrieved.*/
    HashMap commandMap;

    /** Stores the current list of connected clients, against their sessionIds.*/
    HashMap clientMap;

    // Config stuff.
       
    /** The port which this server is attached to.*/
    int port; 

    /** ID used for this POS in its POC_TO_RCS command IDs.*/
    String id;

    /** Reconnect delay time (msec).*/
    long delay;

    /** Interactive prompt string.*/
    String prompt = "\nPOS>>";

    /** Remote server host.*/
    String remoteHost;

    /** Remote server port.*/
    int remotePort;

    /** Server connection backlog - concurent requests queued.*/
    int backlog;

    /** True if secure connection. - Default is NOT*/
    boolean secure = false;
    
    /** Keyword for special authentication mechanism - NOT DEFINED.*/
    String specialAuthMethod;

    /** Line terminator.*/
    String terminator;

    /** True if camera control is required during slew.*/
    boolean cameraControlEnabled;

    /** Camera control address string.*/
    String cameraControlAddress;

    /** Camera control ON command.*/
    String cameraControlOnCommand;

    /** Camera control OFF command.*/
    String cameraControlOffCommand;

    /** Camera control pass.*/
    String cameraControlPassPhrase;

    /** the Camera controller.*/
    Axis2100LightController cameraController;

    /** Set True when a mosaic is beleived to be in progress.*/
    boolean mosaicInProgress;

    // Vars.
    volatile int connectionCount;

    volatile long requestCount = 0;

    volatile int requestCountInc = 0;

    /** Set by USERID command - identifies controlling user.*/
    String controlUserId;
    
    // Logging.
    Logger traceLog;
  
    /** Create a POS_CommandRelay using specified settings.*/
    public POS_CommandRelay(String id) {
	super(id+"_t", true);	
	this.id = id;
	iso8601.setTimeZone(UTC);
	sdf.setTimeZone(UTC);
	adf.setTimeZone(UTC);
	mdf.setTimeZone(UTC);

	// Setup logging.
	traceLog   = LogManager.getLogger(TRACE);

	//traceLog.setLogLevel(ALL);

	commandLogLevels = new HashMap();
	
	commandMap = new HashMap();
	
	clientMap  = new HashMap();

	// Setup formatting.
	nf2.setMaximumIntegerDigits(2);
	nf2.setMinimumIntegerDigits(2);
	nf2.setParseIntegerOnly(true);
	nf2.setGroupingUsed(false);
	nf12.setMaximumIntegerDigits(12);
	nf12.setMinimumIntegerDigits(12);
	nf12.setParseIntegerOnly(true);
	nf12.setGroupingUsed(false);

	requestCountInc = 0;
	
    }
    
    public static void main(String args[]) {

	if (args.length < 1) { 
	   usage();
	   return;
	}
	
	// Locate the config file.
	File configFile    = new File(args[0]);
	FileInputStream in = null;
	try {
	    in = new FileInputStream(configFile);
	} catch (IOException iox) {
	   System.err.println("Error locating config file: "+configFile.getPath()+" : "+iox);
	   usage();
	   return;
	}

	// Load config settings. 
	ConfigurationProperties config = new ConfigurationProperties();
	try {
	    config.load(in);
	} catch (IOException iox) {
	    System.err.println("Error loading config data: "+iox);
	    usage();
	    return;
	}

	// ### TEMP DEBUG
	config.list(System.err);

	// ID.
	String id = config.getProperty("id", DEFAULT_ID);
	
	// Setup logging from config.
	Enumeration e = config.propertyNames();
	while (e.hasMoreElements()) {
	    String key = (String)e.nextElement();
	    int index = key.indexOf(".logger");
	    if (index != -1) {
		String logname = key.substring(0, index);
		String strLogLevel = config.getProperty(key, "OFF");
		String strFileLevel    = config.getProperty(logname+".file", "OFF");
		String ftype = "TEXT-format";
		String flev  = "OFF";
		if (strFileLevel.trim().startsWith("html")) {
		    ftype = "HTML-format";
		    flev = strFileLevel.trim().substring(5);
		} else if
		    (strFileLevel.trim().startsWith("csv")) {
		    ftype = "CSV-format";		
		    flev = strFileLevel.trim().substring(4);
		} else if
		    (strFileLevel.trim().startsWith("txt")) {
		    ftype = "TEXT-format";		
		    flev = strFileLevel.trim().substring(4);
		} else if
		    (strFileLevel.trim().startsWith("xml")) {
		    ftype = "XML-format";		
		    flev = strFileLevel.trim().substring(4);
		} else {
		    ftype = "Bogstan-format";
		    flev = strFileLevel.trim();
		}

		int ll =  Logging.OFF;
		if (strLogLevel.equals("OFF"))
		    ll = Logging.OFF;
		else if
		    (strLogLevel.equals("ALL"))
		    ll = Logging.ALL;
		else { 
		    try {
			ll = Integer.parseInt(strLogLevel.trim());
		    } catch (NumberFormatException nx) {
			System.err.println("Error parsing cc: "+nx);
		    }		    
		}
		
		int ff =  Logging.OFF;
		if (flev.equals("OFF"))
		    ff = Logging.OFF;
		else if
		    (flev.equals("ALL"))
		    ff = Logging.ALL;
		else { 
		    try {
			ff = Integer.parseInt(flev.trim());
		    } catch (NumberFormatException nx) {
			System.err.println("Error parsing cc: "+nx);
		    }		    
		}
		
		LogFormatter ffmt = null;
		if (ftype.equals("HTML-format")) 
		    ffmt = new HtmlLogFormatter();
		else if
		    (ftype.equals("TEXT-format"))
		    ffmt = new SimpleLogFormatter();
		else if
		    (ftype.equals("XML-format"))
		    ffmt = new XmlLogFormatter();
		else if
		    (ftype.equals("CSV-format")) {
		    ffmt = new CsvLogFormatter();
		    ((CsvLogFormatter)ffmt).setSeperator(";");
		} else
		    ffmt = new BogstanLogFormatter();
		
		// Make a Logger.
		Logger logger = LogManager.getLogger(logname);
		logger.setLogLevel(ll);

		// Check for a FileHandler.
		if (ff != Logging.OFF) {
		    try {
			LogHandler file = new FileLogHandler(id+"_"+logname, ffmt);
			file.setLogLevel(ff);
			logger.addHandler(file);
		    } catch (IOException ex) {
			System.err.println("Unable to create file handler for: "+logname+" : "+ex);
		    }
		}

		String strConsoleLevel = config.getProperty(logname+".console", "OFF");
		// Check for ConsoleHandler.
		int cc =  Logging.OFF;
		if (strConsoleLevel.equals("OFF"))
		    cc = Logging.OFF;
		else if
		    (strConsoleLevel.equals("ALL"))
		    cc = Logging.ALL;
		else { 
		    try {
			cc = Integer.parseInt(strConsoleLevel.trim());
		    } catch (NumberFormatException nx) {
			System.err.println("Error parsing console level: "+nx);
		    }		    
		}
		if (cc != Logging.OFF) {
		    LogHandler console = new ConsoleLogHandler(new BogstanLogFormatter());
		    console.setLogLevel(cc);
		    logger.addHandler(console);
		}
		
		System.err.println("..Logger: "+logname+
				   "\n\tLog Level:      "+ll+
				   "\n\tFile handler:   "+ftype+" Level: "+ff+
				   "\n\tConsole: Level: "+cc);
		
	    }
	}	

	
	POS_CommandRelay pos = new POS_CommandRelay(id);

	// Command log levels.

	// e.g. CCDMOVING.log.level = 3

	Enumeration e1 = config.propertyNames();
	while (e1.hasMoreElements()) {
	    String key   = (String)e1.nextElement();
	    int    index = key.indexOf(".log.level");
	    if (index != -1) {
		String cmdname = key.substring(0, index);

		int level =  config.getIntValue(key, DEFAULT_COMMAND_LOG_LEVEL);
		System.err.println("Set log level for: "+cmdname+" to "+level);
		pos.setCommandLogLevel(cmdname, level);		

	    }
	}

	// Determine settings.

	// Connection-mode.
	boolean secure = config.getBooleanValue("secure", false);
	pos.setSecure(secure);

	// Server port.
	int port = config.getIntValue("server.port", DEFAULT_SERVER_PORT);
	pos.setServerPort(port);

	// Remote host.
	String rHost = config.getProperty("remote.host", DEFAULT_REMOTE_HOST);
	pos.setRemoteHost(rHost);

	int rPort = config.getIntValue("remote.port", DEFAULT_REMOTE_PORT);
	pos.setRemotePort(rPort);

	// Backlog.
	int backlog = config.getIntValue("backlog", DEFAULT_BACKLOG);
	pos.setBacklog(backlog);

	// Reconnect delay.
	long delay = config.getLongValue("delay", DEFAULT_DELAY);
	pos.setDelay(delay);

	// Special Authentication.
	String sam = config.getProperty("special.auth", DEFAULT_SPECIAL_AUTH);
	sam = sam.trim();
	pos.setSpecialAuthMethod(sam);

	// Prompt.
	String prompt =  config.getProperty("prompt", DEFAULT_PROMPT);
	pos.setPrompt(prompt);

	// Terminator.
	//String terminator = config.getProperty("terminator", DEFAULT_TERMINATOR);
	//pos.setTerminator(terminator);

	// Camera control.
	boolean cameraEnabled = config.getBooleanValue("camera.control.enabled");
	
	if (cameraEnabled) {
	    pos.setCameraControlEnabled(true);
	    String cameraAddress   = config.getProperty("camera.control.url", DEFAULT_CAMERA_CONTROL_URL);
	    pos.setCameraControlAddress(cameraAddress);
	    String cameraOn = config.getProperty("camera.control.on.command", DEFAULT_CAMERA_ON_ACTION);
	    pos.setCameraControlOnCommand(cameraOn);
	    String cameraOff = config.getProperty("camera.control.off.command", DEFAULT_CAMERA_OFF_ACTION);
	    pos.setCameraControlOffCommand(cameraOff);
	    String cameraPass = config.getProperty("camera.control.passphrase", DEFAULT_CAMERA_PASSPHRASE);
	    pos.setCameraControlPassPhrase(cameraPass);
	}

	// IC File load settings. 
	String icFileName = config.getProperty("inst.config", DEFAULT_INST_CONFIG_FILE);
	
	FileInputStream icin = null;
	try {
	    icin = new FileInputStream(icFileName);	  
	} catch (IOException iox) {
	    System.err.println("Error locating config file: "+icFileName+" : "+iox);
	    usage();
	    return;
	}
	
	ConfigurationProperties instConfig = new ConfigurationProperties();
	try {
	    instConfig.load(icin);  
	    System.err.println("..Loaded instrument configuration.");
	} catch (IOException iox) {
	    System.err.println("Error loading inst-config data: "+iox);
	    usage();
	    return;
	}
	pos.setInstConfig(instConfig);
	
	
	// OK so far - start the server.
	pos.start();
	
    }
	
    public static void usage() {
	 System.err.println("\n\nUsage: java POS_CommandRelay <config-file>"+
			    "\n\nWhere Config file contains the following (defaults in brackets):"+
			    "\n\n"+
			    "\nid           : ID of this server (POS_GENERIC)."+
			    "\nserver.port  : The port to bind to (6563)."+
			    "\nremote.host  : The remote (RCS) host address (localhost)."+
			    "\nremote.port  : Port at remote host (6567)."+
			    "\nbacklog      : Number of connections to queue (5)."+
			    "\nsecure       : True if secure comms required (false)."+
			    "\nspecial.auth : Special authentication mechanism (BASIC)."+
			    "\ninst.config  : Location of the instrument config file (./instrument.properties)."+
			    "\n[delay]      : Reconnection delay in millis (-)."+
			    "\n[prompt]     : Interactive prompt string (POS>>)."+
			    "\n[terminator] : Interactive line terminator (\\n).");
    }

    /** Sets a comamnd log level.*/
    public void setCommandLogLevel(String command, int level) {
	commandLogLevels.put(command, new Integer(level));
    }

    public void setSecure(boolean secure) { this.secure = secure; }

    public void setServerPort(int port)  { this.port = port; }

    public void setRemoteHost(String remoteHost) { this.remoteHost = remoteHost; }

    public void setRemotePort(int remotePort) { this.remotePort = remotePort; }

    public void setId(String id) { 
	this.id = id; 
	setName(id+"-T");
    }

    public void setBacklog(int backlog) { this.backlog = backlog; }

    public void setDelay(long delay) { this.delay = delay; }

    public void setSpecialAuthMethod(String sam) { this.specialAuthMethod = sam; }

    public void setPrompt(String prompt) { this.prompt = prompt; }

    public void setTerminator(String terminator) { 
	this.terminator = terminator; 
	CR = terminator;
    }

    public void setInstConfig(ConfigurationProperties instConfig) { this.instConfig = instConfig; }
    
    public void setCameraControlEnabled(boolean cameraControlEnabled) { this.cameraControlEnabled = cameraControlEnabled;}

    /** Camera control address string.*/
    public void setCameraControlAddress(String cameraControlAddress) { this.cameraControlAddress = cameraControlAddress;}

    /** Camera control ON command.*/
    public void setCameraControlOnCommand(String cameraControlOnCommand) { this.cameraControlOnCommand = cameraControlOnCommand;}

    /** Camera control OFF command.*/
    public void setCameraControlOffCommand(String cameraControlOffCommand) { this.cameraControlOffCommand = cameraControlOffCommand;}

    /** Camera control passphrase.*/
    public void setCameraControlPassPhrase(String cameraControlPassPhrase) { this.cameraControlPassPhrase = cameraControlPassPhrase; }

    /** the Camera controller.*/
    Axis2100LightController cameraLights;

    /** Setup the server.*/
    public void initialise() {
		
	traceLog.log(INFO, 1, CLASS, id,
		     "Initializing POS:"+
		     "\nID:               "+id+
		     "\nMode:             "+(secure ? "SECURE" : "INSECURE")+
		     "\nSpecial Auth:     "+specialAuthMethod+
		     "\nAttached to port: "+port+
		     "\nRemote server:    "+remoteHost+
		     "\nRemote port:      "+remotePort+
		     "\nQueue size:       "+backlog+
		     "\nCam light control:"+(cameraControlEnabled ?  " ENABLED" : " DISABLED"));
	
	if (secure) {
	    // SSL server sockets.
	    SSLServerSocketFactory ssf = (SSLServerSocketFactory)SSLServerSocketFactory.getDefault();
	    try {
		serverSocket= ssf.createServerSocket(port, 10);
		((SSLServerSocket)serverSocket).setNeedClientAuth(true);
		traceLog.log(INFO, 1, CLASS, id, "init", 
			     "Opened server socket on port: "+port+
			     " **SECURE CONNECTION - CLIENT MUST AUTHENTICATE.**");
	    } catch (IOException iox) {
		traceLog.log(ERROR, 1, CLASS, id, "init", 
			     "Error creating SECURE server socket: "+iox); 
		terminate(); 
		return;
	    }	    
	} else {
	    // Ordinary Sockets.
	    try {
		serverSocket = new ServerSocket(port, backlog);
		traceLog.log(INFO, 1, CLASS, id, "init", 
			     "Opened server socket on port: "+port+
			     " **THIS IS NOT A SECURE CONNECTION!**");
	    } catch (IOException iox) {
		traceLog.log(ERROR, 1, CLASS, id, "init", 
			     "Error creating INSECURE server socket: "+iox); 
		terminate(); 
		return;
	    }
	}
	
	keepAliveTimeout = DEFAULT_KEEPALIVE_TIMEOUT;
	
	try {
	    serverSocket.setSoTimeout((int)keepAliveTimeout);
	    traceLog.log(INFO, 1, CLASS, id, "init", 
			 "Set server timeout to: "+serverSocket.getSoTimeout()+" millis.");
	} catch (IOException iox) {
	    traceLog.log(ERROR, 1, CLASS, id, "init", 
			 "Error setting server timeout: "+iox); 
	    terminate(); 
	    return;
	}

	// Read the request counter from file (add MAX_REQ just incase we failed to write last time)..
	try {
	    ObjectInputStream rin = 
		new ObjectInputStream(new FileInputStream(DEFAULT_REQUEST_COUNTER_FILE));
	    requestCount = rin.readLong() + MAX_REQUEST_COUNT_INC;
	    requestCountInc = 0;
	    rin.close(); 
	    traceLog.log(INFO, 1, CLASS, id, "init", 
			 "Read initial request counter: "+requestCount);
	    
	    ObjectOutputStream rout = 
		new ObjectOutputStream(new FileOutputStream(DEFAULT_REQUEST_COUNTER_FILE));
	    rout.writeLong(requestCount);	   
	    rout.close();
	    traceLog.log(INFO, 1, CLASS, getName(), "incRequestCount",
			     "Wrote request counter to file: "+requestCount);
	} catch (Exception ex) {
	    traceLog.log(ERROR, 1, CLASS, id, "init", 
			 "Error reading/writing current request counter - assuming first boot: "+ex); 	
	    saveRequestCount();
	}
	
	// Create anyway.
	cameraLights = new Axis2100LightController(cameraControlAddress, cameraControlPassPhrase);
	
    }
    
    /** Run the server.*/
    public void mainTask() {

	traceLog.log(INFO, 1, CLASS, id, "mainTask",
		     "Server started");	
 
	// Listen for Client connections. 
	while (canRun() && !isInterrupted()) {  
	    traceLog.log(INFO, 3, CLASS, id, "mainTask",
			 "Server ready next connection");	
 
	    try {
		clientSocket = serverSocket.accept();
		traceLog.log(INFO, 3, CLASS, id, "mainTask",
			     "Client attached from: " + clientSocket.getInetAddress()+
			     " port: " + clientSocket.getPort());
		
		// A connection is made - start reading commands.
		if (clientSocket != null) {
		    ConnectionThread connectionThread = 
			new ConnectionThread(clientSocket);
		    connectionThread.start();
		}
		   		
	    } catch (InterruptedIOException iix) {
		// Socket timed-out so we should die off here.
		traceLog.log(ERROR, 3, CLASS, id, "mainTask", "Server timed out ## OVERRIDE NO QUIT");
		//terminate();
	    } catch (IOException iox) {
		traceLog.log(ERROR, 1, CLASS, id, "mainTask", "Server connection error:");
		traceLog.dumpStack(2, iox);
		try {Thread.sleep(delay);} catch (InterruptedException e){}
	    }
	    
	}
       
    }

    /** Release resources.*/
    public void shutdown() {
	// Saeb the request counter.
	saveRequestCount();
	// Close the ServerConnection
	if (serverSocket != null) {
	    try {		
		serverSocket.close();	
		traceLog.log(INFO, 2, CLASS, getName(), "shutdown",
			     "Closed down server:");
	    } catch (IOException iox) {
		traceLog.log(ERROR, 1, CLASS, id, "shutdown",
			     "Error closing down server: "+iox);
	    }
	}
    }

    /** Increments the requestCount via a lock file.
     * Current implementation is TEMP just uses system.time.
     * Future mechanism will involve writing the rn to a file
     * every N then on reboot incrementing the read out no by N
     * incase there was a fault during the last period. Could also
     * write the access time into the file to check on reboot times.
     * @param save Set true to force the counter to be stored.
     */
    private synchronized long incrementRequestCount(boolean save) {
	boolean dosave = save;
	requestCount++;
	requestCountInc++;
	if (requestCountInc == MAX_REQUEST_COUNT_INC) {
	    dosave = true;
	    requestCountInc = 0;
	}
	if (dosave) {
	    saveRequestCount();
	}
	return requestCount;
    }

    /** Saves the request counter.*/
    private void saveRequestCount() {	
	try {
	    ObjectOutputStream rout = 
		new ObjectOutputStream(new FileOutputStream(DEFAULT_REQUEST_COUNTER_FILE));
		rout.writeLong(requestCount);		
		rout.close();
		traceLog.log(INFO, 2, CLASS, getName(), "saveRequestCount",
			     "Wrote request counter to file: "+requestCount);
	} catch (Exception ex) {
	    traceLog.log(ERROR, 1, CLASS, id, "incRequestCount",
			 "Error writing request counter to file: "+ex);
	}	
    }

    /** Handles a Client connection session.*/
    private class ConnectionThread extends ControlThread {
	
	/** The Classname of this class.*/
	String CLASS;
	
	/** Session start time.*/
	long   sessionStart;
	
	/** Socket for client.*/
	Socket clientSocket;
	
	/** Client's Address.*/
	String clientAddress;

	/** Session identifier.*/
	String sessionId;

	/** Counts no of commands received.*/
	int cmdCount;

	/** Saves the connection-count value for this thread.*/
	int cid;

	/** Input stream from client.*/
	BufferedReader cin;
  
	/** Output stream to client.*/
	PrintStream cout;

	ConnectionThread(Socket clientSocket) {
	    super("CONNECTION", false);
	    this.clientSocket = clientSocket;
	    cid = ++connectionCount;
	    sessionStart = System.currentTimeMillis();
	    sessionId    = id+"-"+cid+"-"+mdf.format(new Date(sessionStart));
	    CLASS = POS_CommandRelay.this.CLASS+".ConnectionThread";
	    setName("CONNECTION-"+cid);
	}
	
	
	/** Set up I/O streams between here and client. 
	 * Sends preamble response.
	 * Terminates connection if IO errors occur or authentication fails.
	 */
	protected void initialise() {
	    if (initializeConnection()) {		
		preambleReply();
		// Save the client ID.
		clientMap.put(sessionId, clientSocket.getInetAddress().getHostAddress());
		//System.err.println("Stored client:"+sessionId);
	    } else {
		terminate();
	    }
	}
	
	/** Loop reading commands, creating handlers and sending onwards.*/
	protected void mainTask() {
	    // Loop reading commands.	
	    try {
		readCommands();	
	    } catch (EOFException eox) {
		traceLog.log(ERROR, 3, CLASS, id, "readCommands", 
			     "Null command - EOF");
		terminate();		
	    } catch (IOException iox) {
		traceLog.log(ERROR, 1, CLASS, id, "readCommands", 
			     "Error reading command: "+iox);
		terminate();
	    }
	}
	
	/** Initialize the client connection. Synchronized with reply() method to allow the 
	 * output stream to be changed on the fly if the connection breaks.
	 */
	private synchronized boolean initializeConnection() {
	    // Make connections to Client. 
	   

	    traceLog.log(INFO, 3, CLASS, id, "mainTask",
			 "Creating Connection for Client:"+
			 "\n\tSessionID:  "+sessionId+
			 "\n\tAt host:    "+clientSocket.getInetAddress()+
			 "\n\tOn Port:    "+clientSocket.getPort()+
			 "\n\tLocal port: "+clientSocket.getLocalPort());
	    
	    try {
		//clientSocket.setSoTimeout(40000);
		clientSocket.setTcpNoDelay(true);   // send small packets immediately.
		//clientSocket.setSoLinger(true, 600); // give up and close after 5mins.
		cin = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
		traceLog.log(INFO, 3, CLASS, getName(), "initializeConnection",
			     "Opened INPUT stream from Client: " + clientSocket.getInetAddress()+":"+
			     clientSocket.getPort()+" timeout: "+clientSocket.getSoTimeout()+" secs.");	
		traceLog.log(INFO, 2, CLASS, id, "initializeConnection",
			       "Client is attached: "+
			       "\n\tAddress:    "+clientSocket.getInetAddress()+
			       "\n\tPort:       "+clientSocket.getPort()+
			       "\n\tLocal Port: "+clientSocket.getLocalPort()+
			       "\n\tSO Timeout: "+clientSocket.getSoTimeout()+" millis."+
			       "\n\tTCP (Nagel):"+clientSocket.getTcpNoDelay()+
			       "\n\tSO Linger:  "+clientSocket.getSoLinger()+" secs.");
		
		
	    } catch (IOException ie1) {
		traceLog.log(ERROR, 1, CLASS, getName(), "initializeConnection",
			     "Error opening input stream from Client: " + 
			     clientSocket.getInetAddress()+
			     " : "+clientSocket.getPort());
		traceLog.dumpStack(2, ie1);
		terminate(); // cant send Error message as connect failed. Client will see IOException.
		return false;
	    }
	    
	    try {
		cout = new PrintStream(clientSocket.getOutputStream());
		traceLog.log(INFO, 2, CLASS, getName(), "initializeConnection",
			     "Opened OUTPUT stream to Client: " + clientSocket.getInetAddress()+" : "+
			     clientSocket.getPort()+" and flushed header:");
	    } catch (IOException ie2) {
		traceLog.log(ERROR, 1, CLASS, getName(), "initializeConnection",
			     "Error opening output stream to Client: " + clientSocket.getInetAddress() +
			     ":" + clientSocket.getPort());
		traceLog.dumpStack(2, ie2);
		terminate(); // cant send Error message as connect failed. Client will see IOException.
		return false;
	    }		    
	    
	    // Preamble response. (## Do any special authentication here ##).
	    
	    //#### START SPECIAL AUTHENTICATION
	    if (secure) {
		if (specialAuthMethod.equals(NO_SPECIAL_AUTHENTICATION)) {
		    traceLog.log(INFO, 3, CLASS, getName(), "initializeConnection",
				 "No special authentication"); 
		    clientAddress = clientSocket.getInetAddress().getHostAddress();
		    return true;
		}

		traceLog.log(INFO, 3, CLASS, getName(), "initializeConnection",
			     "Starting special authentication using mechanism: "+specialAuthMethod);
		
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
				clientAddress = val; // ####### Setting the address - i.e. RTOC_ID
			}
		    }
		    
		    traceLog.log(INFO, 3, CLASS, getName(), "initializeConnection",
				 "Special authentication: Client Verified:"+buff.toString());
		} catch (SSLPeerUnverifiedException px) {
		    traceLog.log(WARNING, 1, CLASS, getName(), "initializeConnection",
				 "Special authentication: Could not verify peer: "+px);
		}
						
	    } else {
		clientAddress = clientSocket.getInetAddress().getHostAddress();
		if (clientAddress.startsWith("16"))
		    clientAddress = "STEVE-FRASER";
		
	    }
	    //#### END SPECIAL AUTHENTICATION
	    
	    return true;
	}
	
	/** Sends the preamble response.*/
	private void preambleReply() {
	    //
	    InetAddress client = clientSocket.getInetAddress();
	    String      cAddr  = client.getHostAddress();
	    String      cName  = client.getHostName();
	    String      user   = "";
	    if (cName.indexOf("ltobs5") != -1) {
		user = "Cj";
	    } else if
		(cName.indexOf("ltobs6") != -1) {
		user = "Javaman";
	    } else if
		(cName.indexOf("ltobs2") != -1) {
		user = "Dr Bob";
	    } else if
		(cName.indexOf("ltccd1") != -1) {
		user = "Mr Fraser";
	    } else if
		(cName.indexOf("vaio") != -1) {
		user = "Dr Steele";
	    } else if
		(cName.indexOf("estar") != -1) {
		user = "E-Science bod @ <<"+cAddr+">>";
	    } else
		user = "Unknown-User @ <<"+cAddr+">>";
	    
	    String replyStr = initialReply(user);
	    cout.print(replyStr+prompt);
	    traceLog.log(INFO, 3, CLASS, getName(), "initializeConnection",
			 "Sending preamble response to Client:   \n["+replyStr+"]");
	    cout.flush();
	}
       



	/** Loop reading commands and passing to handling threads.*/
	private void readCommands() throws IOException {
	    
	    String commandStr = null;    
	    
	    while (canRun() && !isInterrupted()) {
		
		// 1. Read the data from the Client.
		// Plan (A).
		///System.err.println("\n..");
		commandStr = cin.readLine();
		//System.err.println("..");
		cmdCount++;
		
		// 2. Pass to a handler thread.
		if (commandStr != null) {
		    // We would like an overall message number here but its not
		    // been generated yet.
		    traceLog.log(INFO, 1, CLASS, getName(), "readCommands", 
				 "Read Command ["+cmdCount+"]  : "+commandStr);		
		
		    CommandHandlerThread handlerThread = 
			new CommandHandlerThread(commandStr, clientAddress, cmdCount, cout);
		    
		    if (handlerThread == null) {
			traceLog.log(ERROR, 1, CLASS, id, "readCommands", 
				     "Error creating CommandHandler Thread for command: ");
		    } else {
			traceLog.log(INFO, 3, CLASS, id, "readCommands", 
				     "Starting CommandHandler Thread for client connection: ");
			handlerThread.start();
		    }
		} else {
		    throw(new EOFException("Null command message from Client (= EOF)"));
		}
		
	    }
	}
			
	/** Create the preamble for the specified user.*/
	private String initialReply(String user) {
	    return 
		"Planetarium Control Relay: ("+id+")"+CR+
		"Date:     | "+sdf.format(new Date())+CR+
		"Connect:  | "+(secure ? "SECURE - <<CERT IS REQUIRED>>" :
			       "NON-SECURE - <<CERT NOT REQUIRED>>")+CR+
		"Hello     | "+user+CR+
		"Confirmed | "+clientAddress+CR+
		"SessionID | "+sessionId+CR;
	}
	
	/** Release resources.*/
	public void shutdown() { 
	    long time = System.currentTimeMillis() - sessionStart;
	    traceLog.log(INFO, 3, CLASS, id, "shutdown",
			 "Closing client session: "+sessionId+
			 "\n\tRTOC:           "+clientAddress+
			 "\n\tSession user:   "+controlUserId+
			 "\n\tSession length: "+(time/1000.0)+" secs."+
			 "\n\tReceived:       "+(cmdCount-1)+ " commands.");
	    clientMap.remove(sessionId);
	    try {
		if (cout != null)
		    cout.close();
		if (cin != null)
		    cin.close();
		if (clientSocket != null)
		    clientSocket.close();
	    } catch (IOException iox) {
		
	    }	    
	}
  
    } // [ConnectionThread].
    
    
    
    

    /** Handles processing of a single command.*/
    private class CommandHandlerThread extends ControlThread {
	
	/** The command string.*/
	String commandStr;
    
	/** Controller Address for Control Authorization against WSF.*/
	String ctrlAddr;

	/** The Classname of this class.*/
	String CLASS;

	/** Holds the command to send.*/
	POS_TO_RCS command;

	/** Holds the response.*/
	POS_TO_RCS_DONE done;

	/** Output stream to client.*/
	PrintStream cout;

	/** The ACK message.*/
	String ack = null;

	/** The DONE message.*/
	String replyStr;

	/** Set if the lights are being controlled.*/
	boolean lights;

	long reqNo = 0;

	SocketConnection remote;
	
	/** Create a CommandConnection with the specified command.
	 * @param commandStr The Command String.
	 */
	CommandHandlerThread(String commandStr, String ctrlAddr, int cmdId, PrintStream cout) {	    
	    super("HANDLER", false);
	    setName("HANDLER-"+(cmdId));
	    this.commandStr = commandStr;	 
	    this.ctrlAddr   = ctrlAddr;
	    this.cout       = cout;
	    CLASS           = POS_CommandRelay.this.CLASS+".CommandHandlerThread";
	} // (Constructor).
	
		
	/** Does nothing. */
	protected void initialise() {	   
	} // (initialise).
	
	protected void mainTask() {
	    
	    // 1. Parse and range check - code  NOT zero => an error.
	    int     code  = 0;  
	    // local indicates local processing (as opposed to relaying).
	    boolean local = false;

	    if (commandStr == null) {
		code = 1; // (The command was not specified).
	    } else {
		try {
		    if
			(commandStr.startsWith("SHUTDOWN")) {		
			processSHUTDOWN(commandStr);	
		    } else if
			(commandStr.startsWith("TESTLINK")) {
			command = processTESTLINK(commandStr);
		    } else if
			(commandStr.startsWith("CCDFIXED")) {			
			command = processCCDFIXED(commandStr);		
		    } else if
			(commandStr.startsWith("CCDMOVING")) {
			command = processCCDMOVING(commandStr);					
		    } else if
			(commandStr.startsWith("CCDPROCESS")) {
			command = processCCDPROCESS(commandStr);
		    } else if
			(commandStr.startsWith("GETQUEUE")) {
			command = processGETQUEUE(commandStr);
		    } else if
			(commandStr.startsWith("ABORT")) {
			command = processABORT(commandStr);
		    } else if
			(commandStr.startsWith("TELSTATUS")) {
			command = processTELSTATUS(commandStr);
		    } else if
			(commandStr.startsWith("METSTATUS")) {
			command = processMETSTATUS(commandStr);
		    } else if
			(commandStr.startsWith("CCDSTATUS")) {
			command = processCCDSTATUS(commandStr);
		    } else if
			(commandStr.startsWith("OFFLINE")) {
			command = processOFFLINE(commandStr);
		    } else if
			(commandStr.startsWith("USERID")) {
			command = processUSERID(commandStr);
		    } else if
			(commandStr.startsWith("KEEPALIVE")) {
			processKEEPALIVE(commandStr);return;
		    } else if			      
			(commandStr.startsWith("show")) {
			processSHOW(commandStr);return;		  
		    } else if 
			(commandStr.startsWith("help")) {
			processHELP();return;
		    } else
			code = 1; // UNKNOWN COMMAND.				 
		} catch (ParseException px) {
		    // A parsing or range check error.
		    code = px.getErrorOffset();
		    traceLog.log(ERROR, 1, CLASS, getName(), "mainTask",
				 "Command parser failed: "+px);
		}
	    }
	    
	    if (code != 0) {
		// Command not recognized.
		if (code == 1) {
		    traceLog.log(ERROR, 1, CLASS, getName(), "mainTask",
				 "Unknown command: "+commandStr);
		}
		ack = "ACKNOWLEDGE FAIL "+nf2.format(code) + CR;		
		reply(ack);
		traceLog.log(INFO, 3, CLASS, getName(), "mainTask",
			     "Sent ack to Client: "+ack);
		return;
	    }
	    
	    // OK so far - try to open the link.

	    // Increment the request number using lock file or similar.
	    reqNo = incrementRequestCount(false);
	
	    remote = new SocketConnection(remoteHost, remotePort);
	    
	    try {
		remote.open();
		traceLog.log(INFO, 3, CLASS, getName(), "mainTask",
			     "Opened connection to remote host: ");
	    } catch (ConnectException cx) {
		if (command instanceof TESTLINK) {
		    ack = "ACKNOWLEDGE OK "+nf12.format(reqNo) + CR;
		    //cout.print(ack+"\n");
		    reply(ack);
		    replyStr = "RESULT OK "+nf12.format(reqNo)+" LINK_DOWN" + CR;
		    //cout.print(replyStr+CR);
		    reply(replyStr);
		    return;
		} else {
		    ack = "ACKNOWLEDGE FAIL NO_COMMS" + CR;		
		    //cout.print(ack+CR);
		    reply(ack);
		    traceLog.log(ERROR, 1, CLASS, getName(), "mainTask",
				 "Unable to connect to remote host: "+remoteHost+":"+remotePort, null, cx);
		    return;
		}
	    }

	    // Set the request number and RTOC Address and send the command onwards.
	    command.setRequestNumber((int)reqNo);
	    command.setControllerAddress(ctrlAddr);
	    command.setId(id+"-"+controlUserId+"-"+adf.format(new Date())+"-"+reqNo);
	    //## WILL HAVE USERID also. LIV_MUSEUM-SCHOOL324-20010615-448 etc
	    // E.g. LIV_MUSEUM-20010615-213
	    try {
		remote.send(command);
		traceLog.log(INFO, 3, CLASS, getName(), "mainTask",
			     "Sent the command: "+command.getClass().getName());
	    } catch (IOException iox) {
		if (command instanceof TESTLINK) {
		    ack = "ACKNOWLEDGE OK "+nf12.format(reqNo) + CR;
		    //cout.print(ack+CR);
		    reply(ack);
		    replyStr = "RESULT OK "+nf12.format(reqNo)+" LINK_DOWN" + CR;
		    //cout.print(replyStr+CR);
		    reply(replyStr);
		    return;
		} else {
		    ack = "ACKNOWLEDGE FAIL NO_COMMS" + CR;		
		    //cout.print(ack+CR);
		    reply(ack);
		    traceLog.log(ERROR, 1, CLASS, getName(), "mainTask",
				 "Failed to forward command.", null, iox);
		    return;
		}
	    }
	    
	    // At this point we appear to be Ok so tell client.
	   
	    ack = "ACKNOWLEDGE OK "+nf12.format(reqNo) + CR;		
	    //cout.print(ack+CR);
	    reply(ack);
	    
	    // Save the original command string incase we get a GETQUEUE before its done.
	    commandMap.put(new Long(reqNo), commandStr);
	    
	    // Wait for ACK and DONE from remote POS.
	    
	    long timeout = DEFAULT_TIMEOUT;
	    
	    boolean completed = false;
	    boolean failed    = false;
	    // Object response = null;
	    
	    while ( ! completed && ! failed ) {
		Object response = null;
		try { 
		    response = remote.receive(timeout);
		    if (response instanceof ACK) {
			timeout = ((ACK)response).getTimeToComplete()+DEFAULT_TIMEOUT;
			traceLog.log(INFO, 3, CLASS, getName(), "mainTask",
				     "Received ACK setting timeout to: "+timeout+" millis.");
			continue;
		    } else if
			(response instanceof COMMAND_DONE) {
			COMMAND_DONE adone = (COMMAND_DONE)response;

			traceLog.log(INFO, 3, CLASS, getName(), "mainTask",
				     "Received "+response+
				     " Success = "+adone.getSuccessful());
			
			completed = ((COMMAND_DONE)response).getSuccessful();
			failed    = !completed;

			if (failed) {
			    COMMAND_DONE err = (COMMAND_DONE)response;
			    traceLog.log(INFO, 1, CLASS, getName(), "mainTask",
					 "** INFO - Command Failed: "+err.getErrorNum()+" : "+err.getErrorString());
			}

			if
			    (response instanceof TESTLINK_DONE) {
			    replyStr = processTESTLINK_DONE((POS_TO_RCS_DONE)response);
			} else if
			    (response instanceof CCDOBSERVE_DONE) {			  
			
			    replyStr = processCCDOBSERVE_DONE((POS_TO_RCS_DONE)response);			   
			
			} else if
			    (response instanceof CCDPROCESS_DONE) {
			    
			    replyStr = processCCDPROCESS_DONE((POS_TO_RCS_DONE)response);
			
			} else if
			    (response instanceof GETQUEUE_DONE) {
			    replyStr = processGETQUEUE_DONE((POS_TO_RCS_DONE)response);
			} else if
			    (response instanceof ABORT_DONE) {
			    replyStr = processABORT_DONE((POS_TO_RCS_DONE)response);
			} else if
			    (response instanceof TELSTATUS_DONE) {
			    replyStr = processTELSTATUS_DONE((POS_TO_RCS_DONE)response);
			} else if
			    (response instanceof METSTATUS_DONE) {
			    replyStr = processMETSTATUS_DONE((POS_TO_RCS_DONE)response);
			} else if
			    (response instanceof CCDSTATUS_DONE) {
			    replyStr = processCCDSTATUS_DONE((POS_TO_RCS_DONE)response);
			} else if
			    (response instanceof OFFLINE_DONE) {
			    replyStr = processOFFLINE_DONE((POS_TO_RCS_DONE)response);
			} else if
			    (response instanceof USERID_DONE) {
			    replyStr = processUSERID_DONE((POS_TO_RCS_DONE)response);
			} else {		
			    // Set a U/S error if we dont have a proper reply.
			    if (failed) {
				String failure = "";

				traceLog.log(INFO, 1, CLASS, getName(), "mainTask", 
					     "** INFO - Handle error from generic reply class:");

				COMMAND_DONE done = (COMMAND_DONE)response;
				switch (done.getErrorNum()) {
				case POS_TO_RCS.CLIENT_ABORTED:
				    failure = "CLIENT_ABORTED";
				    break;
				case POS_TO_RCS.TASK_ABORTED:
				    failure = "TASK_ABORTED";
				    break;
				case POS_TO_RCS.SERVER_BUSY:
				    failure = "SERVER_BUSY";
				    break;
				case POS_TO_RCS.TIMED_OUT:
				    failure = "TIMED_OUT";
				    break;
				case POS_TO_RCS.UNSPECIFIED_ERROR:
				    failure = "UNSPECIFIED_ERROR";
				    break;
				case POS_TO_RCS.NOT_IN_CONTROL:
				    failure = "NOT_IN_CONTROL";
				    break;
				case POS_TO_RCS.INVALID_RTOC:
				    failure = "INVALID_RTOC";
				    break;
				case POS_TO_RCS.NOT_OPERATIONAL:
				    failure = "NOT_OPERATIONAL";
				    break;
				case TESTLINK.OVERRIDDEN:
				    failure = "OVERRIDDEN";
				    break;
				default:
				    failure = "UNSPECIFIED_ERROR";
				    break;
				}
				replyStr = "RESULT FAIL "+nf12.format(reqNo)+" "+failure+CR;	
			    }	
			}
			break;
			
		    } else {			
			traceLog.log(INFO, 3, CLASS, getName(), "mainTask",
				     "Received RESPONSE: "+
				     "\nClass: "+(response != null ? response.getClass().getName() : "Null"));			
			replyStr = "RESULT FAIL "+nf12.format(reqNo)+" UNSPECIFIED_ERROR"+CR;
			failed = true;
			//break;
		    }
		} catch (IOException iox) {
		    traceLog.log(FATAL, 1, CLASS, getName(), "mainTask",
				 "IO Error: Latest RESPONSE: "+
				 "\nClass: "+(response != null ? response.getClass().getName() : "Null")+
				 "\nError: "+iox);
		    traceLog.dumpStack(1, iox);
		    
		    replyStr = "RESULT FAIL "+nf12.format(reqNo)+" UNSPECIFIED_ERROR"+CR;
		    failed = true;
		    //break;
		}
	    } // end while (receiving Acks and a Done).
	    
	    // 4. Reply to Client. 
	    //cout.print(replyStr+CR); 

	    // Check which commands we want to log.

	    int clevel = commandLogLevel(commandStr, DEFAULT_COMMAND_LOG_LEVEL);

	    traceLog.log(INFO, clevel, CLASS, getName(), "mainTask",
			 "Sending response: "+replyStr);
	    
	    reply(replyStr);
	   
	    // The Command is done so remove from the map. 
	    // GETQUEUE will no longer get a process descriptor for this from the RCS.

	    //###########TEMP PUT THIS BACK LATER #################
	    //###### RCS does not remove stuff from the queue at the moment ##########
	    //commandMap.remove(new Integer(reqNo));
	    
	    
	} //  main().

	/** Release resources.*/
	public void shutdown() {
	    // Close the connection - the server won't do it for us !
	    if (remote != null)
		remote.close();
	    traceLog.log(INFO, 3, CLASS, getName(), "shutdown",
			 "Closed connection to remote server");

	    if (cameraControlEnabled && lights) {
		try {
		    cameraLights.switchOff();
		    traceLog.log(INFO, 3, CLASS, getName(), "mainTask",
				 "Lights OFF after slewing");
		} catch (IOException iox) {
		    traceLog.log(1, CLASS, getName(), "mainTask",
				 "Unable to switch camera lights out: "+iox);
		}
		lights = false;
	    }			
	    
	}
    
	/** Converts the mode into a string.*/
	private String toModeString(int mode) {
	    switch (mode) {
	    case CCDOBSERVE.SINGLE:
		return "SINGLE";
	    case CCDOBSERVE.MOSAIC_SETUP:
		return "MOSAIC_SETUP";
	    case CCDOBSERVE.MOSAIC:
		return "MOSAIC";
	    default:
		return "UNKNOWN";
	    }
	}

	/** Wraps a write call to the client's output stream. This method is synchronized
	 * so that in the event of the connection breaking the server can reset the connection
	 * while any handlers are forced to wait.*/
	private synchronized void reply(String message) {
	    //System.err.println("Reply::cout: "+cout+" msg: "+message);
	    cout.print(message);
	}
      
	/** Process a SHOW command.*/
	protected void processSHOW(String commandStr)   throws ParseException {
	    String args = commandStr.substring(4).trim();
	    //System.err.println("{"+args+"}");
	    if (args == null || args.length() < 1) {
		throw new ParseException("Missing parameters", 2);
	    }
	    
	    if (args.equalsIgnoreCase("CLIENTS")) {		
		String       key       = null;
		StringBuffer replyBuff = new StringBuffer();
		Iterator it = clientMap.keySet().iterator();	  
		while (it.hasNext()) {
		    key = (String)it.next();
		    replyBuff.append(key+"@"+clientMap.get(key));
		    if (it.hasNext())
			replyBuff.append(",");
		}	
		
		ack = "ACKNOWLEDGE OK 000" + CR;				
		reply(ack);	  
		reply(replyBuff.toString() + CR);
		return;
	    } else if
		(args.equalsIgnoreCase("FILTERS")) {
		String key = null;
		StringBuffer replyBuff = new StringBuffer("Filters:");
		Enumeration filters = instConfig.propertyNames();
		while (filters.hasMoreElements()) {
		   key = (String)filters.nextElement();
		   if (key.startsWith("ccd.filter") &&
		       key.endsWith(".lower")) {
		       String combo = key.substring(11,13);
		       String lower = instConfig.getProperty("ccd.filter."+combo+".lower");
		       String upper = instConfig.getProperty("ccd.filter."+combo+".upper");
		       replyBuff.append("("+combo+") [L="+lower+", U="+upper+"],");
		   }
		}
		
		ack = "ACKNOWLEDGE OK 000" + CR;				
		reply(ack);	  
		reply(replyBuff.toString() + CR);
		return;
	    }

	    throw new ParseException("Unknown parameter "+args, 2);
	}

	/** Process a KEEPALIVE message.*/
	protected void processKEEPALIVE(String commandStr)   throws ParseException { 
	     String args = commandStr.substring(9);
	     if (args == null || args.length() < 1)
		throw new ParseException("Missing parameters", 2);
	     String sto = args.trim();
	     long to = 0L;
	     try {
		 to = Long.parseLong(sto);
	     } catch (NumberFormatException nx) {
		 throw new ParseException("Illegal timeout", 2);
	     }
	     //stopper.keepalive(to);
	}
	

	/** Process the OFFLINE command.*/
	protected POS_TO_RCS processOFFLINE(String commandStr)   throws ParseException { 
	    String args = commandStr.substring(7);
	    if (args == null || args.length() < 1)
		throw new ParseException("Missing parameters", 2);
	    // Ok we have some parameters.
	    String stime = args.trim();
	    long time = 0L;
	    Date date = null;
	    try {
		date = sdf.parse(stime);
		time = date.getTime();
	    } catch (ParseException px) {
		throw new ParseException("Illegal time format", 2);
	    } 
	    traceLog.log(INFO, 3, CLASS, getName(), "processOFFLINE",			
			 "\n\tOffline until: "+stime+
			 "\n\tISO 8601:      "+iso8601.format(date));
	    return new OFFLINE("", time);
	}

	/** Process the USERID command.*/
	protected POS_TO_RCS processUSERID(String commandStr)   throws ParseException { 
	    String args = commandStr.substring(6);
	    if (args == null || args.length() < 1)
		throw new ParseException("Missing parameters", 2);
	    // Ok we have some parameters.
	    String uid =  args.trim();
	    if (uid == null || uid.equals(""))
		throw new ParseException("No UserID set", 2);
	    // Set local userid.
	    controlUserId = uid;
	  	   
	    return new USERID("", uid);
	}

	/** Process the SHUTDOWN command.*/
	protected void processSHUTDOWN(String commandStr)   throws ParseException { 
	    String args = commandStr.substring(8);
	    if (args == null || args.length() < 1)
		throw new ParseException("Missing parameters", 2);
	    // Ok we have some parameters.
	    if (args.trim().equals("OFF"))
		System.exit(SYSTEM_OFF);
	    else if
		(args.trim().equals("REBOOT"))
		System.exit(SYSTEM_REBOOT);
	    else
		throw new ParseException("Unknown SHUTDOWN state: "+args.trim(), 2); 
	}
	
	/** Process the TESTLINK command.*/
	protected POS_TO_RCS processTESTLINK(String commandStr)   throws ParseException { return new TESTLINK(""); }
	
	/** Process the CCDMOVING command.*/ 
	protected POS_TO_RCS processCCDMOVING(String commandStr)   throws ParseException {
	    String args = commandStr.substring(9);
	    if (args == null || args.length() < 1)
		throw new ParseException("Missing parameters", 2);
	    // Ok we have some parameters.
	    StringTokenizer tok = new StringTokenizer(args);
	    // Missing args: tokens + 1 (+1 for the command)
	    if (tok.countTokens() < 8) {
		traceLog.log(ERROR, 1, CLASS, getName(), "processCCDMOVING",
			     "Insufficient arguments - only "+tok.countTokens()+" not 8.");
		throw new ParseException("Missing parameters", tok.countTokens()+2);
	    }
	    
	    String src  = tok.nextToken();
	    int    isrc = 0;
	    if (src.equals("MOON")) {
		isrc = CCDMOVING.MOON;
	    } else if
		(src.equals("MERCURY")) {
		isrc = CCDMOVING.MERCURY;
	    } else if
		(src.equals("VENUS")) {
		isrc = CCDMOVING.VENUS;
	    } else if
		(src.equals("MARS")) {
		isrc = CCDMOVING.MARS;
	    } else if
		(src.equals("JUPITER")) {
		isrc = CCDMOVING.JUPITER;
	    } else if
		(src.equals("SATURN")) {
		isrc = CCDMOVING.SATURN;
	    } else if
		(src.equals("URANUS")) {
		isrc = CCDMOVING.URANUS;
	    } else if
		(src.equals("NEPTUNE")) {
		isrc = CCDMOVING.NEPTUNE;
	    } else if
		(src.equals("PLUTO")) {
		isrc = CCDMOVING.PLUTO;
	    } else {
		traceLog.log(ERROR, 1, CLASS, getName(), "processCCDMOVING",
			     "Parsing SourceID: Unknown source: ["+src+"]");
		throw new ParseException("Parsing SourceID: Unknown source: "+src, 2);
	    }

	    String strMode = tok.nextToken();
	    int mode = 0;
	    if (strMode.equals("SINGLE")) {
		mode = CCDMOVING.SINGLE; 
		mosaicInProgress = false;
	    }  else if
		(strMode.equals("MOSAIC")) {		
		if (mosaicInProgress) {		    
		    mode = CCDMOVING.MOSAIC;
		} else {
		    mode = CCDMOVING.MOSAIC_SETUP;
		    mosaicInProgress = true;
		}  
	    } else {
		traceLog.log(ERROR, 1, CLASS, getName(), "processCCDMOVING",
			     "Reading Mode: Unknown mode: "+strMode);
		throw new ParseException("Reading Mode: Unknown mode: "+strMode, 3);
	    }

	    String xostr = tok.nextToken();
	    double xoff = 0.0;
	    try {
		xoff = Double.parseDouble(xostr);
	    } catch (NumberFormatException nx) {
		traceLog.log(ERROR, 1, CLASS, getName(), "processCCDMOVING",
			     "Parsing X-Off: "+nx);
		throw new ParseException("Parsing X-Off: "+nx, 4);
	    }
	    
	    String yostr = tok.nextToken();
	    double yoff = 0.0;
	    try {
		yoff = Double.parseDouble(yostr);
	    } catch (NumberFormatException nx) {
		traceLog.log(ERROR, 1, CLASS, getName(), "processCCDMOVING",
			     "Parsing Y-Off: "+nx);
		throw new ParseException("Parsing Y-Off: "+nx, 5);
	    }

	    String rotstr = tok.nextToken();
	    double rotation = 0.0;
	    try {
		rotation = Double.parseDouble(rotstr);
	    } catch (NumberFormatException nx) {
		traceLog.log(ERROR, 1, CLASS, getName(), "processCCDMOVING",
			     "Parsing Rotation: "+nx);
		throw new ParseException("Parsing Rotation: "+nx, 6);
	    }
	    if (rotation < 0.0 || rotation > 360.0) {
		traceLog.log(ERROR, 1, CLASS, getName(), "processCCDMOVING",
			     "Parsing Rotation: Out of range: 0 -360");
		throw new ParseException("Parsing Rotation: Out of range 0 -360", 6);
	    }

	    String filter = tok.nextToken();
	    // Should check against config here.

	    String lower = instConfig.getProperty("ccd.filter."+filter+".lower");
	    String upper = instConfig.getProperty("ccd.filter."+filter+".upper");
	    
	    if (lower == null || upper == null)
		throw new ParseException("Parsing filter combo - unknown filter combo", 7);
	    
	    int fclass = CCDOBSERVE.UNKNOWN_FILTER;
	    String strfclass = instConfig.getProperty("ccd.filter."+filter+".class");
	    if (strfclass.equals("red"))
		fclass = CCDOBSERVE.RED_FILTER;
	    else if
		(strfclass.equals("blue"))
		fclass = CCDOBSERVE.BLUE_FILTER;
	    else if
		(strfclass.equals("green"))
		fclass = CCDOBSERVE.GREEN_FILTER;
	    // just because we dont know the filter class isnt a problem as
	    // we may just be doing a one-off, its only a prblme if we are using
	    // a multicolor processing stage.

     
	    String bins = tok.nextToken();
	    int bin = 0;
	    try {
		bin = Integer.parseInt(bins);
	    } catch (NumberFormatException nx) {
		traceLog.log(ERROR, 1, CLASS, getName(), "processCCDMOVING",
			     "Parsing bins: "+nx);
		throw new ParseException("Parsing CCD bins: "+nx, 8);
	    }
		
	    if (bin < 1 || bin > instConfig.getIntValue("ccd.bin.max", 4))
		throw new ParseException("Parsing CCD bins: Out of range", 8);
	    
	    String exps = tok.nextToken();
	    double exp = 0.0;
	    try {
		exp = Double.parseDouble(exps);
	    } catch (NumberFormatException nx) {
		traceLog.log(ERROR, 1, CLASS, getName(), "processCCDMOVING",
			     "Parsing exposure: "+nx);
		throw new ParseException("Parsing Exposure time: "+nx, 9);
	    }
	    
	    if (exp > instConfig.getDoubleValue("ccd.exposure.max", 7200))
		throw new ParseException("Parsing Exposure time: Too long", 9);
	    
	    traceLog.log(INFO, 2, CLASS, getName(), "processCCDMOVING",			
			 "\n\tSource:   "+src+
			 "\n\tMode:     "+toModeString(mode)+
			 "\n\tRotation: "+rotation+"degrees"+
			 "\n\tX-Offset: "+xoff+" arcsec"+
			 "\n\tY-Offset: "+yoff+" arcsec"+
			 "\n\tFilters:  "+filter+" [L="+lower+", U="+upper+"]"+
			 "\n\tBinning:  "+bin+"x"+bin+			 
			 "\n\tExposure: "+exp+" secs"+
			 "\n\tFClass:   "+fclass);
	    
	    
	    CCDMOVING ccdMOVING = new CCDMOVING(src+"-test");
	    ccdMOVING.setSrcId(isrc);  
	    ccdMOVING.setMode(mode);
	    ccdMOVING.setXOffset(xoff); 
	    ccdMOVING.setYOffset(yoff);
	    ccdMOVING.setRotation(Math.toRadians(rotation));
	    ccdMOVING.setBin(bin);
	    ccdMOVING.setFilter1(lower);
	    ccdMOVING.setFilter2(upper);
	    ccdMOVING.setExposure(exp*1000.0);
	    ccdMOVING.setFilterClass(fclass);

	    // Select the mode of operation.
	    
	    if (exp < 0.0) {

		// A slew
		if (strMode.equals("MOSAIC")) {

		    mosaicInProgress = true;
		    mode = CCDMOVING.MOSAIC_SETUP;

		} else if
		    (strMode.equals("SINGLE")) {
		    
		    if (mosaicInProgress) {
			
			mosaicInProgress = false;
			mode = CCDMOVING.SLEW_PLUS_ROT;
			
		    } else {
			
			mode = CCDMOVING.SLEW_ONLY;
			
		    }

		}

		
	    } else {
		
		// Just another observation. (NOT a slew).
		
		if (strMode.equals("MOSAIC")) {
		    
		    mode = CCDMOVING.MOSAIC;
		

		} else if
		    (strMode.equals("SINGLE")) {
		    
		    if (mosaicInProgress) {
			
			mosaicInProgress = false;
			mode = CCDMOVING.SLEW_PLUS_ROT;
			
		    } else {
			
			mode = CCDMOVING.SLEW_ONLY;
			
		    }
		}
		
	    }



	    if (cameraControlEnabled && exp < 0.0) {
		try {
		    cameraLights.switchOn();
		    traceLog.log(INFO, 3, CLASS, getName(), "processCCDMOVING",
				 "Lights ON for slew only");
		    lights = true;
		} catch (IOException iox) {
		    traceLog.log(1, CLASS, getName(), "processCCDMOVING",
				 "Failed to switch lighs on: "+iox);
		}
	    }
	    return ccdMOVING;	
	}


	/** Process the CCDFIXED command.*/
	protected POS_TO_RCS processCCDFIXED(String commandStr) throws ParseException { 
	    String args = commandStr.substring(8);
	  
	    if (args == null || args.length() < 1)
		throw new ParseException("Missing parameters", 2);
	    // Ok we have some parameters.

	    // Check for quoted target id.

	    String a2 = args.replace('\"', '%');
	    String a3 = a2.replace('\'', '%');	
	    
	    int f = a3.indexOf("%");
	    int l = a3.lastIndexOf("%");
	  
	    if (f != -1) {
		if (f == l) {
		    traceLog.log(ERROR, 1, CLASS, getName(), "processCCDFIXED",
				 "Parsing Args: Unmatched quotes");
		    throw new ParseException("Parsing Args: Unmatched quotes", 2);
		}		
		String id = a3.substring(f+1, l).replace(' ', '$');
		args = a3.substring(0,f).concat(id).concat(a3.substring(l+1)); 					    
	    } else 
		args = a3;
	    
	    StringTokenizer tok = new StringTokenizer(args);
	    // Missing args: tokens + 1 (+1 for the command)
	    if (tok.countTokens() < 10) {
		//if (tok.countTokens() < 11) {
		traceLog.log(ERROR, 1, CLASS, getName(), "processCCDFIXED",
			     "Insufficient arguments - only "+tok.countTokens()+" not 10.");
		throw new ParseException("Missing parameters", tok.countTokens()+2);
	    }

	    String srcId = tok.nextToken();
	    srcId = srcId.replace('$', ' ');

	    String ras = tok.nextToken();
	    double ra = 0.0;
	    try {
		ra = Position.parseHMS(ras);
	    } catch (ParseException px) {
		traceLog.log(ERROR, 1, CLASS, getName(), "processCCDFIXED",
			     "Parsing RA: "+px);
		throw new ParseException("Parsing RA: "+px, 3);
	    }
	    
	    String decs = tok.nextToken();
	    double dec = 0.0;
	    try {
		dec = Position.parseDMS(decs);
	    } catch (ParseException px) {
		traceLog.log(ERROR, 1, CLASS, getName(), "processCCDFIXED",
			     "Parsing dec: "+px);
		throw new ParseException("Parsing Dec: "+px, 4);
	    }

	    String strMode = tok.nextToken();
	    int mode = 0;
	    if (strMode.equals("SINGLE")) {
		mode = CCDFIXED.SINGLE; 
		mosaicInProgress = false;
	    }  else if
		(strMode.equals("MOSAIC")) {		
		if (mosaicInProgress) {		    
		    mode = CCDFIXED.MOSAIC;
		} else {
		    mode = CCDFIXED.MOSAIC_SETUP;
		    mosaicInProgress = true;
		}  
	    } else {
		traceLog.log(ERROR, 1, CLASS, getName(), "processCCDFIXED",
			     "Reading Mode: Unknown mode: "+strMode);
		throw new ParseException("Reading Mode: Unknown mode: "+strMode, 3);
	    }

	    String xostr = tok.nextToken();
	    double xoff = 0.0;
	    try {
		xoff = Double.parseDouble(xostr);
	    } catch (NumberFormatException nx) {
		traceLog.log(ERROR, 1, CLASS, getName(), "processCCDFIXED",
			     "Parsing X-Off: "+nx);
		throw new ParseException("Parsing X-Off: "+nx, 6);
	    }
	    
	    String yostr = tok.nextToken();
	    double yoff = 0.0;
	    try {
		yoff = Double.parseDouble(yostr);
	    } catch (NumberFormatException nx) {
		traceLog.log(ERROR, 1, CLASS, getName(), "processCCDFIXED",
			     "Parsing Y-Off: "+nx);
		throw new ParseException("Parsing Y-Off: "+nx, 7);
	    }

	    String rotstr = tok.nextToken();
	    double rotation = 0.0;
	    try {
		rotation = Double.parseDouble(rotstr);
	    } catch (NumberFormatException nx) {
		traceLog.log(ERROR, 1, CLASS, getName(), "processCCDFIXED",
			     "Parsing Rotation: "+nx);
		throw new ParseException("Parsing Rotation: "+nx, 8);
	    }
	    if (rotation < 0.0 || rotation > 360.0) {
		traceLog.log(ERROR, 1, CLASS, getName(), "processCCDFIXED",
			     "Parsing Rotation: Out of range: 0 -360");
		throw new ParseException("Parsing Rotation: Out of range 0 -360", 8);
	    }

	    String filter = tok.nextToken();
	    // Should check against config here.

	    String lower = instConfig.getProperty("ccd.filter."+filter+".lower");
	    String upper = instConfig.getProperty("ccd.filter."+filter+".upper");
	    
	    if (lower == null || upper == null)
		throw new ParseException("Parsing filter combo - unknown filter combo", 9);

	    int fclass = CCDOBSERVE.UNKNOWN_FILTER;
            String strfclass = instConfig.getProperty("ccd.filter."+filter+".class", "blue");
            if (strfclass.equals("red"))
                fclass = CCDOBSERVE.RED_FILTER;
            else if
                (strfclass.equals("blue"))
                fclass = CCDOBSERVE.BLUE_FILTER;
            else if
                (strfclass.equals("green"))
                fclass = CCDOBSERVE.GREEN_FILTER;
            // just because we dont know the filter class isnt a problem as
            // we may just be doing a one-off, its only a prblme if we are using
            // a multicolor processing stage.
	    
	    
	    String bins = tok.nextToken();
	    int bin = 0;
	    try {
		bin = Integer.parseInt(bins);
	    } catch (NumberFormatException nx) {
		traceLog.log(ERROR, 1, CLASS, getName(), "processCCDFIXED",
			     "Parsing bins: "+nx);
		throw new ParseException("Parsing CCD bins: "+nx, 10);
	    }
		
	    if (bin < 1 || bin > instConfig.getIntValue("ccd.bin.max", 4))
		throw new ParseException("Parsing CCD bins: Out of range", 10);
	    
	    String exps = tok.nextToken();
	    double exp = 0.0;
	    try {
		exp = Double.parseDouble(exps);
	    } catch (NumberFormatException nx) {
		traceLog.log(ERROR, 1, CLASS, getName(), "processCCDFIXED",
			     "Parsing exposure: "+nx);
		throw new ParseException("Parsing Exposure time: "+nx, 11);
	    }
	    
	    if (exp > instConfig.getDoubleValue("ccd.exposure.max", 7200))
		throw new ParseException("Parsing Exposure time: Too long", 11);
	    

	    //String sourceType = tok.nextToken();

	    traceLog.log(INFO, 3, CLASS, getName(), "processCCDFIXED",			
			 "\n\tTarget:   "+srcId+
			 "\n\tRA:       "+Position.toHMSString(ra)+
			 "\n\tDec:      "+Position.toDMSString(dec)+
			 "\n\tMode:     "+toModeString(mode)+
			 "\n\tX-offset: "+xoff+
			 "\n\tY-offset: "+yoff+
			 "\n\tRotation: "+rotation+
			 "\n\tFilters:  "+filter+
			 "\n\tBinning:  "+bin+			 
			 "\n\tExposure: "+exp+
			 "\n\tFClass:   "+fclass);

	    // "\n\tSourceType:" +sourceType
	    	    	   
	    CCDFIXED ccdFixed = new CCDFIXED("test");
	    ccdFixed.setSourceId(srcId);
	    ccdFixed.setPosition(new Position(ra, dec));	
	    ccdFixed.setMode(mode);
	    ccdFixed.setXOffset(xoff);
	    ccdFixed.setYOffset(yoff);
	    ccdFixed.setRotation(Math.toRadians(rotation));
	    ccdFixed.setBin(bin);
	    ccdFixed.setFilter1(lower);
	    ccdFixed.setFilter2(upper);
	    ccdFixed.setExposure(exp*1000.0);
	    //ccdFixed.setSourceType(sourceType);
	    ccdFixed.setFilterClass(fclass);

	    if (cameraControlEnabled && exp < 0.0) {
		try {
		    cameraLights.switchOn();
		    traceLog.log(INFO, 3, CLASS, getName(), "processCCDMOVING",
				 "Lights ON for slew only");
		    lights = true;
		} catch (IOException iox) {
		    traceLog.log(1, CLASS, getName(), "processCCDMOVING",
				 "Failed to switch lights on: "+iox);
		}
	    }

	    return ccdFixed;	
	}
	
	/** Process the CCDPROCESS command.*/
	protected POS_TO_RCS processCCDPROCESS(String commandStr) throws ParseException { 

	    // This is a quick fix to turn off the mosaic flag.
	    // We assume that a call to PROCESS is made after a series of mosaic obs
	    // With G2 commands this will disappear !

	    mosaicInProgress = false;
	    
	    String args = commandStr.substring(10);
	    if (args == null || args.length() < 1)
		throw new ParseException("Missing parameters", 2);
	    // Ok we have some parameters.
	    StringTokenizer tok = new StringTokenizer(args);
	    // Missing args: tokens + 1 (+1 for the command)
	    if (tok.countTokens() < 5) {
		traceLog.log(ERROR, 1, CLASS, getName(), "processCCDPROCESS",
			     "Insufficient arguments - only "+tok.countTokens()+" not 5.");
		throw new ParseException("Missing parameters", tok.countTokens()+2);
	    }
	    
	    String dests = tok.nextToken();
	    int dest = 0;
	    if (dests.equals("SERVERPC"))
		dest = CCDPROCESS.SERVERPC;
	    else if
		(dests.startsWith("ALTERNATE")) {
		if (dests.length() < 10) {
		    traceLog.log(ERROR, 1, CLASS, getName(), "processCCDPROCESS",
				 "Parsing Destination: ALTERNATE??");
		    throw new ParseException("Parsing Destination: ALTERNATE??", 2);
		}
		try {
		    dest = Integer.parseInt(dests.substring(9));
		} catch (NumberFormatException nx) {
		    traceLog.log(ERROR, 1, CLASS, getName(), "processCCDPROCESS",
				 "Parsing Destination: "+nx);
		    throw new ParseException("Parsing Destination: "+nx, 2);
		}	
	    } else if
		(dests.startsWith("ALT")) {
		if (dests.length() < 4) {
		    traceLog.log(ERROR, 1, CLASS, getName(), "processCCDPROCESS",
				 "Parsing Destination: ALT??");
		    throw new ParseException("Parsing Destination: ALT??", 2);
		}
		try {
		    dest = Integer.parseInt(dests.substring(3));
		} catch (NumberFormatException nx) {
		    traceLog.log(ERROR, 1, CLASS, getName(), "processCCDPROCESS",
				 "Parsing Destination: "+nx);
		    throw new ParseException("Parsing Destination: "+nx, 2);
		}	
	    } else 
		throw new ParseException("Illegal destination: "+dests, 2);
	    
	    String types = tok.nextToken();
	    int type = 0;
	    if (types.equals("COLORJPEG"))
		type = CCDPROCESS.COLOR_JPEG;
	    else if 
		(types.equals("COLOURJPEG"))
		type = CCDPROCESS.COLOR_JPEG;
	    else if
		(types.equals("BESTJPEG"))
		type = CCDPROCESS.BEST_JPEG;
	    else if
		(types.equals("JPEG"))
		type = CCDPROCESS.JPEG;
	    else if
		(types.equals("BESTFITS"))
		type = CCDPROCESS.BEST_FITS;
	    else if
		(types.equals("FITS"))
		type = CCDPROCESS.FITS;
	    else if
		(types.equals("MOSAICFITS"))
		type = CCDPROCESS.MOSAIC_FITS;
	    else if
		(types.equals("MOSAICJPEG"))
		type = CCDPROCESS.MOSAIC_JPEG;
	    else
		throw new ParseException("Illegal type: "+types, 3);

	    String starts = tok.nextToken();
	    long   start = 0;
	    try {
		start = Long.parseLong(starts);
	    } catch (NumberFormatException nx) {
		throw new ParseException("Parsing Start Frame: "+nx, 4);
	    }

	    String ends = tok.nextToken();
	    long   end = 0;
	    try {
		end = Long.parseLong(ends);
	    } catch (NumberFormatException nx) {
		throw new ParseException("Parsing End Frame: "+nx, 5);
	    }
	    
	    String srctypestr = tok.nextToken();
	    int srcType = 0;
	    if (srctypestr.equals("PLANETARY"))
		srcType = CCDPROCESS.PLANETARY;
	    else if
		(srctypestr.equals("STELLAR"))
		srcType = CCDPROCESS.STELLAR;
	    else if
		(srctypestr.equals("GALACTIC"))
		srcType = CCDPROCESS.GALACTIC;
	    else {
		traceLog.log(ERROR, 1, CLASS, getName(), "processCCDPROCESS",
			     "Parsing Source-type: Unknown source type");
		throw new ParseException("Parsing Source-type: Unknown source type", 6);
	    }	  

	    traceLog.log(INFO, 3, CLASS, getName(), "processCCDPROCESS",			
			 "\n\tDestination: "+dest+
			 "\n\tProc type:   "+type+
			 "\n\tStart frame: "+start+
			 "\n\tLast frame:  "+end+
			 "\n\tTotal:       "+(end-start+1)+" frames."+			 
			 "\n\tSource type: "+srcType);
	    
	    return new CCDPROCESS("", dest, type, start, end, srcType); 
	}

	/** Process the GETQUEUE command.*/
	protected POS_TO_RCS processGETQUEUE(String commandStr)   throws ParseException { return new GETQUEUE(""); }
	
	/** Process the ABORT command.*/
	protected POS_TO_RCS processABORT(String commandStr)      throws ParseException { 
	    String args = commandStr.substring(5);
	    if (args == null || args.length() < 1)
		throw new ParseException("Missing parameters", 2);
	    // Ok we have some parameters.
	    StringTokenizer tok = new StringTokenizer(args);
	    // Missing args: tokens + 1 (+1 for the command)
	    if (tok.countTokens() < 1) {
		traceLog.log(ERROR, 1, CLASS, getName(), "processABORT",
			     "Insufficient arguments - only "+tok.countTokens()+" not 1.");
		throw new ParseException("Missing parameters", tok.countTokens()+2);
	    }
	    
	    String codes = tok.nextToken();
	    if (codes.trim().equals("ALL"))
		return new ABORT("", 0, true);
	    
	    int code = 0;
	    try {
		code = Integer.parseInt(codes);
	    } catch (NumberFormatException nx) {
		throw new ParseException("Parsing Request code: "+nx, 2);
	    }
	    
	    return new ABORT("", code, false);
	}
	
	/** Process the TELSTATUS command.*/
	protected POS_TO_RCS processTELSTATUS(String commandStr)  throws ParseException { return new TELSTATUS(""); }
	
	/** Process the METSTATUS command.*/
	protected POS_TO_RCS processMETSTATUS(String commandStr)  throws ParseException { return new METSTATUS(""); }
	
	/** Process the CCDSTATUS command.*/
	protected POS_TO_RCS processCCDSTATUS(String commandStr)  throws ParseException { return new CCDSTATUS(""); }
	
	
	
	
	private void processHELP() {
	    String help =  "POS Command list:"+
		"\n\n HELP       - Print this list of commands. Extra info on any command"+
		"\n                 can be obtained by typing:- help <command>."+
		"\n\n TESTLINK   - Tests the link to the RCS - POS Server."+
		"\n\n CCDOBSERVE - Instruct POS to insert a CCD Observation."+
		"\n\n CCDPROCESS - Instruct POS to carry out processing of a series of observations."+	
		"\n\n GETQUEUE   - Returns a list of currently executing processes."+
		"\n\n ABORT      - Aborts the current process."+
		"\n\n TELSTATUS  - Returns telescope status info."+     
		"\n\n METSTATUS  - Returns meteorological status info."+
		"\n\n CCDSTATUS  - Returns CCD status info."+
		"\n\n SHUTDOWN   - Stop everything and disconnect.";
	    //cout.print(help+"\n");
	    reply(help);
	}
	
	/** Process the USERID_DONE response.*/
	protected String processUSERID_DONE(POS_TO_RCS_DONE response)    { 
	    USERID_DONE done = (USERID_DONE)response;
	    if ( ! done.getSuccessful()) {
		String failure = "";
		switch (done.getErrorNum()) {
		case POS_TO_RCS.INVALID_RTOC:
		    failure = "INVALID_RTOC";
		    break;
		case POS_TO_RCS.NOT_IN_CONTROL:
		    failure = "NOT_IN_CONTROL";
		    break;
		default:
		    failure = "UNSPECIFIED_ERROR";
		}
		return "RESULT FAIL "+nf12.format(reqNo)+" "+failure+CR;	
	    } else {
		return "RESULT OK "+nf12.format(reqNo)+CR;
	    }
	}
	
	/** Process the OFFLINE_DONE response.*/
	protected String processOFFLINE_DONE(POS_TO_RCS_DONE response)    { 
	    OFFLINE_DONE done = (OFFLINE_DONE)response;
	    if ( ! done.getSuccessful()) {
		String failure = "";
		switch (done.getErrorNum()) {
		case POS_TO_RCS.INVALID_RTOC:
		    failure = "INVALID_RTOC";
		    break;
		case POS_TO_RCS.NOT_IN_CONTROL:
		    failure = "NOT_IN_CONTROL";
		    break;
		case OFFLINE.WINDOW_EXCEEDED:
		    failure = "WINDOW_EXCEEDED";
		    break;
		default:
		    failure = "UNSPECIFIED_ERROR";
		}
		return "RESULT FAIL "+nf12.format(reqNo)+" "+failure+CR;	
	    } else {
		return "RESULT OK "+nf12.format(reqNo)+CR;
	    }
	}


	/** Process the TESTLINK_DONE response.*/
	protected String processTESTLINK_DONE(POS_TO_RCS_DONE response)    { 
	    TESTLINK_DONE done = (TESTLINK_DONE)response;
	    System.err.println("Handling specific TESTLINK reply: "+done);

	    if ( ! done.getSuccessful()) {
		return "RESULT FAIL "+nf12.format(reqNo)+" UNSPECIFIED_ERROR"+CR;	
	    } else {
		switch (done.getReturnCode()) {
		case TESTLINK.PLANETARIUM_MODE:
		    return "RESULT OK "+nf12.format(reqNo)+" PLANETARIUM_MODE"+CR;
		case TESTLINK.NOT_PLANETARIUM_MODE:
		    return "RESULT OK "+nf12.format(reqNo)+" NOT_PLANETARIUM_MODE"+CR;
		case POS_TO_RCS.NOT_OPERATIONAL:
		    return "RESULT OK "+nf12.format(reqNo)+" NOT_OPERATIONAL"+CR;
		case POS_TO_RCS.INVALID_RTOC:
		    return "RESULT OK "+nf12.format(reqNo)+" INVALID_RTOC"+CR;		  
		case POS_TO_RCS.NOT_IN_CONTROL:
		    return "RESULT OK "+nf12.format(reqNo)+" NOT_IN_CONTROL"+CR;
		case TESTLINK.OVERRIDDEN:
		    return "RESULT OK "+nf12.format(reqNo)+" OVERRIDDEN"+CR;
		case TESTLINK.PLANETARIUM_INITIALIZING:
		    return "RESULT OK "+nf12.format(reqNo)+" PLANETARIUM_MODE_INITIALIZING"+CR;
		case TESTLINK.ENGINEERING_MODE:
		    return "RESULT OK "+nf12.format(reqNo)+" ENGINEERING"+CR;
		default:
		    return "RESULT OK "+nf12.format(reqNo)+" LINK_DOWN"+CR;
		}
	    }
	}

	/** Process the CCDOBSERVE_DONE response.*/
	protected String processCCDOBSERVE_DONE(POS_TO_RCS_DONE response)  {
	    CCDOBSERVE_DONE done = (CCDOBSERVE_DONE)response;

	    traceLog.log(INFO, 1, CLASS, id, "Start specific handling of: CCDOBSERVE_DONE response:");

	    if ( ! done.getSuccessful()) {
		String failure = "";

		// This is a quick fix to turn off the mosaic flag.
		// We assume that a failed observe means we cant 
		// possibly be doing a given mosaic anymore.
		// With G2 commands this will disappear !
		
		mosaicInProgress = false;
	    
	       	traceLog.log(INFO, 3, CLASS, id, "Switch mosaic flag off on failed obs: "+done.getErrorString());

		switch (done.getErrorNum()) {
		case CCDOBSERVE.CCD_FAULT:
		    failure = "CCD_FAULT";
		    break;
		case CCDOBSERVE.TELESCOPE_FAULT:
		    failure = "TELESCOPE_FAULT";
		    break;
		case CCDOBSERVE.ENCLOSURE_FAULT:
		    failure = "ENCLOSURE_FAULT";
		    break;
		case CCDOBSERVE.BAD_WEATHER:
		    failure = "BAD_WEATHER";
		    break;
		case CCDOBSERVE.BAD_COORDS:
		    failure = "BAD_COORDS";
		    break;
		case CCDOBSERVE.BAD_BINNING:
		    failure = "BAD_BINNING";
		    break;
		case CCDOBSERVE.BAD_FILTER:
		    failure = "BAD_FILTER";
		    break;
		case CCDOBSERVE.OBJECT_SET:
		    failure = "OBJECT_SET";
		    break;	
		case POS_TO_RCS.CLIENT_ABORTED:
		    failure = "CLIENT_ABORTED";	    
		    break;
		case POS_TO_RCS.NOT_PLANETARIUM_MODE:
		    failure = "NOT_PLANETARIUM_MODE";
		    break;
		case POS_TO_RCS.NOT_OPERATIONAL:
		    failure = "NOT_OPERATIONAL";
		    break;
		case POS_TO_RCS.NOT_IN_CONTROL:
		    failure = "NOT_IN_CONTROL";
		    break;
		case POS_TO_RCS.INVALID_RTOC:
		    failure = "INVALID_RTOC";
		    break;
		case 662:
		    // Special treatment for OVERRIDE...
		    failure = "OVERRIDDEN";
		    break;
		case TESTLINK.OVERRIDDEN:
		    failure = "OVERRIDDEN";
		    break;
		default:
		    //failure = "UNSPECIFIED_ERROR";
		    failure = "UNSPECIFIED_ERROR: ";
		    break;
		}
		return "RESULT FAIL "+nf12.format(reqNo)+" "+failure+CR;
	    } else {	
		return "RESULT OK "+nf12.format(reqNo)+" "+done.getFrameNumber()+CR;	    
	    }
	}

	/** Process the CCDPROCESS_DONE response.*/
	protected String processCCDPROCESS_DONE(POS_TO_RCS_DONE response)  { 
	    CCDPROCESS_DONE done = (CCDPROCESS_DONE)response; 
	    if ( ! done.getSuccessful()) {
		String failure = "";
		switch (done.getErrorNum()) {
		case CCDPROCESS.MISSING_IMAGE:
		    failure = "MISSING_IMAGE";
		    break;
		case CCDPROCESS.IMCOLOR_FAULT:
		    failure = "IMCOLOR_FAULT";
		    break;
		case CCDPROCESS.IMBEST_FAULT:
		    failure = "IMBEST_FAULT";
		    break;
		case CCDPROCESS.JPEG_FAULT:
		    failure = "JPEG_FAULT";
		    break;
		case CCDPROCESS.COMPRESSION_FAULT:
		    failure = "COMPRESSION_FAUL";
		    break;
		case CCDPROCESS.TRANSFER_FAULT:
		    failure = "TRANSFER_FAULT";
		    break;
		case CCDPROCESS.MOSAIC_FAULT:
		    failure = "MOSAIC_FAULT";
		    break;
		case POS_TO_RCS.NOT_OPERATIONAL:
		    failure = "NOT_OPERATIONAL";
		    break;
		case POS_TO_RCS.NOT_IN_CONTROL:
		    failure = "NOT_IN_CONTROL";
		    break;
		case POS_TO_RCS.INVALID_RTOC:
		    failure = "INVALID_RTOC";
		    break;
		default:
		    failure = "UNSPECIFIED_ERROR";
		    break;
		}
		return "RESULT FAIL "+nf12.format(reqNo)+" "+failure+CR;
	    } else {			
		return "RESULT OK "+nf12.format(reqNo)+" "+done.getFilename()+CR;
	    }
	}

	/** Process the GETQUEUE_DONE response.*/
	protected String processGETQUEUE_DONE(POS_TO_RCS_DONE response)    { 
	    GETQUEUE_DONE done = (GETQUEUE_DONE)response;
	    if ( ! done.getSuccessful()) {
		return "RESULT FAIL "+nf12.format(reqNo)+" UNSPECIFIED_ERROR"+CR;	
	    } else {
		Vector list = done.getProcessList();
		if (list == null || (list.size() == 0)) {
		    return "RESULT OK "+nf12.format(reqNo)+" EMPTY"+CR;
		} else {
		    Iterator it = list.iterator();
		    StringBuffer buff = new StringBuffer();
		    IntegerPair pair = null;
		    while (it.hasNext()) {
			pair = (IntegerPair)it.next();
			int procReqNo  = pair.i;
			int procState  = pair.j;
			String state = ""; 
			switch (procState) {
			case GETQUEUE.EXECUTING:
			    state = "EXECUTING";
			    break;
			case GETQUEUE.PENDING:
			    state = "PENDING";
			    break;		  
			default:
			    state = "UNKNOWN";
			}
			buff.append((String)commandMap.get(new Long(procReqNo))+","+
				    nf12.format(procReqNo)+","+
				    state+"&");
		    }
		    return "RESULT OK "+nf12.format(reqNo)+" "+buff.toString()+CR;		
		}
	    }
	}
	
	/** Process the ABORT_DONE response.*/
	protected String processABORT_DONE(POS_TO_RCS_DONE response)       { 
 	    ABORT_DONE done =(ABORT_DONE)response; 
	    if ( ! done.getSuccessful()) {
		String failure = "";
		switch (done.getErrorNum()) {
		case ABORT.NO_SUCH_REQUEST:
		    failure = "NO_SUCH_REQUEST";
		    break;
		default:
		    failure = "UNSPECIFIED_ERROR";
		}		
		return "RESULT FAIL "+nf12.format(reqNo)+" "+failure+CR;	
	    } else {
		return "RESULT OK "+nf12.format(reqNo)+" 0"+CR;
	    }
	}

	/** Processes the TELSTATUS_DONE response.*/
	protected String processTELSTATUS_DONE(POS_TO_RCS_DONE response)   { 
	    TELSTATUS_DONE done = (TELSTATUS_DONE)response; 
	    if ( ! done.getSuccessful()) {
		String failure = "";
		switch (done.getErrorNum()) {
		case TESTLINK.NOT_PLANETARIUM_MODE:
		    failure = "NOT_PLANETARIUM_MODE";
		    break;
		default:
		    failure = "UNSPECIFIED_ERROR";
		}	
		return "RESULT FAIL "+nf12.format(reqNo)+" "+failure+CR;	
	    } else {
		Hashtable hash = done.getStatus();
		Enumeration e = hash.keys(); 
		StringBuffer buff = new StringBuffer();
		String key = null;
		while (e.hasMoreElements()) {
		    key = (String)e.nextElement();
		    buff.append(key+","+hash.get(key)+"&");
		}
		return "RESULT OK "+nf12.format(reqNo)+" "+buff.toString()+CR;
	    }
	}

	/** Processes the METSTATUS_DONE response.*/
	protected String processMETSTATUS_DONE(POS_TO_RCS_DONE response)   {  
	    METSTATUS_DONE done = (METSTATUS_DONE)response;
	    if ( ! done.getSuccessful()) {
		String failure = "";
		switch (done.getErrorNum()) {
		case TESTLINK.NOT_PLANETARIUM_MODE:
		    failure = "NOT_PLANETARIUM_MODE";
		    break;
		default:
		    failure = "UNSPECIFIED_ERROR";
		}	
		return "RESULT FAIL "+nf12.format(reqNo)+" "+failure+CR;	
	    } else {
		Hashtable hash = done.getStatus();
		Enumeration e = hash.keys(); 
		StringBuffer buff = new StringBuffer(); 
		String key = null;
		while (e.hasMoreElements()) {
		    key = (String)e.nextElement();
		    buff.append(key+","+hash.get(key)+"&");
		}
		return "RESULT OK "+nf12.format(reqNo)+" "+buff.toString()+CR;
	    }
	}

	/** Process the CCDSTATUS_DONE response.*/
	protected String processCCDSTATUS_DONE(POS_TO_RCS_DONE response)   {  
	    CCDSTATUS_DONE done = (CCDSTATUS_DONE)response; 
	    if ( ! done.getSuccessful()) {
		String failure = "";
		switch (done.getErrorNum()) {
		case TESTLINK.NOT_PLANETARIUM_MODE:
		    failure = "NOT_PLANETARIUM_MODE";
		    break;
		default:
		    failure = "UNSPECIFIED_ERROR";
		}	
		return "RESULT FAIL "+nf12.format(reqNo)+" "+failure+CR;	
	    } else {
		Hashtable hash = done.getStatus();
		Enumeration e = hash.keys(); 
		StringBuffer buff = new StringBuffer(); 
		String key = null;
		while (e.hasMoreElements()) {
		    key = (String)e.nextElement();
		    buff.append(key+","+hash.get(key)+"&");
		}
		return "RESULT OK "+nf12.format(reqNo)+" "+buff.toString()+CR;
	    }	    
	}

	/** Returns the log-level at which to log this command's response.*/
	protected int commandLogLevel(String commandStr, int def) {
	    if (commandStr == null)
		return def;
	    // Find the command part.
	    String command = null;
	    int index = commandStr.indexOf(" ");
	    if (index == -1)
		command = commandStr;
	    else
		command = commandStr.substring(0, index);

	    if (commandLogLevels.containsKey(command))
		return ((Integer)commandLogLevels.get(command)).intValue();
	    return def;	    
	}
	
    }

}




