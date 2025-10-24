package org.raincityvoices.ttrack.service.storage.mapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TimestampPropertyHandlerDecorator<E> extends PropertyHandlerDecorator<E> {

    private final Class<?> propType;

    public TimestampPropertyHandlerDecorator(PropertyHandler<E> base, Class<?> propType) {
        super(base);
        this.propType = propType;
        if (!DateHelper.SUPPORTED_TYPES.contains(propType)) {
            throw new IllegalArgumentException("Unsupported property type: " + propType);
        }
    }

    @Override
    public Object convertToPojoType(Object tableValue) {
        return DateHelper.convertFromOffsetDateTime(tableValue, propType);
    }

    @Override
    public Object convertToTableType(Object pojoValue) {
        return DateHelper.convertToOffsetDateTime(pojoValue);
    }

}
