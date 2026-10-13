package com.powerdialer.serialization;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.powerdialer.model.Server;

/**
 * Serializer for Server class
 */
public class ServerSerializer extends JsonSerializer<Server> {

  @Override
  public void serialize(Server value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
    jgen.writeStartObject();
    jgen.writeObjectField("id", value.getId());
    jgen.writeObjectField("ip", value.getIp());
    jgen.writeEndObject();
  }

}
