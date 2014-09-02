import java.io.File;

/**
 * 
 */

/**
 * @author eng
 *
 */
public class TestParseFile {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			File file = new File(args[0]);
			
			XMLParser parser = new XMLParser();
			
			parser.parseFile(file);
			
			
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}
