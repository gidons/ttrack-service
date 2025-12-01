package org.raincityvoices.ttrack.service.storage.mapper;

import com.azure.core.util.ETag;

public class ETagPropertyHandlerDecorator<T> extends PropertyHandlerDecorator<T> {

    private Class<?> valueType;

    public ETagPropertyHandlerDecorator(PropertyHandler<T> base, Class<?> valueType) {
        super(base);
        if (valueType != String.class && valueType != ETag.class) {
            throw new IllegalArgumentException("Specified " + valueType + " as ETag attribute type; only String and ETag are supported.");
        }
        this.valueType = valueType;
    }

    @Override
    public Object convertToTableType(Object pojoValue) {
        final String original;
        if (valueType == ETag.class) {
            ETag eTag = (ETag) pojoValue;
            original = (eTag == null ? null : eTag.toString());
        } else if (valueType == String.class) {
            original = (String) pojoValue;
        } else {
            throw new IllegalStateException();
        }
        return original == null ? null : original.endsWith("\"") ? original : original + '\"';
    }

    @Override
    public Object convertToPojoType(Object tableValue) {
        assert tableValue != null;
        final String original = (String)tableValue;
        // Apparent bug in Azure: the ETag header is returned missing the final quote?
        final String eTagString = original.endsWith("\"") ? original : original + '\"';
        return valueType == ETag.class ? new ETag(eTagString) : eTagString;
    }

}
