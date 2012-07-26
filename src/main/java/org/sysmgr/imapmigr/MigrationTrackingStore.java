package org.sysmgr.imapmigr;

import java.io.Closeable;
import java.util.Set;

public interface MigrationTrackingStore extends Closeable
{
  void close();

  boolean isUserDone(String username);
  void markUserDone(String username);

  void recordMigration(String username, String fingerprint, int size,
    String payload);
  boolean isMigrated(String username, String fingerprint);
  Set<String> getMigratedForUser(String username);

  void log(String system, String username, String payload);
}
