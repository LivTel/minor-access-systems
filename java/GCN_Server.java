// GCN_Server.java
// $Header: /home/dev/src/planetarium/java/RCS/GCN_Server.java,v 1.12 2005/05/03 12:48:34 cjm Exp $
import java.io.*;
import java.net.*;
import java.util.*;
import java.text.*;

import java.applet.*;

import ngat.util.*;
import ngat.util.logging.*;
import ngat.astrometry.*;

/** 
 * This class implements the Remote Agent (RA) component of the GCN 
 * Target of Opportunity Service. 
 * Version $Revision: 1.12 $
 */
public class GCN_Server extends Thread
{
	/**
	 * Revision control system version id.
	 */
	public final static String RCSID = "$Id: GCN_Server.java,v 1.12 2005/05/03 12:48:34 cjm Exp $";

	/**
	 * Default name for this GCN_Server.
	 */
	public static final String DEFAULT_ID = "GCN_SERVER";

	/**
	 * Default SA host.
	 */
	public static final String DEFAULT_SERVICE_HOST = "localhost";

	/** Default SA port.*/
	public static final int    DEFAULT_SERVICE_PORT = 8410;

	/** Default listen port.*/
	public static final int    DEFAULT_PORT = 5169;

	/** Default multicast port.*/
	public static final int    DEFAULT_MULTICAST_PORT = 2005;

	/** Default multicast group address.*/
	public static final String DEFAULT_MULTICAST_ADDRESS = "224.103.114.98";

	/** Default timeout for the socket after connection is established (millis). 
	 * This allows the server to re-connect if no packets are received within a period.*/
	public static final int    DEFAULT_SOCKET_TIMEOUT = 1200000;

	/** Input buffer - easily big enough at 256.*/
	public final static int BUFF_SIZE = 256;

	/** Server ID.*/
	protected String id;

	/** GCN port to listen to.*/
	int port;

	/** Stream to write (echo) to socket.*/
	DataOutputStream out = null;
    
	/** Stream to read from socket.*/
	DataInputStream in = null;
    
	/** Stream to read from buffer.*/
	ByteArrayInputStream bin = null;

	/** Date stream to read from buffer.*/
	DataInputStream din = null;
    
	/** Buffer to store packet.*/
	byte[] buffer;
    
	/** Host for the service agent (SA) counterpart at the telescope site.*/
	protected String serviceHost;

	/** Port for the service agent (SA) counterpart at the telescope site.*/
	protected int servicePort;

	/** Timeout period for socket after connection is established.*/
	protected int timeout;

	/** Logging directory.*/
	protected String logDirName;

	/** Logger.*/
	protected Logger logger;

	/** Mulicast sender - relays packets about for interested parties to catch.*/
	MulticastSocket mcast;

	/** Multicast group address name.*/
	String maddr;

	/** Multicast group address.*/
	InetAddress groupAddress;

	/** Multicast port.*/
	int mport;

	/** Multicast is ok.*/
	boolean mcastOkay;

	/** Create a GCN_Server.*/
	public GCN_Server(String id) {
		super("X-"+id);
		this.id = id;	
		mcastOkay = false;
	}

	public static void main(String args[]) {
	
		CommandParser parser = new CommandParser();	
		try {
			parser.parse(args);
		} catch (ParseException px) {
			System.err.println("Error parsing command line: "+px);
			usage();
			return;
		}	

		ConfigurationProperties config = parser.getMap();

		boolean help = config.getBooleanValue("help", false);
		if (help) {
			usage();
			return;
		}

		String id     = config.getProperty("id",      DEFAULT_ID);
		int    port   = config.getIntValue("port",    DEFAULT_PORT);
		String shost  = config.getProperty("svchost", DEFAULT_SERVICE_HOST);
		int    sport  = config.getIntValue("svcport", DEFAULT_SERVICE_PORT );
		String logdir = config.getProperty("logs",    System.getProperty("user.dir"));
	
		int    timeout= config.getIntValue("timeout", DEFAULT_SOCKET_TIMEOUT);

		int    mport  = config.getIntValue("mport",   DEFAULT_MULTICAST_PORT);
		String maddr  = config.getProperty("maddr",   DEFAULT_MULTICAST_ADDRESS);
	
		try {
			GCN_Server gcnServer = new GCN_Server(id);
			gcnServer.setPort(port);
			gcnServer.setServiceHost(shost);
			gcnServer.setServicePort(sport);
			gcnServer.setTimeout(timeout);
			gcnServer.setLogDirName(logdir);
			gcnServer.setMulticastAddress(maddr);
			gcnServer.setMulticastPort(mport);
			gcnServer.start();
		}  catch (Exception e) {
			usage();
			return;
		}	
	}
    
