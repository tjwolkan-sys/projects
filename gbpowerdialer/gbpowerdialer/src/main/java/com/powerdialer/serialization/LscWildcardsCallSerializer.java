package com.powerdialer.serialization;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.powerdialer.model.Call;

/**
 * Custom JSON serializer for the {@link Call} class used in the {@link com.powerdialer.engine.LSCEngine}.
 * This serializer ensures that only relevant fields of the {@link Call} object are serialized
 * when evaluating wildcards in LSCEngine.
 *
 * IMPORTANT: If existing field names in the {@link Call} class change,
 * this serializer MUST NOT be modified to maintain proper functionality.
 * If existing field names in this serializer are updated, the LSCEngine wildcard processing might break.
 */
public class LscWildcardsCallSerializer extends JsonSerializer<Call> {

    @Override
    public void serialize(Call value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeStartObject();
        jgen.writeStringField("callerId", value.getCallerId());
        jgen.writeStringField("calledNumber", value.getCalledNumber());
        jgen.writeStringField("attempts", String.valueOf(value.getAttempts()));
        jgen.writeEndObject();
    }

}
