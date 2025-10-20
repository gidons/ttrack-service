package org.raincityvoices.ttrack.service.storage.mapper;

import com.azure.data.tables.models.TableEntity;

public interface PropertyHandler<E> {
    String getName();
    PropertyValue getProperty(E pojo);
    void setProperty(E pojo, Object value);
    default void setPropertyFromEntity(E pojo, TableEntity entity) {
        Object value = entity.getProperty(getName());
        setProperty(pojo, value);
    }
    boolean isReadOnly();
}
