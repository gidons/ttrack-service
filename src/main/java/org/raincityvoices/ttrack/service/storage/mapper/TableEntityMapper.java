package org.raincityvoices.ttrack.service.storage.mapper;

import java.beans.PropertyDescriptor;
import java.beans.Transient;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;

import com.azure.core.util.ETag;
import com.azure.data.tables.implementation.TablesConstants;
import com.azure.data.tables.models.TableEntity;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import lombok.Getter;
import lombok.experimental.PackagePrivate;

/**
 * A serializer/deserializer for table entities, similar to DDB's DynamoDBMapper.
 */
public class TableEntityMapper<E> {

    @Getter
    private final Class<E> entityClass;

    private final ImmutableList<PropertyHandler<E>> propertyHandlers;
    private final Constructor<E> constructor;

    public TableEntityMapper(Class<E> entityClass) {
        this.entityClass = entityClass;
        propertyHandlers = findPropertyProviders(entityClass);
        constructor = findConstructor(entityClass);
    }

    private Constructor<E> findConstructor(Class<E> cls) {
        try {
            return entityClass.getConstructor();
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Class " + entityClass + " does not have a no-arg constructor.");
        } catch (SecurityException e) {
            throw new RuntimeException("Failed to get a no-arg constructor for class " + entityClass, e);
        }
    }

    public TableEntity toTableEntity(E pojo) {
        List<PropertyValue> propertyValues = getAllPropertyValues(pojo);
        Map<String, Object> properties = new HashMap<>();
        for (PropertyValue pv : propertyValues) {
            pv.addToMap(properties);
        }
        Object partitionKey = properties.remove(TablesConstants.PARTITION_KEY);
        if (partitionKey == null) {
            throw new IllegalArgumentException("Missing PartitionKey property");
        }
        Object rowKey = properties.remove(TablesConstants.ROW_KEY);
        if (rowKey == null) {
            rowKey = "";
        }
        TableEntity entity = new TableEntity((String) partitionKey, (String) rowKey);
        entity.setProperties(properties);
        return entity;
    }

    @PackagePrivate List<PropertyValue> getAllPropertyValues(E pojo) {
        List<PropertyValue> propertyValues = propertyHandlers.stream()
                .flatMap(pp -> pp.getProperties(pojo).stream())
                .toList();
        return propertyValues;
    }

    public E fromTableEntity(TableEntity entity) {
        final E pojo;
        try {
            pojo = constructor.newInstance();
        } catch(Exception e) {
            throw new RuntimeException("Failed to instantiate " + entityClass, e);
        }
        for (PropertyHandler<E> ph : propertyHandlers) {
            try {
                ph.setProperties(pojo, entity);
            } catch(IllegalArgumentException e) {
                throw new IllegalArgumentException("Unsupported value for property " + ph.getName(), e);
            } catch(Exception e) {
                throw new RuntimeException("Failed to set value for property " + ph.getName(), e);
            }
        }
        return pojo;
    }

    @VisibleForTesting
    ImmutableList<PropertyHandler<E>> findPropertyProviders(Class<E> cls) {
        ImmutableList.Builder<PropertyHandler<E>> builder = ImmutableList.builder();
        PropertyDescriptor[] beanProps = BeanUtils.getPropertyDescriptors(entityClass);
        for (java.beans.PropertyDescriptor bp : beanProps) {
            if (bp.getName().equals("class")) { continue; }
            PropertyHandler<E> handler = createPropertyHandler(bp);
            if (handler != null) {
                builder.add(handler);
            }
        }
        return builder.build();
    }

    @VisibleForTesting 
    static <E> PropertyHandler<E> createPropertyHandler(PropertyDescriptor descriptor) {
        Method getter = descriptor.getReadMethod();
        if (getter == null) {
            return null;
        }
        if (getter.getAnnotation(Transient.class) != null) {
            return null;
        }
        if (getter.getAnnotation(Embedded.class) != null) {
            return new EmbeddedPropertyHandler<E>(descriptor);
        }
        final String name;
        final String odataType;
        Property propAnnotation = getter.getAnnotation(Property.class);
        if (getter.getAnnotation(PartitionKey.class) != null) {
            name = TablesConstants.PARTITION_KEY;
        } else if (getter.getAnnotation(RowKey.class) != null) {
            name = TablesConstants.ROW_KEY;
        } else if (getter.getAnnotation(Timestamp.class) != null) {
            name = TablesConstants.TIMESTAMP_KEY;
        } else if (propAnnotation != null && !propAnnotation.value().isBlank()) {
            name = propAnnotation.value();
        } else if (getter.getReturnType().equals(ETag.class) || getter.getAnnotation(org.raincityvoices.ttrack.service.storage.mapper.ETag.class) != null) {
            name = TablesConstants.ODATA_ETAG_KEY;
        } else {
            // This allows a property named "parititionKey" or "rowKey" to be used as PK or RK.
            // It also conforms to the apparent standard PascalCase convention for Tables properties.
            name = StringUtils.capitalize(descriptor.getName());
        }
        if (name.equals(TablesConstants.PARTITION_KEY) || name.equals(TablesConstants.ROW_KEY)) {
            if (getter.getReturnType() != String.class) {
                throw new IllegalArgumentException("Property used for " + name + " is not a String");
            }
        }
        if (name.equals(TablesConstants.TIMESTAMP_KEY)) {
            if (!DateHelper.SUPPORTED_TYPES.contains(getter.getReturnType())) {
                throw new IllegalArgumentException("Property used for " + name + " is not a supported date/time type.");
            }
        }
        if (name.equals(TablesConstants.ODATA_ETAG_KEY)) {
            if (getter.getReturnType() != ETag.class && getter.getReturnType() != String.class) {
                throw new IllegalArgumentException("Property used for ETag must be a String or ETag.");
            }
        }

        boolean jsonSerialize = false;
        String annotatedType = (propAnnotation != null) ? StringUtils.trim(propAnnotation.type()) : null;
        if (StringUtils.isEmpty(annotatedType)) {
            odataType = null;
        } else if (annotatedType.startsWith("Edm.")) {
            odataType = annotatedType;
        } else if (annotatedType.equals("json")) {
            odataType = null;
            jsonSerialize = true;
        } else {
            throw new IllegalArgumentException("Unsupported type '" + annotatedType + "' in @Property: only EDM type tags or 'json' are allowed.");
        }

        PropertyHandler<E> baseHandler = new BeanUtilsPropertyHandler<>(name, descriptor, odataType);
        if (name.equals(TablesConstants.TIMESTAMP_KEY)) {
            return new TimestampPropertyHandlerDecorator<>(baseHandler, getter.getReturnType());
        } else if (name.equals(TablesConstants.ODATA_ETAG_KEY)) {
            return new ETagPropertyHandlerDecorator<>(baseHandler, getter.getReturnType());
        } else if (jsonSerialize) {
            return new JsonPropertyHandlerDecorator<>(baseHandler, TypeFactory.defaultInstance().constructType(getter.getGenericReturnType()));
        } else {
            return baseHandler;
        }
    }
}
