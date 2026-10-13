package com.powerdialer.serialization;

import java.io.IOException;

import org.asteriskjava.manager.action.OriginateAction;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

/**
 * JSON serializer for CustomOriginateAction class
 */
public class OriginateActionSerializer extends JsonSerializer<OriginateAction> {

  @Override
  public void serialize(OriginateAction value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
    jgen.writeStartObject();
    jgen.writeStringField("channel", value.getChannel());
    jgen.writeStringField("context", value.getContext());
    if (value.getPriority() != null)
      jgen.writeNumberField("priority", value.getPriority());
    jgen.writeBooleanField("async", value.getAsync());
    if (value.getTimeout() != null)
      jgen.writeNumberField("timeout", value.getTimeout());
    jgen.writeObjectField("variables", value.getVariables());
    jgen.writeStringField("actionId", value.getActionId());
    jgen.writeStringField("account", value.getAccount());
    jgen.writeStringField("exten", value.getExten());
    jgen.writeEndObject();
  }

}
