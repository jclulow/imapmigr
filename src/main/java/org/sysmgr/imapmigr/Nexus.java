package org.sysmgr.imapmigr;

import java.util.Properties;
import java.util.HashMap;
import java.util.Map;
import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;

public class Nexus
{
  private Properties config;
  private MigrationTrackingStore mts;
  private Map<String, AccountListLine> accountList;

  public String ps(String name)
  {
    String val = config.getProperty(name);
    if (val == null)
      throw new RuntimeException("Must specify a value for '" + name
        + "' in properties file.");
    return val;
  }

  public int pi(String name)
  {
    try {
      return Integer.parseInt(ps(name));
    } catch (NumberFormatException nfe) {
      throw new RuntimeException("Must specify a valid integer value for '"
        + name + "' in properties file.");
    }
  }

  public boolean pb(String name)
  {
    String val = ps(name);
    if (val.equalsIgnoreCase("true") || val.equalsIgnoreCase("yes"))
      return true;
    if (val.equalsIgnoreCase("false") || val.equalsIgnoreCase("no"))
      return false;
    throw new RuntimeException("Must specify true or false for '" + name
      + "' in properties file.");
  }

  public MigrationTrackingStore getMigrationTrackingStore()
  {
    if (mts == null) {
      mts = new MTSImplPostgres(
        ps("mts.hostname"),
        pi("mts.port"),
        ps("mts.dbname"),
        ps("mts.username"),
        ps("mts.password"),
        pi("mts.maxconns"));
    }
    return mts;
  }

  public Map<String, AccountListLine> getAccountList()
  {
    if (accountList == null) {
      accountList = new HashMap<String, AccountListLine>();
      LineIterator i;
      try {
        i = FileUtils.lineIterator(new File(ps("accountsfile")));
      } catch (IOException ioe) {
        throw new RuntimeException("Could not read account list file", ioe);
      }
      while (i.hasNext()) {
        try {
          AccountListLine all = new AccountListLine(i.nextLine());
          if (accountList.containsKey(all.canonicalUsername))
            throw new RuntimeException("Duplicate username ("
              + all.canonicalUsername + ") in account list file.");
          accountList.put(all.canonicalUsername, all);
        } catch (AccountListLine.EmptyLine el) {
          // ignore empty or comment lines
        } catch (Exception e) {
          throw new RuntimeException("Could not read account list file", e);
        }
      }
      if (accountList.size() < 1)
        throw new RuntimeException("Account list file contained no entries.");
    }
    return accountList;
  }

  public Nexus(Properties config)
  {
    this.config = config;
  }
}
