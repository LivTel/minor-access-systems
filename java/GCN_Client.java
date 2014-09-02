import java.io.*;
import java.net.*;
import java.util.*;
import java.text.*;

import ngat.util.*;

public class GCN_Client extends Thread {
    
    public static final int SUSP_GRB        = 0x00000001;	/* Suspected GRB */
    public static final int DEF_GRB         = 0x00000002;	/* Definitely a GRB */
    public static final int NEAR_SUN        = 0x00000004;	/* Coords is near the Sun (<15deg) */
    public static final int SOFT_SF         = 0x00000008;	/* Spectrum is soft, h_ratio > 2.0 */
    public static final int SUSP_PE         = 0x00000010;	/* Suspected Particle Event */
    public static final int DEF_PE          = 0x00000020;	/* Definitely a Particle Event */
    public static final int X_X_X_X_XXX     = 0x00000040;	/* spare */
    public static final int DEF_UNK         = 0x00000080;	/* Definitely an Unknown */
    public static final int EARTHWARD	    = 0x00000100;	/* Location towards Earth center */
    public static final int SOFT_FLAG	    = 0x00000200;	/* Small hardness ratio (>1.5) */
    public static final int NEAR_SAA	    = 0x00000400;	/* It is near/in the SAA region */
    public static final int DEF_SAA	    = 0x00000800;	/* Definitely an SAA region */
    public static final int SUSP_SF         = 0x00001000;	/* Suspected Solar Flare */
    public static final int DEF_SF          = 0x00002000;	/* Definitely a Solar Flare */
    public static final int OP_FLAG	    = 0x00004000;	/* Op-dets flag set */
    public static final int DEF_NOT_GRB     = 0x00008000;	/* Definitely not a GRB event */
    public static final int ISO_PE          = 0x00010000;	/* Datelowe Iso param is small (PE) */
    public static final int ISO_GRB         = 0x00020000;	/* D-Iso param is large (GRB/SF) */
    public static final int NEG_H_RATIO     = 0x00040000;	/* Negative h_ratio */
    public static final int NEG_ISO_BC      = 0x00080000;	/* Negative iso_part[1] or iso_part[2]*/
    public static final int NOT_SOFT        = 0x00100000;	/* Not soft flag, GRB or PE */
    public static final int HI_ISO_RATIO    = 0x00200000;	/* Hi C3/C2 D-Iso ratio */
    public static final int LOW_INTEN       = 0x00400000;	/* Inten too small to be a real GRB */

    public static final int HETE_POSS_GRB    = 0x00000010;
    public static final int HETE_DEF_GRB     = 0x00000020;
    public static final int HETE_DEF_NOT_GRB = 0x00000040;
    public static final int HETE_POSS_SGR    = 0x00000100;
    public static final int HETE_DEF_SGR     = 0x00000200;
    public static final int HETE_POSS_XRB    = 0x00000400;
    public static final int HETE_DEF_XRB     = 0x00000800;
    public static final int HETE_PROB_XRB    = 0x10000000;
    public static final int HETE_PROB_SGR    = 0x20000000;
    public static final int HETE_PROB_GRB    = 0x40000000;

    String host;

    int port;

    int seq;

    int count;

    long delay;

    Socket socket = null;

    DataOutputStream out = null;
    
    DataInputStream in = null;

