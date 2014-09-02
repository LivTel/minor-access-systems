import java.util.Date;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

/**
 * 
 */

/**
 * @author eng
 *
 */
public class MailSender {
	
	private String smtpHost;

	private String mailToAddr;

	private String mailFromAddr;

	private String mailCcAddr;

	private String mailSubj;

	/**
	 * @param smtpHost
	 */
	public MailSender(String smtpHost) {
		super();
		this.smtpHost = smtpHost;
		Properties props = System.getProperties();
		props.put("mail.smtp.host", smtpHost);
	}

	/**
	 * @param mailToAddr the mailToAddr to set
	 */
	public void setMailToAddr(String mailToAddr) {
		this.mailToAddr = mailToAddr;
	}

	/**
	 * @param mailFromAddr the mailFromAddr to set
	 */
	public void setMailFromAddr(String mailFromAddr) {
		this.mailFromAddr = mailFromAddr;
	}

	/**
	 * @param mailCcAddr the mailCcAddr to set
	 */
	public void setMailCcAddr(String mailCcAddr) {
		this.mailCcAddr = mailCcAddr;
	}

	/**
	 * @param mailSubj the mailSubj to set
	 */
	public void setMailSubj(String mailSubj) {
		this.mailSubj = mailSubj;
	}
	
	public void send(String text) throws Exception {
		Properties props = System.getProperties();
		Session session = Session.getDefaultInstance(props, null);
		// -- Create a new message --
		Message msg = new MimeMessage(session);
		// -- Set the FROM and TO fields --
		msg.setFrom(new InternetAddress(mailFromAddr));
		msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(mailToAddr, false));
		msg.setRecipients(Message.RecipientType.CC, InternetAddress.parse(mailCcAddr, false));

		// -- Set the subject and body text --
		if (mailSubj == null)
			mailSubj = "OCR-Error";
		msg.setSubject(mailSubj);
		msg.setText(text);
		// -- Set some other header information --
		msg.setHeader("X-Mailer", "JavaMailOnProxy");
		msg.setSentDate(new Date());
		// -- Send the message --
		Transport.send(msg);
		System.out.println("Mail Message sent OK.");
	}
	
}
