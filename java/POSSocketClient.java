import java.io.*;
import java.net.*;
import java.util.*;
import javax.net.ssl.*;
import javax.security.cert.*;

/**
 *	POSSocketClient
 *
 *      A simple SSL client for testing the POS_CommandRelay.
 *     
 *      This can be run from the command line by entering:-
 *      <p>
 *      <i>java POSSocketClient <host-address> [<port>]</i>
 *      where port defaults to 8080.
 * 
 *      An instance of POSSocketClient can also be constructed and commands sent
 *      using the public method sendCommand(String). The associated interface
 *      POSSocketListener can be used to register callbacks from the Client.
 *      This is accomplished by implementing the methods:
 *      <ul>
 *        <li>handleAcknowledge(String) to receive the ACKNOWLEDGE response.
 *        <li>handleResult(string) to receive the RESULT response.
 *      </ul>
 *      and setting the implementing class as a listener via
 *      public method setSocketListener(POSSocketListener).
 *
 *      The following code snippet demonstrates an example of a client which sends
 *      regular METSTATUS commands and extracts the windspeed for plotting.
 *
 *      <pre>
 *       public class POSTester implements POSSocketListener {
 *
 *          private final long   DELAY   = 5000L; 
 *          private final String COMMAND = "METSTATUS";
 *
 *
 *          private POSSocketClient client;
 *       
 *          public static void main(String args[]) {
 *             // use args to specify host and port of the POS_CommandRelay.            
 *             POSTester tester = new POSTester(args[0], args[1]);
 *             try {
 *                 tester.execute();
 *             } catch (Exception e) { System.err.println("Executing: "+e); return; }
 *          }     
 *       
 *          POSTester(String host, int port) {
 *             client = new POSSocketClient(host, port);
 *           
 *          }
 *       
 *          // Loop forever..breakout if any I/O error occurs.
 *          public void execute() throws IOException { 
 *             client.connect();
 *             while (true) {
 *                client.sendCommand(COMMAND);
 *                try { Thread.sleep(DELAY); } catch (InterruptedException ix) {}
 *             }
 *             client.shutdown();
 *          }
 *       
 *          // From POSSocketListener.       
 *          public void handleAcknowledge(String result) {
 *             System.err.println("Received ACK: "+result);
 *          }
 *
 *          // From POSSocketListener.    
 *          public void handleResult(String result) {
 *             System.err.println("Received RESPONSE: "+result);
 *             // Hopefully RESULT OK + comma seperated list of MET data.
 *             parseAndPlot(result);
 *          }   
 
 *                     
 *          private void parseAndPlot(String result) { 
 *             // Parse AND Plot stuff 
 *          }           
 *       }
 */
public class POSSocketClient {
    
    /** Location specific host address.*/
    private static final String HOST = "ltccd1";

    /** Location specific host port.*/
    private static final int    PORT = 8080;

    /** Output to the POS_CommandRelay.*/
    protected PrintStream out;
    
    /** Input from the POS_CommandRelay.*/
    protected BufferedReader in;

    /** User input from System.in.*/
    protected BufferedReader uin;

    /** The Socket.*/
    protected Socket socket;

    /** The POS_CommandRelay host address (name or quad).*/
    protected String host;

    /** The POS_CommandRelay port.*/
    protected int port;

    /** True if secure connection.*/
    protected boolean secure = false;

    /** Listener (one of) to receive ACK and RESULT callbacks.*/
    protected POSSocketListener listener;

    /** create a POSSOcketClient to connect to the specified host.
     * @param host The POS_CommandRelay host address (name or quad format).
     * @param port The port the POS_CommandRelay is listening on.
     */
    public POSSocketClient(String host, int port, boolean secure) {
	this.host   = host;
	this.port   = port;
	this.secure = secure;
    }

    /** Invoke from command line as. <i> java POSSocketClient <host> <port> </i>.*/
    public static void main(String[] args) {
	
	String host = HOST;
	if (args.length >= 1)
	    host = args[0];
	
	int port = PORT;
	if (args.length >= 2) {
	    try {
		port = Integer.parseInt(args[1]);
	    } catch (Exception e) {}
	}

	boolean secure = false;
	if (args.length == 3) {
	    if (args[2].equals("secure"))
		secure = true;
	}
	
	System.err.println("Opening connection for: "+host+" : "+port);
	POSSocketClient client = new POSSocketClient(host, port, secure);

	try {
	    client.run();
	} catch (Exception e) {
	    System.err.println("Error: "+e);
	    e.printStackTrace();
	    return;
	}
	
    } // (Main).

    /** Send a command string to the POS_CommandRelay.
     * @param command The correctly formatted command string for the POS_CommandRelay.
     * @exception IOException If anything untoward occurs during send or read.
     */
    public void sendCommand(String command) throws IOException {
	// Forward.
	out.println(command);
	// Read ACK.
	String line = in.readLine();
	if (listener != null)
	    listener.handleAcknowledge(line);
	// ACK FAILED then no response extra to this.
	if (line.indexOf("FAIL") == -1) {
	    line = in.readLine();
	    if (listener != null)
		listener.handleResult(line);
	}
    }
    
