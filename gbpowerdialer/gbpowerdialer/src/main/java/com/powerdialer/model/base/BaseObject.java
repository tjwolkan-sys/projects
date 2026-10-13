package com.powerdialer.model.base;

import java.util.Objects;
import java.io.Serializable;

@SuppressWarnings("serial")
public abstract class BaseObject implements Serializable {

  /**
   * Returns a multi-line String with key=value pairs.
   * @return a String representation of this class.
   */
  public abstract String toString();

  /**
   * Compares object equality. When using Hibernate, the primary key should not
   * be a part of this comparison.
   * @param o object to compare to
   * @return true/false based on equality tests
   */
  public abstract boolean equals(Object o);

  /**
   * When you override equals, you should override hashCode. See
   * "Why are equals() and hashCode() importation" for more information:
   * http://www.hibernate.org/109.html
   * @return hashCode
   */
  public abstract int hashCode();

  /**
   * Hash generator. For usual cases base and multiplier should be number from
   * 10 till 99. For strongest unique increase this values.
   */
  public static int generateHash(int base, int multiplier, Object... obj) {
    int hash = base;
    for (Object anObj : obj) {
      hash = multiplier * hash + (anObj != null ? anObj.hashCode() : 0);
    }
    return hash;
  }

  /**
   * Concat strings in effective way using StringBuffer
   */
  public static String generateString(Object... text) {
    StringBuilder builder = new StringBuilder();
    for (Object aText : text) {
      builder.append(aText);
    }
    return builder.toString();
  }

  /**
   * Length of parameters should be even Parameters equals by pairs. 1 with 2 ,
   * 3 with 4 , ....
   */
  public static boolean generateEquals(Object... obj) {
    if (obj.length % 2 == 0) {
      for (int i = 0; i < obj.length / 2; i++) {
        if (!eql(obj[i * 2], obj[i * 2 + 1])) {
          return false;
        }
      }
      return true;
    }
    System.out.println("ERROR !!!!!  Check params for equals method");
    return false;
  }

  private static boolean eql(Object o1, Object o2) {
    return Objects.equals(o1, o2);
  }

}
