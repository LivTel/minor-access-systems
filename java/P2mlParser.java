import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import ngat.phase2.IAcquisitionConfig;
import ngat.phase2.IAutoguiderConfig;
import ngat.phase2.IInstrumentConfig;
import ngat.phase2.IRotatorConfig;
import ngat.phase2.ISequenceComponent;
import ngat.phase2.ITarget;
import ngat.phase2.ITimingConstraint;
import ngat.phase2.XAcquisitionConfig;
import ngat.phase2.XAirmassConstraint;
import ngat.phase2.XArc;
import ngat.phase2.XAutoguiderConfig;
import ngat.phase2.XBeamSteeringConfig;
import ngat.phase2.XBranchComponent;
import ngat.phase2.XDetectorConfig;
import ngat.phase2.XEphemerisTimingConstraint;
import ngat.phase2.XExecutiveComponent;
import ngat.phase2.XExtraSolarTarget;
import ngat.phase2.XFilterDef;
import ngat.phase2.XFilterSpec;
import ngat.phase2.XFixedTimingConstraint;
import ngat.phase2.XFlexibleTimingConstraint;
import ngat.phase2.XFocusControl;
import ngat.phase2.XFocusOffset;
import ngat.phase2.XGroup;
import ngat.phase2.XHourAngleConstraint;
import ngat.phase2.XImagerInstrumentConfig;
import ngat.phase2.XInstrumentConfigSelector;
import ngat.phase2.XIteratorComponent;
import ngat.phase2.XIteratorRepeatCountCondition;
import ngat.phase2.XLampDef;
import ngat.phase2.XLampFlat;
//import ngat.phase2.XLunarDistanceConstraint;
//import ngat.phase2.XLunarElevationConstraint;
//import ngat.phase2.XLunarPhaseConstraint;
import ngat.phase2.XMinimumIntervalTimingConstraint;
import ngat.phase2.XMonitorTimingConstraint;
import ngat.phase2.XMultipleExposure;
import ngat.phase2.XOpticalSlideConfig;
import ngat.phase2.XPhotometricityConstraint;
import ngat.phase2.XPositionOffset;
import ngat.phase2.XRotatorConfig;
import ngat.phase2.XSeeingConstraint;
import ngat.phase2.XSkyBrightnessConstraint;
import ngat.phase2.XSlew;
//import ngat.phase2.XSolarElevationConstraint;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;

/**
 * 
 */

/**
 * Parses documents in P2ML version 2.0+
 * 
 * @author eng
 * 
 */
public class P2mlParser {

	private SAXBuilder builder;

	private OfflineRelay server;

	static String RATCAM = "RATCam";
	static String RINGO = "RINGO2";
	static String FRODO = "FRODO";
	static String FRODO_RED = "FRODO_RED";
	static String FRODO_BLUE = "FRODO_BLUE";
	static String RISE = "RISE";
	static String IOTHOR = "IO:THOR";
	static String IOO = "IO:O";

	private boolean test;

	/**
	 * Create a P2ML Parser.
	 */
	public P2mlParser(OfflineRelay server) {
		this.server = server;
		builder = new SAXBuilder();
	}

	public void processDocument(File file) throws Exception {

		Document doc = builder.build(file);
		Element p2ml = doc.getRootElement();

		// ADD_GROUP the only one we want for now
		Element action = p2ml.getChild("add-group");
		if (action != null) {
			processAddGroup(action);
			return;
		}

		action = p2ml.getChild("replace-group");
		if (action != null) {
			processReplaceGroup(action);
			return;
		}

		action = p2ml.getChild("add-inst-config");
		if (action != null) {
			processAddInstConfig(action);
			return;
		}

	}