    /** Initialise the connection.
     * @exception IOException If any I/O error occurs during conection setup.
     */
    public void connect() throws IOException {
	
	
	if (secure) {
	    // Get an SSL SocketFactory.
	    SSLSocketFactory sf = (SSLSocketFactory)SSLSocketFactory.getDefault();
	    socket = sf.createSocket(host,port);
	    
	    // Get the input and output streams.
	    // These will be  encrypted transparently.
	
	    // ##### START >>> SPECIAL AUTHENTICATION ####
	    SSLSession session = ((SSLSocket)socket).getSession();
	    X509Certificate[] chain = null; 
	    StringBuffer    buff = new StringBuffer();
	    try {
		chain = session.getPeerCertificateChain();
		X509Certificate         cert = chain[0];
		java.security.Principal cp   = cert.getSubjectDN();
		String                  cn   = cp.getName();
		
		StringTokenizer tok  = new StringTokenizer(cn, ",");
		
		String          item = null;
		while (tok.hasMoreTokens()) {
		    item = tok.nextToken();
		    if (item.indexOf("=") != -1) {
			String key = item.substring(0, item.indexOf("=")).trim();
			String val = item.substring(item.indexOf("=")+1).trim();
			buff.append("\n\t"+key+"\t=\t"+val);		
		    }
		}
	    } catch (SSLPeerUnverifiedException px) {
		System.err.println("Special authentication: Could not verify peer: "+px);
		throw new IOException("Unable to verify peer: "+px);
	    }
			
	    System.err.println("Server Verified:"+buff.toString());
	    // ##### END <<< SPECIAL AUTHENTICATION ####
	} else {
	    socket = new Socket(host, port);
	}
	
	System.err.println("Connection established to POS_CommandRelay on "+
			   host+" : "+port+".\n");

	out = new PrintStream(socket.getOutputStream());
	in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
	
	// Read the preamble. 5 lines + POS>> prompt
	
	String line = in.readLine();
	System.err.println("1:"+line);
	line = in.readLine();
	System.err.println("2:"+line);
	line = in.readLine();
	System.err.println("3:"+line);
	line = in.readLine();
	System.err.println("4:"+line);
	line = in.readLine();
	System.err.println("5:"+line);
	line = in.readLine();
	System.err.println("6:"+line);
	for (int i = 0; i < 5; i++){
	    int chr = in.read();
	    System.err.print((char)chr);
	}		    
	
    }

    /** Shuts down the connection gracefully?.*/
    public void shutdown() {
	System.err.println("Shutting down..");
	if (out != null) {
	    try {
		out.close();
	    } catch (Exception e) {e.printStackTrace();}
	}
	if (in != null) { 
	    try {
		in.close(); 
	    } catch (Exception e) {e.printStackTrace();}
	}
	if (socket != null) { 
	    try {
		socket.close(); 
	    } catch (Exception e) {e.printStackTrace();}
	}
    }

    /** Registers an implementor of the POSSocketListener interface for
     * callbacks when the ACKNOWLEDGE and RESULT messages are returned from
     * the POS_CommandRelay.
     * @param listener The implementor to register.
     */
    public void setSocketListener(POSSocketListener listener) {
	this.listener = listener;
    }
    
    /** Called by main() when run from command line. 
     * Reads commands from System.in and forwards to the POS_CommandRelay.
     * To stop this either enter SHUTDOWN OFF (kills the POS_ComandRelay)
     * or use ctrl-C or similar. QUIT may also work.
     */
    private void run() throws IOException {

	connect();

	uin = new BufferedReader(new InputStreamReader(System.in));
	
	String line = null;
	boolean run = true;
	while (run) {
	    // 1. Read the user input and send onwards.
	    try {
		line = uin.readLine(); 
		if (line.trim().equalsIgnoreCase("QUIT")) {
		    run = false;
		    continue;
		}
		out.println(line);
		out.flush();		 
	    } catch (Exception e) {
		System.err.println("EXC: "+e); 
		e.printStackTrace();
	    }
	    
	    // 2. Read the server ACK responses. 
	    try {
		line = in.readLine();
		System.err.println("ServerAck:"+line);
	    } catch (Exception e) {
		System.err.println("EXC: "+e);  
		e.printStackTrace();
	    }
	    
	    // x. Quit if NULL.
	    if (line == null) {
		run = false;
		System.err.println("Connection broken");
		continue;
	    }
	    
	    // 3. ACK FAILED then no response extra to this.
	    if (line.indexOf("FAIL") == -1) {
		try {
		    line = in.readLine();
		    System.err.println("ServerResponse:"+line);
		} catch (Exception e) {
		    System.err.println("EXC: "+e);
		    e.printStackTrace();
		}
	    } 
	    
	}
	
	shutdown();
	
    } // (run).

}
    



