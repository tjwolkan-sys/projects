package com.powerdialer.serialization;

import java.io.IOException;
import java.util.Date;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.powerdialer.model.ResultCode;
import com.powerdialer.util.Constants;

/**
 * Deserializer for ResultCode class
 */
public class ResultCodeDeserializer extends JsonDeserializer<ResultCode> {

  @Override
  public ResultCode deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
    JsonNode node = jp.getCodec().readTree(jp);
    ResultCode resultCode = new ResultCode();

    if (node.has("id"))
      resultCode.setId(node.get("id").asLong());

    if (node.has("resultCodeText"))
      resultCode.setName(node.get("resultCodeText").asText());

    if (node.has("final"))
      resultCode.setFinalStatus(node.get("final").asBoolean());

    if (node.has("dnc"))
      resultCode.setDncIdentifier(node.get("dnc").asBoolean());

    if (node.has("callbackNumber"))
      resultCode.setCallbackNumber(node.get("callbackNumber").asText());

    if (node.has("callbackDate"))
      resultCode.setCallbackDate(new Date(node.get("callbackDate").asLong()));

    if (node.has("callbackType")) {
      int callbackType = node.get("callbackType").asInt();
      resultCode.setCallbackType(Constants.CALLBACKTYPE.fromId(callbackType));
    }

    return resultCode;
  }

}
