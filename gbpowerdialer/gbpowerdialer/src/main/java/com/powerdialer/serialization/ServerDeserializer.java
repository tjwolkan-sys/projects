package com.powerdialer.serialization;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import org.asteriskjava.manager.ManagerConnectionState;

import com.powerdialer.model.Server;
import com.powerdialer.util.Constants;

/**
 * Deserializer for Server class
 */
public class ServerDeserializer extends JsonDeserializer<Server> {

  @Override
  public Server deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
    JsonNode node = jp.getCodec().readTree(jp);
    Server server = new Server();

    if (node.has("id"))
      server.setId(node.get("id").asLong());

    if (node.has("ip"))
      server.setIp(node.get("ip").asText());

    if (node.has("name"))
      server.setName(node.get("name").asText());

    if (node.has("domain"))
      server.setDomain(node.get("domain").asText());

    if (node.has("properties"))
      server.setProperties(node.get("properties").asText());

    if (node.has("userCount"))
      server.setUserCount(node.get("userCount").asInt());

    if (node.has("master"))
      server.setMaster(node.get("master").asBoolean());

    if (node.has("enabled"))
      server.setEnabled(node.get("enabled").asBoolean());

    if (node.has("inUse"))
      server.setInUse(node.get("inUse").asBoolean());

    if (node.has("deleted"))
      server.setDeleted(node.get("deleted").asBoolean());

    if (node.has("connectionState")) {
      try {
        ManagerConnectionState connectionState = ManagerConnectionState.valueOf(node.get("connectionState").asText());
        server.setConnectionState(connectionState);
      } catch (Exception e) {
        server.setConnectionState(null);
      }
    }

    if (node.has("maxLines")) {
      server.setMaxLines(node.get("maxLines").asInt());
    }

    if (node.has("cpsLimit")) {
      server.setCpsLimit(node.get("cpsLimit").asInt());
    }

    if (node.has("type")) {
      Constants.SERVER_TYPE[] serverTypes = Constants.SERVER_TYPE.values();
      int ordinal = node.get("type").asInt();
      if (ordinal >= 0 && ordinal < serverTypes.length)
        server.setType(serverTypes[ordinal]);
    }

    return server;
  }

}