	private void processAddGroup(Element action) throws Exception {

		String propName = action.getAttributeValue("proposal");

		System.err.println("Lookup proposal: " + propName);

		ProgramInfo prog = server.findProgram(propName);
		Map propNameMap = prog.getPropNameMap();

		if (!propNameMap.containsKey(propName))
			throw new OcrException("P2mlParser::Add/Group: Cannot match proposal name: " + propName);

		long pid = ((Long) propNameMap.get(propName)).longValue();

		Element gnode = action.getChild("group");
		XGroup group = getGroup(gnode);
		System.err.println("The group is: " + group);

		// sequence
		Element snode = gnode.getChild("sequence");
		ISequenceComponent root = getSequence(prog, snode);

		String display = DisplaySeq.display(0, root);
		System.err.println(display);

		// now do the thing...
		String groupName = propName + "/" + group.getName();
		// is this one in the table already ?
		Map groupNameMap = prog.getGroupNameMap();
		if (groupNameMap.containsKey(groupName))
			throw new OcrException("P2mlParser::AddGroup: Group named " + group.getName() + " is already in table");

		long gid = server.getPhase2().addGroup(pid, group);

		// update sequence
		try {
			server.getPhase2().addObservationSequence(gid, root);
		} catch (Exception e) {

			// try to wipe the group we just added
			try {
				server.getPhase2().deleteGroup(gid);
				throw new OcrException("P2mlParser::AddGroup: Unable to set group sequence for " + group.getName()
						+ " , group was added but has been removed again due to: " + e);
			} catch (Exception e2) {
				throw new OcrException("P2mlParser::AddGroup: Unable to set group sequence for " + group.getName()
						+ " , due to: " + e + ", group was added but could not then be removed due to: " + e2);
			}

		}
		// storegroup(
		groupNameMap.put(groupName, new Long(gid));
		server.sendResponse("done", "AddGroup: Completed successfully with id: " + gid);

	}

	private void processReplaceGroup(Element action) throws Exception {

		String propName = action.getAttributeValue("proposal");

		System.err.println("Lookup proposal: " + propName);

		ProgramInfo prog = server.findProgram(propName);
		Map propNameMap = prog.getPropNameMap();

		if (!propNameMap.containsKey(propName))
			throw new OcrException("P2mlParser::ReplaceGroup: Cannot match proposal name: " + propName);

		long pid = ((Long) propNameMap.get(propName)).longValue();

		Element gnode = action.getChild("group");
		XGroup group = getGroup(gnode);
		System.err.println("The group is: " + group);

		String groupName = propName + "/" + group.getName();
		Map groupNameMap = prog.getGroupNameMap();
		if (!groupNameMap.containsKey(groupName))
			throw new OcrException("P2mlParser::ReplaceGroup: Cannot match group name: " + groupName);

		long gid = ((Long) groupNameMap.get(groupName)).longValue();

		// sequence
		Element snode = gnode.getChild("sequence");
		ISequenceComponent root = getSequence(prog, snode);

		String display = DisplaySeq.display(0, root);
		System.err.println(display);

		server.getPhase2().deleteObservationSequenceOfGroup(gid);
		System.err.println("P2mlParser::ReplaceGroup: Deleted group observation sequence for: " + gid);
		server.getPhase2().deleteGroup(gid);
		System.err.println("P2mlParser::ReplaceGroup: Deleted group: " + gid);

		long newgid = server.getPhase2().addGroup(pid, group);
		System.err.println("P2mlParser::ReplaceGroup: Added new group: " + newgid);
		// update sequence
		server.getPhase2().addObservationSequence(newgid, root);
		System.err.println("ReplaceGroup::Set sequence for group: " + newgid);

		groupNameMap.remove(groupName);
		groupNameMap.put(groupName, new Long(newgid));

		server.sendResponse("done", "ReplaceGroup: Completed successfully with new id: " + newgid);
	}

	private void processAddInstConfig(Element action) throws Exception {

		// read the config info and create if its not already there

	}

	private XGroup getGroup(Element gnode) throws Exception {

		String name = gnode.getAttributeValue("name");
		XGroup group = new XGroup();
		group.setName(name);
		group.setActive(true);

		// check for urgent flag
		String strurgent = gnode.getAttributeValue("urgent");
		if (strurgent.equalsIgnoreCase("true"))
			group.setUrgent(true);

		// constraints
		Element cnode = gnode.getChild("constraints");
		addConstraints(group, cnode);

		// timing
		Element tnode = gnode.getChild("timing");
		ITimingConstraint timing = getTiming(tnode);
		group.setTimingConstraint(timing);
		System.err.println("Timing: " + timing);

		return group;

	}

