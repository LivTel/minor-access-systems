import java.io.*;
import java.net.*;
import java.util.*;


import ngat.util.logging.*;
import ngat.astrometry.*;
/** Class to perform GRB service requests. Currently we attach to the GCN local multicast
 * to receive forwarded packets from GCN_Relay. These are unmodified from the original 
 * GCN packets. On receipt of a packet (e.g. type 41) the appropriate handler method is called
 * and started in a new Thread.
 */
public class GrbServiceAgent implements Runnable {
   
    /** Default packet buffer size.*/ 
    public static final int BUFFER_SIZE = 1024;

    /** The socket to listen to.*/
    MulticastSocket socket;

    /** Byte stream.*/
    ByteArrayInputStream bin;

    /** Datastream.*/
    DataInputStream din;
    
    /** Logger.*/
    Logger logger;

    /** Create a GrbServiceAgent listening to the supplied M/Cast address and port.*/
    public GrbServiceAgent(InetAddress groupAddress,  int port) throws IOException {
	socket = new MulticastSocket(port);
	socket.joinGroup(groupAddress);
	
	// Logging.
	logger = LogManager.getLogger("GRB_SA");
	logger.setLogLevel(Logging.ALL);
	
	File logFile = new File(System.getProperty("user.dir"), "GCN");
	
	// Set up hourly/daily logs to GRB_SA.
	LogHandler console = new ConsoleLogHandler(new BogstanLogFormatter());
	console.setLogLevel(Logging.ALL);
	logger.addHandler(console);

	try {
	    LogHandler hflog   = new FileLogHandler(logFile.getPath(), 
						    new BogstanLogFormatter(), 
						    FileLogHandler.DAILY_ROTATION);
	    hflog.setLogLevel(Logging.ALL);
	    logger.addHandler(hflog);
	} catch (IOException iox) {
	     logger.log(3,"Failed to create file log handler: "+iox);
	}

    }

    /** Listen for packets and handle depending on type.*/
    public void run() {	
	
	while (true) {
	    try {
		byte[] buffer = new byte[BUFFER_SIZE];
		DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
		socket.receive(packet);
		//System.err.println("1. Got packet");
		buffer = packet.getData();	
		//System.err.println("2. Got data: buffer size: "+buffer.length);
		bin = new ByteArrayInputStream(buffer);
		//System.err.println("3. Opened BAIS");
		din  = new DataInputStream(bin);	
		//System.err.println("4. Opened DIS");
		
		int type = readType();
		
		logger.log(3,"--------------------");
		logger.log(3,"Read packet type: "+type);
		switch (type) {
		case 3: 
		    logger.log(1," [IMALIVE]");
		    handleImalive();
		    break;
		case 4:
		    //killed = true;
		    logger.log(1," [KILL-IGNORED]");
		    break;
		case 34:  
		    logger.log(1," [SAX/WFC_GRB_POS]");
		    handleSax();
		    break;
		case 40: 
		    logger.log(1," [HETE_ALERT]");			
		    handleHeteAlert();			   
		    break;
		case 41:
		    logger.log(1," [HETE_UPDATE]");		   
		    handleHeteUpdate();
		    break; 
		case 51:
		    logger.log(1," [INTEGRAL_POINTDIR]");
		    handleIntegralPointing();
		    break;
		default:
		    logger.log(1," [TYPE-"+type+"]");
		}
		
	    } catch (IOException e) {
		logger.log(3,"Error: "+e);
		break;
	    } finally {
		try {
		    if (din != null)
			din.close();
		} catch (IOException iox) {
		    logger.log(3,"Error closing BIN: "+iox);
		}
		
	    }		
	    din = null; 
	    bin = null;	    
	}	
	
    }
    
    /** Start a GrbServiceAgent listening on supplied MCast.*/
    public static void main(String args[]) {
	
	if (args == null || args.length == 0) {
	    usage();
	    return;
	}
	
	InetAddress address;	
	try {
	    address = InetAddress.getByName(args[0]);
	} catch (IOException e) {
	    usage();
	    return;
	}
	
	int port = 5656;
	try {
	    port = Integer.parseInt(args[1]);
	} catch (NumberFormatException e) {
	    usage();
	    return;
	}
	
	try {
	     GrbServiceAgent agent = new GrbServiceAgent(address, port);
	     new Thread(agent).start();
	} catch (IOException e) {
	    System.err.println("Error opening socket: "+e);
	    return;
	}	
    }
    
    private static void usage() {
	System.err.println("java GrbServiceAgent <multicast_address> <port>\n"+
			   "multicast_address : A multicast address e.g. 228.0.0.2\n"+
			   "             port : A port number 1024 - 65536.");
    }
    
