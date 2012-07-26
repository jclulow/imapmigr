package org.sysmgr.imapmigr;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class Main
{
  private static void printUsage() {
    String bspath = Main.class.getPackage().getName().replaceAll("\\.", "/");
    Properties p = Utils.loadPropertiesFromClasspath(bspath
      + "/buildstamp.properties");
    System.err.println("Usage: " + p.getProperty("pom.name")
      + " -p <config.properties> <command>");
    System.err.println("Version: " + p.getProperty("pom.version"));
  }

  public static void main(String[] argv) throws IOException, ParseException
  {
    Options o = new Options();
    o.addOption("p", "props", true, "Name of configuration properties file");

    CommandLineParser clp = new GnuParser();
    CommandLine cl = clp.parse(o, argv);

    // Load properties file
    Properties config = new Properties();
    try {
      config.load(new FileInputStream(cl.getOptionValue("props")));
    } catch (Throwable t) {
      printUsage();
      System.err.println("ERROR: Could not load properties file: "
        + t.getMessage());
      System.exit(2);
      return;
    }

    // Parse method
    Nexus n = new Nexus(config);
    Action a = null;

    if (isCommand(cl, "imapsync")) {
      int numthr;
      try {
        numthr = Integer.parseInt(cl.getArgs()[1]);
      } catch (Throwable t) {
        printUsage();
        System.err.println("ERROR: imapsync requires a thread count as an "
          + "argument!");
        System.exit(3);
        return;
      }
      Set<String> accs = new HashSet<String>();
      /*
       * If we have additional command line arguments, then ONLY migrate the
       * accounts present on the command line, rather than the full list.
       */
      for (int q = 2; q < cl.getArgs().length; q++)
        accs.add(cl.getArgs()[q]);
      a = new IMAPSyncAction(n, numthr, accs);
    }

    if (a == null) {
      printUsage();
      System.exit(1);
      return;
    }
    a.doAction();
    a.close();
  }

  private static boolean isCommand(CommandLine cl, String match) {
    if (cl.getArgList().size() < 1)
      return false;
    return ((String) cl.getArgList().get(0)).trim().
      equalsIgnoreCase(match);
  }
}
