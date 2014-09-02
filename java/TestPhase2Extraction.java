import java.io.File;

import org.estar.rtml.*;
import org.estar.tea.*;

import java.util.*;
import java.rmi.*;
import ngat.phase2.*;
import ngat.util.CommandTokenizer;
import ngat.util.ConfigurationProperties;
import ngat.oss.model.*;

/**
 * 
 */

/**
 * @author eng
 * 
 */
public class TestPhase2Extraction {

	/**
	 * @param args
	 */
	public static void main(String[] args) {

		// usage: java TestPhase2Extraction <id> <cfgfile> <doc>

		try {

			ConfigurationProperties config = CommandTokenizer.use("--").parse(args);

			String id = config.getProperty("id", "TEST");

			File configFile = new File(config.getProperty("config"));

			TelescopeEmbeddedAgent tea = new TelescopeEmbeddedAgent(id);

			tea.configure(configFile);
			
			tea.start();

			// read a rtml doc from somewhere

			File docfile = new File(config.getProperty("doc"));

			RTMLParser parser = new RTMLParser();
			parser.init(false);

			RTMLDocument doc = parser.parse(docfile);

			// lookup the p2model and geta list of proposals back
			String rhost = config.getProperty("rhost", "localhost");

			IPhase2Model phase2 = (IPhase2Model) Naming.lookup("rmi://" + rhost + "/Phase2Model");
			IAccessModel access = (IAccessModel) Naming.lookup("rmi://" + rhost + "/AccessModel");

			List plist = new Vector();

			List ulist = access.listUsers();
			Iterator iu = ulist.iterator();

			long now = System.currentTimeMillis();

			while (iu.hasNext()) {

				IUser user = (IUser) iu.next();
				long uid = user.getID();
				String name = user.getName();

				System.err.println("User: " + user);

				List alist = access.listAccessPermissionsOfUser(uid);
				Iterator ia = alist.iterator();
				while (ia.hasNext()) {

					IAccessPermission accp = (IAccessPermission) ia.next();
					System.err.println("LocateAccess: " + accp);

					long pid = accp.getProposalID();
					ITag tag = phase2.getTagOfProposal(pid);
					System.err.println("Get TAG of Prop: returned: " + tag);

					IProposal proposal = phase2.getProposal(pid);
					System.err.println("LocateProposal: " + proposal);

					plist.add(proposal);

				}
			}

			// pass the proposallist to extractor
			Phase2ExtractorTNG p2x = new Phase2ExtractorTNG(tea);

			RTMLDocument reply = p2x.handleRequest(doc);
		
			System.err.println(""+reply.toString());
			
			
		} catch (Exception e) {
			e.printStackTrace();
			System.err
					.println("Usage: TestPhase2Extraction --id <id> --config <cfgfile> --doc <rtmldoc> [--rhost <host>]");
		}
	}

}
