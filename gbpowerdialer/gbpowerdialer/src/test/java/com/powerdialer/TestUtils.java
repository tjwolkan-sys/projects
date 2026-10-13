package com.powerdialer;

import java.lang.reflect.Field;

/**
 * Utils for testing
 */
public class TestUtils {

  /**
   * Set private static variable to specified value. Notice thar final values
   * can be optimized by compiler so that in bytecode may be no usages of that
   * variable
   * @param clazz
   * @param field
   * @param newValue
   * @throws Exception
   */
  public static void setFinalStatic(Class<?> clazz, String field, Object newValue) throws Exception {
    if (!isTestEnvironment()) {
      throw new SecurityException("This method should only be used in test environments");
    }

    try {
      Field declaredField = clazz.getDeclaredField(field);

      boolean wasAccessible = declaredField.isAccessible();

      try {
        if (!wasAccessible) {
          declaredField.setAccessible(true);
        }

        declaredField.set(null, newValue);

      } finally {
        if (!wasAccessible) {
          declaredField.setAccessible(wasAccessible);
        }
      }
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new Exception("Failed to set static field value: " + e.getMessage(), e);
    }
  }

  /**
   * Checks if the current environment is a test environment.
   *
   * @return true if in a test environment, false otherwise
   */
  private static boolean isTestEnvironment() {
    String env = System.getProperty("environment");
    if (env != null && (env.equals("test") || env.equals("dev"))) {
      return true;
    }

    try {
      Class.forName("org.junit.Test");
      return true;
    } catch (ClassNotFoundException e) {
      // Continue checking other frameworks
    }

    try {
      Class.forName("org.testng.annotations.Test");
      return true;
    } catch (ClassNotFoundException e) {
      // Continue checking other indicators
    }

    String workingDir = System.getProperty("user.dir");
    if (workingDir != null && workingDir.contains("test")) {
      return true;
    }

    return false;
  }
}
