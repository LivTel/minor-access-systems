import java.io.*;
import java.util.*;
import java.text.*;

import javax.net.*;
import javax.net.ssl.*;
import javax.security.cert.*;

import ngat.net.*;
import ngat.util.*;
import ngat.util.logging.*;
import ngat.math.*;
import ngat.astrometry.*;
import ngat.phase2.*;

import ngat.message.base.*;
import ngat.message.OSS.*;

public class AddObsGroup extends CLTool {
  
    public static final int DEFAULT_PORT = 6500;
    public static final int DEFAULT_PRIORITY = 5;

    String proposalPathName;

    Group group;

    public AddObsGroup(String host,
		       int    port,
		       String proposalPathName, 
		       Group  group,
		       Map    smap,
		       Map    imap,
		       Map    tmap) {

	super(host,port);

	// Group params.
	this.proposalPathName = proposalPathName ;
	this.group            = group;

	ADD_GROUP add = new ADD_GROUP("CLT_AddObsGroup");
	add.setProposalPath(new Path(proposalPathName));
	add.setGroup(group);	   

	add.setSrcMap(smap);
	add.setIcMap(imap);
	add.setTcMap(tmap);

	add.setClientDescriptor(new ClientDescriptor("CLT", 
						     ClientDescriptor.TOCS_CLIENT, 
						     ClientDescriptor.TOCS_PRIORITY));
	add.setCrypto(new Crypto("/to_agent"));

	command = add;

    }


    public static void main(String args[]) {
	COMMAND_DONE done = null;

	try {
	CommandTokenizer ct = new CommandTokenizer("--");
	ct.parse(args);

	ConfigurationProperties config = CommandTokenizer.use("--").parse(args);

	// AddObsGroup PropId GrpId Priority Expiry(+h) Interval(h) TargId IcSpec Expose(s) Mult
	//               0      1      2          3         4         5     6      7         8       

	String host = config.getProperty("host", "localhost");
	int    port = config.getIntValue("port", DEFAULT_PORT);
	
	boolean secure = (config.getProperty("secure") != null);

	long now = System.currentTimeMillis();

	String propId = config.getProperty("proposal");

	String gid    = config.getProperty("gid");

	int priority = config.getIntValue("priority", DEFAULT_PRIORITY);

	long endDate = 0L;	
	int expiryH = config.getIntValue("expires-in", 24);
	endDate = now + 3600*1000*expiryH;

	long period = 0L;

	double pH = config.getDoubleValue("period-h", 1.0);
	period = (long)(3600.0*1000.0*pH);

	MonitorGroup group = new MonitorGroup(gid);

	group.setExpiryDate(endDate);
	group.setStartDate(now);
	group.setEndDate(endDate);
	group.setPeriod(period);
	group.setFloatFraction(0.95f);
	group.setPriority(priority);

	group.setMinimumLunar(Group.BRIGHT);
	group.setMinimumSeeing(Group.POOR);
	group.setTwilightUsageMode(Group.TWILIGHT_USAGE_NEVER);
		
	String targetId = config.getProperty("target");
	if (targetId == null) {
	    System.err.println("No TARGET");
	    System.exit(1);
	}

	String icId = config.getProperty("config");
	if (icId == null) {
	    System.err.println("No CONFIG");
	    System.exit(1);
	}
	
	float expose = (float)(config.getDoubleValue("expose", 1.0));     
	expose = 1000.0F*expose;

	int mult = config.getIntValue("runs", 1);

	Observation obs = new Observation("Obs");

	obs.setExposeTime(expose);
	obs.setNumRuns(mult);

	Mosaic mosaic = new Mosaic();
	mosaic.setPattern(Mosaic.SINGLE);
	obs.setMosaic(mosaic);
	
	group.addObservation(obs);

	Map smap = new HashMap();
	smap.put(obs, targetId);
	
	Map imap = new HashMap();
	imap.put(obs, icId);
	
	Map tmap = new HashMap();
	tmap.put(obs, "DEFAULT");
	
	AddObsGroup add = new AddObsGroup(host, port, propId, group, smap, imap, tmap);
	
	done = add.send(secure);
	
	System.err.println("AddObsGroup; Got; "+done);
	
	
	} catch (Exception e) {
	    e.printStackTrace();
	}
		

	if (done.getSuccessful())
	    System.exit(0);
	else
	    System.exit(1);
    }
    
    public static void usage() {
	System.err.println("Usage: AddObsGroup --host <host --port <port> "+
			   "\n                 --proposal <PropId> --gid <GroupId> "+
			   "\n                 --priority <Priority> --expires-in <Expiry(+h)> "+
			   "\n                 --period <Interval(h)> --target <TargId> --config <IcSpec> "+
			   "\n                 --expose <Expose(s)> --runs <Multruns>");
    } 
    
}
