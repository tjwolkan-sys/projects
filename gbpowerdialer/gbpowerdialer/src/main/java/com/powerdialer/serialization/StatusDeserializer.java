package com.powerdialer.serialization;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.powerdialer.model.Status;

/**
 * Deserializer for Status class
 */
public class StatusDeserializer extends JsonDeserializer<Status> {

  @Override
  public Status deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
    JsonNode node = jp.getCodec().readTree(jp);
    Status status = new Status();

    if (node.has("id"))
      status.setId(node.get("id").asLong());

    if (node.has("statusText"))
      status.setName(node.get("statusText").asText());

    if (node.has("availableForDialerCalls"))
      status.setAvailableForDialerCalls(node.get("availableForDialerCalls").asBoolean());

    return status;
  }

}