	private void addConstraints(XGroup group, Element cnode) throws Exception {

		// <seeing class = "AVERAGE" />
		/*
		 * Element senode = cnode.getChild("seeing"); if (senode != null) {
		 * XSeeingConstraint xsee = new XSeeingConstraint(); String seclass =
		 * senode.getAttributeValue("class").trim(); if
		 * (seclass.equalsIgnoreCase("GOOD")) xsee.setSeeingValue(0.8); else if
		 * (seclass.equalsIgnoreCase("AVERAGE")) xsee.setSeeingValue(1.3); else
		 * if (seclass.equalsIgnoreCase("POOR")) xsee.setSeeingValue(3.0); else
		 * if (seclass.equalsIgnoreCase("USABLE")) xsee.setSeeingValue(5.0);
		 * else throw new OcrException(
		 * "P2mlParser::addConstraints: Unknown seeing constraint category: " +
		 * seclass); group.addObservingConstraint(xsee);
		 * System.err.println("Adding seeing constraint: " + xsee);
		 * 
		 * }
		 */

		Element senode = cnode.getChild("seeing");
		if (senode != null) {
			XSeeingConstraint xsee = new XSeeingConstraint();
			String strmax = senode.getAttributeValue("max").trim();
			double seemax = Double.parseDouble(strmax);
			if (seemax <= 0.0)
				throw new OcrException("P2mlParser::addConstraints: Illegal seeing value: (" + seemax
						+ ") seeing > 0.0");
			xsee.setSeeingValue(seemax);
			group.addObservingConstraint(xsee);
			System.err.println("Adding seeing constraint: " + xsee);

		}

		// <extinction class = "PHOTOM" />
		Element exnode = cnode.getChild("extinction");
		if (exnode != null) {
			XPhotometricityConstraint xphot = new XPhotometricityConstraint();
			String pclass = exnode.getAttributeValue("class").trim();
			if (pclass.equalsIgnoreCase("PHOTOMETRIC"))
				xphot.setPhotometricityCategory(XPhotometricityConstraint.PHOTOMETRIC);
			else if (pclass.equalsIgnoreCase("SPECTROSCOPIC"))
				xphot.setPhotometricityCategory(XPhotometricityConstraint.NON_PHOTOMETRIC);
			else
				throw new OcrException("P2mlParser::addConstraints: Unknown extinction constraint category: " + pclass);
			group.addObservingConstraint(xphot);
			System.err.println("Adding extinction constraint: " + xphot);

		}

		// <airmass max = "2.0" />
		Element anode = cnode.getChild("airmass");
		if (anode != null) {
			XAirmassConstraint xair = new XAirmassConstraint();
			String strmax = anode.getAttributeValue("max").trim();
			double airmass = Double.parseDouble(strmax);
			// range checking
			if (airmass < 1.0 || airmass > 5.0)
				throw new OcrException("P2mlParser::addConstraints: Illegal airmass (" + airmass
						+ ") 1.0 <= airmass <= 5.0");
			xair.setMaximumAirmass(airmass);
			group.addObservingConstraint(xair);
			System.err.println("Adding airmass constraint: " + xair);
		}

		// <hour-angle min = "-3" max = "3" units = "hours"/>
		Element hnode = cnode.getChild("hour-angle");
		if (hnode != null) {
			XHourAngleConstraint xha = new XHourAngleConstraint();
			double hmin = getAngle(hnode, "min");
			double hmax = getAngle(hnode, "max");
			if (hmin < -Math.PI || hmin > Math.PI)
				throw new OcrException("P2mlParser::addConstraints: Illegal min ha (" + hmin
						+ ") -180.0 <= ha <= 180.0");
			if (hmax < -Math.PI || hmax > Math.PI)
				throw new OcrException("P2mlParser::addConstraints: Illegal max ha (" + hmax
						+ ") -180.0 <= ha <= 180.0");

			xha.setMinimumHourAngle(hmin);
			xha.setMaximumHourAngle(hmax);
			group.addObservingConstraint(xha);
			System.err.println("Adding hour angle constraint: " + xha);
		}

		Element skynode = cnode.getChild("sky-brightness");
		if (skynode != null) {
			XSkyBrightnessConstraint xsky = new XSkyBrightnessConstraint();
			String skyclass = skynode.getAttributeValue("class").trim();
			if (skyclass.equalsIgnoreCase("DAYTIME"))
				xsky.setSkyBrightnessCategory(XSkyBrightnessConstraint.DAYTIME);
			else if (skyclass.equalsIgnoreCase("10_MAG"))
				xsky.setSkyBrightnessCategory(XSkyBrightnessConstraint.MAG_10);
			else if (skyclass.equalsIgnoreCase("6_MAG"))
				xsky.setSkyBrightnessCategory(XSkyBrightnessConstraint.MAG_6);
			else if (skyclass.equalsIgnoreCase("4_MAG"))
				xsky.setSkyBrightnessCategory(XSkyBrightnessConstraint.MAG_4);
			else if (skyclass.equalsIgnoreCase("2_MAG"))
				xsky.setSkyBrightnessCategory(XSkyBrightnessConstraint.MAG_2);
			else if (skyclass.equalsIgnoreCase("1.5_MAG"))
				xsky.setSkyBrightnessCategory(XSkyBrightnessConstraint.MAG_1P5);
			else if (skyclass.equalsIgnoreCase("0.75_MAG"))
				xsky.setSkyBrightnessCategory(XSkyBrightnessConstraint.MAG_0P75);
			else if (skyclass.equalsIgnoreCase("DARK"))
				xsky.setSkyBrightnessCategory(XSkyBrightnessConstraint.DARK);
			else
				throw new OcrException("P2mlParser::addConstraints: Unknown sky constraint category: " + skyclass);

			group.addObservingConstraint(xsky);
			System.err.println("Adding sky constraint: " + xsky);

		}

	}

