package org.sysmgr.imapmigr;

import com.google.gson.JsonObject;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPMessage;
import com.sun.mail.imap.IMAPStore;
import java.util.Date;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import java.io.StringWriter;
import java.io.PrintWriter;

public class IMAPSync implements Runnable
{
  public static class ServerDetails
  {
    private boolean doProxy = false;
    private String hostname;
    private String username;
    private String password;
    private String proxyusername;
    private boolean ssl = false;

    /**
     * Regular connection using the user's own username and password.
     *
     * @param hostname IMAP Server
     * @param username User's login
     * @param password User's password
     * @param ssl Use IMAPS?
     */
    public ServerDetails(String hostname, String username, String password,
      boolean ssl)
    {
      this.doProxy = false;
      this.hostname = hostname;
      this.username = username;
      this.password = password;
      this.ssl = ssl;
    }

    /**
     * Do IMAP PROXYAUTH to log in as an administrative user and then switch to
     * the correct user.
     *
     * @param hostname Server
     * @param adminusername Administrative login.
     * @param adminpassword Administrative password.
     * @param username User to PROXYAUTH to (i.e. the user to migrate)
     * @param ssl Use IMAPS?
     */
    public ServerDetails(String hostname, String adminusername,
      String adminpassword, String username, boolean ssl)
    {
      this.doProxy = true;
      this.hostname = hostname;
      this.username = adminusername;
      this.password = adminpassword;
      this.proxyusername = username;
      this.ssl = ssl;
    }

    protected Properties getSessionProperties()
    {
      Properties p = new Properties();
      if (doProxy) {
        p.setProperty("mail." + getStoreType() + ".auth.login.disable", "true");
        p.setProperty("mail." + getStoreType() + ".auth.plain.disable", "true");
        p.setProperty("mail." + getStoreType() + ".proxyauth.user", proxyusername);
      }
      p.setProperty("mail." + getStoreType() + ".connectiontimeout", "45000");
      p.setProperty("mail." + getStoreType() + ".timeout", "45000");
      return p;
    }

    protected String getStoreType()
    {
      if (ssl)
        return "imaps";
      else
        return "imap";
    }

    protected IMAPStore connect()
    {
      try {
        Session session = Session.getInstance(getSessionProperties());
        IMAPStore imap = (IMAPStore) session.getStore(getStoreType());
        imap.connect(hostname, username, password);
        return imap;
      } catch (MessagingException ex) {
        throw new RuntimeException("Could not connect to IMAP Server: " + ex.getMessage(), ex);
      }
    }
  }

  private String canonicalUsername;
  private ServerDetails sdsrc;
  private ServerDetails sddst;
  private MigrationTrackingStore mts;
  private final CountDownLatch latch = new CountDownLatch(1);
  private IMAPStore src;
  private IMAPStore dst;
  private boolean keepRunning = true;
  int countSkipped = 0;
  int countCopied = 0;
  private String errorSummary = null;
  private Set<String> mtsCache;
  private boolean wasDone = false;

  public IMAPSync(String canonicalUsername, MigrationTrackingStore mts,
    ServerDetails from, ServerDetails to)
  {
    this.mts = mts;
    this.mtsCache = mts.getMigratedForUser(canonicalUsername);
    this.sdsrc = from;
    this.sddst = to;
    this.canonicalUsername = canonicalUsername;
    //log = new PrintWriter(logTo, true);
    src = sdsrc.connect();
    dst = sddst.connect();
  }

  private void log(String msg)
  {
    // log.println(msg);
    mts.log("imapsync", canonicalUsername, msg);
  }

  public void join()
  {
    while (latch.getCount() != 0) {
      try {
        latch.await();
      } catch (InterruptedException ex) {
      }
    }
  }

  public int getCountSkipped()
  {
    return countSkipped;
  }

  public int getCountCopied()
  {
    return countCopied;
  }

  public String getErrorSummary()
  {
    return errorSummary;
  }

  public boolean getWasDone()
  {
    return wasDone;
  }

  public void shutdown()
  {
    keepRunning = false;
  }

  public void run()
  {
    if (latch.getCount() == 0)
      throw new IllegalStateException("Cannot run a finished IMAPSync a second"
        + " time.");

    try {
      log("INFO: Starting IMAPSync Session");
      log("INFO: Found " + mtsCache.size() + " entries in MTS Cache");
      mirror();
      log("INFO: Done with IMAPSync Session");
    } catch (Throwable ex) {
      log("ERROR: IMAPSync failed: " + ex.getClass().getName() + ": "
        + ex.getMessage());
      errorSummary = "IMAP Sync Failed (" + ex.getClass().getName() + " @ "
        + new Date().toString() + "): " + ex.getMessage();
      throw new RuntimeException(errorSummary, ex);
    } finally {
      latch.countDown();
    }
  }

