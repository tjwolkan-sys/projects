package com.powerdialer.serialization;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerdialer.model.Group;
import com.powerdialer.model.Queue;
import com.powerdialer.model.ResultCode;

/**
 * Deserializer for Group class
 */
public class GroupDeserializer extends JsonDeserializer<Group> {

  private static final String GENERAL_PROPERTIES_OPEN_TAG = "<generalProperties";
  private static final String GENERAL_PROPERTIES_CLOSE_TAG = "</generalProperties>";
  private static final String WRAPUP_TIMER_OPEN_TAG = "<resultCodeTimer>";
  private static final String WRAPUP_TIMER_CLOSE_TAG = "</resultCodeTimer>";

  @Override
  public Group deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
    JsonNode node = jp.getCodec().readTree(jp);
    ObjectMapper objectMapper = new ObjectMapper();
    Group group = new Group();

    if (node.has("id"))
      group.setId(node.get("id").asLong());

    if (node.has("groupName"))
      group.setName(node.get("groupName").asText());

    if (node.has("queues")) {
      Set<Queue> queues = new HashSet<>();
      for (JsonNode jsonNode : node.get("queues"))
        queues.add(objectMapper.convertValue(jsonNode, Queue.class));
      group.setQueues(queues);
    }

    if (node.has("groupResultCodes")) {
      List<ResultCode> resultCodes = new ArrayList<>();
      for (JsonNode jsonNode : node.get("groupResultCodes"))
        resultCodes.add(objectMapper.convertValue(jsonNode, ResultCode.class));
      group.setResultCodes(resultCodes);
    }

    if (node.hasNonNull("groupPropertyXML"))
      group.setWrapupTimerSeconds(parseWrapupTimerSeconds(node.get("groupPropertyXML").asText()));

    return group;
  }

  /**
   * Webphone grup XML'inde (JAXB {@code GroupProperty}) "Wrapup timer" {@code generalProperties}
   * bloğundaki {@code resultCodeTimer} elemanıdır; blok açılış tag'i attribute taşıyabilir ve değer
   * null ise eleman hiç yazılmaz. Yalnız bu blok içinde aranır; yok veya sayı değilse null döner.
   */
  static Integer parseWrapupTimerSeconds(String groupPropertyXml) {
    int tagStart = groupPropertyXml.indexOf(GENERAL_PROPERTIES_OPEN_TAG);
    if (tagStart < 0) {
      return null;
    }
    int tagEnd = groupPropertyXml.indexOf('>', tagStart);
    int blockEnd = groupPropertyXml.indexOf(GENERAL_PROPERTIES_CLOSE_TAG, tagStart);
    if (tagEnd < 0 || blockEnd < 0 || tagEnd > blockEnd) {
      return null;
    }
    String generalProperties = groupPropertyXml.substring(tagEnd + 1, blockEnd);
    int start = generalProperties.indexOf(WRAPUP_TIMER_OPEN_TAG);
    if (start < 0) {
      return null;
    }
    start += WRAPUP_TIMER_OPEN_TAG.length();
    int end = generalProperties.indexOf(WRAPUP_TIMER_CLOSE_TAG, start);
    if (end < 0) {
      return null;
    }
    try {
      return Integer.valueOf(generalProperties.substring(start, end).trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

}
