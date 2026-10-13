package com.powerdialer.serialization;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.powerdialer.model.Skill;

/**
 * Deserializer for Skill class
 */
public class SkillDeserializer extends JsonDeserializer<Skill> {

  @Override
  public Skill deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
    JsonNode node = jp.getCodec().readTree(jp);
    Skill skill = new Skill();

    if (node.has("id"))
      skill.setId(node.get("id").asLong());

    if (node.has("skillName"))
      skill.setName(node.get("skillName").asText());

    return skill;
  }

}