    public static void main(String args[]) {

	if (args.length == 0) {
	    usage();
	    return;
	}

	CommandParser parser = new CommandParser("@");
	try {
	    parser.parse(args);
	} catch (ParseException px) {
	    System.err.println("Error parsing command arguments: "+px);
	    usage();
	    return;
	}
	double ra      = 0.0;
	double dec     = 0.0;
	double error   = 0.0;
	int    trigflags = 0x00000000;
	Date   date    = new Date();
	String dstr    = "";
	String tstr    = "";
	int    trignum = 0;
	int    mesgnum = 0;


	ConfigurationProperties map = parser.getMap();
	
	try {
	    ra    = map.getDoubleValue("ra", 0.0);
	    dec   = map.getDoubleValue("dec", 0.0);
	    error = map.getDoubleValue("error", 0.0);
	    tstr  = map.getProperty("type", "DEF_UNK");
	    trignum = map.getIntValue("trigno", -1);
	    mesgnum = map.getIntValue("mesgno", -1);
	} catch (Exception e) {}

	// Parse the type.
	
	if (tstr.indexOf("not a grb") != -1)
	    trigflags += HETE_DEF_NOT_GRB;
	if (tstr.indexOf("definite grb") != -1)
	    trigflags += HETE_DEF_GRB;
	if (tstr.indexOf("possible grb") != -1)
	    trigflags += HETE_POSS_GRB;
	if (tstr.indexOf("probable grb") != -1)
	    trigflags += HETE_PROB_GRB;
	
	if (tstr.indexOf("definite sgr") != -1)
	    trigflags += HETE_DEF_SGR;
	if (tstr.indexOf("possible sgr") != -1)
	    trigflags += HETE_POSS_SGR;
	if (tstr.indexOf("probable sgr") != -1)
	    trigflags += HETE_PROB_SGR;
	
	if (tstr.indexOf("definite xrb") != -1)
	    trigflags += HETE_DEF_XRB;
	if (tstr.indexOf("possible xrb") != -1)
	    trigflags += HETE_POSS_XRB;
	if (tstr.indexOf("probable xrb") != -1)
	    trigflags += HETE_PROB_XRB;
	
	dstr  = map.getProperty("date", "");
	
	SimpleDateFormat sdf = new SimpleDateFormat("EEE dd MMM yyyy HH:mm:ss");
	
	try {
	    date = sdf.parse(dstr);
	    System.err.println("Successfully Parsed date to: "+sdf.format(date)+" = "+date.getTime());
	} catch (ParseException px) {	   
	    date = new Date(); 
	    System.err.println("Error parsing date: "+px+" setting to now: "+sdf.format(date));
	}

	String host = map.getProperty("host", "ltccd1");
	int port = 8010;
	try {
	    port = map.getIntValue("port", 8010);
	} catch (Exception e) {}
	
	boolean run = true;
	long delay = map.getLongValue("run", -1L);
	if (delay < 0L) {
	    delay = 2000L;
	    run = false;
	} else
	    run = true;
	
	GCN_Client client = new GCN_Client(host, port, delay);
	
	if (run)
	    client.start();
	else {
	    String alert = map.getProperty("alert", "none");
	    String update = map.getProperty("update", "none");
	    if (!alert.equals("none")) {
		try {
		    client.connect();    
		    client.sendHeteAlert(trigflags, error, date);
		} catch (IOException iox) {
		    System.err.println("Error opening connection to server: "+iox);
		    iox.printStackTrace(System.err);
		    return;
		}
	    } else if
		(!update.equals("none")) {
		try {
		    client.connect();    
		    client.sendHeteUpdate(ra, dec, error, date, trigflags, trignum, mesgnum);
		} catch (IOException iox) {
		    System.err.println("Error opening connection to server: "+iox);
		    iox.printStackTrace(System.err);
		    return;
		}		
	    }
	}
    }

    public static void usage() {
	System.err.println("USAGE: java GCN_Client [options]"+
			   "\n where the following options are supported:-"+
			   "\n @run   <delay> Run the client in continuous mode."+
			   "\n @host  <host-addr> Host address for the GCN Server."+
			   "\n @port  <port> Port to connect to at the server."+
			   "\n @ra    <ra> RA of a burst source (decimal degrees)."+
			   "\n @dec   <dec> Declination of a burst source (decimal degrees)."+
			   "\n @type  <type> Burst source category - one of:-"+
			   "\n               possible/probable/not a/definite/ then grb/sgr/xrb"+
			   "\n               May be concatenated. "+
			   "\n               E.g. \"not a grb:probable xrb:possible sgr\"."+
			   "\n @error <size> Size of the error box (arc minutes)."+
			   "\n @date  <date> Date of the burst (EEE dd MMM yyyy HH:mm:ss)."+
			   "\n @alert <id>   Send a HETE alert with GRB-ID = id."+
			   "\n @update <id>  Send a HETE update with ra and dec as specified."+
			   "\n @trigno <num> Burst trigger number."+
			   "\n @mesgno <num> Message sequence number (for trigno)");
    }
    
