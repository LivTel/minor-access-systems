import java.io.*;
import java.net.*;
import javax.net.ssl.*;

import dev.lt.RATCamConfig;

import java.util.*;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.text.*;
import java.applet.*;

import ngat.oss.model.IAccessModel;
import ngat.oss.model.IHistoryModel;
import ngat.oss.model.IPhase2Model;
import ngat.phase2.*;
import ngat.util.logging.*;
import ngat.astrometry.ReferenceFrame;
import ngat.message.base.*;
import ngat.net.*;
import ngat.util.*;

public class OfflineRelay extends Thread implements OcrProcessingMonitor {

	/** A date formatter. */
	public static SimpleDateFormat odf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
	public static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm");
	public static SimpleDateFormat fdf = new SimpleDateFormat("yyyyMMdd'T'HHmmss");
	public static final SimpleTimeZone UTC = new SimpleTimeZone(0, "UTC");

	public static final long OCR_REG_ID = 6565656L;

	/** Error code: Indicates an unknown request action type. */
	public static final int UNKNOWN_REQUEST = 710301;

	/** Error code: Indicates a problem parsing the request. */
	public static final int PARSE_ERROR = 710302;

	/** Error code: Indicates unable to connect to Proxy Server. */
	public static final int PROXY_CONNECT_ERROR = 710303;

	/** Error code: Indicates an IO error between OCR and Proxy Server. */
	public static final int PROXY_IO_ERROR = 710304;

	/**
	 * Error code: Indicates that no response was received from the Proxy
	 * Server.
	 */
	public static final int PROXY_RESPONSE_ERROR = 710305;

	/**
	 * Error code: Indicates some general exception occurred during the
	 * communications with the Proxy Server.
	 */
	public static final int PROTOCOL_ERROR = 710306;

	/** Default Server port. */
	public static final int DEFAULT_RELAY_PORT = 5555;

	/** Default Logging lvel. */
	public static final int DEFAULT_LOG_LEVEL = 2;

	public static final double MIN_OFFSET = 1.0;// ARCSEC

	/** Relay port. */
	protected int relayPort;

	/** True if connection must be secure. */
	protected boolean secure;

	/** Name of the file to dump XML into. */
	protected String dumpFileName = "request";

	private IPhase2Model phase2;
	private IHistoryModel historyModel;

	// private Map propNameMap; // maps propname to pids
	// private Map groupNameMap; // maps propname/groupname to gids
	// private Map targetNameMap;
	// private Map configNameMap;

	private Map progMap;
	private Map propProgMap; // map propname to programinfo
	private Map progNameMap; // map progname to programinfo

	/** Name for logging. */
	String name;

	PrintStream out = null;

	PrintStream fout = null;

	BufferedReader in = null;

	Logger logger;

	// int progId;

	String rhost;

	List<OcrProcessingListener> listeners;

	PersistentUniqueInteger puid = new PersistentUniqueInteger("/proxy/tmp/%ocr_uid");

	private MailSender mailer;
	
