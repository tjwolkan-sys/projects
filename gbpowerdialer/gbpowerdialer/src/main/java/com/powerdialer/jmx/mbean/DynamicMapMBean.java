package com.powerdialer.jmx.mbean;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.DynamicMBean;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;

public abstract class DynamicMapMBean implements DynamicMBean {

  private static final long REFRESH_INTERVAL_MS = 60000;

  private MBeanInfo mbeanInfo;
  private final String mBeanClassName;
  private final Supplier<Map<String, Long>> metricMapSupplier;
  private long lastPropertiesRefreshTimestamp;

  /**
   * Construct MBean with specified name and dynamic properties supplied by metricMapSupplier
   * @param mBeanClassName - The name of the Java class of the MBean
   * @param metricMapSupplier
   */
  public DynamicMapMBean(String mBeanClassName, Supplier<Map<String, Long>> metricMapSupplier) {
    this.mBeanClassName = mBeanClassName;
    this.metricMapSupplier = metricMapSupplier;
    rebuildDynamicMBeanInfo();
  }

  public abstract String getPropertiesPrefix();

  /**
   * Get time (in milliseconds) spent for executing last query for specified campaign
   * @param attribute
   * @return
   */
  @Override
  public Object getAttribute(String attribute) {
    return metricMapSupplier.get().get(attribute.replaceAll(getPropertiesPrefix(), ""));
  }

  @Override
  public AttributeList getAttributes(String[] attributes) {
    if (attributes == null) {
      return new AttributeList();
    }
    final List<Attribute> result = new ArrayList<>(attributes.length);
    for (String attr : attributes) {
      Long queryTime = metricMapSupplier.get().get(attr.replaceAll(getPropertiesPrefix(), ""));
      result.add(new Attribute(attr, queryTime));
    }
    return new AttributeList(result);
  }

  @Override
  public MBeanInfo getMBeanInfo() {
    if (isPropertiesStaled()) {
      rebuildDynamicMBeanInfo();
      lastPropertiesRefreshTimestamp = System.currentTimeMillis();
    }
    return mbeanInfo;
  }

  private boolean isPropertiesStaled() {
    return System.currentTimeMillis() - lastPropertiesRefreshTimestamp > REFRESH_INTERVAL_MS;
  }

  @Override
  public Object invoke(String arg0, Object[] arg1, String[] arg2) {
    return null;
  }

  @Override
  public void setAttribute(Attribute arg0) {
  }

  @Override
  public AttributeList setAttributes(AttributeList arg0) {
    return null;
  }

  private void rebuildDynamicMBeanInfo() {
    Map<String, Long> metricMap = metricMapSupplier.get();
    MBeanAttributeInfo[] mbeanAttributesInfo = new MBeanAttributeInfo[metricMap.size()];
    int i = 0;
    for (Map.Entry<String, Long> queryInfoEntry : metricMap.entrySet()) {
      mbeanAttributesInfo[i] = new MBeanAttributeInfo(getPropertiesPrefix() + queryInfoEntry.getKey(), "java.lang.Long", "", true, false, false);
      i++;
    }
    mbeanInfo = new MBeanInfo(mBeanClassName, "", mbeanAttributesInfo, null, null, null);
  }

}
