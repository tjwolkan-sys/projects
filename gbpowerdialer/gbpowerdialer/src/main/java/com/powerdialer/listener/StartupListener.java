package com.powerdialer.listener;

import com.powerdialer.util.Constants.APP_CONFIG_PROPERTY;
import com.powerdialer.util.PropertiesUtil;
import org.springframework.util.StringUtils;
import org.springframework.web.context.support.SpringBeanAutowiringSupport;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.util.*;

import static com.powerdialer.util.Constants.APP_CONFIG;

/**
 * StartupListener class used to populate any application-wide information.
 */
public class StartupListener implements ServletContextListener {

  @Override
  public void contextInitialized(ServletContextEvent event) {
    SpringBeanAutowiringSupport.processInjectionBasedOnCurrentContext(this);
    ServletContext context = event.getServletContext();
    context.setAttribute(APP_CONFIG, createApplicationInformationMap(context));
  }

  /**
   * Get application properties and create application information map
   *
   * @param context
   */
  private Map<String, String> createApplicationInformationMap(ServletContext context) {
    List<PropertiesUtil> propertySources = new ArrayList<>();
    if (getClass().getClassLoader().getResource("gbpowerdialer.properties") != null) {
      propertySources.add(new PropertiesUtil("gbpowerdialer.properties"));
    }
    propertySources.add(new PropertiesUtil("application.properties"));
    propertySources.add(new PropertiesUtil("oneaccess.properties"));
    Map<String, String> appConfig = new HashMap<>();
    for (APP_CONFIG_PROPERTY configProperty : APP_CONFIG_PROPERTY.values()) {
      Iterator<PropertiesUtil> propertySourcesIterator = propertySources.iterator();
      boolean configPropertySet = false;
      while (propertySourcesIterator.hasNext() && !configPropertySet) {
        PropertiesUtil propertySource = propertySourcesIterator.next();
        String value = propertySource.getProperty(configProperty.getPropertyName());
        if (StringUtils.hasLength(value)) {
          appConfig.put(configProperty.getPropertyName(), value);
          configPropertySet = true;
        }
      }
    }
    return appConfig;
  }

  @Override
  public void contextDestroyed(ServletContextEvent event) {

  }

}
