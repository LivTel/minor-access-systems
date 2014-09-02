//package ngat.rcs;

import java.util.*;
import java.text.*;
import java.io.*;

import ngat.net.*;
import ngat.math.*;
import ngat.util.*;
import ngat.util.logging.*;
import ngat.astrometry.*;
import ngat.message.base.*;
import ngat.message.RCS_TCS.*;
import ngat.message.POS_RCS.*;

/** Dummy version of a Generic command handler for POS_RCS commands.
 * Just returns some made up data - always succeeds.
 *
 * <dl>
 * <dt><b>RCS:</b>
 * <dd>$Id: POS_DummyCommandImpl2.java,v 1.1 2006/11/17 09:53:45 snf Exp $
 * <dt><b>Source:</b>
 * <dd>$Source: /home/dev/src/planetarium/java/RCS/POS_DummyCommandImpl2.java,v $
 * </dl>
 * @author $Author: snf $
 * @version $Revision: 1.1 $
 */
public class POS_DummyCommandImpl2 implements RequestHandler {

    public static SimpleDateFormat pdf = new SimpleDateFormat("DDDHH");

    public static SimpleDateFormat ydf = new SimpleDateFormat("yyDDD");
    
    public static final String DEFAULT_SERVERPC_URL     = "localhost";
    
    public static final String DEFAULT_ALTERNATIVE_URL  = "localhost";

    public static final int    DEFAULT_SERVERPC_PORT    = 5555;
    
    public static final int    DEFAULT_ALTERNATIVE_PORT = 6666;

    public static final long   DEFAULT_HANDLING_TIME    = 5000L;

    public static final int    DEFAULT_BANDWIDTH        = 20; // KBytes/sec.

    public static final long   DEFAULT_ACK_DELAY = 2000L;

    public static final long   DEFAULT_RESPONSE_DELAY = 5000L;

    public static final int    DEFAULT_ERROR_CODE = 0;
    
    /** Default Slew time 25 secs.*/
    public static long DEF_SLEW_TIME   = 25000L;

    /** Default inst-config + aquire time 20 secs.*/
    public static long DEF_CONFIG_TIME = 20000L;

    static final int ZERO = 0;

    static int frameCount = 0;

    static int processCount = 0;

    static Hashtable ccdStatus;

    static Hashtable metStatus;

    static Hashtable telStatus;

    ConfigurationProperties config;

    POS_DummyServer pos;

    JMSMA_ProtocolServerImpl serverImpl;

    POS_TO_RCS command;

    SSLFileTransfer.Client client;

    long delay = 1000L;

    String aurl;
    
    int aport;

    String surl;

    int sport;

    /// Some values to initialize these random vars.
    static double humidity  = 50.0;
    static double temp      = 25.0;
    static double windspeed = 23.0;
    static double winddir   = 180.0;
    static double pressure  = 800.0;

    static {
	ccdStatus = new Hashtable();
	ccdStatus.put("CCDSTATE", "EXPOSING");
	ccdStatus.put("CCDTEMP", "-24.5");
	ccdStatus.put("CCDBIN", "2");
	ccdStatus.put("CCDFILT0", "Sloan-B");
	ccdStatus.put("CCDFILT1", "clear");
	ccdStatus.put("REQUESTEDEXPOSURE", "20.0");
	ccdStatus.put("ELAPSEDEXPOSURE", "15.0");
	
	metStatus = new Hashtable();
	metStatus.put("METSTATE", "GOOD");
	metStatus.put("HUMIDITY", "70");
	metStatus.put("TEMP", "24.44");
	metStatus.put("WINDSPEED", "23");
	metStatus.put("WINDDIRECTION", "240.0");
	metStatus.put("PRESSURE", "780.0");
	metStatus.put("RAIN", "CLEAR");

	telStatus = new Hashtable();
	telStatus.put("UT1", "15:23:24.6");
	telStatus.put("LST", "03:12:23.3");
	telStatus.put("MJD", "58102.345");
	telStatus.put("TARGET_RA", "15:24:45.45");
	telStatus.put("TARGET_DEC", "12:13:34.4");
	telStatus.put("AZIMUTH_DEMAND", "124.4");
	telStatus.put("AZIMUTH_ACTUAL", "112.2");
	telStatus.put("ALTITUDE_DEMAND", "63.34");
	telStatus.put("ALTITUDE_ACTUAL", "65.3");
	telStatus.put("ROTATOR_DEMAND", "232.3");
	telStatus.put("ROTATOR_ACTUAL", "165.3");
	telStatus.put("ENCLOSURE1", "OPEN");
	telStatus.put("ENCLOSURE2", "OPEN");
	telStatus.put("MIRROR_COVER", "OPEN");
	telStatus.put("FOLD_MIRROR", "1");
	telStatus.put("TELSTATE", "OKAY");
	
    }

