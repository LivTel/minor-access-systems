import java.io.*;
import java.util.*;

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


public class CLTool {

    String host;

    int port;

    protected volatile COMMAND command;
  
    protected volatile COMMAND_DONE reply;
    
    public CLTool(String host, int port) {
	this.host = host;
	this.port = port;
    }

    public void setHost(String host) {
	this.host = host;
    }

    public void setPort(int port) {
	this.port = port;
    }

    /** Blocks waiting for command to complete or fail.*/
    public COMMAND_DONE send(boolean secure) {

	Handler handler = new Handler(command);
	JMSMA_ProtocolClientImpl protocol = null;

	if (secure) {

	    SocketFactory sf = SSLSocketFactory.getDefault();
	    
	    protocol = 
		new JMSMA_ProtocolClientImpl((JMSMA_Client)handler, 
					     new SocketConnection(host, port, sf));
	} else {
	    protocol = 
		new JMSMA_ProtocolClientImpl((JMSMA_Client)handler, 
					     new SocketConnection(host, port));
	    
	    
	}
	//System.err.println("Sending onwards to OSS...");
	
	protocol.implement();

	return reply;

    }


    /** Temporarily handles comms with OSS.*/
    class Handler extends JMSMA_ClientImpl {
	
	Handler(COMMAND command){
	    super();
	    timeout = 600000L;
	    this.command = command;
	    //System.err.println("Handler ready for: "+command);
	}
	
	public void failedConnect(Exception e) {	   
	    //sendError(PROXY_CONNECT_ERROR, "Unable to connect to OSS: "+e);
	    System.err.println("Failed connect: "+e);
	}
	
	public void failedDespatch(Exception e) {
	    //sendError(PROXY_IO_ERROR, "Could not send command to update OSS: "+e);
	    System.err.println("Failed despatch: "+e);
	}
	
	public void failedResponse(Exception e) {
	    //sendError(PROXY_RESPONSE_ERROR, "No response was received from OSS: "+e);
	    System.err.println("Failed response: "+e);
	}
	
	public void exceptionOccurred(Object source, Exception e) {
	    //sendError(PROTOCOL_ERROR, "Protocol exception: Source: "+source+" Exc: "+e);
	    //System.err.println("Exception: Source: "+source+" Exception: "+e);
	}

	public void handleAck(ACK ack) {
	    //sendAck(ack.getTimeToComplete(), "From OSS");
	    //System.err.println("Handle Ack: "+ack.getTimeToComplete()+" millis.");
	}
	
	public void handleDone(COMMAND_DONE done) {
	    //System.err.println("Handle Response: "+done);	

	    reply = done;

	}
	
	public void sendCommand(COMMAND command) {}
	
    }


}
