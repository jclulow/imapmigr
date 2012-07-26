package org.sysmgr.imapmigr;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import org.postgresql.ds.PGPoolingDataSource;

public class MTSImplPostgres implements MigrationTrackingStore
{

  private PGPoolingDataSource pool;

  public MTSImplPostgres(String hostname, int port, String database,
    String username, String password, int maxConns)
  {
    pool = new PGPoolingDataSource();
    pool.setDataSourceName("Outmail-MigrationTrackingStore");
    pool.setServerName(hostname);
    pool.setPortNumber(port);
    pool.setDatabaseName(database);
    pool.setUser(username);
    pool.setPassword(password);
    pool.setMaxConnections(maxConns);
  }

  public void close()
  {
  }

  private void checkOpen()
  {
    //if (closed)
    //  throw new IllegalStateException("This Migration Tracking Store has been closed.");
  }

  public Set<String> getMigratedForUser(String username)
  {
    Connection c = null;
    PreparedStatement ps = null;
    ResultSet rs = null;
    try {
      c = pool.getConnection();

      String query = ""
        + "SELECT "
        + "   FINGERPRINT "
        + "FROM "
        + "   MAILS "
        + "WHERE "
        + "   USERNAME = ? ";
      ps = c.prepareStatement(query, ResultSet.TYPE_SCROLL_INSENSITIVE,
        ResultSet.CONCUR_READ_ONLY);
      ps.setString(1, username);
      rs = ps.executeQuery();
      Set<String> mails = new HashSet<String>();
      while (rs.next())
        mails.add(username + "/" + rs.getString("FINGERPRINT"));
      // XXX Should alter this interface to return set without username/ prefix
      //    on every entry.
      return mails;
    } catch (SQLException sqle) {
      throw new RuntimeException("Could not get migrated user set for "
        + username + ": SQLException: " + sqle.getMessage(), sqle);
    } finally {
      Utils.closeQuietly(rs);
      Utils.closeQuietly(c);
    }
  }

  public boolean isMigrated(String username, String fingerprint)
  {
    Connection c = null;
    PreparedStatement ps = null;
    ResultSet rs = null;
    try {
      c = pool.getConnection();

      String query = ""
        + "SELECT "
        + "   FINGERPRINT "
        + "FROM "
        + "   MAILS "
        + "WHERE "
        + "   USERNAME = ? AND FINGERPRINT = ? ";
      ps = c.prepareStatement(query, ResultSet.TYPE_SCROLL_INSENSITIVE,
        ResultSet.CONCUR_READ_ONLY);
      ps.setString(1, username);
      ps.setString(2, fingerprint);
      rs = ps.executeQuery();
      return rs.next(); // true only if there's a matching row
    } catch (SQLException sqle) {
      throw new RuntimeException("Could not get migrated mail status for "
        + username + ": SQLException: " + sqle.getMessage(), sqle);
    } finally {
      Utils.closeQuietly(rs);
      Utils.closeQuietly(c);
    }
  }

  public boolean isUserDone(String username)
  {
    Connection c = null;
    PreparedStatement ps = null;
    ResultSet rs = null;
    try {
      c = pool.getConnection();

      String query = ""
        + "SELECT "
        + "   USERNAME "
        + "FROM "
        + "   DONELIST "
        + "WHERE "
        + "   USERNAME = ? ";
      ps = c.prepareStatement(query, ResultSet.TYPE_SCROLL_INSENSITIVE,
        ResultSet.CONCUR_READ_ONLY);
      ps.setString(1, username);
      rs = ps.executeQuery();
      return rs.next(); // true only if there's a matching row
    } catch (SQLException sqle) {
      throw new RuntimeException("Could not get user donelist status for "
        + username + ": SQLException: " + sqle.getMessage(), sqle);
    } finally {
      Utils.closeQuietly(rs);
      Utils.closeQuietly(c);
    }
  }

  protected void markUserDone(String username, String payload)
  {
    Connection c = null;
    PreparedStatement ps = null;
    try {
      c = pool.getConnection();
      c.setAutoCommit(true);

      String query = ""
        + "INSERT INTO DONELIST "
        + "   (USERNAME, PAYLOAD) "
        + "   VALUES (?, ?) ";
      ps = c.prepareStatement(query);
      ps.setString(1, username);
      ps.setString(2, payload);
      int uc = ps.executeUpdate();
    } catch (SQLException sqle) {
      if (sqle.getSQLState().equals("23505")) {
        // System.err.println("SQL Exception was OK, 23505 is UNIQUE VIOLATION");
        return;
      }
      throw new RuntimeException("Could not set user donelist status for "
        + username + ": SQLException: " + sqle.getMessage(), sqle);
    } finally {
      Utils.closeQuietly(ps);
      Utils.closeQuietly(c);
    }
  }

  public void markUserDone(String username)
  {
    markUserDone(username, new Date().toString());
  }

  public void recordMigration(String username, String fingerprint, int size,
    String payload)
  {
    Connection c = null;
    PreparedStatement ps = null;
    try {
      c = pool.getConnection();
      c.setAutoCommit(true);

      String query = ""
        + "INSERT INTO MAILS "
        + "   (USERNAME, FINGERPRINT, MAILSIZE, PAYLOAD) "
        + "   VALUES (?, ?, ?, ?) ";
      ps = c.prepareStatement(query);
      ps.setString(1, username);
      ps.setString(2, fingerprint);
      ps.setInt(3, size);
      ps.setString(4, payload);
      int uc = ps.executeUpdate();
    } catch (SQLException sqle) {
      if (sqle.getSQLState().equals("23505")) {
        // System.err.println("SQL Exception was OK, 23505 is UNIQUE VIOLATION");
        return;
      }
      throw new RuntimeException("Could not set user donelist status for "
        + username + ": SQLException: " + sqle.getMessage(), sqle);
    } finally {
      Utils.closeQuietly(ps);
      Utils.closeQuietly(c);
    }
  }

  public void log(String system, String username, String payload)
  {
    Connection c = null;
    PreparedStatement ps = null;
    try {
      c = pool.getConnection();
      c.setAutoCommit(true);

      String query = ""
        + "INSERT INTO LOG "
        + "   (SYSTEM, USERNAME, PAYLOAD) "
        + "   VALUES (?, ?, ?) ";
      ps = c.prepareStatement(query);
      ps.setString(1, system);
      ps.setString(2, username);
      ps.setString(3, payload);
      int uc = ps.executeUpdate();
    } catch (SQLException sqle) {
      throw new RuntimeException("Could not add log message: (" +
        system + ", " + username + ", " + payload + ") " +
        ": SQLException: " + sqle.getMessage(), sqle);
    } finally {
      Utils.closeQuietly(ps);
      Utils.closeQuietly(c);
    }
  }

}
