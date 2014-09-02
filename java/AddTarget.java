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

public class AddTarget extends CLTool {
  
    String proposalPathName;

    Source src;

    public AddTarget(String host,
		     int    port,
		     String proposalPathName,
		     Source src) {
	super(host,port);
	this.proposalPathName = proposalPathName ;
	this.src = src;

	ADD_SOURCE add = new ADD_SOURCE("CLT_AddTarget");
	add.setProposalPath(new Path(proposalPathName));
	add.setSource(src);

	ClientDescriptor cd = new ClientDescriptor("CLT", 
						     ClientDescriptor.TOCS_CLIENT, 
						   ClientDescriptor.TOCS_PRIORITY);
	    
	add.setClientDescriptor(cd);
	add.setCrypto(new Crypto("/to_agent"));

	System.err.println("Using: "+cd);
	
	command = add;

    }

    public static void main(String args[]) {

	COMMAND_DONE done = null;

	try {
	String host = System.getProperty("HOST");
	int    port = 0;
	try {
	    port = Integer.parseInt(System.getProperty("PORT"));
	} catch (NumberFormatException nx) {
	    usage();
	    System.exit(1);
	}

	boolean secure = (System.getProperty("SECURE") != null);
	
	if (args == null ||
	    args.length < 4) {
	    usage();
	    System.exit(1);
	}

	String propId   = args[0];
	String targetId = args[1];
	
	double ra = 0.0;
	try {
	    ra = Position.parseHMS(args[2], ":");
	} catch (ParseException px) {
	    System.err.println("Bad format (RA): Expected (hh:mm:ss.ss), Got: "+args[2]);
	     System.exit(1);
	}

	double dec = 0.0;
	try {
	    dec = Position.parseDMS(args[3], ":");
	} catch (ParseException px) {
	    System.err.println("Bad format (Dec): Expected (+/-dd:mm:ss.ss), Got: "+args[3]);
	     System.exit(1);
	}
	
	ExtraSolarSource src = new ExtraSolarSource(targetId);
	src.setRA(ra);
	src.setDec(dec);
        src.setEquinoxLetter('J');
	src.setEquinox(2000.0f);
	src.setEpoch(2000.0f);
	src.setFrame(Source.FK5);

	AddTarget add = new AddTarget(host, port, propId, src);
	
	done = add.send(secure);

	System.err.println("AddTarget; Got; "+done);
		
	
	} catch (Exception e) {
	    e.printStackTrace();
	}

	if (done.getSuccessful())
	    System.exit(0);
	else
	    System.exit(1);

    }

    public static void usage() {
	System.err.println("Usage: AddTarget <ProposalPath> <Target-ID> <RA (hh:mm:ss.ss)> <Dec (dd:mm:ss.ss)>");
    }

}

