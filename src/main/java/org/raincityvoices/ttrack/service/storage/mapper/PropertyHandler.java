package org.raincityvoices.ttrack.service.storage.mapper;

import java.util.List;

import com.azure.data.tables.models.TableEntity;

public interface PropertyHandler<E> {
    String getName();
    default PropertyValue getProperty(E pojo) { 
        throw new UnsupportedOperationException("Either getProperty() or getProperties() must be implemented."); 
    }
    default List<PropertyValue> getProperties(E pojo) { 
        PropertyValue pv = getProperty(pojo);
        return pv == null ? List.of() : List.of(pv); 
    }
    default void setProperty(E pojo, Object value) {
        throw new UnsupportedOperationException("Either setProperty() or setProperties() must be implemented."); 
    }
    default void setProperties(E pojo, TableEntity entity) {
        Object value = entity.getProperty(getName());
        setProperty(pojo, value);
    }
    boolean isReadOnly();
}
