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

import dev.lt.RATCamConfig; //import com.ibm.xml.parsers.*;

import ngat.phase2.*; //import ngat.phase2.nonpersist.*;
import ngat.astrometry.*;

import java.text.*;
import java.util.*;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * A sample SAX writer. This sample program illustrates how to register a SAX
 * DocumentHandler and receive the callbacks in order to print a document that
 * is parsed.
 * 
 * @version Revision: 06 1.5 samples/sax/SAXWriter.java, samples, xml4j2,
 *          xml4j2_0_15
 */
public class XMLParser extends DefaultHandler {

	private boolean doing = false;

	static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm");
	static SimpleTimeZone utc = new SimpleTimeZone(0, "UTC");

	String currentElement = "";

	Group cGroup = null;

	Observation cObs = null;

	Source cSrc = null;

	String cSrcName = null;

	String cgtype = null;

	boolean lock = false;

	int cSrcFrame = 0;
	float cSrcEpoch = 2000.0f;
	float cSrcEquinox = 0.0f;
	char cSrcEquinoxLetter = 'A';

	InstrumentConfig cIc = null;

	TelescopeConfig cTc = null;

	Detector cDet = null;

	Mosaic cMos = null;

	EphemerisSource.Coordinate cCoord = null;

	StringBuffer ccra = new StringBuffer();
	StringBuffer ccdec = new StringBuffer();

	String cParam = null;

	String cPropId = null;
	String cGroupId = null;

	Map srcMap = new HashMap();
	Map icMap = new HashMap();
	Map tcMap = new HashMap();

	String action = null;

	/** Set if a fatal error occurs to pass back to caller. */
	volatile boolean hasError = false;

	Exception fatalError = null;

	public XMLParser() {
		sdf.setTimeZone(utc);
	}

	// public void parseURI(String uri) throws Exception {
	public void parseFile(File file) throws Exception {

		SAXParserFactory saxFactory = SAXParserFactory.newInstance();
		SAXParser parser = saxFactory.newSAXParser();
		// parser.setErrorHandler((DefaultHandler)this);

		try {
			// parser.parse(uri, this);
			parser.parse(file, this);
		} catch (Exception e) {
			System.err.println("XMLParser: Setting error flag due: " + e);
			e.printStackTrace();
			fatalError = e;
			hasError = true;
		}

		// XMLReader xr = XMLReaderFactory.createXMLReader();
		// xr.setContentHandler(this);
		// xr.setErrorHandler(this);

		// FileReader r = new FileReader(file);
		// xr.parse(new InputSource(r));

	}

	/** Processing instruction. */
	public void processingInstruction(String target, String data) {
		System.out.print("Processing - " + target);
		if (data != null && data.length() > 0) {
			System.out.print(" data: ");
			System.out.print(data);
		}
	} // processingInstruction(String,String)

	/** Start document. */
	public void startDocument() {
		System.out.println("Starting to parse......\n\n");
	} // startDocument()

