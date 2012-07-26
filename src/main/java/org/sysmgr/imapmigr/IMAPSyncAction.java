package org.sysmgr.imapmigr;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

public class IMAPSyncAction implements Action
{
  private class CredentialPair
  {
    public String username;
    public IMAPSync.ServerDetails src;
    public IMAPSync.ServerDetails dst;

    private IMAPSync.ServerDetails mkDetails(String pfx, String username)
    {
      String un = n.ps(pfx + ".userpattern").replaceAll("%%USER%%", username);
      return new IMAPSync.ServerDetails(n.ps(pfx + ".hostname"), un,
        n.ps(pfx + ".password"), n.pb(pfx + ".usessl"));
    }

    CredentialPair(String canonUser, String srcuser, String dstuser) {
      this.username = canonUser;
      this.src = mkDetails("src", srcuser);
      this.dst = mkDetails("dst", dstuser);
    }
  }

  private LinkedBlockingQueue<CredentialPair> queue;
  private ArrayList<Worker> workers = new ArrayList<Worker>();
  private int maxThreads;
  private Nexus n;
  private MigrationTrackingStore mts;
  private Map<String, AccountListLine> al;

  private final Object mutex = new Object();
  private PrintWriter globalLog;
  private boolean closing = false;
  private Set<String> accountIds;

  public IMAPSyncAction(Nexus n, int maxThreads, Set<String> accountIds)
  {
    this.n = n;
    this.maxThreads = maxThreads;
    if (accountIds != null && accountIds.size() > 0)
      this.accountIds = accountIds;

    al = n.getAccountList();
    queue = new LinkedBlockingQueue<CredentialPair>(maxThreads * 15);
    mts = n.getMigrationTrackingStore();
    initLog();
    Runtime.getRuntime().addShutdownHook(new SDHook());
  }

  public void enqueueAccount(CredentialPair t)
  {
    if (closing)
      return;
    try {
      queue.put(t);
    } catch (InterruptedException ex) {
      throw new RuntimeException(ex);
    }
    synchronized (mutex) {
      if (!closing && workers.size() < maxThreads) {
        workers.add(new Worker());
      } else {
      }
    }
  }

  public void waitForSteadyStateEnd()
  {
    synchronized (mutex) {
      while (workers.size() > 0)
        try {
          mutex.wait();
        } catch (InterruptedException ex) {
        }
    }
  }

  public void shutdown()
  {
    synchronized (mutex) {
      closing = true;
      for (Worker w : workers) {
        IMAPSync t = w.is;
        if (t != null)
          t.shutdown();
      }
    }
  }

  private void initLog()
  {
    String fn = n.ps("imap.log.filename");
    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd.HHmmss");
    Date now = new Date();
    fn = fn.replaceAll("%%DATE%%", sdf.format(now));
    File logfilef = new File(fn);
    if (logfilef.exists()) {
      throw new RuntimeException("Will not overwrite existing logfile: "
        + logfilef.getName());
    }
    try {
      globalLog = new PrintWriter(new FileOutputStream(logfilef, false), true);
    } catch (FileNotFoundException ex) {
      throw new RuntimeException("Could not create log file '" + fn + "'", ex);
    }
    globalLog.println("#Program:    outmail");
    globalLog.println("#Started-At: " + now);
    globalLog.println("#Task:       imapsync");
  }

  public void doAction()
  {
    Map<String, String> pairs = new HashMap<String, String>();
    int i = 0;

    globalLog.println("MAIN: Scheduling migrations ...");

    if (accountIds != null) {
      globalLog.println("MAIN: Using arguments to restrict which accounts "
        + " from the account file are migrated.");
      // preflight check:
      for (String aci: accountIds) {
        if (!al.containsKey(aci))
          throw new RuntimeException("Could not find account '" + aci + "' in"
            + " account list file.");
      }
    } else {
      globalLog.println("MAIN: Processing all accounts in account list file.");
    }

    // enqueue the work:
    for (AccountListLine all: al.values()) {
      if (accountIds != null && !accountIds.contains(all.canonicalUsername))
        continue;
      CredentialPair cp = new CredentialPair(all.canonicalUsername,
        all.srcUsername, all.dstUsername);
      enqueueAccount(cp);
      i++;
    }

    globalLog.println("MAIN: Finished scheduling " + i
      + " accounts for migration, waiting for queue exhaustion...");
    waitForSteadyStateEnd();

    globalLog.println("MAIN: All threads terminated, ending action.");
    Utils.closeQuietly(globalLog);
  }