  private void mirror() throws MessagingException
  {
    mirrorDir((IMAPFolder) src.getDefaultFolder(),
      (IMAPFolder) dst.getDefaultFolder());

    if (keepRunning)
      wasDone = true;

    Utils.closeQuietly(src);
    Utils.closeQuietly(dst);
  }

  private void mirrorDir(IMAPFolder srcf, IMAPFolder dstf)
    throws MessagingException
  {
    if (!keepRunning) {
      log("INFO: Terminating early due to shutdown request.");
      return;
    }
    IMAPFolder odstf = dstf;

    if (srcf.getFullName() != null && srcf.getFullName().trim().length() > 1) {
      // not the default root folder (empty name), so...

      // create destination directory if not extant
      try {
        if (!dstf.exists()) {
          if (dstf.create(srcf.getType())) {
            log("INFO: Created destination directory '"
              + dstf.getFullName() + "'");
          } else {
            log("WARNING: Could not create destination directory '"
              + dstf.getFullName() + "', but wanted to!");
            // NB: exception will be thrown when we try to open, so for now
            //   just WARN that we THOUGHT we wanted to create but couldn't.
          }
        }
      } catch (MessagingException ex) {
        log ("ERROR: Could not create destination directory '"
          + dstf.getFullName() + "': " + ex.getMessage());
        throw ex;
      }

      // open source directory for copy
      try {
        srcf.open(Folder.READ_ONLY);
      } catch (MessagingException ex) {
        if (ex.getMessage().contains("folder cannot contain messages")) {
          // Well, process the child folders then.
          log("WARNING: Source directory '" + srcf.getFullName()
            + "' cannot contain messages.  Skipping.");
          handleChildren(srcf, odstf);
          return;
        } else {
          log("ERROR: Could not open source directory '" + srcf.getFullName()
            + "': " + ex.getMessage());
          throw ex;
        }
      }

      // open destination directory for copy
      try {
        dstf.open(Folder.READ_WRITE);
      } catch (MessagingException ex) {
        log("ERROR: Could not open destination directory '"
          + dstf.getFullName() + "': " + ex.getMessage());
        throw ex;
      }

      // copy mail
      int localCopied = 0;
      int localSkipped = 0;
      log("INFO: Copying '" + srcf.getFullName() + "' --> '"
        + dstf.getFullName() + "'");

      Message[] msrc = srcf.getMessages();
      for (Message m : msrc) {
        if (!keepRunning) {
          log("INFO: Copied " + localCopied + " Skipped " + localSkipped);
          log("INFO: Terminating early due to shutdown request.");
          srcf.close(false);
          dstf.close(false);
          return;
        }

        IMAPMessage im = null;
        JsonObject jo = null;
        try {
          im = (IMAPMessage) m;
          jo = IMAPUtils.getInfoJSON(im);

          if (mtsCache.contains(canonicalUsername + "/"
              + jo.get("Fingerprint").getAsString())) {
            // We've already got a snapshot of this e-mail so don't
            //   copy it again
            countSkipped++;
            localSkipped++;
          } else {
            // We've not seen this e-mail before, copy it
            dstf.appendMessages(new Message[]{im});
            mtsCache.add(canonicalUsername + "/" + jo.get("Fingerprint").getAsString());
            mts.recordMigration(canonicalUsername, jo.get("Fingerprint").getAsString(), im.getSize(), jo.toString());
            countCopied++;
            localCopied++;
          }
        } catch (MessagingException ex) {
          log("INFO: Copied " + localCopied + " Skipped " + localSkipped
            + " before this error.");
          log("ERROR: MessagingException: " + ex.getMessage());
          StringWriter trace = new StringWriter();
          ex.printStackTrace(new PrintWriter(trace));
          log("TRACE: Stacktrace: " + trace.toString());
          if (jo != null) {
            log("TRACE: Message Info: " + jo.toString());
          }
          throw ex;
        } catch (RuntimeException ex) {
          log("INFO: Copied " + localCopied + " Skipped " + localSkipped
            + " before this error.");
          log("ERROR: RuntimeException: " + ex.getMessage());
          throw ex;
        }
      }

      log("INFO: Copied " + localCopied + " Skipped " + localSkipped);
      srcf.close(false);
      dstf.close(false);
    }
    
    handleChildren(srcf, odstf);
  }

  private void handleChildren(IMAPFolder srcf, IMAPFolder dstf) throws MessagingException
  {
    // Handle Child Directories...
    for (Folder child : srcf.list()) {
      if (!keepRunning) {
        log("INFO: Terminating early due to shutdown request.");
        return;
      }

      String newn = IMAPUtils.mapFolderName(child.getName(), child.getSeparator());
      if (newn != null) {
        IMAPFolder newsrc = (IMAPFolder) child;
        IMAPFolder newdst = (IMAPFolder) dstf.getFolder(newn);
        mirrorDir(newsrc, newdst);
      }
    }
  }
}
