package org.sysmgr.imapmigr;

import java.io.Closeable;

public interface Action extends Closeable
{
  public void doAction();
  public void close();
}
