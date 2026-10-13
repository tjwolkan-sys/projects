package com.powerdialer.util;

import java.io.InputStream;

import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class PropertiesUtil {

  private static final Log LOG = LogFactory.getLog(PropertiesUtil.class);

  private String propertiesFileName;
  private Properties properties;

  public Properties getProperties() {
    return properties;
  }

  public void setProperties(Properties properties) {
    this.properties = properties;
  }

  public String getPropertiesFileName() {
    return propertiesFileName;
  }

  public void setPropertiesFileName(String propertiesFileName) {
    this.propertiesFileName = propertiesFileName;
  }

  public PropertiesUtil(String propertiesFileName) {
    this.propertiesFileName = propertiesFileName;
  }

  public String getProperty(String key) {
    if (key == null) {
      LOG.info("Key is NULL.");
      return "";
    }
    if (getProperties() == null) {
      properties = loadProperties();
    }
    return getProperties().getProperty(key);
  }

  private Properties loadProperties() {
    Properties properties = new Properties();
    try (InputStream stream = this.getClass().getClassLoader().getResourceAsStream(getPropertiesFileName())) {
      if (stream != null) {
        properties.load(stream);
        LOG.info(getPropertiesFileName() + " is loaded.");
      }
    } catch (Exception e) {
      LOG.error("", e);
    }
    return properties;
  }

}
