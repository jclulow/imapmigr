package org.sysmgr.imapmigr;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class SetDiff<E>
{
  private Set<E> minus = new HashSet<E>();
  private Set<E> plus = new HashSet<E>();
  private Set<E> common = new HashSet<E>();

  public SetDiff(Set<E> left, Set<E> right)
  {
    common.addAll(left);
    common.addAll(right);

    Iterator<E> ci = common.iterator();
    while (ci.hasNext()) {
      E ct = ci.next();
      if (!right.contains(ct)) {
        minus.add(ct);
        ci.remove();
      } else if (!left.contains(ct)) {
        plus.add(ct);
        ci.remove();
      }
    }
  }

  public Set<E> getCommonEntries()
  {
    return common;
  }

  public Set<E> getLeftOnlyEntries()
  {
    return minus;
  }

  public Set<E> getRightOnlyEntries()
  {
    return plus;
  }
  
}
