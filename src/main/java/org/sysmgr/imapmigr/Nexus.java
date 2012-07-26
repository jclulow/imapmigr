package org.sysmgr.imapmigr;

import java.util.Properties;
import org.apache.commons.io.FileUtils;

public class Nexus
{
  private Properties config;
  private MigrationTrackingStore mts;
  private List<String> accountList;

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

  public List<String> getAccountList()
  {
    if (accountList == null) {
      accountList = FileUtils.readLines(new File(ps("accountsfile")));
    }
    return accountList;
  }

  public Nexus(Properties config)
  {
    this.config = config;
  }
}