    /** Holds the response to a TOCS command.*/
    private static class Response {

	public Hashtable info = new Hashtable();
		
	public void put(String key, Object value) {
	    info.put(key,value);
	}

	public Object get(String key) {	    
	    return info.get(key);
	}

    }
    
    /** Read the packet type.*/
    protected int readType() throws IOException {
	int type = din.readInt();	
	return type;
    }
    
    /** Read the packet terminator.*/
    protected void readTerm() throws IOException {
	din.readByte();
	din.readByte();
	din.readByte();
	din.readByte();
	logger.log(3,"-----Terminator");
    }
    
    /** Read the header. */
    protected void readHdr() throws IOException {
	int seq  = din.readInt(); // SEQ_NO.
	int hop  = din.readInt(); // HOP_CNT. 
	logger.log(3,"Header: Packet Seq.No: "+seq+" Hop Count: "+hop);
    }
    
    /** Read the SOD for date.*/
    protected void readSod() throws IOException {
	int sod = din.readInt();
	logger.log(3,"SOD: "+sod);
    }
    
    /** read stuffing bytes. */
    protected void readStuff(int from, int to) throws IOException {
	for (int i = from; i <= to; i++) {
	    din.readInt();
	}
	logger.log(3,"Skipped: "+from+" to "+to);
    }
    
    /** Handle an IMALIVE message.*/
    public void handleImalive() {
	try {
	    readHdr();
	    readSod();
	    readStuff(4, 38);
	    readTerm();
	} catch (IOException e) {
	    logger.log(1,"IM_ALIVE: Error reading packet: "+e);
	}
    }
    
    /** Handle a SAX/WFC message.*/
    public void handleSax() {
	try {
	    readHdr(); // 0, 1, 2
	    readSod();     // 3
	    readStuff(4,4);   // 4 - spare
	    int burst_tjd = din.readInt(); // 5 - burst_tjd
	    int burst_sod = din.readInt(); // 6 - burst_sod
	    logger.log(1,"Burst: TJD:"+burst_tjd+" SOD: "+burst_sod);
	    int bra =  din.readInt(); // 7 - burst RA [ x10000 degrees]
	    int bdec = din.readInt(); // 8 - burst Dec [x10000 degrees].
	    int bint = din.readInt(); // 9 - burst intens mCrab.
	    logger.log(1,"RA: "+bra+" Dec: "+bdec+" Intensity:"+bint+" [mcrab]");
	    readStuff(10, 10);   // 10 - spare
	    int berr  = din.readInt(); // 11 - burst error
	    int bconf = din.readInt(); // 12 - burst conf [% x 100].
	    logger.log(1,"Burst Error: "+berr+" Confidence: "+bconf);
	    readStuff(13, 17); // 13,, 17 - spare.		
	    int trig_id = din.readInt(); // 18 - trigger flags.
	    logger.log(1,"Trigger Flags: "+trig_id);
	    din.readInt(); // 19 - stuff.
	    readStuff(20, 38); // 20,, 38 - spare.
	    readTerm(); // 39 - TERM.	   
	} catch (IOException e) {
	    logger.log(1,"SAX_WFC_POS: Error reading packet: "+e);
	}
    }
    
    /** Handle a HETE_ALERT message.*/
    public void handleHeteAlert() {
	try {
	    readHdr(); // 0, 1, 2
	    readSod();     // 3
	    int tsn = din.readInt();   // 4 - trig_seq_num
	    int burst_tjd = din.readInt(); // 5 - burst_tjd
	    int burst_sod = din.readInt(); // 6 - burst_sod
	    logger.log(1,"Trig. Seq. No: "+tsn+" Burst: TJD:"+burst_tjd+" SOD: "+burst_sod);
	    readStuff(7, 8); // 7, 8 - spare 
	    //int trig_flags = GAMMA_TRIG | WXM_TRIG | PROB_GRB;
	    int trig_flags = din.readInt(); // 9 - trig_flags
	    logger.log(1,"Trigger Flags: "+trig_flags);
	    int gamma = din.readInt();   // 10 - gamma_cnts
	    int wxm = din.readInt(); // 11 - wxm_cnts
	    int sxc = din.readInt();  // 12 - sxc_cnts
	    logger.log(1,"Counts:: Gamma: "+gamma+" Wxm: "+wxm+" Sxc: "+sxc);
	    int gammatime = din.readInt(); // 13 - gamma_time
	    int wxmtime = din.readInt(); // 14 - wxm_time
	    int scpoint = din.readInt(); // 15 - sc_point
	    logger.log(1,"Time:: Gamma: "+gammatime+" Wxm: "+wxmtime);
	    logger.log(1,"SC Point:"+scpoint);
	    readStuff(16, 38); // 16,, 38 spare
	    readTerm(); // 39 - TERM.
	} catch  (IOException e) {
	    logger.log(1,"HETE ALERT: Error reading packet: "+e);
	}
      
    }
    
