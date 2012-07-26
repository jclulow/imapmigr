package org.sysmgr.imapmigr;

import com.google.gson.JsonObject;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPMessage;
import com.sun.mail.imap.IMAPStore;
import java.util.regex.Pattern;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;

public class IMAPUtils
{

  // XXX TODO - Get this from properties file:
  public static String mapFolderName(String src, char sep) {
    if (src.equalsIgnoreCase("SENT"))
      return "Sent Items";
    if (src.equalsIgnoreCase("TRASH"))
      return "Deleted Items";
    if (src.equalsIgnoreCase("SHARED FOLDERS"))
      return null;
    return src;
  }
  
  // XXX TODO - Get this from properties file:
  private static boolean mirrorSkip(String name) {
    if (name.equalsIgnoreCase("INBOX")
      || name.equalsIgnoreCase("TRASH")
      || name.equalsIgnoreCase("DRAFTS")
      || name.equalsIgnoreCase("SENT")
      || name.equalsIgnoreCase("SHARED FOLDERS"))
      return true;
    return false;
  }

  public static JsonObject getInfoJSON(IMAPMessage m)
  {
    try {
      String messageId = m.getMessageID();
      if (messageId != null) {
        messageId = messageId.trim().toLowerCase();
      } else {
        /*
         * Unfortunately sometimes Message-ID is null, so make some shit up
         * and pretend.
         */
        messageId = m.getSubject() + "|" + m.getSender() + "|"
          + m.getReceivedDate().getTime();
      }

      String size = Integer.toString(m.getSize());
      String indate = Long.toString(m.getReceivedDate().getTime());
      String fingerprint = Utils.getMD5(messageId + "|" + size + "|" + indate
        + "|" + m.getFolder().getFullName().trim().toLowerCase());

      JsonObject oo = new JsonObject();
      oo.addProperty("Source-Folder", m.getFolder().getFullName());
      oo.addProperty("Message-ID", messageId);
      oo.addProperty("Size", m.getSize());
      oo.addProperty("Internal-Date", m.getReceivedDate().getTime());
      oo.addProperty("Subject", m.getSubject());
      if (m.getSender() != null)
        oo.addProperty("Sender", m.getSender().toString());
      oo.addProperty("Fingerprint", fingerprint);
      return oo;
    } catch (NullPointerException ex) {
      try {
        System.err.println("=========== NULL POINTER MESSAGE: ===============");
        System.err.println("Message ID: " + m.getMessageID());
        System.err.println("Subject: " + m.getSubject());
        if (m.getSender() != null)
          System.err.println("Sender: " + m.getSender().toString());
        System.err.println("Flags: " + getFlagsInfo(m.getFlags()));
        System.err.println("=========== NULL POINTER MESSAGE^ ===============");
      } catch (MessagingException me) {
      }
      throw ex;
    } catch (MessagingException ex) {
      throw new RuntimeException(ex);
    }
  }

  public static String getFlagsInfo(Flags f)
  {
    String sys = "";
    for (Flags.Flag flag : f.getSystemFlags()) {
      if (flag.equals(Flags.Flag.ANSWERED))
        sys += " " + "ANSWERED";
      else if (flag.equals(Flags.Flag.DELETED))
        sys += " " + "DELETED";
      else if (flag.equals(Flags.Flag.DRAFT))
        sys += " " + "DRAFT";
      else if (flag.equals(Flags.Flag.FLAGGED))
        sys += " " + "FLAGGED";
      else if (flag.equals(Flags.Flag.RECENT))
        sys += " " + "RECENT";
      else if (flag.equals(Flags.Flag.SEEN))
        sys += " " + "SEEN";
      else if (flag.equals(Flags.Flag.USER))
        sys += " " + "USER";
      else
        sys += " UNKNOWN:" + flag;
    }
    String user = "";
    for (String uf : f.getUserFlags()) {
      user += " " + uf;
    }
    return "SYS: [" + sys.trim() + "] USER: [" + user.trim() + "]";
  }
}
