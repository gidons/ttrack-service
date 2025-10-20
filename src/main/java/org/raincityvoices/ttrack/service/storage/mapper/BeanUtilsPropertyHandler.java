package org.raincityvoices.ttrack.service.storage.mapper;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.OffsetDateTime;

import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Value
@RequiredArgsConstructor
public class BeanUtilsPropertyHandler<E> implements PropertyHandler<E> {
    private final String name;
    private final PropertyDescriptor descriptor;
    private final String odataType;

    BeanUtilsPropertyHandler(String name, PropertyDescriptor descriptor) {
        this(name, descriptor, null);
    }

    @Override
    public boolean isReadOnly() {
        return setter() == null;
    }

    @Override
    public PropertyValue getProperty(E pojo) {
        log.debug("Getting property '{}' using getter {}", name, getter());
        try {
            Object value = getter().invoke(pojo);
            return new PropertyValue(name, odataType, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get property using " + getter(), e);
        }
    }

    @Override
    public void setProperty(E pojo, Object value) {
        log.debug("Setting property '{}' to value '{}' ({}) using setter {}", name, value, value == null ? "<null>" : value.getClass().getSimpleName(), setter());
        if (value == null) {
            return;
        }
        if (setter() == null) {
            throw new RuntimeException("No setter for property " + name);
        }
        if (value instanceof OffsetDateTime && getter().getReturnType() == Instant.class) {
            value = ((OffsetDateTime)value).toInstant();
        }
        try {
            setter().invoke(pojo, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set property using " + setter(), e);
        }
    }

    private Method getter() {
        return descriptor.getReadMethod();
    }

    private Method setter() {
        return descriptor.getWriteMethod();
    }
}