	public static void usage() {
		System.err.println("USAGE: java GCN_Server [options]: "+
				   "\n       where options include:"+
				   "\n       -id <id>        : identity."+
				   "\n       -port <port>    : port to listen to."+
				   "\n       -svchost <host> : SA host at telescope."+
				   "\n       -svcport <port> : SA port."+
				   "\n       -mport   <port> : Multicast port."+
				   "\n       -maddr   <addr> : Multicast address.");
	
	}
    
	/** Set the SA host.*/
	protected void setServiceHost(String sh) { this.serviceHost = sh; }
    
	/** Set the SA port.*/
	protected void setServicePort(int p) { this.servicePort = p; }
    
	/** Set the listen port.*/
	protected void setPort(int p) { this.port = p; }
    
	/** Set the Logging directory name.*/
	protected void setLogDirName(String ld) { this.logDirName = ld; }

	/** Set the Multicast relay address name.*/
	protected void setMulticastAddress(String maddr) {this.maddr = maddr; }

	/** Set the Multicast relay port.*/
	protected void setMulticastPort(int p) { this.mport = p; }

	/** Set the socket timeout (millis) after connection is established.*/
	protected void setTimeout(int t) { this.timeout = t; }

	public void run() {

		logger = LogManager.getLogger("GCN_RA");
		logger.setLogLevel(Logging.ALL);

		File logFile = new File(logDirName, "GCN");
	
		// Set up hourly/daily logs to GCN_RA.
		LogHandler console = new ConsoleLogHandler(new BasicLogFormatter());
		console.setLogLevel(Logging.ALL);
		logger.addHandler(console);

		try {
			LogHandler hflog   = new FileLogHandler(logFile.getPath(), 
								new BasicLogFormatter(), 
								FileLogHandler.DAILY_ROTATION);
			hflog.setLogLevel(Logging.ALL);
			logger.addHandler(hflog);
		} catch (IOException iox) {
			logger.log(3,"Failed to create file log handler: "+iox);
		}

		// Try to setup a Multicaster - if not tough!
		try {
			mcast = new MulticastSocket(mport);
			groupAddress = InetAddress.getByName(maddr);
			mcast.joinGroup(groupAddress);
			mcastOkay = true;
		} catch (Exception e) {
			logger.log(1, "Failed to setup Multicaster: "+e);
		}
       
		boolean disconnected = false; // set by KILL packet from GCN.

		ServerSocket server = null;
		Socket       socket = null;

		long socketStartTime = 0L;

		while (true) {
			logger.log(1,"..");

			try { Thread.sleep(1000L); } catch (InterruptedException e){}
	    
			// If the connection died due to a KILL request we wait 10M before reestablishing.
			if (disconnected) {
				System.err.println("Waiting to re-establish connection.....");
				for (int i = 0; i < 10; i++) {
					System.err.println("..."+(10-i)+" M to go");
					try {Thread.sleep(60*1000L); } catch (InterruptedException ix) {}
				}
				disconnected = false;
			}

			// This is either the first time or we are re-establishing after having
			// closed an existing server ...
			try {
				server= new ServerSocket(port);
				logger.log(1,"Started GCN Relay Bound to port: "+port+
					   " Multicasting to: "+groupAddress+" : "+mport);
			} catch (IOException e) {
				logger.log(1,"Error starting GCN-server: "+e);
				System.exit(1);
			}
	 
	   
			try {
				logger.log(1,"Waiting connection..");
				socket = server.accept(); 

				socket.setTcpNoDelay(true);	
				socket.setSoTimeout(timeout);

				logger.log(1,"Established connection: "+
					   "\n\tRemote Host: "+socket.getInetAddress().getHostName()+
					   "\n\tRemote Port: "+socket.getPort()+
					   "\n\tLocal Port:  "+socket.getLocalPort()+
					   "\n\tLinger:      "+socket.getSoLinger()+
					   "\n\tSo_Timeout:  "+socket.getSoTimeout()+" millis"+
					   "\n\tTCP_Nagel:   "+(socket.getTcpNoDelay() ? "NO" : "COALESCE"));
				if(System.getProperty("java.vm.version").startsWith("1.2") == false)
					logger.log(1,"\tKeepAlive:   "+(socket.getKeepAlive() ? "TRUE" : "FALSE"));
				else
					logger.log(1,"\tKeepAlive:UNKNOWN (1.2 JVM)");
			} catch (IOException e) { 
				logger.log(1,"Accept::Error opening connection: "+e);
				continue;
			}
	    
			try {
				//in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
				in = new DataInputStream(socket.getInputStream());
				logger.log(3,"Opened input stream");
		
				//out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream())); 
				out = new DataOutputStream(socket.getOutputStream());
				out.flush();
				logger.log(3,"Opened output stream and flushed");
		
			} catch (IOException e) {
				logger.log(1,"Streams::Error opening connection: "+e);
				continue;
			}

			socketStartTime = System.currentTimeMillis();		

			// Keep reading packets.
			while (! disconnected) {
				buffer = new byte[BUFF_SIZE];
				try {
					int nb = in.read(buffer);
					if (nb == -1) {
						logger.log(1,"** NO DATA IN PACKET - EOC **");
						break;
					}
					logger.log(3,"\n\n-----------------------"+
						   "\nRead "+nb+" bytes from GCN");
					//System.err.write(buffer, 0, nb);
					logger.log(3,"\n-----------------------");
					// Echo back to GCN.
					out.write(buffer, 0, nb);
					logger.log(3,"Echoed back: ");
					out.flush();
					logger.log(3,"Flushed");

					// Try to multicast out.
					if (mcastOkay) {
						try {
							DatagramPacket packet = new DatagramPacket(buffer, buffer.length, groupAddress, mport);	
							mcast.send(packet);
							logger.log(1, "Multicasted packet.");
						} catch (IOException iox) {
							logger.log(2, "Error multicasting packet: "+iox);
						}
					}

					// Create an input stream from the buffer.
					bin = new ByteArrayInputStream(buffer, 0, nb);
					din = new DataInputStream(bin);

					int type = readType();
					logger.log(3,"Read packet type: "+type);
					switch (type) {
						case 3: 
							logger.log(1," [IMALIVE]");
							readImalive();
							break;
						case 4:
							disconnected =true;
							logger.log(1," [KILL]");
							break;
						case 34:  
							logger.log(1," [SAX/WFC_GRB_POS]");
							readSax();
							break;
						case 40: 
							logger.log(1," [HETE_ALERT]");			
							readHeteAlert();			   
							break;
						case 41:
							logger.log(1," [HETE_UPDATE]");
							readHeteUpdate();
							break; 
						case 43:
							logger.log(1," [HETE_GNDANA]");
							readHeteGroundAnalysis();
							break; 
						case 44:
							logger.log(1," [HETE_TEST]");
							break;
						case 51:
							logger.log(1," [INTEGRAL_POINTDIR]");
							readIntegralPointing();
							break;
						case 52:
							logger.log(1," [INTEGRAL_SPIACS]");
							break;
						case 53:
							logger.log(1," [INTEGRAL_WAKEUP]");
							readIntegralPosition();
							break;
						case 54:
							logger.log(1," [INTEGRAL_REFINED]");
							readIntegralPosition();
							break;
						case 55:
							logger.log(1," [INTEGRAL_OFFLINE]");
							break;
						case 60:
							logger.log(1," [SWIFT_BAT_GRB_ALERT]");
							break;
						case 61:
							logger.log(1," [SWIFT_BAT_GRB_POSITION]");
							readSwiftBatGRBPosition();
							break;
						case 62:
							logger.log(1," [SWIFT_BAT_GRB_NACK_POSITION]");
							break;
						case 65:
							logger.log(1," [SWIFT_FOM_OBSERVE]");
							break;
						case 66:
							logger.log(1," [SWIFT_SC_SLEW]");
							break;
						case 67:
							logger.log(1," [SWIFT_XRT_POSITION]");
							readSwiftXrtGRBPosition();
							break;
						case 81:
							logger.log(1," [SWIFT_UVOT_POSITION]");
							break;
						case 82:
							logger.log(1," [SWIFT_BAT_GRB_POS_TEST]");
							break;
						default:
							logger.log(1," [TYPE-"+type+"]");
					}
		   
				} catch (InterruptedIOException iix) {
					long time = System.currentTimeMillis() - socketStartTime;
					disconnected = true;
					logger.log(1,"Socket read timed out after: "+(time/60000L)+" minutes.");
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
		
			} // read next packet.

			try {
				if (in != null)
					in.close();
			} catch (IOException iox) {
				logger.log(1,"Error closing IN: "+iox);
			}
			in = null;

			try {
				if (out != null)
					out.close();
			} catch (IOException iox) {
				logger.log(1,"Error closing OUT: "+iox);
			}
			out = null;
	    
			try {
				if (socket != null)
					socket.close();
			} catch (IOException iox) {
				logger.log(1,"Error closing Socket: "+iox);
			}
			socket = null;
	    
			try {
				if (server != null)
					server.close();
			} catch (IOException iox) {
				logger.log(1,"Error closing ServerSocket: "+iox);
			}
			server = null;	   

		} // Connection re-start.
	
	} // (run).
    
	protected int readType() throws IOException {
		int type = din.readInt();	
		return type;
	}
    
	protected void readTerm() throws IOException {
		din.readByte();
		din.readByte();
		din.readByte();
		din.readByte();
		logger.log(3,"-----Terminator");
	}
    
	/** Write the header. */
	protected void readHdr() throws IOException {
		int seq  = din.readInt(); // SEQ_NO.
		int hop  = din.readInt(); // HOP_CNT. 
		logger.log(3,"Header: Packet Seq.No: "+seq+" Hop Count: "+hop);
	}
    
	/** Write the SOD for date.*/
	protected void readSod() throws IOException {
		int sod = din.readInt();
		logger.log(3,"SOD: "+sod);
	}
    
	/** Write stuffing bytes. */
	protected void readStuff(int from, int to) throws IOException {
		for (int i = from; i <= to; i++) {
			din.readInt();
		}
		logger.log(3,"Skipped: "+from+" to "+to);
	}
    
	public void readImalive() {
		try {
			readHdr();
			readSod();
			readStuff(4, 38);
			readTerm();
		} catch (IOException e) {
			logger.log(1,"IM_ALIVE: Error reading: "+e);
		}
	}
    
	public void readSax() {
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
			logger.log(1,"SAX_WFC_POS: Error reading: "+e);
		}
	}
    
