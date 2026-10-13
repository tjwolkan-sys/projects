package com.powerdialer.serialization;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.powerdialer.model.Customer;

import java.io.IOException;

public class CustomerDeserializer extends JsonDeserializer<Customer> {

    @Override
    public Customer deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
        JsonNode node = jp.getCodec().readTree(jp);
        Customer customerInfo = new Customer();

        if (node.has("name"))
            customerInfo.setName(node.get("name").asText());

        if (node.has("surname"))
            customerInfo.setSurname(node.get("surname").asText());

        return customerInfo;
    }
}
