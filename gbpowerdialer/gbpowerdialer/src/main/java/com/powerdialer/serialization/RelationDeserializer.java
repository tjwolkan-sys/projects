package com.powerdialer.serialization;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.powerdialer.model.Relation;

import java.io.IOException;

public class RelationDeserializer extends JsonDeserializer<Relation> {

    @Override
    public Relation deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
        JsonNode node = jp.getCodec().readTree(jp);
        Relation relationInfo = new Relation();

        if (node.has("type"))
            relationInfo.setType(node.get("type").asInt());
        if (node.has("description"))
            relationInfo.setDescription(node.get("description").asText());

        return relationInfo;
    }
}