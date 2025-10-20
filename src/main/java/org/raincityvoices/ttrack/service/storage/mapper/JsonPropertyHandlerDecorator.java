package org.raincityvoices.ttrack.service.storage.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JsonPropertyHandlerDecorator<E, T> implements PropertyHandler<E> {

    private final PropertyHandler<E> base;
    private final JavaType valueType;
    private final ObjectMapper mapper = JsonMapper.builder().build();
    
    public JsonPropertyHandlerDecorator(PropertyHandler<E> base, JavaType valueType) {
        this.base = base;
        this.valueType = valueType;
    }

    public JsonPropertyHandlerDecorator(PropertyHandler<E> base, Class<?> valueType) {
        this.base = base;
        this.valueType = TypeFactory.defaultInstance().constructType(valueType);
    }

    @Override
    public String getName() {
        return base.getName();
    }

    @Override
    public PropertyValue getProperty(E pojo) {
        final PropertyValue baseValue = base.getProperty(pojo);
        final String jsonValue;
        if (baseValue.getValue() == null) {
            jsonValue = null;
        } else {
            try {
                jsonValue = mapper.writeValueAsString(baseValue.getValue());
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize property " + getName() + " as JSON", e);
            }
        }
        return new PropertyValue(getName(), null, jsonValue);
    }

    @Override
    public void setProperty(E pojo, Object jsonValue) {
        if (jsonValue == null) {
            return;
        }
        if (jsonValue instanceof String) {
            Object value;
            try {
                value = mapper.readValue((String)jsonValue, valueType);
            } catch(JsonProcessingException e) {
                throw new RuntimeException("Failed to deserialize property " + getName() + " from JSON value: '" + (String)jsonValue + "'", e);
            }
            base.setProperty(pojo, value);
        } else {
            throw new RuntimeException("Unexpected non-String value used for a JSON-serialized property " + getName());
        }
    }

    @Override
    public boolean isReadOnly() {
        return base.isReadOnly();
    }

}
