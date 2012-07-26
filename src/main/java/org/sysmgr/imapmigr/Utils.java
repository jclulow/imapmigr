package org.sysmgr.imapmigr;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;
import java.util.zip.GZIPInputStream;
import com.sun.mail.imap.IMAPStore;

public class Utils
{

  public static Properties loadPropertiesFromClasspath(String pfname)
  {
    Properties p = new Properties();
    // ClassLoader cl = ClassLoader.getSystemClassLoader();
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    InputStream is = cl.getResourceAsStream(pfname);
    if (is == null)
      throw new RuntimeException("Could not find " + pfname +" in CLASSPATH");
    try {
      p.load(is);
    } catch (Exception e) {
      throw new RuntimeException("Could not load " + pfname + ": "
        + e.getMessage());
    }
    return p;
  }

  public static String truncate(String s, int limit)
  {
    if (s.length() <= 16)
      return s;
    return s.substring(0, 16);
  }

  public static void printDiff(BeanDiff<?> diff)
  {
    for (BeanDiff.Change c : diff.getChanges()) {
      switch (c.getType()) {
        case COLLECTION_ADD: {
          System.out.println("+" + c.getPropertyName() + ": " + c.getNewValue());
        }
        break;
        case COLLECTION_REMOVE: {
          System.out.println("-" + c.getPropertyName() + ": " + c.getOldValue());
        }
        break;
        case PROPERTY_REPLACE: {
          if (c.getOldValue() != null) {
            System.out.println("-" + c.getPropertyName() + ": " + c.getOldValue());
          }
          System.out.println("+" + c.getPropertyName() + ": " + c.getNewValue());
        }
        break;
      }
    }
  }

  public static void closeQuietly(IMAPStore c)
  {
    try {
      if (c != null)
        c.close();
    } catch (Throwable t) {
    }
  }

  public static void closeQuietly(Closeable c)
  {
    try {
      if (c != null)
        c.close();
    } catch (Throwable t) {
    }
  }

  public static void closeQuietly(Connection c)
  {
    try {
      if (c != null)
        c.close();
    } catch (Throwable t) {
    }
  }

  public static void closeQuietly(Statement s)
  {
    try {
      if (s != null)
        s.close();
    } catch (Throwable t) {
    }
  }

  public static void closeQuietly(ResultSet rs)
  {
    try {
      if (rs != null)
        rs.close();
    } catch (Throwable t) {
    }
  }

  public static void sleep(int millis)
  {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException iee) {
    }
  }

  public static int parseInt(String s, int defval)
  {
    if (s == null)
      return defval;
    try {
      return Integer.parseInt(s);
    } catch (NumberFormatException nfe) {
      return defval;
    }
  }

  public static String convertToHex(byte[] data)
  {
    StringBuffer buf = new StringBuffer();
    for (int i = 0; i < data.length; i++) {
      int halfbyte = (data[i] >>> 4) & 0x0F;
      int two_halfs = 0;
      do {
        if ((0 <= halfbyte) && (halfbyte <= 9)) {
          buf.append((char) ('0' + halfbyte));
        } else {
          buf.append((char) ('a' + (halfbyte - 10)));
        }
        halfbyte = data[i] & 0x0F;
      } while (two_halfs++ < 1);
    }
    return buf.toString();
  }

  public static String getGZIPHash(File f)
  {
    try {
      return getStreamHash(new GZIPInputStream(
        new BufferedInputStream(
        new FileInputStream(f))));
    } catch (IOException ioe) {
      return null;
    }
  }

  public static String getStreamHash(InputStream is)
  {
    if (is == null) {
      return null;
    }
    MessageDigest sha;
    try {
      sha = MessageDigest.getInstance("SHA");
    } catch (NoSuchAlgorithmException nsae) {
      // XXX Should throw exception here
      System.err.println("WARNING: Could get get SHA MessageDigest instance ("
        + nsae + ")");
      return null;
    }
    sha.reset();
    int len = 0;
    byte[] buf = new byte[8192];
    try {
      while ((len = is.read(buf)) != -1) {
        sha.update(buf, 0, len);
      }
      return convertToHex(sha.digest()).toLowerCase();
    } catch (IOException ioe) {
      return null;
    } finally {
      try {
        if (is != null) {
          is.close();
        }
      } catch (Throwable t) {
      }
    }
  }

  public static String getMD5(String input)
  {
    MessageDigest md5;
    try {
      md5 = MessageDigest.getInstance("MD5");
    } catch (NoSuchAlgorithmException ex) {
      throw new RuntimeException("Could not use MD5 hash function.", ex);
    }
    md5.reset();
    md5.update(input.getBytes());
    return convertToHex(md5.digest()).toLowerCase();
  }
}