	/**
	 * Extract the group's timing constraints.
	 * 
	 * @param tnode
	 *            The node to parse.
	 * @return A timing constraint class.
	 * @throws Exception.
	 */
	private ITimingConstraint getTiming(Element tnode) throws Exception {

		String strClass = tnode.getAttributeValue("class").trim();
		if (strClass.equalsIgnoreCase("monitor")) {

			String strStart = tnode.getChildTextTrim("start");
			long start = (OfflineRelay.sdf.parse(strStart)).getTime();

			String strEnd = tnode.getChildTextTrim("end");
			long end = (OfflineRelay.sdf.parse(strEnd)).getTime();

			Element pnode = tnode.getChild("period");
			long period = getPeriod(pnode, null);

			Element wnode = tnode.getChild("window");
			long window = getPeriod(wnode, null);

			XMonitorTimingConstraint xmon = new XMonitorTimingConstraint(start, end, period, window);

			return xmon;

		} else if (strClass.equalsIgnoreCase("flexible")) {

			String strStart = tnode.getChildTextTrim("start");
			long start = (OfflineRelay.sdf.parse(strStart)).getTime();

			String strEnd = tnode.getChildTextTrim("end");
			long end = (OfflineRelay.sdf.parse(strEnd)).getTime();

			XFlexibleTimingConstraint xflex = new XFlexibleTimingConstraint(start, end);

			return xflex;

		} else if (strClass.equalsIgnoreCase("ephemeris")) {

			String strStart = tnode.getChildTextTrim("start");
			long start = (OfflineRelay.sdf.parse(strStart)).getTime();

			String strEnd = tnode.getChildTextTrim("end");
			long end = (OfflineRelay.sdf.parse(strEnd)).getTime();

			Element pnode = tnode.getChild("period");
			long period = getPeriod(pnode, null);

			Element wnode = tnode.getChild("window");
			long window = getPeriod(wnode, null);

			String strPhase = tnode.getChildTextTrim("phase");
			double phase = Double.parseDouble(strPhase);

			XEphemerisTimingConstraint xephem = new XEphemerisTimingConstraint();
			xephem.setStart(start);
			xephem.setEnd(end);
			xephem.setCyclePeriod(period);
			xephem.setWindow(window);
			xephem.setPhase(phase);

			return xephem;

		} else if (strClass.equalsIgnoreCase("interval")) {

			String strStart = tnode.getChildTextTrim("start");
			long start = (OfflineRelay.sdf.parse(strStart)).getTime();

			String strEnd = tnode.getChildTextTrim("end");
			long end = (OfflineRelay.sdf.parse(strEnd)).getTime();

			Element pnode = tnode.getChild("interval");
			long interval = getPeriod(pnode, null);

			String strMax = tnode.getChildTextTrim("max-repeats");
			int max = Integer.parseInt(strMax);

			XMinimumIntervalTimingConstraint xmin = new XMinimumIntervalTimingConstraint();
			xmin.setStart(start);
			xmin.setEnd(end);
			xmin.setMinimumInterval(interval);
			xmin.setMaximumRepeats(max);

			return xmin;

		} else if (strClass.equalsIgnoreCase("fixed")) {

			String strFixed = tnode.getChildTextTrim("at");
			long fixedTime = (OfflineRelay.sdf.parse(strFixed)).getTime();

			Element wnode = tnode.getChild("window");
			long slack = getPeriod(wnode, null);

			XFixedTimingConstraint xfix = new XFixedTimingConstraint(fixedTime, slack);

			return xfix;

		}
		throw new OcrException("P2mlParser::getTiming: Unknown timing constraint type: " + strClass);
	}

