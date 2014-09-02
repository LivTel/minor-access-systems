import java.util.HashMap;
import java.util.Map;

/**
 * 
 */

/**
 * @author eng
 *
 */
public class ProgramInfo {
	
	private int id;
	private Map propNameMap; // maps propname to pids
	private Map groupNameMap; // maps propname/groupname to gids
	private Map targetNameMap;
	private Map configNameMap;
	/**
	 * 
	 */
	public ProgramInfo() {
		propNameMap = new HashMap();
		groupNameMap = new HashMap();
		targetNameMap = new HashMap();
		configNameMap = new HashMap();
	}
	
	
	
	/**
	 * @return the id
	 */
	public int getId() {
		return id;
	}



	/**
	 * @param id the id to set
	 */
	public void setId(int id) {
		this.id = id;
	}



	/**
	 * @return the propNameMap
	 */
	public Map getPropNameMap() {
		return propNameMap;
	}
	/**
	 * @param propNameMap the propNameMap to set
	 */
	public void setPropNameMap(Map propNameMap) {
		this.propNameMap = propNameMap;
	}
	/**
	 * @return the groupNameMap
	 */
	public Map getGroupNameMap() {
		return groupNameMap;
	}
	/**
	 * @param groupNameMap the groupNameMap to set
	 */
	public void setGroupNameMap(Map groupNameMap) {
		this.groupNameMap = groupNameMap;
	}
	/**
	 * @return the targetNameMap
	 */
	public Map getTargetNameMap() {
		return targetNameMap;
	}
	/**
	 * @param targetNameMap the targetNameMap to set
	 */
	public void setTargetNameMap(Map targetNameMap) {
		this.targetNameMap = targetNameMap;
	}
	/**
	 * @return the configNameMap
	 */
	public Map getConfigNameMap() {
		return configNameMap;
	}
	/**
	 * @param configNameMap the configNameMap to set
	 */
	public void setConfigNameMap(Map configNameMap) {
		this.configNameMap = configNameMap;
	}
	
	
	
}