	public void startElement(String uri, String localName, String name, Attributes attrs) throws SAXException {
		super.startElement(uri, localName, name, attrs);
		System.err.println("Called startElement(" + name + ")");
		System.err.println("Checking attributes...");

		if (attrs == null) {
			System.err.println("Attributes were NULL");
		} else {
			System.err.println("Attributes length: " + attrs.getLength());
			int len = attrs.getLength();
			for (int i = 0; i < len; i++) {
				System.err.println("  Found Element: " + name + " attrib: [" + attrs.getQName(i) + "] = "
						+ attrs.getValue(i));
			}
		}
		currentElement = name;

		try {

			if (name.equals("p2ml")) {
				System.err.println("Starting P2ML...");
			} else if (name.equals("request")) {
				System.err.println("Starting REQUEST...");
			} else if (name.equals("action")) {
				System.err.println("Starting ACTION...");
			} else if (name.equals("param")) {
				cParam = getAttr(attrs, "name");
				System.err.println("Starting action with param: " + cParam);
			} else if (name.equals("group")) {
				cgtype = (getAttr(attrs, "type"));

				if (cgtype.equals("monitor")) {
					cGroup = new MonitorGroup();
					((MonitorGroup) cGroup).setPeriod(86400 * 1000L);
					((MonitorGroup) cGroup).setStartDate(System.currentTimeMillis() + 86400 * 1000 * 365);
					((MonitorGroup) cGroup).setEndDate(System.currentTimeMillis() + 86400 * 1000 * 366);
					((MonitorGroup) cGroup).setFloatFraction(0.5f);
				} else if (cgtype.equals("fixed"))
					cGroup = new FixedGroup();
				else if (cgtype.equals("interval"))
					cGroup = new RepeatableGroup();
				else
					cGroup = new Group();

				cGroup.setName(getAttr(attrs, "group-id"));
				System.err.println("Creating a Group....." + cGroup.getName());
				cGroup.setMinimumLunarDistance(Math.toRadians(15.0));
				// Default MLD now...

				String strmoon = getAttr(attrs, "moon");

				// if not set we get UNKNOWN...
				if (strmoon.equals("true")) {
				    cGroup.setMoon(true);
				    System.err.println("Setting group as moon obs");
				}

				String strPriority = getAttr(attrs, "priority");
				if (strPriority == null) {
					cGroup.setPriority(1);
					System.err.println("**WARNING set group priority to [1] as NOT SPECIFIED");
				} else {
					try {
						cGroup.setPriority(Integer.parseInt(strPriority));
					} catch (Exception e) {
						System.err.println("**WARNING set group priority to [1] due to: " + e);
					}
				}

				if (cPropId != null)
					cGroup.setPath(cPropId);
				System.err.println("Group added Path: " + cPropId + " Group: [" + cGroup.getFullPath());
				cGroup.setNotifyWhenDone(getAttr(attrs, "notify-when-done").equals("TRUE") ? true : false);
			} else if (name.equals("constraints")) {

				cGroup.setMinimumLunar(Group.BRIGHT);
				String strLunar = getAttr(attrs, "lunar");
				if (strLunar.equals("DARK"))
					cGroup.setMinimumLunar(Group.DARK);
				else if (strLunar.equals("BRIGHT"))
					cGroup.setMinimumLunar(Group.BRIGHT);

				cGroup.setMinimumSeeing(Group.POOR);
				String strSeeing = getAttr(attrs, "seeing");
				if (strSeeing.equals("POOR"))
					cGroup.setMinimumSeeing(Group.POOR);
				else if (strSeeing.equals("AVERAGE"))
					cGroup.setMinimumSeeing(Group.AVERAGE);
				else if (strSeeing.equals("EXCELLENT") || strSeeing.equals("GOOD"))
					cGroup.setMinimumSeeing(Group.EXCELLENT);

				cGroup.setTwilightUsageMode(Group.TWILIGHT_USAGE_OPTIONAL);
				String strTwilight = getAttr(attrs, "twilight");
				if (strTwilight.equals("NEVER")) {
					cGroup.setTwilightUsageMode(Group.TWILIGHT_USAGE_NEVER);
					System.err.println("Group constraint: TWI NEVER");
				} else if (strTwilight.equals("OPTIONAL")) {
					cGroup.setTwilightUsageMode(Group.TWILIGHT_USAGE_OPTIONAL);
					System.err.println("Group constraint: TWI OPT");
				} else if (strTwilight.equals("MANDATORY")) {
					cGroup.setTwilightUsageMode(Group.TWILIGHT_USAGE_ALWAYS);
					System.err.println("Group constraint: TWI MAND");
				}

				String strMeridian = getAttr(attrs, "meridian-limit");
				// cGroup.setMeridianLimit(Double.parseDouble(strMeridian));
				cGroup.setMeridianLimit(0.0);
				System.err.println("Group: Meridian: " + strMeridian + " CHANGED to 0");

			} else if (name.equals("observation")) {
				cObs = new Observation();
				cObs.setName(getAttr(attrs, "obs-id"));
				cObs.setPipelineConfig(new PipelineConfig());
				cObs.setAutoGuiderUsageMode(TelescopeConfig.AGMODE_NEVER);
				cObs.setRotatorMode(TelescopeConfig.ROTATOR_MODE_SKY);
				cObs.setRotatorAngle(0.0);
				cObs.setFocusOffset(0.0);

				// default single mosaic.
				Mosaic dmosaic = new Mosaic();
				dmosaic.setPattern(Mosaic.SINGLE);
				cObs.setMosaic(dmosaic);
				System.err.println("Creating an Observation...." + cObs.getName());
				String ic = getAttr(attrs, "ic-id");
				System.err.println("Uses IC: " + ic);
				String tc = getAttr(attrs, "tc-id");
				System.err.println("Uses TC: " + tc);
				String src = getAttr(attrs, "src-id");
				System.err.println("Uses SR: " + src);
				srcMap.put(cObs, src);
				cObs.setSource(new ExtraSolarSource(src)); 
				icMap.put(cObs, ic);
				cObs.setInstrumentConfig(new InstrumentConfig(ic)); 
				cGroup.addObservation(cObs);
				
			} else if (name.equals("inst-config")) {
			
				// for compatibility assume ratcam if not specified
				
				String itype = getAttr(attrs, "type");
				System.err.println("Instrument type: "+itype);
				if (itype.equalsIgnoreCase("frodo"))
					cIc = new FrodoSpecConfig();
				else if (itype.equalsIgnoreCase("io:thor")) 
						cIc = new THORConfig();
				else if (itype.equalsIgnoreCase("io:o")) {
					cIc = new OConfig();
					// default the slides to clear
					((OConfig)cIc).setFilterName(2, OConfig.CLEAR);
					((OConfig)cIc).setFilterName(3, OConfig.CLEAR);
				} else
					cIc = new RATCamConfig();
				
				cIc.setName(getAttr(attrs, "ic-id"));
				
				System.err.println("Created IC: " + cIc.getName());
			} else if (name.equals("tracking")) {
				System.err.println("Check tracking....");
				String strTrack = getAttr(attrs, "mode");
				if (strTrack.equals("Non-sidereal")) {
					cObs.setNonSiderealTracking(true);
					System.err.println("...Tracking is NON Sidereal");
				} else {
					cObs.setNonSiderealTracking(false);
					System.err.println("...Tracking is default Sidereal");
				}
			} else if (name.equals("mosaic")) {
				System.err.println("Creating a Mosaic....");
				cMos = new Mosaic();

				String strPatt = getAttr(attrs, "pattern");
				if (strPatt.equals("ARRAY")) {
					cMos.setPattern(Mosaic.ARRAY);
				} else if (strPatt.equals("CROSS")) {
					cMos.setPattern(Mosaic.CROSS);
				} else if (strPatt.equals("SINGLE")) {
					cMos.setPattern(Mosaic.SINGLE);
				}

				String strScale = getAttr(attrs, "scale");
				if (strScale.equals("TRUE")) {
					cMos.setScaleToPixel(true);
				} else if (strScale.equals("FALSE")) {
					cMos.setScaleToPixel(false);
				}
				cObs.setMosaic(cMos);
			} else if (name.equals("exposure")) {
				cObs.setExposeTime((float) Double.parseDouble(getAttr(attrs, "expose-time")));
				cObs.setConditionalExposure(getAttr(attrs, "conditional").equals("TRUE") ? true : false);
				cObs.setNumRuns(Integer.parseInt(getAttr(attrs, "repeats")));
			} else if (name.equals("rotator")) {
				// cTc = new TelescopeConfig();
				// cTc.setName(getAttr(attrs, "tc-id"));

				String strRotmode = getAttr(attrs, "rotmode");
				// if (strRotmode.equals("PARALLACTIC")){
				// cTc.setUseParallacticAngle(true);
				// } else
				if (strRotmode.equals("SKY")) {
					cObs.setRotatorMode(TelescopeConfig.ROTATOR_MODE_SKY);
				} else if (strRotmode.equals("MOUNT")) {
					cObs.setRotatorMode(TelescopeConfig.ROTATOR_MODE_MOUNT);
				} else if (strRotmode.equals("FLOAT")) {
					cObs.setRotatorMode(TelescopeConfig.ROTATOR_MODE_FLOAT);
				} else if (strRotmode.equals("VERTICAL")) {
					cObs.setRotatorMode(TelescopeConfig.ROTATOR_MODE_VERTICAL);
				}

				// String strAgmode = getAttr(attrs, "autoguide");
				// if (strAgmode.equals("NEVER")) {
				// cTc.setAutoGuiderUsageMode(TelescopeConfig.AGMODE_NEVER);

				// } else if
				// (strAgmode.equals("OPTIONAL")) {
				// cTc.setAutoGuiderUsageMode(TelescopeConfig.AGMODE_OPTIONAL);

				// } else if
				// (strAgmode.equals("MANDATORY")) {
				// cTc.setAutoGuiderUsageMode(TelescopeConfig.AGMODE_MANDATORY);

				// }

				// String strAngle = getAttr(attrs, "angle-offset");
				// cTc.setSkyAngle(Math.toRadians(Double.parseDouble(strAngle)));

				// String strFocus = getAttr(attrs, "focus-offset");
				// cTc.setFocusOffset(Float.parseFloat(strFocus));

				System.err.println("Set Rotator mode: " + strRotmode);
			} else if (name.equals("detector")) {
				cDet = cIc.getDetector(0);
			} else if (name.equals("coordinate")) {
				cCoord = new EphemerisSource.Coordinate();
				cCoord.setTime(sdf.parse(getAttr(attrs, "epoch")).getTime());
				((EphemerisSource) cSrc).addCoordinate(cCoord);
			} else if (name.equals("source")) {
				// cSrc = new ExtraSolarSource();
				System.err.println("Noting a Source....cat UKG");
				cSrcName = getAttr(attrs, "src-id");
				System.err.println("Noted Source: UKG" + cSrcName);
			} else if (name.equals("extra-solar")) {
				System.err.println("Noting a source " + cSrcName + " as EXTRASOLAR");
				cSrc = new ExtraSolarSource();
				cSrc.setName(cSrcName);
				cSrc.setEpoch(cSrcEpoch);
				cSrc.setEquinox(cSrcEquinox);
				cSrc.setEquinoxLetter(cSrcEquinoxLetter);
				cSrc.setFrame(cSrcFrame);
			} else if (name.equals("ephemeris")) {
				System.err.println("Noting a source " + cSrcName + " as EPHEMERIS_LIST");
				cSrc = new EphemerisSource(cSrcName);

				cSrc.setEquinox(cSrcEquinox);
				cSrc.setEquinoxLetter(cSrcEquinoxLetter);
				cSrc.setFrame(cSrcFrame);
			} else if (name.equals("catalog")) {
				System.err.println("Noting a source " + cSrcName + " as CATALOG");
				cSrc = new CatalogSource();
				cSrc.setName(cSrcName);
				
				cSrc.setEquinox(cSrcEquinox);
				
				cSrc.setEquinoxLetter('A');
				cSrc.setEquinox(Source.EPOCH_CURRENT);
				
				cSrc.setFrame(cSrcFrame);
				String strCatId = getAttr(attrs, "cat-id");
				System.err.println("CatName is: " + strCatId);
				// what catid.

				int cid = 0;

				if (strCatId.equalsIgnoreCase("MERCURY")) {
				    cid = CatalogSource.MERCURY;
				} else if (strCatId.equalsIgnoreCase("VENUS")) {
				    cid = CatalogSource.VENUS;
				} else if (strCatId.equalsIgnoreCase("MARS")) {
				    cid = CatalogSource.MARS;
				} else if (strCatId.equalsIgnoreCase("MOON")) {
				    cid = CatalogSource.MOON;
				} else if (strCatId.equalsIgnoreCase("JUPITER")) {
				    cid = CatalogSource.JUPITER;
				} else if (strCatId.equalsIgnoreCase("SATURN")) {
				    cid = CatalogSource.SATURN;
				} else if (strCatId.equalsIgnoreCase("URANUS")) {
				    cid = CatalogSource.URANUS;
				} else if (strCatId.equalsIgnoreCase("NEPTUNE")) {
				    cid = CatalogSource.NEPTUNE;
				} else if (strCatId.equalsIgnoreCase("PLUTO")) {
				    cid = CatalogSource.PLUTO;
				}
				((CatalogSource)cSrc).setCatalogId(cid);
				System.err.println("Noting catalog source: "+strCatId+" mapped to Cat-ID: "+cid);

			} else if (name.equals("comet")) {
				System.err.println("Noting a source " + cSrcName + " as COMET");
				cSrc = new Comet();
				cSrc.setName(cSrcName);

				cSrc.setEquinox(0.0f);

				cSrc.setEquinoxLetter('A');
				cSrc.setEquinox(Source.EPOCH_CURRENT);

				cSrc.setFrame(Source.FK5);
			}
		} catch (Exception e) {
			System.err.println("Exception reading: " + name + " : " + e);
		}

	} // startElement(String,AttributeList)

