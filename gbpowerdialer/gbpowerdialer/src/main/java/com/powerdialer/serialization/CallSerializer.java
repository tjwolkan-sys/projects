package com.powerdialer.serialization;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.powerdialer.model.Call;

/**
 * JSON serializer for Call class
 */
public class CallSerializer extends JsonSerializer<Call> {

  @Override
  public void serialize(Call value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
    jgen.writeStartObject();
    jgen.writeStringField("id", value.getId());
    if (value.getUser() != null)
      jgen.writeStringField("userName", value.getUser().getUsername());
    jgen.writeStringField("calledNumber", value.getCalledNumber());
    jgen.writeEndObject();
  }

}
