package com.powerdialer.serialization;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.powerdialer.model.User;

/**
 * Serializer for User class
 */
public class UserSerializer extends JsonSerializer<User> {

  @Override
  public void serialize(User value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
    jgen.writeStartObject();
    jgen.writeNumberField("id", value.getId());
    jgen.writeStringField("username", value.getUsername());
    jgen.writeStringField("firstname", value.getFirstname());
    jgen.writeStringField("lastname", value.getLastname());
    jgen.writeObjectField("group", value.getGroup());
    jgen.writeEndObject();
  }

}
