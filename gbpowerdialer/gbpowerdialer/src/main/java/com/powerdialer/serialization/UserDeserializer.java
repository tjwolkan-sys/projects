package com.powerdialer.serialization;

import java.io.IOException;
import java.sql.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerdialer.model.Group;
import com.powerdialer.model.Server;
import com.powerdialer.model.Skill;
import com.powerdialer.model.Status;
import com.powerdialer.model.Tenant;
import com.powerdialer.model.User;

/**
 * Deserializer for User class
 */
public class UserDeserializer extends JsonDeserializer<User> {

  @Override
  public User deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
    JsonNode node = jp.getCodec().readTree(jp);
    ObjectMapper objectMapper = new ObjectMapper();
    User user = new User();

    if (node.has("id"))
      user.setId(node.get("id").asLong());

    if (node.has("username"))
      user.setUsername(node.get("username").asText());

    if (node.has("firstname"))
      user.setFirstname(node.get("firstname").asText());

    if (node.has("lastname"))
      user.setLastname(node.get("lastname").asText());

    if (node.has("sessionId"))
      user.setSessionId(node.get("sessionId").asLong());

    if (node.has("persistentGroup"))
      user.setGroup(objectMapper.convertValue(node.get("persistentGroup"), Group.class));

    if (node.has("status"))
      user.setStatus(objectMapper.convertValue(node.get("status"), Status.class));

    if (node.has("skillsToWeights")) {
      Map<Skill, Long> skillWeightPair = new HashMap<>();
      for (JsonNode jsonNode : node.get("skillsToWeights"))
        skillWeightPair.put(objectMapper.convertValue(jsonNode.get(0), Skill.class), jsonNode.get(1).asLong());
      user.setSkillWeightPair(skillWeightPair);
    }

    if (node.has("server"))
      user.setServer(objectMapper.convertValue(node.get("server"), Server.class));

    if (node.has("tenant"))
      user.setTenant(objectMapper.convertValue(node.get("tenant"), Tenant.class));

    if (node.has("statusSetDate")) {
      try {
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy hh:mm:ss a", Locale.US);
        user.setStatusSetDate(new Date(dateFormat.parse(node.get("statusSetDate").asText()).getTime()));
      } catch (ParseException e) {
        e.printStackTrace();
      }
    }

    if (node.has("team"))
      if (node.get("team").has("id"))
        user.setTeamId(node.get("team").get("id").asLong());

    return user;
  }

}