    /** Handle a HETE_UPDATE message.*/
    public void handleHeteUpdate() { 
	int bra = 0;
	int bdec = 0;
	int trigNum = 0;
	int mesgNum = 0;
	try {
	    readHdr(); // 0, 1, 2 - pkt_type, pkt_sernum, pkt_hop_cnt
	    readSod();     // 3 - pkt_sod
	    int tsn = din.readInt();   // 4 - trig_seq_num
	    trigNum = (tsn & 0x0000FFFF);
	    mesgNum = (tsn & 0xFFFF0000) >> 16;
	    int burst_tjd = din.readInt(); // 5 - burst_tjd
	    int burst_sod = din.readInt(); // 6 - burst_sod
	    logger.log(1,"Trigger No: "+trigNum+" Mesg Seq. No: "+mesgNum);
	    logger.log(1,"Burst: TJD:"+burst_tjd+" SOD: "+burst_sod);
	    bra = din.readInt(); // Burst RA (x10e4 degs). // 7 - burst_ra
	    bdec = din.readInt(); // Burst Dec (x10e4 degs). // 8 = burst_dec
	    logger.log(1,"Burst RA: "+Position.toHMSString(Math.toRadians((double)bra)/10000.0));
	    logger.log(1,"Burst Dec: "+Position.toDMSString(Math.toRadians((double)bdec)/10000.0));	   
	    int trig_flags = din.readInt(); // 9 - trig_flags
	    logger.log(1,"Trigger Flags: 0x"+Integer.toHexString(trig_flags));
	    int gamma = din.readInt();   // 10 - gamma_cnts
	    int wxm   = din.readInt(); // 11 - wxm_cnts
	    int sxc   = din.readInt();  // 12 - sxc_cnts
	    logger.log(1,"Counts:: Gamma: "+gamma+" Wxm: "+wxm+" Sxc: "+sxc);
	    int gammatime = din.readInt(); // 13 - gamma_time
	    int wxmtime = din.readInt(); // 14 - wxm_time
	    int scpoint = din.readInt(); // 15 - sc_point
	    int sczra   = (scpoint & 0xFFFF0000) >> 16;
	    int sczdec  = (scpoint & 0x0000FFFF);
	    logger.log(1,"Time:: Gamma: "+gammatime+" Wxm: "+wxmtime);
	    logger.log(1,"SC Pointing: RA: "+Position.toHMSString(Math.toRadians((double)sczra)/10000.0)+
		       " Dec: "+Position.toDMSString(Math.toRadians((double)sczdec)/10000.0));
	    readStuff(16, 35); // skipped the wx, sx error boxes for now! 
	    logger.log(1,"Skipped WXM and SXC error boxes for now !");
	    int posFlags = din.readInt(); // 36 - pos_flags
	    logger.log(1,"Pos Flags: 0x"+Integer.toHexString(posFlags));
	    int validity = din.readInt(); // 37 - validity flags.
	    logger.log(1,"Validity Flag: 0x"+Integer.toHexString(validity));
	    readStuff(38, 38); // 38 -spare
	    readTerm(); // 39 - TERM.
	    
	    // Ok start sending commands to TOCS.
	    String sessionId = null;
	    Response resp = null;
	    
	    try { 
		System.err.println("STARTING:");
		
		for (int i = 0; i < 4; i++) { 
		    try {
			
			if ( (resp = sendCommand("HELO LT_GRB_SA1") ) != null) {
			    String ss = (String)resp.get("sessionID");
			    sessionId = new String(ss);
			    System.err.println("Using sessionID ["+sessionId+"]");
			    break;
			}
		    } catch  (IOException e) {	    
			System.err.println("ERROR: While talking to RCS: "+e);
		    }				    
		}
		
		System.err.println("Gratuitous Status request.");
		sendCommand("STATUS METEO pressure");
		
		// Slew to burst location.
		String str_bra  = Position.formatHMSString(Math.toRadians((double)bra)/10000.0, ":");
		String str_bdec = Position.formatDMSString(Math.toRadians((double)bdec)/10000.0, ":");
		String bid = "HETE_"+trigNum+"_"+mesgNum;
		try {
		    sendCommand("SLEW "+sessionId+" "+bid+" "+str_bra+" "+str_bdec);
		} catch  (IOException e) {
		    System.err.println("ERROR: While talking to RCS: "+e);
		}
				
		// Search phase.
		System.err.println("SEARCH PHASE - START");
		
		sendCommand("INSTR "+sessionId+" RATCAM clear Bessell-V 4");	
		for (int i = 0; i < 5; i++) {
		    for (int j = 0; j < 5; j++) {
			if (sendCommand("OFFSET "+sessionId+" "+(i*10.0)+" "+(j*20.0)) == null)
			    await(3000);
			if (sendCommand("EXPOSE "+sessionId+" 1000.0 1 T") == null)
			    await(3000);
		    }
		}
		System.err.println("SEARCH PHASE - END");
		
		// Op Phase.
		System.err.println("OP PHASE - START");
		sendCommand("INSTR "+sessionId+" RATCAM clear Bessell-B 2");		
		
		for (int tt = 2000; tt < 64000; tt *=2) {
		    if (sendCommand("EXPOSE "+sessionId+" "+tt+" 1 T") == null)
			await(3000);
		}
		System.err.println("OP PHASE - END");
		
		sendCommand("QUIT "+sessionId);
		
	    } catch  (IOException e) {
		System.err.println("ERROR: While talking to RCS: "+e);
	    }
	    System.err.println("ABANDONNED ATTEMPT:");
	    	
	} catch  (IOException e) {
	    logger.log(1,"HETE UPDATE: Error reading packet: "+e);
	}
    }
    
