package com.accsaber.backend.service.item;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.entity.item.ItemType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

@Component
public class ItemValueValidator {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final JsonSchemaFactory SCHEMA_FACTORY =
            JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

    public void validate(ItemType type, Object value) {
        JsonNode schemaNode = type.getValueSchema();
        if (schemaNode == null || schemaNode.isNull() || schemaNode.isEmpty()) {
            return;
        }
        if (value == null) {
            throw new ValidationException("value",
                    "items of type '" + type.getKey() + "' require a non-null value");
        }
        JsonNode valueNode = MAPPER.valueToTree(value);
        JsonSchema schema = SCHEMA_FACTORY.getSchema(schemaNode);
        Set<ValidationMessage> errors = schema.validate(valueNode);
        if (errors.isEmpty()) return;

        String summary = errors.stream()
                .map(ValidationMessage::getMessage)
                .collect(Collectors.joining("; "));
        throw new ValidationException("value",
                "value does not match the '" + type.getKey() + "' contract: " + summary);
    }
}
