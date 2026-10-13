package com.powerdialer.serialization;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import com.powerdialer.model.Campaign;

/**
 * JSON serializer for Campaign class
 */
public class CampaignSerializer extends JsonSerializer<Campaign> {

  @Override
  public void serialize(Campaign value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
    jgen.writeStartObject();
    jgen.writeNumberField("id", value.getId());
    jgen.writeObjectField("type", value.getType());
    jgen.writeNumberField("previewRelax", value.getPreviewRelax());
    jgen.writeNumberField("previewTimeout", value.getPreviewTimeout());
    jgen.writeBooleanField("allowPreviewBeep", value.getAllowPreviewBeep());
    jgen.writeStringField("name", value.getName());
    jgen.writeBooleanField("enabled", value.isEnabled());
    jgen.writeEndObject();
  }

}
