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

import org.xml.sax.AttributeList;
import org.xml.sax.HandlerBase;
import org.xml.sax.Parser;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.ParserFactory;

import com.ibm.xml.parsers.SAXParser;

import java.text.*;
import java.util.*;

/**
 * A sample SAX writer. This sample program illustrates how to
 * register a SAX DocumentHandler and receive the callbacks in
 * order to print a document that is parsed.
 *
 * @version Revision: 06 1.5 samples/sax/SAXWriter.java, samples, xml4j2, xml4j2_0_15 
 */
public class ConfigParser extends HandlerBase 
{

    private int doing = 0;

    private static final int doCfgName = 1; 
    private static final int doCfgDesc = 2;
    
    private String cfgName = "";
   
    private String cfgDesc = "";
    private ClassType cctype = null;
  
    private HashMap classes = new HashMap();
    private Vector items = new Vector();

    private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm");
    
    String currentElement = "";
    
    public void parseURI(String uri)
    {
	SAXParser parser = new SAXParser();
	parser.setDocumentHandler(this);
	parser.setErrorHandler(this);
	try
	    { 
		parser.parse(uri);
	    }
	catch (Exception e)
	    {
		System.err.println(e);
		e.printStackTrace();
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
	
	System.err.println("Entering element: ["+name+"]");
	if (attrs != null){
	    int len = attrs.getLength();
	    for (int i = 0; i < len; i++){
		System.err.println("  Found Element: "+name+" attrib: "+attrs.getName(i)+" = "+attrs.getValue(i));
	    }
	}  
	currentElement = name.trim();
	if (currentElement.equals("cname")) {
	    doing = doCfgName;
	}
	else if (currentElement.equals("description")) {
	    doing = doCfgDesc;
	}
	else if (currentElement.equals("class")) {
	    String className = getAttr(attrs, "name");
	    System.err.println("Creating a class description.."+className);	
	    cctype = new ClassType(className);
	    classes.put(className, cctype);	
	    doing = 0;
	} 
	else if (currentElement.equals("var")) {
	    String varName = getAttr(attrs, "id");
	    String vtp = getAttr(attrs, "type");
	    String rep = getAttr(attrs, "repeat");
	    if (vtp.equals("class")) {
		String linkClass = getAttr(attrs, "class");
		ClassVar cvar = new ClassVar(varName, linkClass);
		System.err.println("Linking to class: "+linkClass);
		if (!rep.equals("")) {
		    try {
			cvar.repeat = Integer.parseInt(rep);
		    } catch (Exception e){}
		}
		cctype.addVar(cvar);
	    } else {
		SimpleVar cvar = new SimpleVar(varName);		
		cvar.varType = vtp;
		if (!rep.equals("")) {
		    try {
			cvar.repeat = Integer.parseInt(rep);
		    } catch (Exception e){}
		}
		cctype.addVar(cvar);
	    }	
	    doing = 0;   
	} 
	else if (currentElement.equals("item")) {
	    // Process an item.
	    String in  = getAttr(attrs, "id");		
	    String itp = getAttr(attrs, "type");	  
	    System.err.println("This ITEM isa "+itp);
	    if (itp.equals("class")) {
		String ic = getAttr(attrs, "class");
		System.err.println("This ITEM isa "+itp+" of Class: "+ic);
		// push this item onto the item tree
		items.add(new ClassItem(in, ic));		
	    } else {
		System.err.println("This ITEM isa Simple: "+itp);
		items.add(new SimpleItem(in, itp));
	    }
	    
	    doing = 0;
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
	return "";
    }
    
  /** Characters. */
  public void characters(char ch[], int start, int length) {
      String stuff = new String(ch, start, length);
      int count = 0;
      for (int i = 0; i < stuff.length(); i++) {
	  if (Character.isWhitespace(stuff.charAt(i)))
	      count++;
      }
      if (count != stuff.length()) {	
	  System.err.println("Read some characters: {"+stuff+"} while doing="+doing);
	  
	  stuff = stuff.trim();
	  
      }
      
      switch (doing) {
      case doCfgName:
	  cfgName = new String(stuff);
	  System.err.println("X1");
	  break;
      case doCfgDesc:
	  cfgDesc = new String(stuff);  
	  System.err.println("X2");
	  break;
      }
      doing = 0;

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
      if (name.equals("class")) {
	  System.err.println("Closing definition of class.."+(cctype.className));
      }
  } // endElement(String)
    
  /** End document. */
  public void endDocument() 
    {
	System.err.println("End of Doc -  Generating HTML form.");

	System.err.println("<html><head><title>"+cfgName+"</title></head>");
	System.err.println("<body bgcolor = \"white\"><center><h2>"+cfgDesc+"</h2></center>");
	//  Iterator it = classes.keySet().iterator();
//  	while (it.hasNext()) {
//  	    String cname = (String)it.next();
//  	    ClassType clazz = (ClassType)classes.get(cname);
//  	    System.err.println("<h3>Class: "+clazz.className);
//  	    Iterator vit = clazz.listVars();
//  	    while (vit.hasNext()) {
//  		Var var = (Var)vit.next();
//  		System.err.println("<h4>&nbsp;&nbsp;Var: "+var.toString());
//  	    }
//  	}
	System.err.println("<form>");
	Iterator iit = items.iterator();
	while (iit.hasNext()) {
	    Item item = (Item)iit.next();
	    System.err.println(item.toString());	    
	}
	System.err.println("</form></body></html>");
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
    
  /** Main program entry point. */
  public static void main(String argv[]) 
      {
	  if (argv.length == 0)
	    {
		System.out.println("Usage:  java saxPhase2 <uri>");
		System.out.println("   where uri is the URI of your XML document.");
		System.out.println("   E.g.:  java saxPhase2 testgroup.xml");
		System.exit(1);
	    }
	
	  ConfigParser s1 = new ConfigParser();
	  s1.parseURI(argv[0]);
      } // main(String[])
  
    class ClassType {
	
	public String className = "";
	
	public String description = "";
	
	public Vector vars;
	
	ClassType(String className) {
	    this.className = className;
	    vars = new Vector();
	}
	
	public void addVar(Var var) {
	    vars.add(var);
	}
	
	public Iterator listVars() {
	    return vars.iterator();
	}
	
    }
    
    class Var {
	public int repeat = 0;
	Var(){}
    }
    
    class SimpleVar extends Var {
	
	public String varName = "";
	
	public String varType = "";
	
	public String min = "";
	
	public String max = "";
	
	SimpleVar(String varName) {
	    this.varName = varName;
	}
	
	public String toString() { return "Simple: "+varName+" : "+varType+" X "+repeat; }
    }
    
    class ClassVar extends Var {
	public String varName = "";
	public String linkClass = "";
	
	ClassVar(String varName,String linkClass) {
	    this.varName = varName;
	    this.linkClass = linkClass;
	}
	
	public String toString() { return "LinkClass: "+linkClass+" X "+repeat; }
    }
        
    class Item {
	

	Item() {}

    }

    class SimpleItem extends Item {

	public String id = "";

	public String type = "";

	SimpleItem(String id,String type) {
	    this.id = id;
	    this.type = type;
	}
	//public String toString() { return "SimpleItem: "+id+" : "+type;}
	public String toString() { return "\n<br>"+id+"<input type = \"TEXT\">"; }
    }

    class ClassItem extends Item {

	String id = "";

	String clazz = "";

	ClassItem(String id,String clazz) {
	    this.id = id;
	    this.clazz = clazz;
	}
	public String toString() {
	    StringBuffer buff = new StringBuffer();
	    processClass(buff, id, clazz);
	    return buff.toString();
	}

	private void processClass(StringBuffer buff, String cid, String cc) {
	    ClassType ct = (ClassType)classes.get(cc);
	    if (ct != null) {
		buff.append("\n<tr><td><table border = 2 bgcolor = pink><tr><td>Class Item:"+cc+".."+cid+"</td></tr><tr><td>....</td></tr>");
		Iterator cit = ct.listVars();
		while (cit.hasNext()) {
		    Var var = (Var)cit.next();
		    if (var instanceof SimpleVar) {
			SimpleVar sv = (SimpleVar)var;
			for (int j = 0; j <= sv.repeat; j++) {
			    buff.append("\n<tr><td></td><td>"+cid+"."+sv.varName+"."+j+
					"</td><td><input type = \"TEXT\"></td></tr>");
			}
		    } else if (var instanceof ClassVar) {
			ClassVar cv = (ClassVar)var;
			for (int j = 0; j <= cv.repeat; j++) {
			    processClass(buff, cid+"."+cv.varName+"."+j, cv.linkClass);
			}
		    }
		}
		buff.append("\n</td></tr></table>");
	    }
	    	    
	}

    }
    
}


