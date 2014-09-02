

import java.rmi.*;
import java.rmi.server.*;
import java.util.*;
import ngat.oss.model.*;
import ngat.sms.*;
import ngat.sms.bds.MysqlHistoryModel;
import ngat.sms.bds.TestScheduleItem;
import ngat.util.CommandTokenizer;
import ngat.util.ConfigurationProperties;

/**
 * @author eng
 *
 */
public class RtmlTestScheduler extends UnicastRemoteObject implements ScheduleDespatcher, AsynchronousScheduler {
	
	private Phase2CompositeModel gphase2;
	
	private IHistoryModel histModel;
	
	private ExecutionUpdateManager xm;
	
	private String groupStart;
	
	protected RtmlTestScheduler(Phase2CompositeModel gphase2, IHistoryModel histModel, ExecutionUpdateManager xm, String groupStart) throws RemoteException {
		super();
		this.gphase2 = gphase2;
		this.histModel = histModel;
		this.xm = xm;
		this.groupStart = groupStart;
	}

	public ScheduleItem nextScheduledJob() throws RemoteException {
		List candidates = new Vector();
		int ng = 0;
		int nc = 0;
		List<GroupItem> grouplist = gphase2.listGroups();
		Iterator<GroupItem> groups = grouplist.iterator();
		while (groups.hasNext()) {
			
			GroupItem group = groups.next();
			ng++;

			if (group.getName().startsWith(groupStart)) {
				nc++;
				candidates.add(group);				
			}
		}
		
		double maxScore = -999.99;
		GroupItem bg = null;
		int ig = 0;
		Iterator cand = candidates.iterator();
		while (cand.hasNext()) {
			GroupItem group = (GroupItem) cand.next();
			double score = Math.random();
		
			if (score > maxScore) {
				bg = group;
			}
			ig++;
		}
		
		if (bg == null)
			return null;

		// create the initial history entry and set in GI
		long hid = histModel.addHistoryItem(bg.getID());
		bg.setHId(hid);
		
		// Create a new ExecutionUpdater to handle the reply
		ExecutionUpdater xu = xm.getExecutionUpdater(bg.getID());
	
		ScheduleItem item = new TestScheduleItem(bg, xu);
		return item;

		
	}

	public void requestSchedule(AsynchronousScheduleResponseHandler asrh) throws RemoteException {
	
		// spin off a numbered thread and let it reply after a while

		AsynchResponder ar = new AsynchResponder(asrh);
		(new Thread(ar)).start();

	}

	private class AsynchResponder implements Runnable {

		AsynchronousScheduleResponseHandler asrh;

		private AsynchResponder(AsynchronousScheduleResponseHandler asrh) {
			this.asrh = asrh;
		}

		public void run() {

			ScheduleItem sched = null;

			try {
				System.err.println("TS:: Calling nextSchedJob() for handler: " + asrh);
				sched = nextScheduledJob();
				System.err.println("TS:: Schedule done");
			} catch (Exception e) {
				System.err.println("TS:: Error obtaining schedule: " + e);
				e.printStackTrace();
				try {
					String message = "Unable to generate schedule: " + e;
					System.err.println("TS:: Sending error message to handler: [" + message + "]");
					asrh.asynchronousScheduleFailure(5566, message);
				} catch (Exception e2) {
					System.err.println("TS:: Unable to send error message to handler: " + e2);
					e2.printStackTrace();
				}
				return;
			}

			// ok wait a while and send reply.....
			try {
				long delay = 5000L + (long) (5000.0 * Math.random());
				System.err.println("TS::I shall be delaying for: " + delay + " ms before replying");
				try {
					asrh.asynchronousScheduleProgress("I'm on the case and will return a schedule to you in " + delay
							+ " ms, please be patient");
				} catch (Exception ee) {
					System.err.println("TS:: Unable to send progress message to handler: " + ee);
				}
				Thread.sleep(delay);
			} catch (InterruptedException ix) {
			}

			try {
				System.err.println("TS:: " + (new Date()) + "Sending schedule reply to handler: " + asrh);
				asrh.asynchronousScheduleResponse(sched);
			} catch (Exception e3) {
				System.err.println("TS:: Unable to send schedule reply to handler: " + e3);
				e3.printStackTrace();
			}
		}

	}

	public static void main(String args[]) {
		
		try {
		
			ConfigurationProperties config = CommandTokenizer.use("--").parse(args);
			
			String p2host = config.getProperty("p2-host", "localhost");
			String p2name = config.getProperty("p2-name", "Phase2GroupModelProvider");
			
			String xmhost = config.getProperty("xm-host", "localhost");
			String xmname = config.getProperty("xm-name", "ExecutionUpdateManager");
		
			String hmhost =  config.getProperty("hm-host", "localhost");
			
			Phase2GroupModelProvider p2gmp = (Phase2GroupModelProvider)Naming.lookup("rmi://"+p2host+"/"+p2name);
			
			Phase2CompositeModel p2g = p2gmp.getPhase2Model();
			String dburl = "jdbc:mysql://" + hmhost+"/phase2odb?user=oss&password=ng@toss";
			MysqlHistoryModel history = new MysqlHistoryModel(dburl);
			
			ExecutionUpdateManager xm = (ExecutionUpdateManager)Naming.lookup("rmi://"+xmhost+"/"+xmname);
			
			String pattern = config.getProperty("pattern");
			
			// create a scheduler which picks up groups matching "starts-with <pattern> "
			RtmlTestScheduler test =new RtmlTestScheduler(p2g,history, xm, pattern);
			
			Naming.rebind("rmi://localhost/AsynchScheduler", test);
			
			System.err.println("Bound asynch rtml-selector scheduler...");
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	
}