	/**
	 * Extract a period/time value from a node.
	 * 
	 * @param node
	 *            The node to parse.
	 * @param key
	 *            A key to look for as an attribute. If null use the node's
	 *            content.
	 * @return A time (millis).
	 * @throws Exception.
	 */
	private long getPeriod(Element node, String key) throws Exception {

		String strUnit = node.getAttributeValue("unit");

		double mult = 1.0;
		if (strUnit.equalsIgnoreCase("ms") || strUnit.equalsIgnoreCase("milli") || strUnit.equalsIgnoreCase("millis")
				|| strUnit.equalsIgnoreCase("msec") || strUnit.equalsIgnoreCase("msecs")
				|| strUnit.equalsIgnoreCase("millisec") || strUnit.equalsIgnoreCase("millisecs"))
			mult = 1.0;
		else if (strUnit.equalsIgnoreCase("s") || strUnit.equalsIgnoreCase("sec") || strUnit.equalsIgnoreCase("secs"))
			mult = 1000.0;
		else if (strUnit.equalsIgnoreCase("m") || strUnit.equalsIgnoreCase("min") || strUnit.equalsIgnoreCase("mins"))
			mult = 60000.0;
		else if (strUnit.equalsIgnoreCase("h") || strUnit.equalsIgnoreCase("hour") || strUnit.equalsIgnoreCase("hours"))
			mult = 3600000.0;
		else if (strUnit.equalsIgnoreCase("d") || strUnit.equalsIgnoreCase("day") || strUnit.equalsIgnoreCase("days"))
			mult = 86400000.0;
		else if (strUnit.equalsIgnoreCase("sd") || strUnit.equalsIgnoreCase("sidereal"))
			mult = 86164091.0;
		else
			throw new OcrException("P2mlParser::getPeriod: Unknown unit: " + strUnit + " in element " + node.getName());

		// Null key means use nodes own content
		String strTime = null;
		if (key == null)
			strTime = node.getTextTrim();
		else
			strTime = node.getAttributeValue(key).trim();

		double time = Double.parseDouble(strTime);

		return (long) (time * mult);

	}