	public void readHeteAlert() {
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

			//Socket gsock = new Socket("ltccd1", 8221);
			//PrintStream gout = new PrintStream(gsock.getOutputStream());
			//DataInputStream gin = new DataInputStream(new BufferedInputStream(gsock.getInputStream()));
	    
			//String reply = gin.readLine();
	    
			//gout.println("grb-alert");


		} catch  (IOException e) {
			logger.log(1,"HETE ALERT: Error in comms to RCS: "+e);
		}
      
	}
    
	public void readHeteUpdate() { 
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
		} catch  (IOException e) {
			logger.log(1,"HETE UPDATE: Error reading: "+e);
		}
		// 	logger.log(1,"Opening connection to TOCS");
		// 	try { 
	  
		// 	    sendCommand("HELO ARI_GCN_RELAY");
		// 	    sendCommand("STATUS");

		// 	    sendCommand("SLEW "+ "HETE-GRB:"+trigNum+"/"+mesgNum+" "+
		// 			((double)bra/10000.0)+" "+((double)bdec/10000.0));
	
		// 	    // Search phase.
		// 	    logger.log(1,"SEARCH PHASE - START");
	    
		// 	    sendCommand("RAT clear Bessell-V 4");	
		// 	    for (int i = 0; i < 5; i++) {
		// 		for (int j = 0; j < 5; j++) {
		// 		    if (!sendCommand("OFFSET "+(i*10.0)+" "+(j*20)))
		// 			await(3000);
		// 		    if (!sendCommand("EXPOSE 1000.0 1 T"))
		// 			await(3000);
		// 		}
		// 	    }
		// 	    logger.log(3,"SEARCH PHASE - END");

		// 	    // Op Phase.
		// 	    logger.log(3,"OP PHASE - START");
		// 	    sendCommand("RAT clear Bessell-B 2");		
	    
		// 	    for (int tt = 2000; tt < 64000; tt *=2) {
		// 		if (!sendCommand("EXPOSE "+tt+" 1 T"))
		// 		    await(3000);
		// 	    }
		// 	    logger.log(1,"OP PHASE - END");

		// 	    sendCommand("STOP");

		// 	} catch  (IOException e) {
		// 	    logger.log(1,"HETE UPDATE: Error in comms to RCS: "+e);
		// 	}
      
	}

	/**
	 * Method to parse TYPE 43 packets (HETE_GNDANA).
	 */
	public void readHeteGroundAnalysis()
	{ 
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
			readStuff(16, 23); // skipped the wxm error box
			int wxErrors = din.readInt(); // 24 WXM Errors (bit-field) - Sys & Stat.
			// wxErrors contains radius in arcsec, of statistical error (top 16 bits) 
			// and systematic (bottom 16 bits.
			logger.log(1,"WXM error box (radius,arcsec) : statistical : "+((wxErrors&0xFFFF0000)>>16)+
				   " : systematic : "+(wxErrors&0x0000FFFF)+".");
			int wxDimSig = din.readInt(); // 25 WXM Packed numbers.
			// wxDimSig contains the maximum dimension of the WXM error box [units arcsec] in top 16 bits
			int wxErrorBoxArcsec = (wxDimSig&0xFFFF0000)>>16;
			logger.log(1,"WXM error box (diameter,arcsec) : "+wxErrorBoxArcsec+".");
			readStuff(26, 33); // skipped the sxc error box
			int sxErrors = din.readInt(); // 34 SC Errors (bit-field) - Sys & Stat.
			// sxErrors contains radius in arcsec, of statistical error (top 16 bits) 
			// and systematic (bottom 16 bits).
			logger.log(1,"SXC error box (radius,arcsec) : statistical : "+((sxErrors&0xFFFF0000)>>16)+
				   " : systematic : "+(sxErrors&0x0000FFFF)+".");
			int sxDimSig = din.readInt(); // 35 SC Packed numbers.
			// sxDimSig contains the maximum dimension of the SXC error box [units arcsec] in top 16 bits
			int sxErrorBoxArcsec = (sxDimSig&0xFFFF0000)>>16;
			logger.log(1,"SXC error box (diameter,arcsec) : "+sxErrorBoxArcsec+".");
			logger.log(1,"Max error box (radius, arcmin) : "+
				   (((double)(Math.max(wxErrorBoxArcsec,sxErrorBoxArcsec)))/
				    (2.0*60.0)));// radius, in arc-min
			int posFlags = din.readInt(); // 36 - pos_flags
			logger.log(1,"Pos Flags: 0x"+Integer.toHexString(posFlags));
			int validity = din.readInt(); // 37 - validity flags.
			logger.log(1,"Validity Flag: 0x"+Integer.toHexString(validity));
			readStuff(38, 38); // 38 -spare
			readTerm(); // 39 - TERM.
		} catch  (IOException e) {
			logger.log(1,"HETE GNDANA: Error reading: "+e);
		}
	}

	private void await(long time) {
		try { Thread.sleep(time); } catch (InterruptedException e){}
	}

	private boolean sendCommand(String command) throws IOException {
		String reply = "";
		BufferedReader gin = null;
		PrintStream gout = null;
		Socket gsock = null;
	
		gsock = new Socket("ltccd1", 8610);
		gout = new PrintStream(gsock.getOutputStream());
		logger.log(1,"Sending: "+command);
		gout.println(command+"\r\n");
		
		gin = new BufferedReader(new InputStreamReader(gsock.getInputStream()));
	
		reply = gin.readLine();
		logger.log(3,reply);
		try {
			gout.close();
			gin.close();
			gsock.close();
		} catch (IOException e) {}
		if (reply.startsWith("OK")) return true;
		return false;
	}


	class Reader extends Thread {

		DataInputStream in;
	
		Reader(DataInputStream in) {
			this.in = in;
		}

		public void run() {
			while (true) {
				try {
					System.err.println(in.readLine());
				} catch (IOException e){
					return;
				}
			}
		}
	}


	public void readIntegralPointing() {
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
			logger.log(1,"INTEGRAL POINTING: Error reading: "+e);
		}
	}

	public void readIntegralPosition() {
		try {
			readHdr(); // 0, 1, 2 - pkt_type, pkt_sernum, pkt_hop_cnt
			readSod(); // 3
			int tsn = din.readInt();   // 4 - trig_seq_num
			int trigNum = (tsn & 0x0000FFFF);
			int mesgNum = (tsn & 0xFFFF0000) >> 16;  
			logger.log(1,"Trigger No: "+trigNum+" Mesg Seq. No: "+mesgNum);
			int burstTjd = din.readInt(); // 5 Burst TJD.
			int burstSod = din.readInt(); // 6 Burst SOD.
			logger.log(1,"Occurred at: "+burstTjd+" TJD Time: "+burstSod+" Sod.");	   
			int bra  = din.readInt();  // 7  burstRA x10e4 deg
			int bdec = din.readInt();  // 8  burstDec x10e4 deg
			logger.log(1,"Location: RA: "+ (bra/150000.0)+"H, Dec: "+(bdec/10000.0));
			int dflag = din.readInt(); // 9  detector flags
			int inten = din.readInt(); // 10 intensity sigma x100
			int berr  = din.readInt(); // 11 error arcsec
			logger.log(1,"Intensity Sigma: "+(inten/100.0)+" Error: "+berr+" Arcsec");
			int tflags =  din.readInt(); // 12 Test Flags.
			logger.log(1,"Test Flags: [0x"+Integer.toHexString(tflags).toUpperCase()+"]");
			din.readInt(); // 13 time-scale.
			int scRA    = din.readInt(); // 14 Next RA *10000.
			int scDec   = din.readInt(); // 15 Next Dec *10000.

			double ra = ((double)scRA)/150000.0;
			double dec= ((double)scDec)/10000.0;
			logger.log(1,"SC Position: RA:"+ra+" H, Dec: "+dec+" degs."); 
			readStuff(16,18);
			int scStat  = din.readInt(); // 19 Status and attitude flags.
			logger.log(1,"Status Flags;: ["+Integer.toHexString(scStat).toUpperCase()+"]");
			readStuff(20, 38);
			readTerm(); // 39 - TERM.	 
		} catch  (IOException e) {
			logger.log(1,"INTEGRAL WAKEUP: Error reading: "+e);
		}
	}

	/**
	 * Swift BAT position (Type 61,SWIFT_BAT_GRB_POSITION).
	 */
	public void readSwiftBatGRBPosition()
	{
		//RA ra = null;
		//Dec dec = null;
		Date burstDate = null;

		try
		{
			readHdr(); // 0, 1, 2 - pkt_type, pkt_sernum, pkt_hop_cnt
			readSod(); // 3
			int tsn = din.readInt();   // 4 - trig_seq_num
			int trigNum = (tsn & 0x00FFFFFF);
			int mesgNum = (tsn & 0xFF000000) >> 24;  
			logger.log(1,"Trigger No: "+trigNum+" Mesg Seq. No: "+mesgNum);
			//TJD=12640 is 01 Jan 2003
			int burstTjd = din.readInt(); // 5 Burst TJD.
			int burstSod = din.readInt(); // 6 Burst SOD. (centi-seconds in the day)
			logger.log(1,"Burst TJD: "+burstTjd+" : "+burstSod+" centi-seconds of day.");
			burstDate = truncatedJulianDateSecondOfDayToDate(burstTjd,burstSod);
			logger.log(1,"Burst Date: "+burstDate);
			int bra    = din.readInt(); // 7 RA(0..359.999)degrees *10000.
			int bdec   = din.readInt(); // 8 Dec(-90..90)degrees *10000.
			//ra = new RA();
			//dec = new Dec();
			//ra.fromRadians(Math.toRadians(((double)bra)/10000.0));
			//dec.fromRadians(Math.toRadians(((double)bdec)/10000.0));
			// The BAT returns J2000 coordinates.
			//logger.log(1,"Burst RA: "+ra);
			//logger.log(1,"Burst Dec: "+dec);
			logger.log(1,"Burst RA (dec hours): "+(((double)bra)/10000.0));
			logger.log(1,"Burst Dec (dec deg): "+(((double)bdec)/10000.0));
			logger.log(1,"Epoch: "+2000.0);
			int burstFlue = din.readInt(); // 9 Burst flue (counts) number of events.
			int burstIPeak = din.readInt(); // 10 Burst ipeak (counts*ff) counts.
			int burstError = din.readInt(); // 11 Burst error degrees (0..180) * 10000)
			// burst error is radius of circle in degrees*10000 containing TBD% of bursts!
			// Initially, hardwired to 4 arcmin (0.067 deg) radius.
			logger.log(1,"Error Box Radius: "+((((double)burstError)*60.0)/10000.0));
			readStuff(12, 17);// Phi, theta, integ_time, spare x 2
			int solnStatus = din.readInt(); // 18 Type of source found (bitfield)
			logger.log(1,"Soln Status : 0x"+Integer.toHexString(solnStatus));
			if((solnStatus & (1<<0))>0)
				logger.log(1,"Soln Status : A point source was found.");
			if((solnStatus & (1<<1))>0)
				logger.log(1,"Soln Status : It is a GRB.");
			if((solnStatus & (1<<2))>0)
				logger.log(1,"Soln Status : It is an interesting source.");
			if((solnStatus & (1<<3))>0)
				logger.log(1,"Soln Status : It is a flight catalogue source.");
			if((solnStatus & (1<<4))>0)
				logger.log(1,"Soln Status : It is an image trigger.");
			else
				logger.log(1,"Soln Status : It is a rate trigger.");
			if((solnStatus & (1<<5))>0)
				logger.log(1,"Soln Status : It is definately not a GRB (ground-processing assigned).");
			if((solnStatus & (1<<6))>0)
				logger.log(1,"Soln Status : It is probably not a GRB (high background level).");
			if((solnStatus & (1<<7))>0)
				logger.log(1,"Soln Status : It is probably not a GRB (low image significance).");
			if((solnStatus & (1<<8))>0)
				logger.log(1,"Soln Status : It is a ground catalogue source.");
			if((solnStatus & (1<<9))>0)
				logger.log(1,"Soln Status : It is probably not a GRB (negative background slope).");
			//if((solnStatus & (1<<10))>0)
			//	logger.log(1,"Soln Status : It is an X-ray burster (automated ground assignment).");
			//if((solnStatus & (1<<11))>0)
			//	logger.log(1,"Soln Status : It is an AGN source.");
			int misc = din.readInt(); // 19 Misc (bitfield)
			logger.log(1,"Misc Bits : 0x"+Integer.toHexString(misc));
			int imageSignif = din.readInt(); // 20 Image Significance (sig2noise *100)
			logger.log(1,"Image Significance (SN sigma) : "+(((double)imageSignif)/100.0));
			int rateSignif = din.readInt(); // 21 Rate Significance (sig2noise *100)
			logger.log(1,"Rate Significance (SN sigma) : "+(((double)rateSignif)/100.0));
			readStuff(22, 38);// note replace this with more parsing later
			readTerm(); // 39 - TERM.
		}
		catch  (Exception e)
		{
			logger.log(1,"SWIFT BAT GRB POSITION: Error reading: ",e);
		}
	}

	/**
	 * Swift XRT position (Type 67,SWIFT_XRT_POSITION).
	 */
	public void readSwiftXrtGRBPosition()
	{
		Date burstDate = null;

		try
		{
			readHdr(); // 0, 1, 2 - pkt_type, pkt_sernum, pkt_hop_cnt
			readSod(); // 3
			int tsn = din.readInt();   // 4 - trig_seq_num
			int trigNum = (tsn & 0x00FFFFFF);
			int mesgNum = (tsn & 0xFF000000) >> 24;  
			logger.log(1,"Trigger No: "+trigNum+" Mesg Seq. No: "+mesgNum);
			//TJD=12640 is 01 Jan 2003
			int burstTjd = din.readInt(); // 5 Burst TJD.
			int burstSod = din.readInt(); // 6 Burst SOD. (centi-seconds in the day)
			logger.log(1,"Burst TJD: "+burstTjd+" : "+burstSod+" centi-seconds of day.");
			burstDate = truncatedJulianDateSecondOfDayToDate(burstTjd,burstSod);
			logger.log(1,"Burst Date: "+burstDate);
			int bra    = din.readInt(); // 7 RA(0..359.999)degrees *10000.
			int bdec   = din.readInt(); // 8 Dec(-90..90)degrees *10000.
			logger.log(1,"Burst RA (dec hours): "+(((double)bra)/10000.0));
			logger.log(1,"Burst Dec (dec deg): "+(((double)bdec)/10000.0));
			logger.log(1,"Epoch: "+2000.0);
			int burstFlue = din.readInt(); // 9 Burst flue (counts) number of events.
			readStuff(10, 10); // 10 spare.
			int burstError = din.readInt(); // 11 Burst error degrees (0..180) * 10000)
			// burst error is radius of circle in degrees*10000 containing 90% of bursts!
			// Initially, hardwired to 9" radius.
			logger.log(1,"Error Box Radius (arcmin): "+((((double)burstError)*60.0)/10000.0));
			readStuff(12, 38);// X_TAM, Amp_Wave, misc, det_sig plus lots of spares.
			readTerm(); // 39 - TERM.
		}
		catch  (Exception e)
		{
			logger.log(1,"SWIFT XRT GRB POSITION: Error reading: ",e);
		}
	}

	/**
	 * Return a Java Date for the specified input fields.
	 * @param tjd Truncated Julian Date, TJD=12640 is 01 Jan 2003.
	 * @param sod Actually centi-seconds in the day, (seconds * 100).
	 * @return The Date.
	 * @exception ParseException Thrown if the TJD start date cannot be parsed.
	 */
	protected Date truncatedJulianDateSecondOfDayToDate(int tjd,int sod) throws ParseException
	{
		DateFormat dateFormat = null;
		Date date = null;
		int tjdFrom2003;
		long millis;

		dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
		// set tjdStartDate to 1st Jan 2003 (TJD 12640)
		date = dateFormat.parse("2003-01-01T00:00:00");
		// get number of days from 1st Jan 2003 for tjd
		tjdFrom2003 = tjd-12640;
		// get number of millis from 1st Jan 2003 for tjd
		millis = ((long)tjdFrom2003)*86400000L; // 60*60*24*1000 = 86400000;
		// get number of millis from 1st Jan 1970 (Date EPOCH) for tjd
		millis = millis+date.getTime();
		// add sod to millis to get date millis from 1st Jan 1970
		// Note sod is in centoseconds
		millis = millis+(((long)sod)*10L);
		// set date time to this number of millis
		date.setTime(millis);
		return date;
	}
}

