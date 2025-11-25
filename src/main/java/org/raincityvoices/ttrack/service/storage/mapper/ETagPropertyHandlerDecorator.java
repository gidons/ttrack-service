package org.raincityvoices.ttrack.service.storage.mapper;

import com.azure.core.util.ETag;

public class ETagPropertyHandlerDecorator<T> extends PropertyHandlerDecorator<T> {

    public ETagPropertyHandlerDecorator(PropertyHandler<T> base) {
        super(base);
    }

    @Override
    public Object convertToTableType(Object pojoValue) {
        ETag eTag = (ETag) pojoValue;
        return eTag == null ? null : eTag.toString();
    }

    @Override
    public Object convertToPojoType(Object tableValue) {
        String eTagString = (String) tableValue;
        // Apparent bug in Azure: the ETag header is returned missing the final quote?
        return eTagString == null ? null : new ETag(eTagString + '\"');
    }

}
