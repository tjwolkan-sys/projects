package com.powerdialer.util;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import java.util.Properties;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO: Add docs
 */
public class FileReader {

  private static final Logger LOG = LoggerFactory.getLogger(FileReader.class);

  /**
   * TODO: Add docs
   */
  public static String readTextFile(String filename) throws IOException {
    try (FileInputStream inputStream = new FileInputStream(filename)) {
      return IOUtils.toString(inputStream);
    } catch (IOException e) {
      LOG.error("", e);
    }
    return null;
  }

  /**
   * TODO: Add docs
   */
  public static Properties readProperties(boolean classpath, String filename) {
    Properties properties = new Properties();
    InputStream inputStream = null;
    try {
	    inputStream = classpath ? FileReader.class.getResourceAsStream(filename) : new FileInputStream(filename);

      try (Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
        properties.load(reader);
      }
    } catch (Exception e) {
      LOG.error("Locale properties not loaded correctly", e);
    } finally {
      try {
        if (inputStream != null)
          inputStream.close();
      } catch (IOException e) {
        LOG.error("Locale properties not loaded correctly", e);
      }
    }
    return properties;
  }
}