	protected String getAttr(Attributes attrs, String which) {
		if (attrs != null) {
			int len = attrs.getLength();
			for (int i = 0; i < len; i++) {
				if (attrs.getQName(i).equals(which)) {
					System.err.println("OK Attrib: " + which + " was: " + attrs.getValue(i));
					return attrs.getValue(i);
				}
			}
		}
		return "UNKNOWN";
	}

	/**
	 * Characters. Process a String of characters - i.e. a name or object field
	 * value.
	 */
	public void characters(char ch[], int start, int length) throws SAXException {
		String stuff = new String(ch, start, length);
		int count = 0;
		for (int i = 0; i < stuff.length(); i++) {
			if (Character.isWhitespace(stuff.charAt(i)))
				count++;
		}
		if (count != stuff.length()) {
			System.err.println("Read some characters: {" + stuff + "} St=" + start + ", Ln=" + length);

			stuff = stuff.trim();
			System.err.println("Current element is: " + currentElement);
			try {

				if (currentElement.equals("action")) {
					action = stuff;
					// Work out what params based on action....
					System.err.println("Action class is: [" + action + "]");
				} else if (currentElement.equals("value") || currentElement.equals("param")) {
					// This is an action-specific parameter
					// and so depends on the current action value.
					if (cParam.equals("proposal-id")) {
						cPropId = stuff;
						System.err.println("Action ********* Setting cPropId to: " + cPropId);
					} else if (cParam.equals("group-id")) {
						cGroupId = stuff;
						System.err.println("Action ********* Setting cGroupId to: " + cGroupId);
					}
				} else if (cParam.equals("lock")) {
					String slock = stuff;

					if (slock.equalsIgnoreCase("TRUE"))
						lock = true;
					else
						lock = false;

				} else if (currentElement.equals("expiry-date")) {
					cGroup.setExpiryDate(sdf.parse(stuff).getTime());

				} else if (currentElement.equals("start-date")) {
					cGroup.setStartingDate(sdf.parse(stuff).getTime());

				} else if (currentElement.equals("datum-start")) {

					Date date = sdf.parse(stuff);
					long st = date.getTime();

					((MonitorGroup) cGroup).setStartDate(st);

					System.err.println("Extracted mon-start date: " + date);

				} else if (currentElement.equals("datum-end")) {

					Date date = sdf.parse(stuff);
					long st = date.getTime();

					((MonitorGroup) cGroup).setEndDate(st);

					System.err.println("Extracted mon-end date: " + date);

				} else if (currentElement.equals("period")) { // secs

					long period = Long.parseLong(stuff);
					((MonitorGroup) cGroup).setPeriod(1000L * period);

				} else if (currentElement.equals("float")) { // window size

					float window = Float.parseFloat(stuff);
					((MonitorGroup) cGroup).setFloatFraction(window);

				} else if (currentElement.equals("interval-start")) {

					Date date = sdf.parse(stuff);
					long st = date.getTime();

					((RepeatableGroup) cGroup).setStartDate(st);

					System.err.println("Extracted minint-start date: " + date);

				} else if (currentElement.equals("interval-end")) {

					Date date = sdf.parse(stuff);
					long st = date.getTime();

					((RepeatableGroup) cGroup).setEndDate(st);

					System.err.println("Extracted minint-end date: " + date);

				} else if (currentElement.equals("min-interval")) { // secs
					long minInt = Long.parseLong(stuff);
					((RepeatableGroup) cGroup).setMinimumInterval(1000L * minInt);

				} else if (currentElement.equals("max-repeats")) {
					int maxrep = Integer.parseInt(stuff);
					((RepeatableGroup) cGroup).setMaximumRepeats(maxrep);

				} else if (currentElement.equals("fixed-time")) {

					Date date = sdf.parse(stuff);
					long ft = date.getTime();

					((FixedGroup) cGroup).setFixedTime(ft);

					System.err.println("Extracted fixed date: " + date);

					// EXTRASOLAR fields
				} else if (currentElement.equals("ra")) {
					((ExtraSolarSource) cSrc).setRA(Position.parseHMS(stuff, ":"));
				} else if (currentElement.equals("dec")) {
					((ExtraSolarSource) cSrc).setDec(Position.parseDMS(stuff, ":"));
				} else if (currentElement.equals("pm-ra")) {
					((ExtraSolarSource) cSrc).setPmRA(Double.parseDouble(stuff));
				} else if (currentElement.equals("pm-dec")) {
					((ExtraSolarSource) cSrc).setPmDec(Double.parseDouble(stuff));
				} else if (currentElement.equals("parallax")) {
					((ExtraSolarSource) cSrc).setParallax(Double.parseDouble(stuff));

					// EPHEMERIS fields

				} else if (currentElement.equals("coord-ra")) {
					ccra.append(stuff);
					System.err.println("Setting ccoord(ra): " + cCoord);
				} else if (currentElement.equals("coord-dec")) {
					ccdec.append(stuff);
					System.err.println("Setting ccoord(dec): " + cCoord);

					// CATALOGSRC fields

					// Comet fields.

				} else if (currentElement.equals("element-epoch")) {
					((Comet) cSrc).setElementEpoch(Double.parseDouble(stuff));
				} else if (currentElement.equals("long-asc-node")) { // Rads
					((Comet) cSrc).setLongAscNode(Math.toRadians(Double.parseDouble(stuff)));
				} else if (currentElement.equals("arg-peri")) { // Rads
					((Comet) cSrc).setArgPeri(Math.toRadians(Double.parseDouble(stuff)));
				} else if (currentElement.equals("peri-dist")) { // AU
					((Comet) cSrc).setPeriDist(Double.parseDouble(stuff));
					System.err.println("Setting comet PD=" + stuff);
				} else if (currentElement.equals("orbital-inc")) { // Rads
					((Comet) cSrc).setOrbitalInc(Math.toRadians(Double.parseDouble(stuff)));
				} else if (currentElement.equals("eccentricity")) { // Frac
					((Comet) cSrc).setEccentricity(Double.parseDouble(stuff));

					// CONFIG fields
				} else if (currentElement.equals("focus-offset")) {
					cObs.setFocusOffset(Double.parseDouble(stuff));
					System.err.println("Setting Obs FocusOffset: " + stuff);
				} else if (currentElement.equals("autoguider")) {

					if (stuff.equals("NEVER")) {
						cObs.setAutoGuiderUsageMode(TelescopeConfig.AGMODE_NEVER);
					} else if (stuff.equals("OPTIONAL")) {
						cObs.setAutoGuiderUsageMode(TelescopeConfig.AGMODE_OPTIONAL);
					} else if (stuff.equals("MANDATORY")) {
						cObs.setAutoGuiderUsageMode(TelescopeConfig.AGMODE_MANDATORY);
					}
				} else if (currentElement.equals("rotator")) {
					double angle = Math.toRadians(Double.parseDouble(stuff));
					cObs.setRotatorAngle(angle);
					System.err.println("Setting Obs Rot Angle: " + stuff + " degs/ " + angle + " rads");

				} else if (currentElement.equals("minimum-lunar-distance")) {
					double mld = Math.toRadians(Double.parseDouble(stuff)); // in
					// degrees
					cGroup.setMinimumLunarDistance(mld);
					System.err.println("Setting minimum lunar distance: " + stuff + "degs/ " + mld + " rads");

					// DETECTOR fields
				} else if (currentElement.equals("xbin")) {
					cDet.setXBin(Integer.parseInt(stuff));
				} else if (currentElement.equals("ybin")) {
					cDet.setYBin(Integer.parseInt(stuff));
				} else if (currentElement.equals("ra-offset")) {
					cMos.setOffsetRA(Double.parseDouble(stuff));
				} else if (currentElement.equals("dec-offset")) {
					cMos.setOffsetDec(Double.parseDouble(stuff));
				} else if (currentElement.equals("ra-cells")) {
					cMos.setCellsRA(Integer.parseInt(stuff));
				} else if (currentElement.equals("dec-cells")) {
					cMos.setCellsDec(Integer.parseInt(stuff));
				} else if (currentElement.equals("offset-ra")) {
					cObs.setSourceOffsetRA(Double.parseDouble(stuff));
				} else if (currentElement.equals("offset-dec")) {
					cObs.setSourceOffsetDec(Double.parseDouble(stuff));
				} else if (currentElement.equals("equinox")) {
					String strEquinox = stuff;
					cSrcEquinoxLetter = strEquinox.charAt(0);
					cSrcEquinox = Float.parseFloat(strEquinox.substring(1));
				} else if (currentElement.equals("epoch")) {
					cSrcEpoch = Float.parseFloat(stuff);
				} else if (currentElement.equals("frame")) {
					String strFrame = stuff;
					if (strFrame.equals("FK4"))
						cSrcFrame = Source.FK4;
					else if (strFrame.equals("FK5"))
						cSrcFrame = Source.FK5;
					
				} else if (currentElement.equals("filter")) {
					((OConfig)cIc).setFilterName(1, stuff);
				} else if (currentElement.equals("lower-slide")) {
					((OConfig)cIc).setFilterName(2, stuff);
				} else if (currentElement.equals("upper-slide")) {
					((OConfig)cIc).setFilterName(3, stuff);
					
				} else if (currentElement.equals("lower-filter")) {
					((CCDConfig)cIc).setLowerFilterWheel(stuff);
				} else if (currentElement.equals("upper-filter")) {
					((CCDConfig)cIc).setUpperFilterWheel(stuff);
				} else if (currentElement.equals("gain")) {
					((THORConfig)cIc).setEmGain(Integer.parseInt(stuff));
				} else if (currentElement.equals("arm")) {
					if (stuff.equalsIgnoreCase("red"))
						((FrodoSpecConfig)cIc).setArm(FrodoSpecConfig.RED_ARM);
					else if
					(stuff.equalsIgnoreCase("blue"))
						((FrodoSpecConfig)cIc).setArm(FrodoSpecConfig.BLUE_ARM);			
				} else if (currentElement.equals("resolution")) {
					
					if(stuff.equalsIgnoreCase("low"))
						((FrodoSpecConfig)cIc).setResolution(FrodoSpecConfig.RESOLUTION_LOW);
					else if
					(stuff.equalsIgnoreCase("high"))
						((FrodoSpecConfig)cIc).setResolution(FrodoSpecConfig.RESOLUTION_HIGH);				
				}

			} catch (Exception e) {
				e.printStackTrace();
				System.err.println("Caught an exception while filling out: " + stuff + " : " + e);
				throw new SAXException("Caught an exception while filling out: " + stuff, e);
			}

		}

	} // characters(char[],int,int);

