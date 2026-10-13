package com.powerdialer.redis.message;

import java.io.Serializable;

import com.powerdialer.util.Constants.MSG_ACTION_TYPE;
import com.powerdialer.util.Constants.MSG_ENTITY_TYPE;

public class RedisMessage implements Serializable {

  private static final long serialVersionUID = -7931580531738612821L;
  private MSG_ACTION_TYPE type;
  private MSG_ENTITY_TYPE entityType;
  private Object serializableObject;

  public RedisMessage() {
  }

  public RedisMessage(MSG_ACTION_TYPE type, MSG_ENTITY_TYPE entityType, Object serializableObject) {
    this();
    this.type = type;
    this.entityType = entityType;
    this.serializableObject = serializableObject;
  }

  public MSG_ACTION_TYPE getType() {
    return type;
  }

  public void setType(MSG_ACTION_TYPE type) {
    this.type = type;
  }

  public MSG_ENTITY_TYPE getEntityType() {
    return entityType;
  }

  public void setEntityType(MSG_ENTITY_TYPE entityType) {
    this.entityType = entityType;
  }

  public Object getSerializableObject() {
    return serializableObject;
  }

  public void setSerializableObject(Object serializedObject) {
    this.serializableObject = serializedObject;
  }

  @Override
  public String toString() {
    return "RedisMessage [type=" + type + ", entityType=" + entityType + ", serializableObject=" + serializableObject + "]";
  }

}
