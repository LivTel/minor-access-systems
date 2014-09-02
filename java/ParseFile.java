import java.io.File;

/**
 * 
 */

/**
 * @author eng
 *
 */
public class ParseFile {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
	
		try {
			
			OfflineRelay server = new OfflineRelay();
			
			File file = new File(args[0]);
			
			
			P2mlParser parser = new P2mlParser(server);
			
			parser.processDocument(file);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		

	}

}