    /** Handle INTEGRAL_POINTDIR message.*/
    public void handleIntegralPointing() {
	try {
	    readHdr(); // 0, 1, 2 - pkt_type, pkt_sernum, pkt_hop_cnt
	    readSod(); // 3
	    int tsn = din.readInt();   // 4 - trig_seq_num
	    int trigNum = (tsn & 0x0000FFFF);
	    int mesgNum = (tsn & 0xFFFF0000) >> 16;  
	    logger.log(1,"Trigger No: "+trigNum+" Mesg Seq. No: "+mesgNum);
	    int slewTjd = din.readInt(); // 5 Slew TJD.
	    int slewSod = din.readInt(); // 6 Slew SOD.
	    logger.log(1,"Slew at: "+slewTjd+" TJD Time: "+slewSod+" Sod.");
	    readStuff(7, 11);
	    int flags   =  din.readInt(); // 12 Test Flags.
	    logger.log(1,"Test Flags: ["+Integer.toHexString(flags).toUpperCase()+"]");
	    din.readInt(); // 13 spare.
	    int scRA    = din.readInt(); // 14 Next RA *10000.
	    int scDec   = din.readInt(); // 15 Next Dec *10000.

	    double ra = ((double)scRA)/10000.0;
	    double dec= ((double)scDec)/10000.0;
	    logger.log(1,"SC Slew to: RA:"+ra+" degs, Dec: "+dec+" degs."); 
	    readStuff(16,18);
	    int scStat  = din.readInt(); // 19 Status and attitude flags.
	    logger.log(1,"Status Flags;: ["+Integer.toHexString(scStat).toUpperCase()+"]");
	    readStuff(20, 38);
	    readTerm(); // 39 - TERM.	 
	} catch  (IOException e) {
	    logger.log(1,"INTEGRAL POINTING: Error reading packet: "+e);
	}
    }
    
    /** Sleep a thread.*/
    private static void await(long time) {
	try { Thread.sleep(time); } catch (InterruptedException e){}
    }
    
    /** Send a command and receive resopnse from TOCM.*/
    private static Response sendCommand(String command) throws IOException {
	String reply = "";
	System.err.println("REQUEST: "+command);
	Socket         gsock = new Socket("ltccd1", 8610);;	
	PrintStream    gout  = new PrintStream(gsock.getOutputStream());	
	BufferedReader gin   = new BufferedReader(new InputStreamReader(gsock.getInputStream()));
	
	System.err.println("SENDING:");
	gout.println(command+"\r\n");
	
	reply = gin.readLine();
	System.err.println("REPLY: "+reply);
	
	try {
	    gout.close();
	    gin.close();
	    gsock.close();
	} catch (IOException e) {}
	
	if (reply != null && reply.startsWith("OK")) {
	    reply = reply.substring(2);
	    Response resp = new Response();
	    StringTokenizer parser = new StringTokenizer(reply, ",");
	    while (parser.hasMoreTokens()) {
		String tok = parser.nextToken();
		if (tok.indexOf("=") != -1) {
		    String key = tok.substring(0, tok.indexOf("="));
		    String val = tok.substring(tok.indexOf("=")+1);
		    System.err.println("Storing: ["+key.trim()+"] ["+val.trim()+"]");
		    resp.put(key.trim(), val.trim());
		}
	    }
	    return resp;
	} else
	    return null;
    }
   
}
