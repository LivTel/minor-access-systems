/*
 * (C) Copyright IBM Corp. 1999  All rights reserved.
 *
 * US Government Users Restricted Rights Use, duplication or
 * disclosure restricted by GSA ADP Schedule Contract with IBM Corp.
 *
 * The program is provided "as is" without any warranty express or
 * implied, including the warranty of non-infringement and the implied
 * warranties of merchantibility and fitness for a particular purpose.
 * IBM will not be liable for any damages suffered by you as a result
 * of using the Program. In no event will IBM be liable for any
 * special, indirect or consequential damages or lost profits even if
 * IBM has been advised of the possibility of their occurrence. IBM
 * will not be liable for any third party claims against you.
 */


import java.io.*;

import org.xml.sax.*;
import org.xml.sax.helpers.*;
//import com.ibm.xml.parsers.*;
import javax.xml.parsers.*;

import ngat.phase2.*;
import ngat.phase2.nonpersist.*;
import ngat.astrometry.*;
import dev.lt.*;

import java.text.*;
import java.util.*;

/**
 * A sample SAX writer. This sample program illustrates how to
 * register a SAX DocumentHandler and receive the callbacks in
 * order to print a document that is parsed.
 *
 * @version Revision: 06 1.5 samples/sax/SAXWriter.java, samples, xml4j2, xml4j2_0_15 
 */
public class ProjectParser extends HandlerBase 
{
    
    private boolean doing = false;
    
    static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm");
    static SimpleTimeZone   utc = new SimpleTimeZone(0, "UTC");
   
    String currentElement = "";
  
    String cParam  = null;  
    String action = null;

    StringBuffer changes = new StringBuffer();

    SAXParser parser = null;

    public ProjectParser() {	 
	sdf.setTimeZone(utc);
    }
    
    public void parseURI(String uri)
    {

	 
	//System.err.println("Parser is a: "+parser.getClass().getName());

	try
	    {    

		SAXParserFactory factory = SAXParserFactory.newInstance();
		
		parser = factory.newSAXParser();

		parser.parse(uri, this);
	    }
	catch (Exception e)
	    {
		System.err.println(e);	
	    }
    }
    
    /** Processing instruction. */
    public void processingInstruction(String target, String data) {
	System.out.print("Processing - "+target);
	if (data != null && data.length() > 0){
	    System.out.print(" data: ");
	    System.out.print(data);
	} 
    } // processingInstruction(String,String)
    
    /** Start document. */
    public void startDocument() 
    {
	System.out.println("Starting to parse......\n\n");
    } // startDocument()
    
    /** Start element. */
    public void startElement(String name, AttributeList attrs) {
	
	System.err.println("Entering element: "+name);
	if (attrs != null){
	    int len = attrs.getLength();
	    for (int i = 0; i < len; i++){
		System.err.println("  Found Element: "+name+" attrib: "+attrs.getName(i)+" = "+attrs.getValue(i));
	    }
	}  
	currentElement = name;
	
	try {
	    
	    if 
		(name.equals("change")) {
		String cdate = (getAttr(attrs, "date"));
		
		Date cd = sdf.parse(cdate);
		
		System.err.println("Found a change on: "+cd);
		changes.append("\nThere was a change on "+cdate);
	    } else if
		(name.equals("detail")) {
				
	    }

	} catch (Exception e) {
	    System.err.println("Exception reading: "+name+" : "+e);
	}
	
    } // startElement(String,AttributeList)
    
    
    protected String getAttr(AttributeList attrs, String which) {
	if (attrs != null){
	    int len = attrs.getLength();
	    for (int i = 0; i < len; i++){
		if (attrs.getName(i).equals(which)) {
		    System.err.println("OK Attrib: "+which+" was: "+attrs.getValue(i));
		    return attrs.getValue(i);		
		}
	    }
	}  	
	return "UNKNOWN";
    }
    
    /** Characters. Process a String of characters - 
     * i.e. a name or object field value.
     */
    public void characters(char ch[], int start, int length) {
	String stuff = new String(ch, start, length);
	int count = 0;
	for (int i = 0; i < stuff.length(); i++) {
	    if (Character.isWhitespace(stuff.charAt(i)))
		count++;
	}
	if (count != stuff.length()) {	
	    System.err.println("Read some characters: {"+stuff+"}");
	    
	    stuff = stuff.trim();
	    
	    try {
		
		if (currentElement.equals("detail")) {
		    action = stuff;
		    // Work out what params based on action....
		    changes.append("Details: "+stuff);
		} 
	    } catch (Exception e) {
		System.err.println("Caught an exception while filling out: "+e);
	    }
	    
	}
	
    } // characters(char[],int,int);
    
      /** Ignorable whitespace. */
    public void ignorableWhitespace(char ch[], int start, int length) 
    {
	// characters(ch, start, length);
	//System.err.println(".......Some crap ...... ignore");
    } // ignorableWhitespace(char[],int,int);
    
      /** End element. */
    public void endElement(String name) 
	  {
	      System.err.println("End of element: "+name);
	   
	      
	  } // endElement(String)
    
    /** End document. */
    public void endDocument() 
    {
	System.err.println("End of Doc");
	
	      
    } // endDocument()
    
      //
      // ErrorHandler methods
      //
    
    /** Warning. */
    public void warning(SAXParseException ex) 
    {
	System.err.println("[Warning] "+
                       getLocationString(ex)+": "+
			   ex.getMessage());
    }
    
    /** Error. */
    public void error(SAXParseException ex) 
  {
      System.err.println("[Error] "+
                       getLocationString(ex)+": "+
			 ex.getMessage());
  }
    
    /** Fatal error. */
    public void fatalError(SAXParseException ex) 
	throws SAXException 
    {
	System.err.println("[Fatal Error] "+
			   getLocationString(ex)+": "+
			   ex.getMessage());
    throw ex;
    }
    
    /** Returns a string of the location. */
    private String getLocationString(SAXParseException ex) 
    {
	StringBuffer str = new StringBuffer();
	
	String systemId = ex.getSystemId();
	if (systemId != null)
	    {
		int index = systemId.lastIndexOf('/');
		if (index != -1)
		    systemId = systemId.substring(index + 1);
		str.append(systemId);
	    }
	str.append(':');
	str.append(ex.getLineNumber());
	str.append(':');
	str.append(ex.getColumnNumber());
	
	return str.toString();
    } // getLocationString(SAXParseException):String

    public String getChanges() { return changes.toString(); }

    /** Main program entry point. */
    public static void main(String argv[]) 
    {
	if (argv.length == 0)
	    {
		System.out.println("Usage:  java ProjectParser <uri>");
		System.out.println("   where uri is the URI of your XML document.");
		System.out.println("   E.g.:  java XMLParser testgroup.xml");
		System.exit(1);
    }

	ProjectParser s1 = new ProjectParser();
	s1.parseURI(argv[0]);

	System.err.println("Changes were:-------");
	System.err.println(s1.getChanges());


    } // main(String[])
} 

