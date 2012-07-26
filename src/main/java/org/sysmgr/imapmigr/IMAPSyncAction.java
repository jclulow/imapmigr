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
    String canonUser;
    IMAPSync.ServerDetails src;
    IMAPSync.ServerDetails dst;

    private static IMAPSync.ServerDetails mkDetails(String pfx, String username)
    {
      String un = n.ps(pfx + ".userpattern").replaceAll("%%USER%%", username);
      return new IMAPSync.ServerDetails(n.ps(pfx + ".hostname"), un,
        n.ps(pfx + ".password"), n.pb(pfx + ".usessl"));
    }

    CredentialPair(Nexus n, String canonUser, String srcuser, String dstuser) {
      username = canonUser;
      src = mkDetails("src", srcuser);
      dst = mkDetails("dst", dstuser);
    }
  }

  private LinkedBlockingQueue<CredentialPair> queue;
  private ArrayList<Worker> workers = new ArrayList<Worker>();
  private int maxThreads;
  private Nexus n;
  private MigrationTrackingStore mts;
  private List<String> al;

  private final Object mutex = new Object();
  private PrintWriter globalLog;
  private boolean closing = false;
  private Set<String> accountIds;

  public IMAPSyncAction(Nexus n, int maxThreads, List<String> accountIds)
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

  public void enqueueAccountId(String srcuser, String dstuser)
  {
    enqueueAccountId(new CredentialPair(n, accountId, password));
  }

  public void enqueueAccountId(CredentialPair t)
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
    if (accountIds != null) {
      globalLog.println("MAIN: Using arguments to restrict which accounts "
        + " from the account file are migrated.");
    }
    for (String s: al) {
    }

    for (String s: accountIds) {
        pairs.put(s, ensyst.getMostRecentPassword(s));
        i++;
        System.err.print(".");
      }
    } else {
      globalLog.println("MAIN: Fetching accounts for migration scheduling ...");
      Iterator<EnsystStatusRecord> ia = ensyst.iterateStatusRecords(false,
        true, 1);
      while (ia.hasNext() && !closing) {
        EnsystStatusRecord esr = ia.next();
        if (mts.isUserDone(esr.getLiveId()))
          continue;
        if (esr.getEventTypeId() == 1 || esr.getEventTypeId() == 3) {
          if (esr.getStatus().equals("0"))
            // Update our current picture of this user...
            pairs.put(esr.getLiveId(), esr.getPassword());
        }
        if (++i % 10000 == 0)
          System.err.print(".");
      }
    }
    System.err.println();
    
    globalLog.println("MAIN: Scheduling migrations ...");
    for (Map.Entry<String, String> e : pairs.entrySet()) {
      System.err.print(">");
      enqueueAccountId(e.getKey(), e.getValue());
    }
    globalLog.println("MAIN: Finished scheduling " + pairs.size()
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
    Utils.closeQuietly(af);
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