//
// $Log: GCN_Server.java,v $
// Revision 1.12  2005/05/03 12:48:34  cjm
// Added new solution status bit 9 (Swift BAT).
//
// Revision 1.11  2005/03/07 10:49:15  cjm
// Fixed input stream copy problems.
//
// Revision 1.10  2005/03/07 10:47:45  cjm
// Added misc, image and rate significance logging to SWIFT BAT.
//
// Revision 1.9  2005/02/17 15:41:11  cjm
// Changed BAT solnStatus bits (again) to match latest socket packet definition document.
//
// Revision 1.8  2005/02/15 15:49:17  cjm
// Added readSwiftXrtGRBPosition.
//
// Revision 1.7  2005/02/11 18:43:40  cjm
// Added new solnStatus bits.
//
// Revision 1.6  2005/02/11 12:31:51  cjm
// Fixed mesg no/trig no parsing for Swift BAT.
//
// Revision 1.5  2005/02/11 12:24:59  cjm
// Added more solution status parsing for Swift BAT.
//
// Revision 1.4  2005/02/09 11:17:07  cjm
// Added type 43 / readHeteGroundAnalysis parsing.
//
// Revision 1.3  2005/01/20 15:34:19  cjm
// Added extra information parsing.
//
// Revision 1.2  2005/01/10 15:32:38  cjm
// Added more prints of socket types.
//
//
