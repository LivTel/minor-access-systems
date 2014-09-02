/** this interface should implemented by any classes which wish to be notified when an
 * ACKNOWLEDGE or RESULT message is returned from the POS_CommandRelay. It is intended to
 * be used in conjunction with the POSSocketClient.
 */
public interface POSSocketListener {

    /** Implementors should handle the ACKNOWLEDGE message passed back from the POS_CommandRelay.
     */
    public void handleAcknowledge(String message);
    
    /** Implementors should handle the RESULT message passed back from the POS_CommandRelay.
     * This could include parsing, checking for OK/FAIL, etc.
     */
    public void handleResult(String message);

}

				    
