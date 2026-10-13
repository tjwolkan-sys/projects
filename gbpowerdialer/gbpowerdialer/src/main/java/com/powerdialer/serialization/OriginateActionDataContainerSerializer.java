package com.powerdialer.serialization;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.powerdialer.engine.asterisk.actions.OriginateActionDataContainer;

/**
 * Serializer for OriginateActionDataContainer class
 */
public class OriginateActionDataContainerSerializer extends JsonSerializer<OriginateActionDataContainer> {

  @Override
  public void serialize(OriginateActionDataContainer value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
    jgen.writeStartObject();
    jgen.writeObjectField("originateAction", value.getOriginateAction());
    jgen.writeObjectField("call", value.getCall());
    jgen.writeObjectField("campaign", value.getCampaign());
    jgen.writeObjectField("server", value.getServer());
    jgen.writeEndObject();
  }

}