	/** Ignorable whitespace. */
	public void ignorableWhitespace(char ch[], int start, int length) {
		// characters(ch, start, length);
		// System.err.println(".......Some crap ...... ignore");
	} // ignorableWhitespace(char[],int,int);

	/** End element. */
	public void endElement(String uri, String localName, String name) throws SAXException {
		super.endElement(uri, localName, name);

		System.err.println("End of element: " + name);

		if (name.equals("observation")) {
			System.err.println("Finished Observation: " + cObs.getFullPath());
			cGroup.addObservation(cObs);
		} else if (name.equals("coordinate")) {
			System.err.println("Finished Coordinate: " + cCoord);
			((EphemerisSource) cSrc).addCoordinate(cCoord);
			System.err.println("Adding new coord to current ephem source: " + cSrc.getName());

		} else if (name.equals("source")) {
		    System.err.println("Finished: SRC: " + cSrc.getName());
		} else if (name.equals("inst-config")) {
		    System.err.println("Finished: IC: " + cIc.getName());
		} else if (name.equals("tele-config")) {
		    System.err.println("Finished: TC: " + cTc.toString());
		} else if (name.equals("coord-ra")) {
			try {
			    cCoord.setRA(Position.parseHMS(ccra.toString(), ":"));
			} catch (ParseException px) {
			    throw new SAXException("Caught exception parsing cc-ra", px);
			}
			System.err.println("Setting ccoord(ra): " + cCoord);
			ccra = new StringBuffer();
		} else if (name.equals("coord-dec")) {
		    
		    try {
			cCoord.setDec(Position.parseDMS(ccdec.toString(), ":"));
			
		    } catch (ParseException px) {
				throw new SAXException("Caught exception parsing cc-dec", px);
		    }
		    System.err.println("Setting ccoord(dec): " + cCoord);
		    ccdec = new StringBuffer();
		}
		
	} // endElement(String)

