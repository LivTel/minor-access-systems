//package ngat.rcs;

import ngat.net.*;
import ngat.message.base.*;
import ngat.message.POS_RCS.*;

/** Factory for generating handlers for POS commands received from
 * the Planetarium Control System.
 * This class can only be used via the singleton pattern, by calling
 * the static method getInstance(). A typical use might be as follows:-
 * <pre>
 *    ..
 *    ..
 *    RequestHandlerFactory factory = POS_CommandImplFactory.getInstance();
 *    RequestHandler handler = factory.createHandler(someProtocolImpl, someCommand);
 *    ..
 *    ..
 * </pre>
 * <br><br>
 * $Id: POS_DummyCommandImplFactory.java,v 1.1 2006/11/17 09:53:45 snf Exp $
 */
public class POS_DummyCommandImplFactory implements RequestHandlerFactory {

    private static POS_DummyCommandImplFactory instance = null;

    public static POS_DummyCommandImplFactory getInstance() {
	if (instance == null)
	    instance = new POS_DummyCommandImplFactory();
	return instance;
    }

    /** Selects the appropriate handler for the specified command. 
     * May return <i>null</i> if the ProtocolImpl is not defined or not an
     * instance of JMSMA_ProtocolServerImpl or the request is not
     * defined or not an instance of POS_TO_RCS. */
    public RequestHandler createHandler(ProtocolImpl serverImpl,
					Object request) {
	System.err.println("POS_CIFactory::createHandler: "+serverImpl+":"+ request);
	// Deal with undefined and illegal args.
	if ( (serverImpl == null) ||
	     ! (serverImpl instanceof JMSMA_ProtocolServerImpl) ) return null;
	if ( (request == null)    || 
	     ! (request instanceof POS_TO_RCS) ) return null;
	
	// Cast to correct subclass.
	POS_TO_RCS command = (POS_TO_RCS) request;
	
	System.err.println("POS_DummyCIFactory::createHandler: Command id: "+command.getId());
	
	return new POS_DummyCommandImpl2((JMSMA_ProtocolServerImpl)serverImpl, command);
	
    }
    
    /** Private constructor for singleton instance.*/
    private POS_DummyCommandImplFactory() {}

}    

/** $Log: POS_DummyCommandImplFactory.java,v $
/** Revision 1.1  2006/11/17 09:53:45  snf
/** Initial revision
/**
/** Revision 1.3  2001/06/08 16:27:27  snf
/** Added GRB_ALERT.
/**
/** Revision 1.2  2001/04/27 17:14:32  snf
/** backup
/**
/** Revision 1.1  2001/03/15 15:27:35  snf
/** Initial revision
/**
/** Revision 1.1  2000/12/14 11:53:56  snf
/** Initial revision
/**
/** Revision 1.1  2000/12/07 16:51:05  snf
/** Initial revision
/** */