	public OfflineRelay() {
		super("OCR");
		odf.setTimeZone(UTC);
		sdf.setTimeZone(UTC);
		fdf.setTimeZone(UTC);
		// propNameMap = new HashMap();
		// groupNameMap = new HashMap();
		// targetNameMap = new HashMap();
		// configNameMap = new HashMap();
		progMap = new HashMap();
		propProgMap = new HashMap();
		progNameMap = new HashMap();

		listeners = new Vector<OcrProcessingListener>();

		// add an internal logging listener for now later we expect external UI
		// to connect...
		try {
			OcrProcessingListener internalListener = new InternalUpdateListener(new File("/proxy/tmp/ocr.dat"));
			addOcrProcessingListener(internalListener);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void main(String args[]) {

		OfflineRelay relay = new OfflineRelay();
		try {
			relay.configure(new File(args[0]));
			relay.sendMail("OCR startup and configuration completed successfully");			
		} catch (Exception e) {
			System.err.println("USAGE: java OfflineRelay <config-file> : " + e);
			try {
			relay.sendMail("OCR startup and configuration failed: "+e);
			} catch (Exception e2) {
				System.err.println("OCR failed to send mail alert: "+e2);
			}
			System.exit(1);
		}

		relay.start();

	}

	public void addOcrProcessingListener(OcrProcessingListener ol) throws RemoteException {

		if (listeners.contains(ol))
			return;

		listeners.add(ol);

	}

	public void removeOcrProcessingListener(OcrProcessingListener ol) throws RemoteException {

		if (!listeners.contains(ol))
			return;

		listeners.remove(ol);

	}

	public void notifyListenersOcrRequestReceived(long time, long id, int type) {

		for (int il = 0; il < listeners.size(); il++) {

			try {
				OcrProcessingListener l = listeners.get(il);
				l.ocrRequestReceived(time, id, type);
			} catch (Exception e) {
				e.printStackTrace();
			}

		}

	}

	public void notifyListenersOcrRequestCompleted(long time, long id) {

		for (int il = 0; il < listeners.size(); il++) {

			try {
				OcrProcessingListener l = listeners.get(il);
				l.ocrRequestCompleted(time, id);
			} catch (Exception e) {
				e.printStackTrace();
			}

		}

	}

	public void notifyListenersOcrRequestFailed(long time, long id, int code, String message) {

		for (int il = 0; il < listeners.size(); il++) {

			try {
				OcrProcessingListener l = listeners.get(il);
				l.ocrRequestFailed(time, id, code, message);
			} catch (Exception e) {
				e.printStackTrace();
			}

		}

	}
	
	public void sendMail(String message) throws Exception {
		
		mailer.send(message);
		
	}

	/** Configure from file. */
	public void configure(File file) throws Exception {
		ConfigurationProperties props = new ConfigurationProperties();
		props.load(new FileInputStream(file));
		configure(props);
	}

	/** Configure from Properties. */
	public void configure(ConfigurationProperties props) throws Exception {

		relayPort = props.getIntValue("relay.port", DEFAULT_RELAY_PORT);

		secure = (props.getProperty("secure", "true").equals("true"));

		int level = props.getIntValue("log.level", DEFAULT_LOG_LEVEL);
		logger = LogManager.getLogger("OCR");
		logger.setChannelID("OCR");
		LogHandler console = new ConsoleLogHandler(new BasicLogFormatter(150));
		console.setLogLevel(level);
		logger.setLogLevel(level);
		logger.addHandler(console);

		
		// If we cant have mail - keep going...
				String smtpHost = props.getProperty("smtp.host", "localhost");
				try {
					mailer= new MailSender(smtpHost);
					String mailToAddr = props.getProperty("mail.to", "snf@astro.livjm.ac.uk");
					mailer.setMailToAddr(mailToAddr);
					String mailFromAddr = props.getProperty("mail.from", "ocr@astro.livjm.ac.uk");
					mailer.setMailFromAddr(mailFromAddr);
					String mailCcAddr = props.getProperty("mail.cc", "amn@astro.livjm.ac.uk");
					mailer.setMailCcAddr(mailCcAddr);

					mailer.setMailSubj("OCR-INFO");

				} catch (Exception e) {
					e.printStackTrace();
				}
		

		rhost = props.getProperty("rhost", "localhost");

		IPhase2Model phase2 = (IPhase2Model) Naming.lookup("rmi://" + rhost + "/Phase2Model");
		IHistoryModel historyModel = (IHistoryModel) Naming.lookup("rmi://" + rhost + "/HistoryModel");

		Iterator keys = props.keySet().iterator();
		while (keys.hasNext()) {
			String key = (String) keys.next();
			// only interested in program.id keys
			if (key.startsWith("program.id")) {

				int progId = props.getIntValue(key);
				System.err.println("OCR::configure(): Configuring program: " + progId);

				ProgramInfo prog = new ProgramInfo();
				progMap.put(new Integer(progId), prog);

				IProgram program = getPhase2().getProgramme(progId);
				progNameMap.put(program.getName(), program);

				prog.setId(progId);
				Map propNameMap = prog.getPropNameMap();
				Map groupNameMap = prog.getGroupNameMap();
				Map configNameMap = prog.getConfigNameMap();
				Map targetNameMap = prog.getTargetNameMap();

				// build the proposal and group name mappings
				List plist = getPhase2().listProposalsOfProgramme(progId);
				Iterator iprop = plist.iterator();
				while (iprop.hasNext()) {

					IProposal proposal = (IProposal) iprop.next();
					propNameMap.put(proposal.getName(), new Long(proposal.getID()));
					System.err.println("OCR::configure(): Adding proposal: " + proposal.getName() + "["
							+ proposal.getID() + "]");

					long pid = proposal.getID();
					// map prop name to program info
					propProgMap.put(proposal.getName(), prog);

					List glist = getPhase2().listGroups(proposal.getID(), false);
					Iterator igroup = glist.iterator();
					while (igroup.hasNext()) {

						IGroup group = (IGroup) igroup.next();
						groupNameMap.put(proposal.getName() + "/" + group.getName(), new Long(group.getID()));
						System.err.println("OCR::configure(): Adding group: " + proposal.getName() + "/"
								+ group.getName() + "[" + group.getID() + "]");
					}

				}

				List cfglist = getPhase2().listInstrumentConfigs(progId);
				Iterator icfg = cfglist.iterator();
				while (icfg.hasNext()) {
					IInstrumentConfig cfg = (IInstrumentConfig) icfg.next();
					configNameMap.put(cfg.getName(), cfg);
					System.err.println("OCR::configure(): Adding config: " + cfg.getName() + "[" + cfg.getID() + "]");
				}

				List tgtlist = getPhase2().listTargets(progId);
				Iterator itgt = tgtlist.iterator();
				while (itgt.hasNext()) {
					ITarget target = (ITarget) itgt.next();
					targetNameMap.put(target.getName(), target);
					System.err.println("OCR::configure(): Adding target: " + target.getName() + "[" + target.getID()
							+ "]");
					if (target instanceof XSlaNamedPlanetTarget) {
						XSlaNamedPlanetTarget planet = (XSlaNamedPlanetTarget) target;
						System.err.println("OCR::configure(): Cat target: " + planet.getName() + " INDX= "
								+ planet.getIndex());
					}
				}
			}
		}

	}

	public void run() {
		ServerSocket server = null;
		Socket socket = null;
		while (true) {

			boolean ok = true;

			if (server == null) {

				if (secure) {
					// SSL server sockets.
					SSLServerSocketFactory ssf = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
					try {
						server = ssf.createServerSocket(relayPort);
						((SSLServerSocket) server).setNeedClientAuth(true);
						logger.log(1, "Started Secure XML_Server Bound to port: " + relayPort);
					} catch (IOException iox) {
						logger.log(1, "Error starting Secure XML-server: " + iox);
						iox.printStackTrace();
						return;
					}
				} else {
					// Ordinary Sockets.
					try {
						server = new ServerSocket(relayPort);
						logger.log(1, "Started XML_Server Bound to port: " + relayPort);
					} catch (IOException iox) {
						logger.log(1, "Error starting XML-server: " + iox);
						iox.printStackTrace();
						System.exit(1);
					}
				}
			}

			// Socket connection.// let the socket timeout every minute so we can monitor its preformance.
			
			try {
				
				// log the server starting in a handy file
				File pingFile = new File("/proxy/tmp/ocr.ping");
				PrintWriter pout = new PrintWriter(pingFile);
				pout.println(sdf.format(new Date())+" "+(System.currentTimeMillis()/1000)+ " OKAY");
				pout.flush();
				pout.close();
				
				// let the socket timeout every minute so we can monitor its preformance.
				server.setSoTimeout(600000); 
				socket = server.accept();
				logger.log(1,
						"Opened connection to: " + socket.getInetAddress().getHostName() + " : " + socket.getPort());
				socket.setSoLinger(false, 0);
			} catch (SocketTimeoutException sx) {
				
				logger.log(1, "Server timeout after 1 minute of inactivity, will restart");
				continue;
				
			} catch (IOException e) {
				logger.log(1, "Error opening connection: Sleep10: " + e);
				e.printStackTrace();

				ok = false;
				try {
					Thread.sleep(10000L);
				} catch (InterruptedException ix) {
				}

				continue;
			}

			File dumpFile = new File("/tmp/" + dumpFileName + "_" + fdf.format(new Date()) + ".xml");
			// Open i/o streams.
			if (ok) {
				try {

					socket.setSoTimeout(120000);

					in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
					logger.log(3, "Opened input stream");

					out = new PrintStream(socket.getOutputStream());
					logger.log(3, "Opened output stream");

					fout = new PrintStream(new FileOutputStream(dumpFile));
					logger.log(3, "Opened temp dump file");

				} catch (IOException e) {
					logger.log(1, "Error opening connection: " + e);
					e.printStackTrace();
					ok = false;
					continue;
				}
			}

			// Keep reading lines.
			boolean p2mlv2 = false;
			boolean done = false;
			while (!done) {
				try {
					String text = in.readLine();
					if (text == null) {
						logger.log(1, "Connection broken during read ? - line was null");
						break;
					}
					System.err.println("-[" + text + "]");
					fout.println(text);

					if (text.trim().equals("</p2ml:document>") || text.trim().equals("</p2ml>")) {
						logger.log(1, "OK Request accepted - Wrote to local file...");
						fout.close();
						done = true;
					}

					if (text.contains("version = \"2.0\""))
						p2mlv2 = true;

				} catch (IOException e) {
					logger.log(1, "Error during read: " + e);
					ok = false;
					break;
				}
			}

			// at this stage we have the document and may or may not want to
			// process it..

			// -1 is used if we dont manage to get a UID correctly eg the lock
			// file is trashed
			int uid = -1;
			try {
				uid = puid.increment();
			} catch (Exception e) {
				e.printStackTrace();
			}
			notifyListenersOcrRequestReceived(System.currentTimeMillis(), uid, 1);

			sendResponseMasterHeader();

			if (p2mlv2) {
				// ======================================
				//  V 2  uses P2mlParser
				// ======================================
				try {
					logger.log(1, "Read request and preparing to parse using V2.0: " + dumpFile.getName());
					P2mlParser parser = new P2mlParser(this);
					parser.processDocument(dumpFile);
					notifyListenersOcrRequestCompleted(System.currentTimeMillis(), uid);
				} catch (Exception e) {
					e.printStackTrace();
					notifyListenersOcrRequestFailed(System.currentTimeMillis(), uid, PARSE_ERROR, e.getMessage());
					sendError(PARSE_ERROR, "The following fatal error occurred during V 2.0 parsing: " + e);
				}
			} else {
				// ======================================
				//  V 1.0  uses XmlParser
				// ======================================
				// Parse it.
				if (ok) {
					// sendResponseMasterHeader();
					sendAck(20000, "Starting parser...");
					logger.log(1, "Starting parser...");
					XMLParser parser = new XMLParser();
					try {
						// parser.parseURI("file://"+dumpFile);
						logger.log(1, "Read request and preparing to parse using V1.0: " + dumpFile.getName());
						parser.parseFile(dumpFile);

						logger.log(1, "After parsing: Check for errors: hasError=" + parser.hasError());

						if (parser.hasError()) {
							Exception px = parser.getFatalEx();
							notifyListenersOcrRequestFailed(System.currentTimeMillis(), uid, 1, "");
							sendError(PARSE_ERROR, "An error was found during parsing: " + px);
							continue;
						}
					} catch (Exception e) {
						e.printStackTrace();
						logger.log(1, "After parsing: An exception was caught, sending failure reply");
						notifyListenersOcrRequestFailed(System.currentTimeMillis(), uid, 1, e.getMessage());
						sendError(PARSE_ERROR, "The following fatal error occurred during parsing: " + e);
						continue;
					}

					String action = parser.getAction();

					if (action == null) {
						notifyListenersOcrRequestFailed(System.currentTimeMillis(), uid, 1, "No-action");
						sendError(UNKNOWN_REQUEST, "Null command action from parser");
						continue;
					}

					try {
						if (action.equals("add-group")) {
							doAddGroupAction(parser, false);
						} else if (action.equals("remove-group")) {
							doRemoveGroupAction(parser);
						} else if (action.equals("add-inst-config")) {
							doAddInstConfigAction(parser, false);
						} else if (action.equals("add-source")) {
							doAddSourceAction(parser, false);
						} else if (action.equals("replace-inst-config")) {
							doAddInstConfigAction(parser, true);
						} else if (action.equals("replace-source")) {
							doAddSourceAction(parser, true);
						} else if (action.equals("replace-group")) {
							doAddGroupAction(parser, true);
						} else if (action.equals("check-group")) {
							doCheckGroupAction(parser);
						} else {
							logger.log(1, "No such command action: " + action);
							notifyListenersOcrRequestFailed(System.currentTimeMillis(), uid, 1, "Unknown action: "
									+ action);
							sendError(UNKNOWN_REQUEST, "No such command action [" + action + "]");
						}
						notifyListenersOcrRequestCompleted(System.currentTimeMillis(), uid);
					} catch (Exception e) {
						e.printStackTrace();
						notifyListenersOcrRequestFailed(System.currentTimeMillis(), uid, 1, e.getMessage());
						sendError(PARSE_ERROR, "The following fatal error occurred during parsing: " + e);

					}
				}
			} // v1 or v2

			try {
				String oldDumpFileName = dumpFile.getName();
				if (dumpFile.delete()) {
					logger.log(1, "Deleted temporary file: " + oldDumpFileName);
				} else {
					logger.log(1, "Failed to delete temporary file: " + oldDumpFileName);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

		}
	}

	public void doAddGroupAction(XMLParser parser, boolean replace) throws Exception {

		Group group = parser.getGroup();

		if (group == null)
			throw new OcrException("Add/ReplaceGroup::No group in XML request");

		Map srcMap = parser.getSrcMap();
		Map icMap = parser.getIcMap();

		System.err.println("Parser srcmap: " + srcMap);
		System.err.println("Parser icmap: " + icMap);

		String proposalPathName = parser.getProposalPathName();
		String propName = findProposalName(proposalPathName);
		if (propName == null)
			throw new OcrException("Add/ReplaceGroup::No proposal name specified");

		ProgramInfo prog = findProgram(propName);
		Map propNameMap = prog.getPropNameMap();

		if (!propNameMap.containsKey(propName))
			throw new OcrException("Add/ReplaceGroup::Cannot match proposal name: " + propName);

		long pid = ((Long) propNameMap.get(propName)).longValue();
		// long pid = findProposal(propName);

		if (replace) {

			String groupName = propName + "/" + group.getName();
			Map groupNameMap = prog.getGroupNameMap();
			if (!groupNameMap.containsKey(groupName))
				throw new OcrException("ReplaceGroup::Cannot match group name: " + groupName);

			long gid = ((Long) groupNameMap.get(groupName)).longValue();
			// long gid = findGroup(propName, group.getName());

			getPhase2().deleteObservationSequenceOfGroup(gid);
			System.err.println("ReplaceGroup::Deleted group observation sequence for: " + gid);
			getPhase2().deleteGroup(gid);
			System.err.println("ReplaceGroup::Deleted group: " + gid);

			XGroup xgroup = createGroup(group);
			long newgid = getPhase2().addGroup(pid, xgroup);
			System.err.println("ReplaceGroup::Added new group: " + newgid);

			ISequenceComponent sequence = createSequence(prog, group, srcMap, icMap);

			// update sequence
			getPhase2().addObservationSequence(newgid, sequence);
			System.err.println("ReplaceGroup::Set sequence for group: " + newgid);

			groupNameMap.remove(groupName);
			groupNameMap.put(groupName, new Long(newgid));

			sendResponse("done", "ReplaceGroup: Completed successfully with new id: " + newgid);

		} else {

			// Create an XGroup from the group info:
			XGroup xgroup = createGroup(group);

			String groupName = propName + "/" + group.getName();
			// is this one in the table already ?
			Map groupNameMap = prog.getGroupNameMap();
			if (groupNameMap.containsKey(groupName))
				throw new OcrException("AddGroup::Group named " + group.getName() + " is already in table");

			long gid = getPhase2().addGroup(pid, xgroup);
			ISequenceComponent sequence = createSequence(prog, group, srcMap, icMap);

			// update sequence
			try {
				getPhase2().addObservationSequence(gid, sequence);
			} catch (Exception e) {

				// try to wipe the group we just added
				try {				// ======================================
					//  V 2  uses P2mlParser
					// ======================================
					getPhase2().deleteGroup(gid);
					throw new OcrException("AddGroup::Unable to set group sequence for " + group.getName()
							+ " , group was added but has been removed again due to: " + e);
				} catch (Exception e2) {
					throw new OcrException("AddGroup::Unable to set group sequence for " + group.getName()
							+ " , due to: " + e + ", group was added but could not then be removed due to: " + e2);
				}

			}
			// storegroup(
			groupNameMap.put(groupName, new Long(gid));
			sendResponse("done", "AddGroup: Completed successfully with id: " + gid);

		}

	}

	public void doRemoveGroupAction(XMLParser parser) throws Exception {
		
		logger.log(1, "Preparing to remove group...");
		
		String proposalPathName = parser.getProposalPathName();
		String propName = findProposalName(proposalPathName);
		if (propName == null)
			throw new OcrException("Add/ReplaceGroup::No proposal name specified");

		ProgramInfo prog = findProgram(propName);
		Map propNameMap = prog.getPropNameMap();

		String groupPathName = parser.getProposalPathName() + "/" + parser.getGroupPathName();
		String groupName = findGroupName(groupPathName);
		System.err.println("Searching for group in name map: " + groupName);
		Map groupNameMap = prog.getGroupNameMap();

	
		if (groupName == null)
			throw new OcrException("RemoveGroup::No group name specified");
		if (!groupNameMap.containsKey(groupName))
			throw new OcrException("RemoveGroup::Cannot match group name: " + groupName);

		long gid = ((Long) groupNameMap.get(groupName)).longValue();
		logger.log(1, "Group has id: "+gid+" Calling: phase2.deleteGroup("+gid+")");
		
		getPhase2().deleteGroup(gid);
		logger.log(1, "Removed group from Phase2 DB");
		
		groupNameMap.remove(groupName);
		logger.log(1, "Removed group from Name map");
		
		sendResponse("done", "RemoveGroup: Completed successfully");
	}

	public void doAddInstConfigAction(XMLParser parser, boolean replace) throws Exception {

		InstrumentConfig ic = parser.getInstConfig();

		if (ic == null)
			throw new OcrException("AddConfig::No config in XML request");
		String proposalPathName = parser.getProposalPathName();
		String propName = findProposalName(proposalPathName);
		if (propName == null)
			throw new OcrException("AddConfig::No proposal name specified");

		ProgramInfo prog = findProgram(propName);
		Map configNameMap = prog.getConfigNameMap();
		int progId = prog.getId();
		if (replace) {

			if (!configNameMap.containsKey(ic.getName()))
				throw new OcrException("ReplaceConfig::Config: " + ic.getName() + " does not exist");

			XInstrumentConfig oldconfig = (XInstrumentConfig) configNameMap.get(ic.getName());
			XInstrumentConfig newconfig = translateConfig(ic);
			newconfig.setID(oldconfig.getID());
			getPhase2().updateInstrumentConfig(newconfig, 666); // fake key for
			// now
			configNameMap.put(ic.getName(), newconfig);
			sendResponse("done", "ReplaceConfig: Completed successfully");

		} else {

			if (configNameMap.containsKey(ic.getName()))
				throw new OcrException("AddConfig::Config: " + ic.getName() + " already exists");

			XInstrumentConfig config = (XInstrumentConfig) translateConfig(ic);
			long cid = getPhase2().addInstrumentConfig(progId, config);
			config.setID(cid);
			System.err.println("Added config: " + config);
			configNameMap.put(ic.getName(), config);
			System.err.println("Cached config locally");
			sendResponse("done", "AddConfig: Completed successfully with id: " + cid);
		}

	}

	private XInstrumentConfig translateConfig(InstrumentConfig ic) throws Exception {
		XInstrumentConfig xconfig = null;

		Detector d = ic.getDetector(0);
		XDetectorConfig xdet = new XDetectorConfig();
		xdet.setXBin(d.getXBin());
		xdet.setYBin(d.getYBin());

		if (ic instanceof RATCamConfig) {
			XImagerInstrumentConfig xrat = new XImagerInstrumentConfig(ic.getName());
			String lf = ((RATCamConfig) ic).getLowerFilterWheel();
			String uf = ((RATCamConfig) ic).getUpperFilterWheel();
			XFilterSpec xfs = new XFilterSpec();
			xfs.addFilter(new XFilterDef(lf));
			xfs.addFilter(new XFilterDef(uf));
			xrat.setFilterSpec(xfs);
			xrat.setInstrumentName("RATCAM");
			xconfig = xrat;
		} else if (ic instanceof IRCamConfig) {
			XImagerInstrumentConfig xir = new XImagerInstrumentConfig(ic.getName());
			String f = ((IRCamConfig) ic).getFilterWheel();
			XFilterSpec xfs = new XFilterSpec();
			xfs.addFilter(new XFilterDef(f));
			xir.setFilterSpec(xfs);
			xir.setInstrumentName("SUPIRCAM");
			xconfig = xir;
		} else if (ic instanceof RISEConfig) {
			XImagerInstrumentConfig xrise = new XImagerInstrumentConfig(ic.getName());
			xrise.setInstrumentName("RISE");
			xconfig = xrise;
		} else if (ic instanceof FrodoSpecConfig) {
			XDualBeamSpectrographInstrumentConfig xfrodo = new XDualBeamSpectrographInstrumentConfig(ic.getName());
			int arm = ((FrodoSpecConfig) ic).getArm();
			switch (arm) {
			case FrodoSpecConfig.RED_ARM:
				xfrodo.setInstrumentName("FRODO_RED");
				break;
			case FrodoSpecConfig.BLUE_ARM:
				xfrodo.setInstrumentName("FRODO_BLUE");
				break;
			}
			int res = ((FrodoSpecConfig) ic).getResolution();
			switch (res) {
			case FrodoSpecConfig.RESOLUTION_LOW:
				xfrodo.setResolution(XDualBeamSpectrographInstrumentConfig.LOW_RESOLUTION);
				break;
			case FrodoSpecConfig.RESOLUTION_HIGH:
				xfrodo.setResolution(XDualBeamSpectrographInstrumentConfig.HIGH_RESOLUTION);
				break;
			}
			xconfig = xfrodo;
		} else if (ic instanceof THORConfig) {
			XTipTiltImagerInstrumentConfig xtip = new XTipTiltImagerInstrumentConfig(ic.getName());
			xtip.setInstrumentName("IO:THOR");
			xtip.setGain(((THORConfig) ic).getEmGain());
			xconfig = xtip;
		} else if (ic instanceof OConfig) {
			XImagerInstrumentConfig xo = new XImagerInstrumentConfig(ic.getName());
			;
			xo.setInstrumentName("IO:O");
			String f1 = ((OConfig) ic).getFilterName(1);
			String f2 = ((OConfig) ic).getFilterName(2);
			String f3 = ((OConfig) ic).getFilterName(3);
			XFilterSpec xfs = new XFilterSpec();
			xfs.addFilter(new XFilterDef(f1));
			xfs.addFilter(new XFilterDef(f2));
			xfs.addFilter(new XFilterDef(f3));
			xo.setFilterSpec(xfs);
			xconfig = xo;
		} else
			throw new OcrException("AddConfig::Unknown config class: " + ic.getClass().getName()
					+ " unable to translate");

		xconfig.setDetectorConfig(xdet);
		return xconfig;

	}

	public void doAddSourceAction(XMLParser parser, boolean replace) throws Exception {

		Source src = parser.getSource();

		if (src == null)
			throw new OcrException("AddSource::No source in XML request");
		String proposalPathName = parser.getProposalPathName();
		String propName = findProposalName(proposalPathName);
		if (propName == null)
			throw new OcrException("AddSource::No proposal name specified");

		ProgramInfo prog = findProgram(propName);
		Map targetNameMap = prog.getTargetNameMap();
		int progId = prog.getId();

		if (replace) {

			if (!targetNameMap.containsKey(src.getName()))
				throw new OcrException("ReplaceSource::Source: " + src.getName() + " does not exist");

			XTarget oldtarget = (XTarget) targetNameMap.get(src.getName());
			XTarget newtarget = translateTarget(src);
			newtarget.setID(oldtarget.getID());
			getPhase2().updateTarget(newtarget, 666); // fake key for now
			targetNameMap.put(src.getName(), newtarget);
			sendResponse("done", "ReplaceSource: Completed successfully");

		} else {

			if (targetNameMap.containsKey(src.getName()))
				throw new OcrException("AddSource::Source: " + src.getName() + " already exists");

			XTarget target = (XTarget) translateTarget(src);
			long tid = getPhase2().addTarget(progId, target);
			target.setID(tid);
			System.err.println("Added target: " + target);
			targetNameMap.put(src.getName(), target);
			System.err.println("Cached target locally");
			sendResponse("done", "AddSource: Completed successfully with id: " + tid);

		}

	}

	/**
	 * Translate from old to new source.
	 * 
	 * @param src
	 * @return
	 * @throws Exception
	 */
	private XTarget translateTarget(Source src) throws Exception {
		XTarget xsrc = null;
		if (src instanceof ExtraSolarSource) {
			XExtraSolarTarget xstar = new XExtraSolarTarget(src.getName());
			xstar.setRa(((ExtraSolarSource) src).getRA());
			xstar.setDec(((ExtraSolarSource) src).getDec());
			xstar.setPmRA(((ExtraSolarSource) src).getPmRA());
			xstar.setPmDec(((ExtraSolarSource) src).getPmDec());
			xstar.setParallax(((ExtraSolarSource) src).getParallax());
			xstar.setRadialVelocity(((ExtraSolarSource) src).getRadialVelocity());
			char equinoxLetter = ((ExtraSolarSource) src).getEquinoxLetter();
			if (equinoxLetter == Source.JULIAN)
				xstar.setFrame(ReferenceFrame.FK5);
			else if (equinoxLetter == Source.BESSELIAN)
				xstar.setFrame(ReferenceFrame.FK4);
			xstar.setEpoch(src.getEpoch());
			xsrc = xstar;
		} else if (src instanceof CatalogSource) {
			XSlaNamedPlanetTarget xplanet = new XSlaNamedPlanetTarget(src.getName());
			xplanet.setIndex(((CatalogSource) src).getCatalogId());
			xsrc = xplanet;
		} else if (src instanceof EphemerisSource) {
			// throw new
			// Exception("AddSource::Ephemeris targets are disabled due to parsing problem");
			XEphemerisTarget xephem = new XEphemerisTarget(src.getName());
			Vector track = ((EphemerisSource) src).getEphemeris();
			Iterator inode = track.iterator();
			while (inode.hasNext()) {
				EphemerisSource.Coordinate node = (EphemerisSource.Coordinate) inode.next();

				XEphemerisTrackNode xnode = new XEphemerisTrackNode(node.getTime(), node.getRA(), node.getDec(),
						node.getRADot(), node.getDecDot());
				System.err.println("TranslateTarget::Adding node: " + xnode);
				xephem.addTrackNode(xnode);
			}
			xsrc = xephem;
		} else
			throw new OcrException("AddSource::Unknown source class: " + src.getClass().getName()
					+ " unable to translate");

		return xsrc;
	}

	public void doCheckGroupAction(XMLParser parser) throws Exception {
		String proposalPathName = parser.getProposalPathName();
		String propName = findProposalName(proposalPathName);
		if (propName == null)
			throw new OcrException("CheckGroup::No proposal name specified");

		ProgramInfo prog = findProgram(propName);
		Map groupNameMap = prog.getGroupNameMap();

		String groupPathName = parser.getProposalPathName() + "/" + parser.getGroupPathName();
		String groupName = findGroupName(groupPathName);
		if (groupName == null)
			throw new OcrException("CheckGroup::No group name specified");

		if (!groupNameMap.containsKey(groupName))
			throw new OcrException("CheckGroup::Cannot match group name: " + groupName);

		long gid = ((Long) groupNameMap.get(groupName)).longValue();

		sendResponseHeader("done");
		sendResponsePart("<history>");

		List history = getHistory().listHistoryItems(gid);
		Iterator it = history.iterator();
		while (it.hasNext()) {

			IHistoryItem hist = (IHistoryItem) it.next();

			int ts = (int) ((hist.getCompletionTime() - hist.getScheduledTime()) / 1000);
			int m = ts / 60;
			int s = ts - 60 * m;
			String message = "";

			int status = hist.getCompletionStatus();
			switch (status) {
			case IHistoryItem.EXECUTION_SUCCESSFUL:
				message = "Completed in " + m + "M " + s + "S";

				break;
			case IHistoryItem.EXECUTION_FAILED:

				message = "Failed after " + m + "M " + s + "S due to [" + hist.getErrorCode() + "] "
						+ hist.getErrorMessage();
				break;
			default:
				message = "Unknown status code: " + status;
				break;
			}
			sendResponsePart("       <history-event>" + "\n          <date>"
					+ sdf.format(new Date(hist.getCompletionTime())) + "</date>" + "\n          <done>"
					+ (hist.getCompletionStatus() == IHistoryItem.EXECUTION_SUCCESSFUL) + "</done>"
					+ "\n          <detail>" + message + "</detail>" + "\n       </history-event>");

		}

		sendResponsePart("</history>");
		sendResponseTail();
		sendResponseMasterTail();

	}

	protected void send(String text) {
		// logger.log(1,text);
		out.println(text);
	}

	protected void sclose() {
		logger.log(1, "Close socket");
		// out.close();
	}

	/** Send the response Master header. */
	protected void sendResponseMasterHeader() {

		send("<p2ml:document>");
	}

	/** Send the response header. */
	protected void sendResponseHeader(String name) {

		send("<p2ml>" + "\n  <response>" + "\n    <data name = \"" + name + "\">" + "\n      <value>");
	}

	/** Send the response tail. */
	protected void sendResponseTail() {

		send("     </value>" + "\n    </data>" + "\n  </response>" + "\n</p2ml>");
	}

	/** Send the response Master tail. */
	protected void sendResponseMasterTail() {

		send("</p2ml:document>\n");
		// out.flush();
		sclose();
	}

	/** Send the response component. */
	protected void sendResponsePart(String text) {

		send(text);
	}

	/**
	 * Send the response - this could encapsulate several complex datatypes -
	 * now just a string.
	 */
	protected void sendResponse(String name, String value) {
		send("<p2ml>" + "\n  <response>" + "\n    <data name = \"" + name + "\">" + "\n      <value> " + value
				+ " </value>" + "\n    </data>" + "\n  </response>" + "\n </p2ml>" + "\n</p2ml:document>\n");
		sclose();
	}

	/** Send an error message. */
	protected void sendError(int code, String message) {
		send("<p2ml>" + "\n  <error> " + "\n    <code> " + code + " </code>" + "\n    <message> " + message
				+ " </message>" + "\n  </error>" + "\n</p2ml>" + "\n</p2ml:document>\n");
		sclose();
	}

	/** Send an ACK onto the client. */
	protected void sendAck(long timeout, String message) {
		send("<p2ml>" + "\n  <acknowledge> " + "\n    <timeout> " + timeout + " </timeout>" + "\n    <message> "
				+ message + " </message>" + "\n  </acknowledge>" + "\n</p2ml>");
	}

	public ProgramInfo findProgramByName(String progName) throws Exception {
		if (progNameMap.containsKey(progName))
			return (ProgramInfo) progNameMap.get(progName);
		throw new Exception("Program not known: " + progName);
	}

	public ProgramInfo findProgram(String propName) throws Exception {
		if (propProgMap.containsKey(propName))
			return (ProgramInfo) propProgMap.get(propName);
		throw new Exception("Program not known for Proposal " + propName);
	}

	public String findProposalName(String proposalPathName) {
		// match the propid to the name supplied
		Path propPath = new Path(proposalPathName);
		String propName = propPath.getProposalByName();
		return propName;
	}

	/**
	 * @return the progMap
	 */
	public Map getProgMap() {
		return progMap;
	}

	/**
	 * @return the propProgMap
	 */
	public Map getPropProgMap() {
		return propProgMap;
	}

	private String findGroupName(String groupPathName) {
		// match the propid to the name supplied
		Path groupPath = new Path(groupPathName);
		String groupName = groupPath.getProposalByName() + "/" + groupPath.getGroupByName();
		return groupName;
	}

	/**
	 * Create an XGroup from a Group.
	 * 
	 * @param group
	 * @return
	 */
	private XGroup createGroup(Group group) {

		XGroup xgroup = new XGroup();
		xgroup.setName(group.getName());

		// Timing
		ITimingConstraint tc = null;
		if (group instanceof MonitorGroup) {
			MonitorGroup mg = (MonitorGroup) group;
			tc = new XMonitorTimingConstraint(mg.getStartDate(), mg.getEndDate(), mg.getPeriod(),
					(long) ((double) mg.getPeriod() * mg.getFloatFraction()));
		} else if (group instanceof FixedGroup) {
			FixedGroup fg = (FixedGroup) group;
			// assume 5 minute window as we cant specifiy in old FGs via XML
			tc = new XFixedTimingConstraint(fg.getFixedTime(), 5 * 60 * 1000L);
		} else if (group instanceof EphemerisGroup) {
			EphemerisGroup eg = (EphemerisGroup) group;
			tc = new XEphemerisTimingConstraint();
			((XEphemerisTimingConstraint) tc).setStart(eg.getStart());
			((XEphemerisTimingConstraint) tc).setEnd(eg.getEnd());
			((XEphemerisTimingConstraint) tc).setCyclePeriod(eg.getPeriod());
			((XEphemerisTimingConstraint) tc).setPhase(eg.getPhase());
			((XEphemerisTimingConstraint) tc).setWindow((long) (eg.getSlopPhase() * (double) eg.getPeriod()));
		} else if (group instanceof RepeatableGroup) {
			RepeatableGroup rg = (RepeatableGroup) group;
			tc = new XMinimumIntervalTimingConstraint();
			((XMinimumIntervalTimingConstraint) tc).setStart(rg.getStartDate());
			((XMinimumIntervalTimingConstraint) tc).setEnd(rg.getEndDate());
			((XMinimumIntervalTimingConstraint) tc).setMinimumInterval(rg.getMinimumInterval());
			((XMinimumIntervalTimingConstraint) tc).setMaximumRepeats(rg.getMaximumRepeats());
		} else {
			long sd = group.getStartingDate();
			long now = System.currentTimeMillis();
			// either now or specified SD, whichever is later
			tc = new XFlexibleTimingConstraint(Math.max(sd, now), group.getExpiryDate());
		}
		xgroup.setTimingConstraint(tc);

		addObservingConstraints(xgroup, group);

		xgroup.setActive(true);

		return xgroup;
	}

	/**
	 * Transfer OCs from Group to XGroup
	 * 
	 * @param xgroup
	 * @param group
	 */
	private void addObservingConstraints(XGroup xgroup, Group group) {
		// work out some observing constraints

		// SEEING
		int osee = group.getMinimumSeeing();
		XSeeingConstraint xsee = new XSeeingConstraint();
		switch (osee) {
		case Group.POOR:
			xsee.setSeeingValue(3.0);
			break;
		case Group.AVERAGE:
			xsee.setSeeingValue(1.3);
			break;
		case Group.EXCELLENT:
			xsee.setSeeingValue(0.8);
			break;
		case Group.CRAP:
			xsee.setSeeingValue(5.0);
		}
		xgroup.addObservingConstraint(xsee);

		// LUNAR ELEVATION
		boolean bright = true;
		int olun = group.getMinimumLunar();
		System.err.println("OLD:Lunar elev: " + olun);
		if (olun == Group.DARK) {
			bright = false;
			System.err.println("Select: dark");
		} else {
			System.err.println("Select: bright");
		}

		// LUNAR DIST
		double mld = group.getMinimumLunarDistance();
		System.err.println("OLD:MLD: " + mld);
		boolean mldle30 = true;
		boolean mldgt30 = false;
		// only add if needed
		if (mld < Math.toDegrees(30.0)) {
			mldle30 = true;
			mldgt30 = false;
		} else {
			mldle30 = false;
			mldgt30 = true;
		}
		// EXTINCTION
		if (group.getPhotometric()) {
			XPhotometricityConstraint xphot = new XPhotometricityConstraint(XPhotometricityConstraint.PHOTOMETRIC, 1.0);
			xgroup.addObservingConstraint(xphot);
		}

		// SOLAR ELEVATION
		int osol = group.getTwilightUsageMode();
		System.err.println("OLD:Twilight mode: " + osol);
		boolean night = false;
		boolean astro = false;
		boolean nautic = false;
		boolean civil = false;
		switch (osol) {
		case Group.SKY_NIGHT:
			night = true;
			System.err.println("Select: night");
			break;
		case Group.SKY_DARK_TWILIGHT:
			astro = true;
			System.err.println("Select: astro");
			break;
		case Group.SKY_BRIGHT_TWILIGHT:
			nautic = true;
			System.err.println("Select: nautic");
			break;
		case Group.SKY_ANY:
			civil = true;
			System.err.println("Select: civil");
			break;
		}

		boolean moon = false;

		if (group.isMoon()) {
			moon = true;
			System.err.println("Select: moon");
		}

		// we now have all the info required...
		int skyb = XSkyBrightnessConstraint.DAYTIME;

		if (moon || civil)
			skyb = XSkyBrightnessConstraint.MAG_10;
		else if (nautic)
			skyb = XSkyBrightnessConstraint.MAG_6;
		else if (astro) {
			if (mldgt30 || bright)
				skyb = XSkyBrightnessConstraint.MAG_4;
			else
				skyb = XSkyBrightnessConstraint.MAG_2;
		} else if (night) {
			if (bright) {
				if (mldle30)
					skyb = XSkyBrightnessConstraint.MAG_1P5;
				else
					skyb = XSkyBrightnessConstraint.MAG_2;
			} else {
				if (mldle30)
					skyb = XSkyBrightnessConstraint.DARK;
				else
					skyb = XSkyBrightnessConstraint.MAG_0P75;
			}
		}

		if (skyb != XSkyBrightnessConstraint.DAYTIME) {
			XSkyBrightnessConstraint xsky = new XSkyBrightnessConstraint();
			xsky.setSkyBrightnessCategory(skyb);
			System.err.println("Setting SKYB constraint to: " + skyb);
			xgroup.addObservingConstraint(xsky);
		} else {
			System.err.println("Not setting any SKYB constraint");
		}
	}

	private ISequenceComponent createSequence(ProgramInfo prog, Group group, Map srcMap, Map icMap) throws Exception {

		XIteratorComponent root = new XIteratorComponent("Root", new XIteratorRepeatCountCondition(1));

		String lastSourceName = null;
		String lastConfigName = null;
		String lastInstrument = null;
		double lastRaOffset = 0.0;
		double lastDecOffset = 0.0;

		Map targetNameMap = prog.getTargetNameMap();
		Map configNameMap = prog.getConfigNameMap();

		// check for frodo obs
		int nred = 0;
		int nblue = 0;
		int nnf = 0;
		Iterator iobsa = group.listAllObservations();
		while (iobsa.hasNext()) {
			Observation obs = (Observation) iobsa.next();
			String cfgName = (String) icMap.get(obs);
			IInstrumentConfig config = (IInstrumentConfig) configNameMap.get(cfgName);
			if (config == null)
				throw new OcrException("createSequence: No config named: " + cfgName + " can be matched in program");
			String instName = config.getInstrumentName().toUpperCase();
			if (instName.equals("FRODO_RED"))
				nred++;
			else if (instName.equals("FRODO_BLUE"))
				nblue++;
			else
				nnf++;

			String srcName = (String) srcMap.get(obs);

		}

		System.err.println("Found: " + nred + " frodo reds and " + nblue + " frodo blue configs and " + nnf
				+ " non frodos");

		if (nred != 0 || nblue != 0) {

			// create a branch area with the frodo stuff...

			throw new OcrException("createSequence: Not allowing " + nred + " red and " + nblue
					+ " blue frodo configs yet");
		}

		int nobs = 0;
		Iterator iobs = group.listAllObservations();
		while (iobs.hasNext()) {
			Observation obs = (Observation) iobs.next();
			// TODO are we (still) autoguiding;

			// has config or target changed ?
			String srcName = (String) srcMap.get(obs);
			if (lastSourceName == null || (!srcName.equals(lastSourceName))) {
				ITarget target = (ITarget) targetNameMap.get(srcName);
				if (target == null)
					throw new OcrException("createSequence: No target named: " + srcName + " can be matched in program");
				System.err.println("Found target for: " + srcName + " as: " + target);

				XRotatorConfig rot = new XRotatorConfig(IRotatorConfig.CARDINAL, 0.0, "RATCam");
				XSlew slew = new XSlew(target, rot, false);

				// See if the obs has requested ns-tracking
				if (obs.getNonSiderealTracking() && (target instanceof XEphemerisTarget))
					slew.setUsesNonSiderealTracking(true);
				// NOTE it is not feasible to mix NonSid and Sid targets in a
				// group

				
				
				XExecutiveComponent targsel = new XExecutiveComponent("Slew/Rotate", slew);
				root.addElement(targsel);
				System.err.println("Add executive: " + targsel);
				lastSourceName = srcName;

			}

			String cfgName = (String) icMap.get(obs);
			if (lastConfigName == null || (!cfgName.equals(lastConfigName))) {
				IInstrumentConfig config = (IInstrumentConfig) configNameMap.get(cfgName);
				if (config == null)
					throw new OcrException("createSequence: No config named: " + cfgName + " can be matched in program");
				System.err.println("Found config for: " + cfgName + " as: " + config);

				String instName = config.getInstrumentName().toUpperCase();
				if (lastInstrument == null || (!lastInstrument.equalsIgnoreCase(instName))) {
					// new instrument so switch agoff (if on), acquire IC, agon
					// if needed
					XAcquisitionConfig acq = new XAcquisitionConfig(IAcquisitionConfig.INSTRUMENT_CHANGE, instName,
							"RATCAM", false);
					XExecutiveComponent acqsel = new XExecutiveComponent("Acquire", acq);
					root.addElement(acqsel);
					lastInstrument = instName;
				}

				XExecutiveComponent cfgsel = new XExecutiveComponent("Config", new XInstrumentConfigSelector(config));
				root.addElement(cfgsel);
				System.err.println("Add executive: " + cfgsel);
				lastConfigName = cfgName;
			}

			// TODO has the focus offset changed

			// TODO has the rotator changed, instrument changed etc -> stopag
			// re-aquire etc
			// Target offset in ARCSEC
			double raOffset = obs.getSourceOffsetRA();
			double decOffset = obs.getSourceOffsetDec();
			if (Math.abs(lastRaOffset - raOffset) > MIN_OFFSET || Math.abs(lastDecOffset - decOffset) > MIN_OFFSET) {

				XExecutiveComponent posoffset = new XExecutiveComponent("Offset", new XPositionOffset(false,
						Math.toRadians(raOffset / 3600.0), Math.toRadians(decOffset / 3600.0)));
				root.addElement(posoffset);
				System.err.println("Add executive: " + posoffset);
				lastRaOffset = raOffset;
				lastDecOffset = decOffset;

			}

			// add an autoguide here...this will fail if we are switching
			// between targets but these should
			// now always be done using the newer p2ml....
			switch (obs.getAutoGuiderUsageMode()) {
			case TelescopeConfig.AGMODE_NEVER:
				break;
			case TelescopeConfig.AGMODE_OPTIONAL:
				XAutoguiderConfig agc1 = new XAutoguiderConfig(IAutoguiderConfig.ON_IF_AVAILABLE, "CASS");
				XExecutiveComponent xag1 = new XExecutiveComponent("Agopt", agc1);
				root.addElement(xag1);
				System.err.println("Add executive: " + xag1);
				break;
			case TelescopeConfig.AGMODE_MANDATORY:
				XAutoguiderConfig agc2 = new XAutoguiderConfig(IAutoguiderConfig.ON, "CASS");
				XExecutiveComponent xag2 = new XExecutiveComponent("Agmand", agc2);
				root.addElement(xag2);
				System.err.println("Add executive: " + xag2);
				break;
			}
			// NOTE fudge to get a name into the ODB - should be multrun-name
			XExecutiveComponent mult = new XExecutiveComponent(obs.getName(), createExposure(obs));
			root.addElement(mult);

			nobs++;
		}
		return root;

	}

	private XMultipleExposure createExposure(Observation obs) {

		XMultipleExposure mult = new XMultipleExposure(obs.getExposeTime(), obs.getNumRuns());
		mult.setName(obs.getName());
		return mult;

	}

	// private void storeProposal(ProgramInfo prog, IProposal proposal) {
	// propNameMap.put(proposal.getName(), new Long(proposal.getID()));
	// }

	// private void storeGroup(ProgramInfo prog, IProposal proposal, IGroup
	// group) {
	// groupNameMap.put(proposal.getName() + "/" + group.getName(), new
	// Long(group.getID()));
	// }

	public IPhase2Model getPhase2() throws Exception {
		return (IPhase2Model) Naming.lookup("rmi://" + rhost + "/Phase2Model");
	}

	private IHistoryModel getHistory() throws Exception {
		return (IHistoryModel) Naming.lookup("rmi://" + rhost + "/HistoryModel");
	}

	/**
	 * @author eng
	 * 
	 */
	private class InternalUpdateListener implements OcrProcessingListener {

		File outputFile;
		PrintStream pout;

		/**
		 * @param outputFile
		 */
		public InternalUpdateListener(File outputFile) throws Exception {
			this.outputFile = outputFile;
			pout = new PrintStream(outputFile);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see OcrProcessingListener#ocrRequestReceived(long, long, int)
		 */
		public void ocrRequestReceived(long time, long id, int type) throws RemoteException {

			// log this to the handler file
			pout.printf("%tF %tT %8d %2d \n", time, time, id, type);

		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see OcrProcessingListener#ocrRequestCompleted(long, long)
		 */
		public void ocrRequestCompleted(long time, long id) throws RemoteException {
			// log this to the handler file
			pout.printf("%tF %tT %8d \n", time, time, id);

		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see OcrProcessingListener#ocrRequestFailed(long, long, int,
		 * java.lang.String)
		 */
		public void ocrRequestFailed(long time, long id, int code, String message) throws RemoteException {
			// log this to the handler file
			pout.printf("%tF %tT %8d %6d %s \n", time, time, id, code, message);

		}

	}

}
