package com.powerdialer.serialization;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerdialer.model.*;

import java.io.IOException;

public class AnamolyReportResultDeserializer extends JsonDeserializer<AnamolyReportResult> {

    @Override
    public AnamolyReportResult deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
        JsonNode node = jp.getCodec().readTree(jp);
        ObjectMapper objectMapper = new ObjectMapper();
        AnamolyReportResult anamolyReportResult = new AnamolyReportResult();

        if (node.has("parentId"))
            anamolyReportResult.setParentId(node.get("parentId").asText());

        if (node.has("customer"))
            anamolyReportResult.setCustomer(objectMapper.convertValue(node.get("customer"), Customer.class));

        if (node.has("relation"))
            anamolyReportResult.setRelation(objectMapper.convertValue(node.get("relation"), Relation.class));


        return anamolyReportResult;
    }

}