	/**
	 * Translate node content into an angle in rads.
	 * 
	 * @param node
	 *            The node containing the data.
	 * @param key
	 *            A key to look for as an attribute. If null use the node's
	 *            content.
	 * @return An angle in rads.
	 * @throws Exception
	 *             If anything goes awry.
	 */
	private double getAngle(Element node, String key) throws Exception {

		String strUnit = node.getAttributeValue("unit");

		double mult = 1.0;

		if (strUnit.equalsIgnoreCase("r") || strUnit.equalsIgnoreCase("rad") || strUnit.equalsIgnoreCase("rads"))
			mult = 1.0;
		else if (strUnit.equalsIgnoreCase("d") || strUnit.equalsIgnoreCase("deg") || strUnit.equalsIgnoreCase("degs"))
			mult = Math.PI / 180.0;
		else if (strUnit.equalsIgnoreCase("a") || strUnit.equalsIgnoreCase("asec") || strUnit.equalsIgnoreCase("asecs")
				|| strUnit.equalsIgnoreCase("arcsec") || strUnit.equalsIgnoreCase("arcsecs"))
			mult = Math.PI / 648000.0;
		else if (strUnit.equalsIgnoreCase("h") || strUnit.equalsIgnoreCase("hour") || strUnit.equalsIgnoreCase("hours"))
			mult = 15.0 * Math.PI / 180.0;
		else
			throw new OcrException("P2mlParser::getAngle: Unknown unit: " + strUnit + " in element " + node.getName());

		// Null key means use nodes own content
		String strAngle = null;
		if (key == null)
			strAngle = node.getTextTrim();
		else
			strAngle = node.getAttributeValue(key).trim();

		double angle = Double.parseDouble(strAngle);

		return angle * mult;

	}

	/**
	 * Extract a distance from the supplied node.
	 * 
	 * @param node
	 *            The node to parse.
	 * @param key
	 *            A key to look for as an attribute. If null use the node's
	 *            content.
	 * @return A distance in mm.
	 * @throws Exception
	 */
	private double getDistance(Element node, String key) throws Exception {
		String strUnit = node.getAttributeValue("unit");

		double mult = 1.0;

		if (strUnit.equalsIgnoreCase("mm"))
			mult = 1.0;
		else if (strUnit.equalsIgnoreCase("micron") || strUnit.equalsIgnoreCase("microns")
				|| strUnit.equalsIgnoreCase("mu"))
			mult = 0.001;
		else
			throw new OcrException("P2mlParser::getDistance: Unknown unit: " + strUnit + " in element "
					+ node.getName());

		// Null key means use nodes own content
		String strDist = null;
		if (key == null)
			strDist = node.getTextTrim();
		else
			strDist = node.getAttributeValue(key).trim();

		double dist = Double.parseDouble(strDist);

		return dist * mult;

	}

