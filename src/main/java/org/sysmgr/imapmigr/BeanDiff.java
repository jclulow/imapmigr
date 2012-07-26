package org.sysmgr.imapmigr;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class BeanDiff<A>
{
  private Set<Change> changes = new HashSet<Change>();

  public Set<Change> getChanges()
  {
    return Collections.unmodifiableSet(changes);
  }

  public static enum ChangeType
  {
    COLLECTION_ADD,
    COLLECTION_REMOVE,
    PROPERTY_REPLACE;
  }

  public class Change
  {

    private ChangeType type = ChangeType.PROPERTY_REPLACE;
    private String propertyName;
    private Object oldValue;
    private Object newValue;

    public Change(ChangeType type, String propertyName, Object oldValue,
      Object newValue)
    {
      this.type = type;
      this.propertyName = propertyName;
      this.oldValue = oldValue;
      this.newValue = newValue;
    }

    public Object getNewValue()
    {
      return newValue;
    }

    public Object getOldValue()
    {
      return oldValue;
    }

    public String getPropertyName()
    {
      return propertyName;
    }

    public ChangeType getType()
    {
      return type;
    }
  }

  public BeanDiff(A left, A right)
  {
    if (!left.getClass().isInstance(right)) {
      throw new RuntimeException("Left and Right Classes must be same type!");
    }

    BeanInfo bi;
    try {
      bi = Introspector.getBeanInfo(left.getClass());
    } catch (IntrospectionException ex) {
      throw new RuntimeException("IntrospectionException: " + ex.getMessage(),
        ex);
    }

    for (PropertyDescriptor pd : bi.getPropertyDescriptors()) {
      Method m = pd.getReadMethod();
      Object leftval;
      Object rightval;

      try {
        leftval = m.invoke(left);
        rightval = m.invoke(right);
      } catch (IllegalAccessException ex) {
        throw new RuntimeException("IllegalAccessException: "
          + ex.getMessage(), ex);
      } catch (IllegalArgumentException ex) {
        throw new RuntimeException("IllegalArgumentException: "
          + ex.getMessage(), ex);
      } catch (InvocationTargetException ex) {
        throw new RuntimeException("InvocationTargetException: "
          + ex.getMessage(), ex);
      }

      if (Set.class.isAssignableFrom(pd.getPropertyType())) {
        // Special Set-handling code
        if (leftval == null && rightval == null) {
          //System.out.println(" " + pd.getName() + ": (null)");
        } else if (leftval == null) {
          //if (((Set) rightval).isEmpty()) {
          // System.out.println("+" + pd.getName() + ": (empty)");
          // XXX Do nothing -- don't really care about structural changes at the moment.
          //} else
          for (Object o : (Set) rightval) {
            // System.out.println("+" + pd.getName() + ": " + o);
            changes.add(new Change(ChangeType.COLLECTION_ADD, pd.getName(), null, o));
          }
        } else if (rightval == null) {
          //if (((Set) leftval).isEmpty()) {
          // System.out.println("-" + pd.getName() + ": (empty)");
          // XXX Do nothing -- don't really care about structural changes at the moment.
          //} else
          for (Object o : (Set) leftval) {
            // System.out.println("-" + pd.getName() + ": " + o);
            changes.add(new Change(ChangeType.COLLECTION_REMOVE, pd.getName(), o, null));
          }
        } else if (!leftval.equals(rightval)) {
          SetDiff diff = new SetDiff((Set) leftval, (Set) rightval);
          for (Object o : diff.getLeftOnlyEntries()) {
            // System.out.println("-" + pd.getName() + ": " + o);
            changes.add(new Change(ChangeType.COLLECTION_REMOVE, pd.getName(), o, null));
          }
          for (Object o : diff.getRightOnlyEntries()) {
            // System.out.println("+" + pd.getName() + ": " + o);
            changes.add(new Change(ChangeType.COLLECTION_ADD, pd.getName(), null, o));
          }
        }
      } else {
        if (leftval == null && rightval == null) {
          //System.out.println(" " + pd.getName() + ": (null)");
        } else if (leftval == null) {
          changes.add(new Change(ChangeType.PROPERTY_REPLACE, pd.getName(), null, rightval));
          // System.out.println("-" + pd.getName() + ": (null)");
          // System.out.println("+" + pd.getName() + ": " + rightval);
        } else if (rightval == null) {
          changes.add(new Change(ChangeType.PROPERTY_REPLACE, pd.getName(), leftval, null));
          // System.out.println("-" + pd.getName() + ": " + leftval);
          // System.out.println("+" + pd.getName() + ": (null)");
        } else if (!leftval.equals(rightval)) {
          changes.add(new Change(ChangeType.PROPERTY_REPLACE, pd.getName(), leftval, rightval));
          // System.out.println("-" + pd.getName() + ": " + leftval);
          // System.out.println("+" + pd.getName() + ": " + rightval);
        }
      }
    }
  }
}
