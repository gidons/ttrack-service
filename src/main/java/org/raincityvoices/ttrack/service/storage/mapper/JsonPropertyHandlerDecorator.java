package org.raincityvoices.ttrack.service.storage.mapper;

import org.raincityvoices.ttrack.service.util.JsonUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JsonPropertyHandlerDecorator<E> extends PropertyHandlerDecorator<E> {

    private final JavaType valueType;
    private final ObjectMapper mapper = JsonUtils.newMapper();
    
    public JsonPropertyHandlerDecorator(PropertyHandler<E> base, JavaType valueType) {
        super(base);
        this.valueType = valueType;
    }

    public JsonPropertyHandlerDecorator(PropertyHandler<E> base, Class<?> valueType) {
        super(base);
        this.valueType = TypeFactory.defaultInstance().constructType(valueType);
    }

    @Override
    public Object convertToTableType(Object pojoValue) {
        assert pojoValue != null;
        try {
            return mapper.writeValueAsString(pojoValue);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize property " + getName() + " as JSON", e);
        }
    }

    @Override
    public Object convertToPojoType(Object jsonValue) {
        assert jsonValue != null;
        if (jsonValue instanceof String) {
            try {
                return mapper.readValue((String)jsonValue, valueType);
            } catch(JsonProcessingException e) {
                throw new RuntimeException("Failed to deserialize property " + getName() + " from JSON value: '" + (String)jsonValue + "'", e);
            }
        } else {
            throw new RuntimeException("Unexpected non-String value used for a JSON-serialized property " + getName());
        }
    }

}