    public GCN_Client(String host, int port, long delay) {
	super("GCN_SImulation");
	this.host = host;
	this.port = port;
	this.delay = delay; 
	count = 0;
	seq = 0;
    }

    protected void connect() throws IOException {
	count++;
	if (delay > 512000L) {
	    System.err.println("Giving up trying to connect after X attempts");
		return;
	}
	if (socket == null) {	
	    System.err.println("Re-connect in "+(delay/1000)+" secs.");
	    try {Thread.sleep(2000L);} catch (InterruptedException e){}
	    System.err.println("Connection attempt: "+count);
	    
	    socket = new Socket(host, port);
	    System.err.println("Opened connection to: "+host+" : "+port);
	    out = new DataOutputStream(socket.getOutputStream()); 
	    System.err.println("Opened output stream");
	    in = new DataInputStream(socket.getInputStream());
	    System.err.println("Opened input stream");
	    count = 0;
	    delay = 2000L;	    
	}
	
    }

    public void run() {
	
	while (true) {
	    
	    try {
		connect();
	    } catch (IOException e) {
		System.err.println("Error opening connection: "+e); 
		delay = delay*2L;		    
		continue;
	    }
	    //new Reader(in).start();
	    HeteAlert    hetealert = new HeteAlert(DEF_UNK, 0.0, new Date());
	    Imalive      imalive   = new Imalive();
	    SaxWfcGrbPos saxWfc    = new SaxWfcGrbPos(0.0, 0.0, DEF_UNK, 0.0, new Date());
	    boolean ok = true;
	    while (ok) {
		try {
		    try {sleep(2000);} catch (InterruptedException e){}
		    System.err.println("Sending sequence: "+seq);
		    // imalive packets.
		    imalive.writeImalive();
		    seq++;
		    // Send a SAX_WFC_GRB_POS every 4 messages.
		    if (seq % 4 == 3) {
			saxWfc.writeSax();
			seq++;
		    }
		    // Send a HETE_ALERT every 7 messsages.
		    if (seq % 7 == 5) {
			hetealert.writeAlert();
			seq++;
		    }
		} catch (IOException e) {
		    System.err.println("Error writing: "+e);
		    socket = null;
		    ok = false;
		    break;
		}	
		try {sleep(20000);} catch (InterruptedException e){}			
	    }
	   
	}
    }

    class Reader extends Thread {

	DataInputStream in;

	Reader(DataInputStream in) {
	    this.in = in;
	}

	public void run() {
	    int val = 0;
	    while (true) {
		try {
		    val = in.readInt();
		    System.err.println("Val: "+val);
		} catch (IOException e) {
		    System.err.println("Error reading: "+e);
		}
	    }
	}
    }

    /** Write the termination char.*/
    protected void writeTerm() throws IOException {
	out.writeByte(0);
	out.writeByte(0);
	out.writeByte(0);
	out.writeByte(10);
    }

    /** Write the header. */
    protected void writeHdr(int type, int seq) throws IOException {
	out.writeInt(type);
	out.writeInt(seq);
	out.writeInt(1); // HOP_CNT 
    }

    /** Write the SOD for date.*/
    protected void writeSod(long time) throws IOException {
	Date date = new Date(time);
	int sod = (date.getHours()*86400 + date.getMinutes()*3600 + date.getSeconds())*100;
	out.writeInt(sod);
    }