    public POS_DummyCommandImpl2(JMSMA_ProtocolServerImpl serverImpl, POS_TO_RCS command) {
	this.serverImpl = serverImpl;
	this.command = command;	
	pos = POS_DummyServer.getInstance();
	config = pos.config;

	surl = 
	    config.getProperty("dest.serverpc.url", DEFAULT_SERVERPC_URL);
	sport=
	    config.getIntValue("dest.serverpc.port", DEFAULT_SERVERPC_PORT);
		
	aurl = 
	    config.getProperty("dest.alternative.url", DEFAULT_ALTERNATIVE_URL);
	aport=
	    config.getIntValue("dest.alternative.port", DEFAULT_ALTERNATIVE_PORT);
	
	client = pos.client;

    }
    
    public long getHandlingTime() { return DEFAULT_HANDLING_TIME; }
    
    
    /** Generate the response based on the command.
     * This is where you can create errors by setting the success field in the
     * COMMAND_DONE response to false.
     * <code>
     *  CCDPROCESS_DONE done = new CCDPROCESS_DONE(command.getId());
     *  done.setSuccessful(false);
     *  done.setErrorNum(CCDPROCESS.JPEG_FAULT);
     *  done.setErrorString("A fault occurred processing the jpeg image: "+imageName+" - file corrupted.");
     *  
     * </code>
     */
    public void handleRequest() {
	
	POS_TO_RCS_DONE done = null;
	
	boolean success = true; // generally.
	
	boolean pausedelay = true;
	
	// date number.
	int dd = 0;
	try {
	    dd = Integer.parseInt(ydf.format(new Date()));
	} catch (Exception e){}
	
	// Check for an errorcode and various delays.
	String cmd = command.getClass().getName();
	
	long ackDelay      = config.getLongValue(cmd+".ack.delay",      DEFAULT_ACK_DELAY);
	long responseDelay = config.getLongValue(cmd+".response.delay", DEFAULT_RESPONSE_DELAY);
	int  errorCode     = config.getIntValue(cmd+".error.code",     DEFAULT_ERROR_CODE);

	
	if (command instanceof ABORT) {
	    done = new ABORT_DONE(command.getId());		
	} else if
	    (command instanceof USERID) {
	    done = new USERID_DONE(command.getId());	
	} else if
	    (command instanceof OFFLINE) {
	    done = new OFFLINE_DONE(command.getId());	
	} else if
	    (command instanceof CCDOBSERVE) {
	    // Uses the exposure time as a delay and add default slew and config.
	    // ### XXXXXXX ### Change the delay value here as required.   
	    // ### XXXXXXX ### Make sure it is a POSITIVE number.

	    double exp = ((CCDOBSERVE)command).getExposure();
	    if (exp < 0.0)
		delay = 5000L;
	    else
		delay = (long) (exp) + DEF_SLEW_TIME + DEF_CONFIG_TIME;

	    done = new CCDOBSERVE_DONE("TEST-CCDOBSERVE");
	    ((CCDOBSERVE_DONE)done).setFrameNumber((long)10000000*dd+(++frameCount));
	} else if	  
	    (command instanceof CCDPROCESS) {
	    
	    int type =  (( CCDPROCESS ) command).getType();
	    String ftype = "";
	    
	    responseDelay = 5000L; // default.
	   
	    switch (type) {
	    case CCDPROCESS.JPEG :		   
	    case CCDPROCESS.BEST_JPEG :
	    case CCDPROCESS.COLOR_JPEG :
	    case CCDPROCESS.MOSAIC_JPEG:
		ftype = ".jpg";
		break;	   
	    case CCDPROCESS.MOSAIC_FITS:
	    case CCDPROCESS.BEST_FITS :		  
	    case CCDPROCESS.FITS :
		ftype = ".fits";
		break;
	    }
	    
	    int dest =  (( CCDPROCESS ) command).getDestination();
	    
	    // NO URL ....
	    done = new CCDPROCESS_DONE("TEST-CCDPROCESS");
	    String filename = "proc-image-"+((long)10000000*dd+(++processCount))+ftype;
	    ((CCDPROCESS_DONE)done).setFilename(filename);
	    
	    // E.g. processed-image-213.jpeg

	    String imageDirName = config.getProperty("image.directory");

	    System.err.println("Looking for images in : "+imageDirName);

	    if (imageDirName == null) {
		success = false;
		done.setSuccessful(false);
		done.setErrorNum(CCDPROCESS.TRANSFER_FAULT);
		done.setErrorString("Image directory was not specified");	
	    }
	    
	    int bandwidth = config.getIntValue("transfer.bandwidth", 5);
	    
	    if ( success ){
		
		File imageDir = new File(imageDirName);
		
		File[] files = imageDir.listFiles();
		if (files.length < 1) {
		    success = false;
		    done.setSuccessful(false);
		    done.setErrorNum(CCDPROCESS.TRANSFER_FAULT);
		    done.setErrorString("Could not find any files in image directory ["+
					imageDir.getPath()+"]");	
		}
		if ( success ) {
		    
		    int rand = (int) Math.min(Math.random()*files.length, files.length-1 );
		    
		    File randomFile = files[rand];
		    
		    System.err.println("Will send: File: "+rand+" of "+
				       files.length+" ["+randomFile.getPath()+"] as: "+filename);
		    
		    if ((client == null)) {
			success = false;
			done.setSuccessful(false);
			done.setErrorNum(CCDPROCESS.TRANSFER_FAULT);
			done.setErrorString("Transfer client not available or would not initialize");    
		    }

		    System.err.println("Transfer client initialized");

		    if ( success ) {
			
			// How long to go (millis).
			ackDelay = 5*(randomFile.length())/(bandwidth);

			// Send the ACK now before we start the transfer.
			ACK ack = new ACK(command.getId());
			ack.setTimeToComplete((int)responseDelay);
			serverImpl.sendAck(ack);
			
			switch (dest) {
			case CCDPROCESS.SERVERPC:		
			    try {		   
				client.send(randomFile.getPath(), filename);	
			
				System.err.println("** Image transferred OK");
			    } catch (Exception iox) {
				success = false;
				done.setSuccessful(false);
				done.setErrorNum(CCDPROCESS.TRANSFER_FAULT);
				done.setErrorString("Failed to transfer image: "+iox);	
			    }
			    break;
			default:		
			    try { 	
				System.err.println("Forward to: "+aurl+" : "+aport);   
				client.forward(aurl, aport, randomFile.getPath(), filename);    
				   
			    } catch (Exception iox) {
				success = false;
				done.setSuccessful(false);
				done.setErrorNum(CCDPROCESS.TRANSFER_FAULT);
				done.setErrorString("Failed to transfer image: "+iox);	
			    }
			    break;
			}			
		    }
		}
	    } // Place to break from ifings.
	    
	} else if
	    (command instanceof CCDSTATUS) {	  
	    done = new CCDSTATUS_DONE("TEST-CCDSTATUS");	   
	    ((CCDSTATUS_DONE)done).setStatus(ccdStatus);
	} else if
	    (command instanceof METSTATUS) {	
	    done = new METSTATUS_DONE("TEST-METSTATUS"); 
	   
	    humidity = random(humidity, 0.0, 100.0, 0.2);
  	    metStatus.put("HUMIDITY",      ""+humidity);
	    temp = random(temp, -20.0, 30.0, 0.1);
  	    metStatus.put("TEMP",          ""+temp);
	    windspeed = random(windspeed, 0.0, 60.0, 1.5);
  	    metStatus.put("WINDSPEED",     ""+windspeed);
	    winddir = random(winddir, 0.0, 360.0, 2.0);
  	    metStatus.put("WINDDIRECTION", ""+winddir);
	    pressure = random(pressure, 750.0, 850.0, 2.0);
  	    metStatus.put("PRESSURE",      ""+pressure);
 
	    ((METSTATUS_DONE)done).setStatus(metStatus);
	} else if
	    (command instanceof TELSTATUS) {	    
	    done = new TELSTATUS_DONE("TEST-TELSTATUS");
	   

	    // Inc the SCHEDFILEID every hour on the hour.

	    String sid = pdf.format(new Date());
	    telStatus.put("SCHEDULE_FILE_ID", sid);
	    
	    ((TELSTATUS_DONE)done).setStatus(telStatus);
	} else if
	    (command instanceof GETQUEUE) {	    
	    // Get the list of processes. #######################################
	    done = new GETQUEUE_DONE("TEST-GETQUEUE");
	    //Vector vec =  POS_Queue.getInstance().listElements();
	    //((GETQUEUE_DONE)done).setProcessList(vec);	
	    Vector vec = new Vector();
	    vec.add(new IntegerPair(66601, GETQUEUE.EXECUTING));
	    vec.add(new IntegerPair(66602, GETQUEUE.PENDING));
	    vec.add(new IntegerPair(66603, GETQUEUE.PENDING));
	    vec.add(new IntegerPair(66604, GETQUEUE.PENDING));
	    ((GETQUEUE_DONE)done).setProcessList(vec);
	    // ###########  == IntPrs with requestcode and status ??

	} else if
	    (command instanceof TESTLINK) {	  
	    // Set the ReturnCode depending on current mode of operation.
	    // TESTLINK.PLANETARIUM_MODE or TESTLINK.NOT_PLANETARIUM_MODE
	    done = new TESTLINK_DONE("TEST-TESTLINK");

	    long now = System.currentTimeMillis();
	    if (now > pos.planetariumStart && now < pos.planetariumEnd)
		((TESTLINK_DONE)done).setReturnCode(TESTLINK.PLANETARIUM_MODE);
	    else 
		((TESTLINK_DONE)done).setReturnCode(TESTLINK.NOT_PLANETARIUM_MODE);
	} 
	
	// Never fails !
	if (errorCode == 0)
	    done.setSuccessful(true);
	else {
	    done.setSuccessful(false);
	    done.setErrorNum(errorCode);
	}
	
	// Send an ACK to the client so it doesn't time-out before we send DONE.
	
	ACK ack = new ACK(command.getId());
	ack.setTimeToComplete((int)ackDelay);
	serverImpl.sendAck(ack);
	
	// Snooze awhile.
	System.err.println("Response delayed: "+responseDelay);
	try {Thread.sleep(responseDelay); } catch (InterruptedException e) {}
	
	// Ok Done,
	serverImpl.sendDone(done);
	
    }
    
    /** Loose stuff.*/
    public void dispose() {
	System.err.println("DummyCI-disposing()");
	serverImpl = null; 
	config     = null;
	pos        = null;
	command    = null;
    }
  
    /** Handle general exception from ProtocolHandler.*/
    public void exceptionOccurred(Object source, Exception ex) {
	System.err.println("DummyCI-ExceptionOccurred: From: "+source+" Ex: "+ex);
    }

    /** Generate random variation in data.*/
    public double random(double current, double from, double to, double var) {
	double value = (Math.random()-0.5)*var + current;
	if (value < from) value = from;
	if (value > to) value = to;
	return value;
    }
    
}

/** $Log: POS_DummyCommandImpl2.java,v $
/** Revision 1.1  2006/11/17 09:53:45  snf
/** Initial revision
/**
/** Revision 1.1  2001/06/08 16:27:27  snf
/** Initial revision
/** */