	/**
	 * Extract a sequence from a node.
	 * 
	 * @param pinfo
	 *            The program associated with the node.
	 * @param snode
	 *            The node to parse.
	 * @return A sequence component (root).
	 * @throws Exception.
	 */
	private ISequenceComponent getSequence(ProgramInfo pinfo, Element snode) throws Exception {

		ISequenceComponent root = null;

		List children = snode.getChildren();

		if (children.size() == 1) {

			// root element specified

			Element e = (Element) children.remove(0);
			root = processComponent(pinfo, e);

		} else {

			// multiple content, infer root element
			root = new XIteratorComponent("root", new XIteratorRepeatCountCondition(1));

			Iterator ic = children.iterator();
			while (ic.hasNext()) {
				Element e = (Element) ic.next();
				// process node e and add sequence component to root
				((XIteratorComponent) root).addElement(processComponent(pinfo, e));

			}
		}

		return root;

	}

	
	/** Recursively build up the sequence tree.
	 * @param prog Info about the program so we can add/find configs and targets.
	 * @param e
	 * @return
	 * @throws Exception
	 */
	private ISequenceComponent processComponent(ProgramInfo prog, Element e) throws Exception {

		if (e.getName().equals("iterator")) {

			String iname = e.getAttributeValue("name");
			XIteratorComponent iter = new XIteratorComponent(iname, new XIteratorRepeatCountCondition(1));

			List children = e.getChildren();
			Iterator ic = children.iterator();
			while (ic.hasNext()) {
				Element c = (Element) ic.next();
				// TODO TEMP we may want to check the action and push a setacinst before it if
				// it turns out to be a slew or aperture - how will we know which instrument ...
				
				iter.addElement(processComponent(prog, c));
			}

			return iter;

		} else if (e.getName().equals("branch")) {

			String iname = e.getAttributeValue("name");
			XBranchComponent branch = new XBranchComponent(iname);

			Element rnode = e.getChild("red");
			List children = rnode.getChildren();
			Iterator ic = children.iterator();
			while (ic.hasNext()) {
				Element c = (Element) ic.next();
				branch.addChildComponent(processComponent(prog, c));
			}
			Element bnode = e.getChild("blue");
			children = bnode.getChildren();
			ic = children.iterator();
			while (ic.hasNext()) {
				Element c = (Element) ic.next();
				branch.addChildComponent(processComponent(prog, c));
			}

			return branch;

		} else if (e.getName().equals("slew")) {

			String targetName = e.getChildTextTrim("target");
			// does this target exist ?
			Map targetNameMap = prog.getTargetNameMap();
			if (!targetNameMap.containsKey(targetName))
				throw new OcrException("P2mlParser::createSequence: No target named: " + targetName
						+ " can be matched in program");

			ITarget target = (ITarget) targetNameMap.get(targetName);

			Element rnode = e.getChild("rotator");
			String strRotMode = rnode.getAttributeValue("mode").trim();
			int mode = IRotatorConfig.CARDINAL;
			if (strRotMode.equalsIgnoreCase("cardinal"))
				mode = IRotatorConfig.CARDINAL;
			else if (strRotMode.equalsIgnoreCase("sky"))
				mode = IRotatorConfig.SKY;
			else if (strRotMode.equalsIgnoreCase("mount"))
				mode = IRotatorConfig.MOUNT;

			String rotInst = "RATCAM";
			if (mode == IRotatorConfig.SKY || mode == IRotatorConfig.CARDINAL) {
				rotInst = rnode.getAttributeValue("focal-plane").trim();
			}

			double rotAngle = 0.0;
			if (mode == IRotatorConfig.SKY || mode == IRotatorConfig.MOUNT) {
				rotAngle = getAngle(rnode, "angle");
			}

			IRotatorConfig rotator = new XRotatorConfig(mode, rotAngle, rotInst);

			XSlew slew = new XSlew(target, rotator, false);

			// TODO if the target is an ephemeris we should set true ???
			return new XExecutiveComponent("slew-" + targetName, slew);

		} else if (e.getName().equals("inst") || e.getName().equals("focal-plane")) {

			String instName = e.getAttributeValue("name");

			return new XExecutiveComponent("aperture-" + instName, new XAcquisitionConfig(
					IAcquisitionConfig.INSTRUMENT_CHANGE, instName, instName, false));

		} else if (e.getName().equals("focus-control")) {

			String instName = e.getAttributeValue("name");

			return new XExecutiveComponent("focus-control-" + instName, new XFocusControl(instName));
			
		} else if (e.getName().equals("beam-steer")) {
			throw new OcrException("P2mlParser::createSequence:Beam steering is temporarily suspended");

			/*
			 * String strUpperPosn = e.getAttributeValue("upper"); // for now we
			 * only accept CLEAR int upperPosn =
			 * XOpticalSlideConfig.POSITION_CLEAR;
			 * 
			 * String strLowerPosn = e.getAttributeValue("lower"); int lowerPosn
			 * = XOpticalSlideConfig.POSITION_UNKNOWN; if
			 * (strLowerPosn.equals("RedBlue")) lowerPosn =
			 * XOpticalSlideConfig.POSITION_DI_RB; else if
			 * (strLowerPosn.equals("BlueRed")) lowerPosn =
			 * XOpticalSlideConfig.POSITION_DI_BR; else if
			 * (strLowerPosn.equals("Mirror")) lowerPosn =
			 * XOpticalSlideConfig.POSITION_AL_MIRROR; else throw new
			 * OcrException
			 * ("Illegal setting for IO:O lower mirror: "+strLowerPosn);
			 * 
			 * XOpticalSlideConfig upper = new
			 * XOpticalSlideConfig(XOpticalSlideConfig.SLIDE_UPPER, upperPosn);
			 * XOpticalSlideConfig lower = new
			 * XOpticalSlideConfig(XOpticalSlideConfig.SLIDE_LOWER, lowerPosn);
			 * 
			 * return new
			 * XExecutiveComponent("beam-"+strUpperPosn+"-"+strLowerPosn, new
			 * XBeamSteeringConfig(upper, lower));
			 */

		} else if (e.getName().equals("autoguide")) {

			String strAgMode = e.getAttributeValue("mode");

			int agMode = IAutoguiderConfig.OFF;
			if (strAgMode.equalsIgnoreCase("on"))
				agMode = IAutoguiderConfig.ON;
			else if (strAgMode.equalsIgnoreCase("optional"))
				agMode = IAutoguiderConfig.ON_IF_AVAILABLE;
			else
				agMode = IAutoguiderConfig.OFF;

			return new XExecutiveComponent("auto-" + strAgMode, new XAutoguiderConfig(agMode, "CASSEGRAIN"));

		} else if (e.getName().equals("configure")) {

			String configName = e.getAttributeValue("config");

			// lookup proper config here.
			Map configNameMap = prog.getConfigNameMap();
			if (!configNameMap.containsKey(configName))
				throw new OcrException("P2mlParser::createSequence: No config named: " + configName
						+ " can be matched in program");

			IInstrumentConfig config = (IInstrumentConfig) configNameMap.get(configName);

			return new XExecutiveComponent("config-" + configName, new XInstrumentConfigSelector(config));

		} else if (e.getName().equals("multrun")) {

			// multrun repeat=5 exposure =

			String id = e.getAttributeValue("id");

			String strRepeat = e.getAttributeValue("repeat");
			int repeats = Integer.parseInt(strRepeat);

			double exposure = (double) getPeriod(e, "exposure");

			boolean std = (e.getAttribute("standard") != null);

			XMultipleExposure xmult = new XMultipleExposure(exposure, repeats, std);
			xmult.setName(id);

			return new XExecutiveComponent(id, xmult);

			// use target mode
		} else if (e.getName().equals("acquire")) {

			String use = e.getAttributeValue("use");
			String target = e.getAttributeValue("target");

			String strMode = e.getAttributeValue("mode");

			int mode = 0;
			if (strMode.equalsIgnoreCase("wcs_fit"))
				mode = IAcquisitionConfig.WCS_FIT;
			else if (strMode.equalsIgnoreCase("brightest"))
				mode = IAcquisitionConfig.BRIGHTEST;

			return new XExecutiveComponent("acquire-" + use, new XAcquisitionConfig(mode, target, use, false));

		} else if (e.getName().equals("arc")) {

			String lampName = e.getAttributeValue("source");

			XArc arc = new XArc();
			arc.setLamp(new XLampDef(lampName));

			return new XExecutiveComponent("arc-" + lampName, arc);

		} else if (e.getName().equals("lamp")) {

			String lampName = e.getAttributeValue("source");

			XLampFlat lamp = new XLampFlat();
			lamp.setLamp(new XLampDef(lampName));

			return new XExecutiveComponent("lampflat-" + lampName, lamp);

		} else if (e.getName().equals("offset")) {

			// note units are fixed for both offsets !
			double dra = getAngle(e, "ra-offset");
			double ddec = getAngle(e, "dec-offset");

			String strMode = e.getAttributeValue("mode");
			boolean relative = strMode.equalsIgnoreCase("relative");

			return new XExecutiveComponent("offby", new XPositionOffset(relative, dra, ddec));

		} else if (e.getName().equals("defocus")) {

			double defocus = getDistance(e, "offset");

			String strMode = e.getAttributeValue("mode");
			boolean relative = strMode.equalsIgnoreCase("relative");

			return new XExecutiveComponent("defocus-" + defocus, new XFocusOffset(relative, defocus));

		} else if (e.getName().equals("select")) {
			
			// <select instrument="IO:O", alt="true"/>
			
			String strInstr = e.getAttributeValue("instrument");
			
			boolean alt = false;
			String strAlt = e.getAttributeValue("alt", "false");
			if (strAlt.equalsIgnoreCase("true"))
				alt = true;
			
			//XSetInst select = new XSetInst(strInstr, alt);
			
			//return new XExecutiveComponent("select-"+strInstr, select);
			return null;
			
			
		}
		return null;
	}

}