  public void close()
  {
    shutdown();
    waitForSteadyStateEnd();
    Utils.closeQuietly(globalLog);
    Utils.closeQuietly(mts);
  }

  class SDHook extends Thread
  {
    @Override
    public void run() {
      System.out.println("SHUTDOWN MANAGER: WAITING FOR THREADS TO COMPLETE");
      close();
      System.out.println("SHUTDOWN MANAGER: THREADS COMPLETED, ENDING");
    }
  }

  class Worker extends Thread
  {
    private IMAPSync is = null;

    public Worker()
    {
      setName("Worker");
      setDaemon(false);
      start();
    }

    @Override
    public void run()
    {
      // random spawn delay of 0-12 seconds
      Utils.sleep((int)Math.random() * 12000);

      globalLog.println("INFO: [" + getId() + "] Worker spinning up.");

      for (;;) {
        if (closing) {
          synchronized (mutex) {
            globalLog.println("INFO: [" + getId() + "] Shutdown requested, "
              + "Worker spinning down.");
            workers.remove(this);
            mutex.notifyAll();
          }
          return;
        }
        CredentialPair cp = queue.poll();
        if (cp == null) {
          synchronized (mutex) {
            globalLog.println("INFO: [" + getId() + "] Worker found no jobs, "
              + "spinning down.");
            workers.remove(this);
            mutex.notifyAll();
          }
          return;
        }
        // Perform the requested work:
        globalLog.println("INFO: [" + getId() + "] Starting " + cp.username);
        mts.log("imapsync", cp.username, "INFO: starting sync");
        long begin = System.nanoTime();
        is = null;
        try {
          // Perform sync:
          is = new IMAPSync(cp.username, mts, cp.src, cp.dst);
          is.run();

          if (is.getWasDone()) {
            globalLog.println("INFO: [" + getId() + "] Marking user "
              + cp.username + " as done");
            mts.log("imapsync", cp.username, "INFO: marking user as done");
            // mark user as done if we didn't error out:
            mts.markUserDone(cp.username);
          }

          // Report statistics:
          long end = System.nanoTime();
          mts.log("imapsync", cp.username, "INFO: sync terminated normally, "
            + "copied " + is.getCountCopied() + " skipped "
            + is.getCountSkipped() + " took " + ((end - begin) / 1e9)
            + " seconds");
          globalLog.println("INFO: [" + getId() + "] finished " + cp.username
            + " copied " + is.getCountCopied() + " skipped "
            + is.getCountSkipped() + " took " + ((end - begin) / 1e9)
            + " seconds");
        } catch (Throwable t) {
          long end = System.nanoTime();
          if (is != null && is.getErrorSummary() != null) {
            globalLog.println("ERROR: [" + getId() + "] For user " + cp.username
              + " (from IMAPSync): " + is.getErrorSummary());
            mts.log("imapsync", cp.username, "ERROR: sync failed: "
              + is.getErrorSummary());
          }
          globalLog.println("ERROR: [" + getId() + "] For user " + cp.username
            + ": " + t.getClass().getName() + ": " + t.getMessage()
            + " -- Aborting after " + ((end - begin) / 1e9) + " seconds");
          mts.log("imapsync", cp.username, "ERROR: sync terminated"
            + " abnormally, aborted after " + ((end - begin) / 1e9)
            + " seconds, error: " + t.getClass().getName() + ": "
            + t.getMessage());
        } finally {
          is = null;
        }
      }
    }
  }
}
