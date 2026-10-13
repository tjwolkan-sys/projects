package com.powerdialer.serialization;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerdialer.model.CallResultCode;
import com.powerdialer.model.ResultCode;
import com.powerdialer.util.Constants;

/**
 * Deserializer for CallResultCode class
 */
public class CallResultCodeDeserializer extends JsonDeserializer<CallResultCode> {

  @Override
  public CallResultCode deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
    JsonNode node = jp.getCodec().readTree(jp);
    ObjectMapper objectMapper = new ObjectMapper();
    CallResultCode callResultCode = new CallResultCode();

    if (node.has("userId"))
      callResultCode.setUserId(node.get("userId").asLong());

    if (node.has("callId"))
      callResultCode.setCallId(node.get("callId").asText());

    if (node.has("parentId"))
      callResultCode.setParentId(node.get("parentId").asText());

    if (node.has("resultCode"))
      callResultCode.setResultCode(objectMapper.convertValue(node.get("resultCode"), ResultCode.class));

    // Opsiyonel additive alan: yoksa, null'sa veya bilinmeyen değerse null
    // kalır (geriye dönük uyumluluk — PHONE davranışı).
    if (node.has("dncScope") && !node.get("dncScope").isNull()) {
      try {
        callResultCode.setDncScope(Constants.DNC_SCOPE.valueOf(node.get("dncScope").asText()));
      } catch (IllegalArgumentException e) {
        // Bilinmeyen scope değeri: contract gereği sessizce null bırakılır.
      }
    }

    return callResultCode;
  }

}
