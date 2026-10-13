package com.powerdialer.serialization;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.powerdialer.model.Queue;

/**
 * Deserializer for Queue class
 */
public class QueueDeserializer extends JsonDeserializer<Queue> {

  @Override
  public Queue deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
    JsonNode node = jp.getCodec().readTree(jp);
    Queue queue = new Queue();

    if (node.has("id"))
      queue.setId(node.get("id").asLong());

    if (node.has("queueName"))
      queue.setName(node.get("queueName").asText());

    return queue;
  }

}