    /** Write stuffing bytes. */
    protected void writeStuff(int from, int to) throws IOException {
	for (int i = from; i <= to; i++) {
	    out.writeInt(0);
	}
    }

    class HeteAlert {
      
	double error;
	int type;
	Date date;

	HeteAlert(int type, double error, Date date) {
	    this.type = type;
	    this.error = error;
	    this.date = date;
	}

	public void writeAlert() throws IOException {
	    long now = System.currentTimeMillis();
	    Date date = new Date(now);
	
		writeHdr(40, seq); // 0, 1, 2
		writeSod(now);     // 3
		out.writeInt(1);   // 4 - trig_seq_num
		out.writeInt(11910+date.getDate()); // 5 - burst_tjd
		out.writeInt((int)(now - 150L)); // 6 - burst_sod
		writeStuff(7, 8); // 7, 8 - spare 
		//int trig_flags = GAMMA_TRIG | WXM_TRIG | PROB_GRB;
		int trig_flags = 11;
		out.writeInt(type); // 9 - trig_flags
		out.writeInt(350);   // 10 - gamma_cnts
		out.writeInt(22786); // 11 - wxm_cnts
		out.writeInt(5467);  // 12 - sxc_cnts
		out.writeInt((int)(now - 2450L)); // 13 - gamma_time
		out.writeInt((int)(now - 2435L)); // 14 - wxm_time
		out.writeInt((240 << 16) | 75); // 15 - sc_point
		writeStuff(16, 38); // 16,, 38 spare
		writeTerm(); // 39 - TERM.
	   
	}
    }
	
    /** HETE UPDATE message. (Packet Type 41). */
    class HeteUpdate {
	
	int burstRA;
	int burstDec;
	double error;
	int trigFlags;
	Date date;
	int trigNum;
	int mesgNum;

	HeteUpdate(double ra, double dec, double error, Date date, int trigFlags, int trigNum, int mesgNum) {
	    burstRA  = (int)(ra*10000.0);
	    burstDec = (int)(dec*10000.0);
	    this.error = error;
	    this.trigFlags = trigFlags;
	    this.trigNum = trigNum;
	    this.mesgNum = mesgNum;
	    this.date = date;
	}
	
	public void write() throws IOException {
	    long now = date.getTime();
	    writeHdr(41, seq);// 0, 1, 2
	    writeSod(now); // 3
	    int tsn = (mesgNum << 16) | trigNum;
	    out.writeInt(tsn); // 4
	    out.writeInt(11910+date.getDate()); // 5 - burst_tjd
	   // writeInt(burstTJD); // 5
	    writeSod(now); // 6
	    out.writeInt(burstRA); // 7
	    out.writeInt(burstDec); // 8
	    out.writeInt(trigFlags); // 9
	    int gammaCts = 1000;
	    out.writeInt(gammaCts); // 10
	    int wxmCts = 500;
	    out.writeInt(wxmCts); // 11
	    int sxcCts = 400;
	    out.writeInt(sxcCts); // 12
	    int gammaTime = 2000;
	    out.writeInt(gammaTime); // 13
	    int wxmTime = 1900;
	    out.writeInt(wxmTime); // 14
	    int scRA = 250000; // 25degs
	    int scDec = 150000; // +15degs
	    out.writeInt((scRA << 16) & scDec); // 15
	    int wxRA1  = burstRA;
	    int wxRA2  = burstRA;
	    int wxRA3  = burstRA;
	    int wxRA4  = burstRA;
	    int wxDec1 = burstDec;
	    int wxDec2 = burstDec;
	    int wxDec3 = burstDec;
	    int wxDec4 = burstDec;
	    out.writeInt(wxRA1); // 16
	    out.writeInt(wxDec1); // 17
	    out.writeInt(wxRA2); // 18
	    out.writeInt(wxDec2); // 19
	    out.writeInt(wxRA3); // 20
	    out.writeInt(wxDec3); // 21
	    out.writeInt(wxRA4); // 22
	    out.writeInt(wxDec4); // 23
	    int wxErrors = (30 << 16) & 5;
	    out.writeInt(wxErrors); // 24
	    int wxDmSig = 50;
	    out.writeInt(wxDmSig); // 25
	    int sxRA1 = burstRA;
	    int sxRA2 = burstRA;
	    int sxRA3 = burstRA;
	    int sxRA4 = burstRA;
	    int sxDec1 = burstDec;
	    int sxDec2 = burstDec;
	    int sxDec3 = burstDec;
	    int sxDec4 = burstDec;
	    out.writeInt(sxRA1); // 26
	    out.writeInt(sxDec1); // 27
	    out.writeInt(sxRA2); // 28
	    out.writeInt(sxDec2); // 29
	    out.writeInt(sxRA3); // 30
	    out.writeInt(sxDec3); // 31
	    out.writeInt(sxRA4); // 32
	    out.writeInt(sxDec4); // 33
	    int sxErrors = (3 << 16) & 1;
	    out.writeInt(sxErrors); // 34
	    int sxDmSig = 30;
	    out.writeInt(sxDmSig); // 35
	    int posFlags = 1;
	    out.writeInt(posFlags); // 36
	    int valid = 1; // BURST_VALID.
	    out.writeInt(valid); // 37
	    writeStuff(38,38); // 38
	    writeTerm(); // 39
	}
	
    }

