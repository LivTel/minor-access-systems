import java.net.*;
import java.io.*;

/** Used for controlling lights on an AXIS 2100 web camera.*/
public class Axis2100LightController {

    /** Camera control URL.*/
    private String baseUrl;
    
    static String DEFAULT_URL = "http://192.168.4.1/axis-cgi/io/output.cgi";
    
    static String ON = "action=1:\\";
    
    static String OFF= "action=1:/";

    static final int SWITCH_ON  = 1;
    
    static final int SWITCH_OFF = 2;

    /** Passphrase = 'Username:password' .*/
    protected String passphrase;

    /** Base64 Encoded passphrase.*/
    protected String encpassphrase;

    /** URL Connection.*/
    protected URLConnection uc;
    
    /** The baseUrl as URL.*/
    protected URL url;
    
    /** Output stream to http connection.*/
    protected DataOutputStream dos;

    /** Input stream from http connection.*/
    protected DataInputStream dis;

    /** Create an Axis2100LightController with supplied command URl and passphrase.
     * @param baseUrl    Camera control URL.
     * @param passphrase Passphrase = 'Username:password'.
     */
    public Axis2100LightController(String baseUrl, String passphrase) {      
	this.baseUrl = baseUrl;
	this.passphrase = passphrase;
    }

    /** Set the passphrase.*/
    public void setPassphrase(String passphrase) { this.passphrase = passphrase; }
    
    /** Set the base URL.*/
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    
    /** Switch the lights on or off.*/
    public void switchState(int state) throws IOException {
	
	String action = "";
	String stateString = "";

	switch (state) {
	case SWITCH_ON:
	    action = ON;
	    stateString = "SWITCH_ON";
	    break;
	case SWITCH_OFF:
	    action = OFF;
	    stateString = "SWITCH_OFF";
	    break;
	default:
	    System.err.println("Illegal switching state: "+state);
	    return;
	}

	encpassphrase = Base64Converter.encode(passphrase.getBytes());

	url = new URL(baseUrl);
	System.err.println("Url ready for "+stateString+" using "+url);

	uc = url.openConnection();
	System.err.println("URL Connection open");
	
	uc.setRequestProperty("Authorization", "Basic "+encpassphrase);
	uc.setDoOutput(true);
	uc.setDoInput(true);
	uc.setAllowUserInteraction(false);
	dos = new DataOutputStream(uc.getOutputStream());
	System.err.println("Output stream open");
	
	dos.writeBytes(action);
	dos.close();
	System.err.println("Output stream closed");
	
	dis = new DataInputStream(uc.getInputStream()); 
	System.err.println("Input stream open");

	String line = null;
	while ((line = dis.readLine()) != null) {
	    System.err.println("Got line: "+line);
	}     
	dis.close();
	System.err.println("Input stream closed");	    
    }

    /** Switch em on.*/
    public void switchOn() throws IOException { switchState(SWITCH_ON); }
    
    /** Switch em off.*/
    public void switchOff() throws IOException { switchState(SWITCH_OFF); }

    /** Start an Axis2100Controller and send ON or OFF dependant on args.*/
    public static void main(String args[]) {

	// Axis2100Controller <url> <user:pass> <ON | OFF>

	if (args.length < 3) {
	    usage();
	    return;
	}

	String baseUrl = args[0];

	String pass = args[1];

	if (args[2].equalsIgnoreCase("ON")) {
	    try {
		(new Axis2100LightController(baseUrl, pass)).switchOn();
	    } catch (IOException iox) {	
		System.err.println("Error: "+iox);
		usage();
		return;
	    }
	} else if
	    (args[2].equalsIgnoreCase("OFF")) {
	    try {
		(new Axis2100LightController(baseUrl, pass)).switchOn();
	    } catch (IOException iox) {	
		System.err.println("Error: "+iox);
		usage();
		return;
	    }
	} else {
	    usage();
	    return;	    
	}
    }

    /** Print usage message.*/
    public static void usage() { 
	System.err.println("Usage: java Axis2100Controller <url> <user:pass> <ON | OFF>");
    }
    
} 

class  Base64Converter {
    
    public static final char [ ]  alphabet = {
	'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H',   //  0 to  7
	'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P',   //  8 to 15
	'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X',   // 16 to 23
	'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f',   // 24 to 31
	'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n',   // 32 to 39
	'o', 'p', 'q', 'r', 's', 't', 'u', 'v',   // 40 to 47
	'w', 'x', 'y', 'z', '0', '1', '2', '3',   // 48 to 55
	'4', '5', '6', '7', '8', '9', '+', '/' }; // 56 to 63
    
    //////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////
    
    public static String  encode ( String  s )
	//////////////////////////////////////////////////////////////////////
    {
	return encode ( s.getBytes ( ) );
    }
    
    public static String  encode ( byte [ ]  octetString )
	//////////////////////////////////////////////////////////////////////
    {
	int  bits24;
	int  bits6;
	
	char [ ]  out
	    = new char [ ( ( octetString.length - 1 ) / 3 + 1 ) * 4 ];
	
	int outIndex = 0;
	int i        = 0;
	
	while ( ( i + 3 ) <= octetString.length )
	    {
		// store the octets
		bits24  = ( octetString [ i++ ] & 0xFF ) << 16; 
		bits24 |= ( octetString [ i++ ] & 0xFF ) <<  8; 
		bits24 |= ( octetString [ i++ ] & 0xFF ) <<  0;
		
		bits6 = ( bits24 & 0x00FC0000 ) >> 18; 
		out [ outIndex++ ] = alphabet [ bits6 ];
		bits6 = ( bits24 & 0x0003F000 ) >> 12; 
		out [ outIndex++ ] = alphabet [ bits6 ];
		bits6 = ( bits24 & 0x00000FC0 ) >> 6; 
		out [ outIndex++ ] = alphabet [ bits6 ];
		bits6 = ( bits24 & 0x0000003F );
		out [ outIndex++ ] = alphabet [ bits6 ]; 
	    }
	
	if ( octetString.length - i == 2 )
	    {
		// store the octets 
		bits24  = ( octetString [ i     ] & 0xFF ) << 16; 
		bits24 |= ( octetString [ i + 1 ] & 0xFF ) <<  8;
		
		bits6 = ( bits24 & 0x00FC0000 ) >> 18;
		out [ outIndex++ ] = alphabet [ bits6 ]; 
		bits6 = ( bits24 & 0x0003F000 ) >> 12; 
		out [ outIndex++ ] = alphabet [ bits6 ]; 
		bits6 = ( bits24 & 0x00000FC0 ) >> 6; 
		out [ outIndex++ ] = alphabet [ bits6 ];
		
		// padding
		out [ outIndex++ ] = '='; 
	    }
	else if ( octetString.length - i == 1 )
	    {
		// store the octets 
		bits24 = ( octetString [ i ] & 0xFF ) << 16;
		
		bits6 = ( bits24 & 0x00FC0000 ) >> 18;
		out [ outIndex++ ] = alphabet [ bits6 ];
		bits6 = ( bits24 & 0x0003F000 ) >> 12; 
		out [ outIndex++ ] = alphabet [ bits6 ];
		
		// padding
		out [ outIndex++ ] = '='; 
		out [ outIndex++ ] = '='; 
	    }
	
	return new String ( out );
    }
    
}



