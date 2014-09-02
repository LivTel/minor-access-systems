//package ngat.rcs;

import ngat.net.*;
import ngat.util.*;
import ngat.util.logging.*;

import java.io.*;
import java.util.*;
import java.text.*;


/** Implementation of the POS server for handling commands sent by the
 * Planetarium relay server.
 * <br><br>
 * $Id: POS_DummyServer.java,v 1.1 2006/11/17 09:53:45 snf Exp $
 */
public class POS_DummyServer extends SocketServer {

    public static final String DEFAULT_SERVERPC_URL     = "localhost";
    
    public static final String DEFAULT_ALTERNATIVE_URL  = "localhost";

    public static final int    DEFAULT_SERVERPC_PORT    = 5555;
    
    public static final int    DEFAULT_ALTERNATIVE_PORT = 6666;

    public static final long   DEFAULT_HANDLING_TIME    = 5000L;

    public static final int    DEFAULT_BANDWIDTH        = 20; // KBytes/sec.

    public static int DEFAULT_SERVER_PORT = 8010;

    /** The single instance of POS_Server.*/
    private static POS_DummyServer instance = null;

    static final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
    
    public long planetariumStart = 0L;

    public long planetariumEnd = 0L;

    public ConfigurationProperties config;

    public SSLFileTransfer.Client client;

    LogHandler console;

    String aurl;
    
    int aport;

    String surl;

    int sport;

    /** Create an POS_Dummy_Server bound to the specified port. <br>
     * This should have been defined in an RCS configuration file 
     * as <b>pos.port</b>.
     * @param port The port to bind to.
     */
    private POS_DummyServer(int port) throws IOException {
	super(port);
	rhFactory = POS_DummyCommandImplFactory.getInstance();
	piFactory = JMSMA_ProtocolImplFactory.getInstance();

	console = new ConsoleLogHandler(new SimpleLogFormatter());
	console.setLogLevel(Logging.ALL);

	Logger logger = LogManager.getLogger("ngat.net.JMSMA_ProtocolServerImpl");
	logger.setLogLevel(3);		
	logger.addHandler(console);      
    }
    
    /** Starts up the server. Just calls start() on its execution
     * thread.*/
    public static void launch() {
	instance.start();
    }

    /** @return The single instance of POS_Dummy_Server. If no instance
     * has yet been created will return null.*/
    public static POS_DummyServer getInstance(int port) throws IOException {
	if (instance == null)
	    instance = new POS_DummyServer(port);
	return instance;
    } 

    /** @return The single instance of POS_Dummy_Server. */
    public static POS_DummyServer getInstance() {
	return instance; 
    }

    public void configure(File file) throws IOException {
	config = new ConfigurationProperties();	
	config.load(new FileInputStream(file));
	
	try {
	    planetariumStart = (sdf.parse(config.getProperty("planetarium.start"))).getTime();
	    planetariumEnd   = (sdf.parse(config.getProperty("planetarium.end"))).getTime();
	} catch (ParseException px) {
	    planetariumStart = System.currentTimeMillis();;
	    planetariumEnd   = planetariumStart + 86400*1000L; // +24 hours.
	}

	Logger classLogger = LogManager.getLogger("SSL");
	classLogger.setLogLevel(Logging.ALL);
	classLogger.addHandler(console);
	
	// Setup the SSL connection here.
	SSLFileTransfer.setLogger("SSL");
	
	surl = 
	    config.getProperty("dest.serverpc.url", DEFAULT_SERVERPC_URL);
	sport=
	    config.getIntValue("dest.serverpc.port", DEFAULT_SERVERPC_PORT);
		
	aurl = 
	    config.getProperty("dest.alternative.url", DEFAULT_ALTERNATIVE_URL);
	aport=
	    config.getIntValue("dest.alternative.port", DEFAULT_ALTERNATIVE_PORT);
	


	client 
	    = new SSLFileTransfer.Client("DUMMY_SERVER", surl, sport);
	
	File   pcaPrivateKeyFile  = new File(config.getProperty("key.file"));
	File   relayPublicKeyFile = new File(config.getProperty("trust.file"));
	String pcaPassword        = config.getProperty("password", "geronimo");
	
	int    bandWidth          = config.getIntValue("transfer.bandwidth", DEFAULT_BANDWIDTH);
	
	client.setBandWidth(bandWidth);
		    
	try {
	    client.initialize(pcaPrivateKeyFile, pcaPassword, relayPublicKeyFile);	   
	} catch (Exception ex) {
	    System.err.println("Error initializing SSL client: "+ex);
	    client = null;
	}

    }

    public static void main(String args[]) {
	if (args.length < 1) {
	    System.err.println("Usage: java POS_DummyServer <config-file> <port>");
	    System.exit(3);
	}
	int port = DEFAULT_SERVER_PORT;
	try {
	    port = Integer.parseInt(args[1]);
	} catch (NumberFormatException nx) {
	    System.err.println("Error parsing port number: "+nx);
	    System.exit(4);
	}
	
	try {
	    POS_DummyServer.getInstance(port).configure(new File(args[0]));
	    System.err.println("Dummy server configuration OK");
	} catch (IOException e) {
	    System.err.println("Failed to configure Dummy server: "+e);
	    System.exit(2);
	}
	
	POS_DummyServer.launch();

    }
	
}

/** $Log: POS_DummyServer.java,v $
/** Revision 1.1  2006/11/17 09:53:45  snf
/** Initial revision
/**
/** Revision 1.1  2001/07/20 09:37:04  snf
/** Initial revision
/**
/** Revision 1.2  2001/04/27 17:14:32  snf
/** backup
/**
/** Revision 1.1  2001/03/15 15:10:49  snf
/** Initial revision
/**
/** Revision 1.1  2000/12/14 11:53:56  snf
/** Initial revision
/** */