    class Imalive {
	
	public void writeImalive() throws IOException {
	    long now = System.currentTimeMillis();
	    Date date = new Date(now);
	    
		writeHdr(3, seq);
		writeSod(now);
		writeStuff(4, 38);
		writeTerm();
	  
	}
	
    }
       
    class SaxWfcGrbPos {

	double ra;
	double dec;
	double error;
	int type;
	Date date;

	SaxWfcGrbPos(double ra, double dec, int type, double error, Date date) {
	    this.ra = ra;
	    this.dec = dec;
	    this.type = type;
	    this.error = error;
	    this.date = date;
	}
	
	public void writeSax() throws IOException {
	    long now = System.currentTimeMillis();
	   
	    writeHdr(34, seq); // 0, 1, 2
	    writeSod(now);     // 3
	    writeStuff(4,4);   // 4 - spare
	    out.writeInt(11910+date.getDate()); // 5 - burst_tjd
	    out.writeInt((int)(date.getTime() - 150L)); // 6 - burst_sod
	    out.writeInt((int)(ra*10000.0)); // 7 - burst RA [ x10000].
	    out.writeInt((int)(dec*10000.0)); // 8 - burst Dec [x10000].
	    out.writeInt((int)(Math.random()*10000.0)); // 9 - burst intens mCrab.
	    writeStuff(10, 10);   // 10 - spare
	    out.writeInt((int)(error*10000.0)); // 11 - burst error
	    out.writeInt((int)(Math.random()*10000.0)); // 12 - burst conf [% x 100].
	    writeStuff(13, 17); // 13,, 17 - spare.
	    //int trig_id = SUSP_GRB;
	    out.writeInt(type); // 18 - trigger flags.
	    out.writeInt(5); // 19 - stuff.
	    writeStuff(20, 38); // 20,, 38 - spare.
	    writeTerm(); // 39 - TERM.
	   
	}
	
    }

    public void sendHeteAlert(int type, double error, Date date) throws IOException {
	HeteAlert alert = new HeteAlert(type, error, date);
	alert.writeAlert();
    }
    
    public void sendHeteUpdate(double ra, double dec, double error, Date date, int trigflags, int trignum, int mesgnum) throws IOException {
	HeteUpdate update = new HeteUpdate(ra, dec, error, date, trigflags, trignum, mesgnum);
	update.write();
    }

}

