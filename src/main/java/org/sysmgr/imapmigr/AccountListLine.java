package org.sysmgr.imapmigr;

public class AccountListLine
{
  public String canonicalUsername;
  public String srcUsername;
  public String dstUsername;

  public class EmptyLine extends Exception
  {
    public EmptyLine() {
      super();
    }
  }

  public AccountListLine(String line) throws EmptyLine
  {
    String oline = line;
    line.replaceAll("#.*", ""); // remove shell-style comments
    line = line.trim();
    if (line.length() < 1)
      throw new EmptyLine();

    String[] terms = line.split("|");
    if (terms.length != 3)
      throw new RuntimeException("Malformed input line: " + oline);

    canonicalUsername = terms[0].trim();
    srcUsername = terms[1].trim();
    dstUsername = terms[2].trim();

    if (canonicalUsername.length() < 1 || srcUsername.length() < 1
        || dstUsername.length() < 1)
      throw new RuntimeException("Malformed input line: " + oline);
  }
}
