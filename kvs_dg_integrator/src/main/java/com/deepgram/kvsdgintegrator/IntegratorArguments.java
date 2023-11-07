package com.deepgram.kvsdgintegrator;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.apache.commons.lang3.Validate;

import java.io.IOException;
import java.util.*;

/**
 * Arguments passed in the POST body of the `/start-session` endpoint for each new integrator session
 */
public record IntegratorArguments(
        String contactId,
        KvsStream kvsStream,
        @JsonDeserialize(using = DgParamsDeserializer.class) Map<String, List<String>> dgParams,
        boolean enforceRealtime) {
    @JsonCreator
    public IntegratorArguments(
            @JsonProperty(required = true, value = "contactId") String contactId,
            @JsonProperty(required = true, value = "kvsStream") KvsStream kvsStream,
            @JsonProperty(required = true, value = "dgParams") Map<String, List<String>> dgParams,
            @JsonProperty(required = true, value = "enforceRealtime") boolean enforceRealtime
    ) {
        this.contactId = Validate.notNull(contactId);
        this.kvsStream = Validate.notNull(kvsStream);
        this.dgParams = Validate.notNull(dgParams);
        this.enforceRealtime = enforceRealtime;
    }

    private static class DgParamsDeserializer extends JsonDeserializer<Map<String, List<String>>> {
        @Override
        public Map<String, List<String>> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.readValueAsTree();
            Map<String, List<String>> map = new HashMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();

            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String key = field.getKey();
                JsonNode value = field.getValue();

                if (value.isArray()) {
                    int numArrayElements = value.size();
                    if (numArrayElements == 0) {
                        throw JsonMappingException.from(p, "dgParams cannot contain empty arrays");
                    }

                    Iterator<JsonNode> arrayElements = value.elements();
                    List<String> stringsFound = new ArrayList<>();

                    for (int i = 0; i < numArrayElements; i++) {
                        JsonNode element = arrayElements.next();
                        if (!element.isTextual()) {
                            throw JsonMappingException.from(p, "arrays within dgParams can only contain strings");
                        }
                        stringsFound.add(element.asText());
                    }

                    map.put(key, stringsFound);
                } else if (value.isTextual()) {
                    map.put(key, List.of(value.asText()));
                } else {
                    throw JsonMappingException.from(p, "dgParams can only contain strings and arrays of strings");
                }
            }

            return map;
        }
    }

    public static IntegratorArguments fromJson(String json) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(json, IntegratorArguments.class);
    }

    public record KvsStream(String arn, String startFragmentNumber) {
            @JsonCreator
            public KvsStream(
                    @JsonProperty(required = true, value = "arn") String arn,
                    @JsonProperty(required = true, value = "startFragmentNumber") String startFragmentNumber
            ) {
                this.arn = Validate.notNull(arn);
                this.startFragmentNumber = Validate.notNull(startFragmentNumber);
            }
        }
}