	/** End document. */
	public void endDocument() {
		System.err.println("End of Doc");

	} // endDocument()

	//
	// ErrorHandler methods
	//

	/** Warning. */
	public void warning(SAXParseException ex) {
		System.err.println("[Warning] " + getLocationString(ex) + ": " + ex.getMessage());
	}

	/** Error. */
	public void error(SAXParseException ex) {
		System.err.println("[Error] " + getLocationString(ex) + ": " + ex.getMessage());
		// hasError=true;
		// fatalError=ex;
	}

	/** Fatal error. */
	public void fatalError(SAXParseException ex) throws SAXException {
		System.err.println("[Fatal Error] " + getLocationString(ex) + ": " + ex.getMessage());
		// hasError=true;
		// fatalError=ex;
		throw ex;
	}

	/** Returns a string of the location. */
	private String getLocationString(SAXParseException ex) {
		StringBuffer str = new StringBuffer();

		String systemId = ex.getSystemId();
		if (systemId != null) {
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

	public boolean hasError() {
		return hasError;
	}

	public Exception getFatalEx() {
		return fatalError;
	}

	public Group getGroup() {
		return cGroup;
	}

	public Map getSrcMap() {
		return srcMap;
	}

	public Map getIcMap() {
		return icMap;
	}

	public Map getTcMap() {
		return tcMap;
	}

	public String getAction() {
		return action;
	}

	public String getProposalPathName() {
		return cPropId;
	}

	public boolean getDoLock() {
		return lock;
	}

	public String getGroupPathName() {
		return cGroupId;
	}

	public InstrumentConfig getInstConfig() {
		return cIc;
	}

	public TelescopeConfig getTeleConfig() {
		return cTc;
	}

	public Source getSource() {
		return cSrc;
	}

	/** Main program entry point. */
	public static void main(String argv[]) {

	} // main(String[])
}
