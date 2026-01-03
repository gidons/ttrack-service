package org.raincityvoices.ttrack.service.storage.mapper;

import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.azure.data.tables.models.TableEntity;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of PropertyHandler that maps a bean property to any number of row attributes
 * using a recursive call to TableEntityMapper.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
@Slf4j
public class EmbeddedPropertyHandler<E> implements PropertyHandler<E> {
    private interface DynamicTypeHelper {
        TableEntityMapper getMapperForValue(Object propertyValue) throws Exception;
        TableEntityMapper getMapperForEntity(TableEntity entity) throws Exception;
    }

    private class StaticTypeHelper implements DynamicTypeHelper {
        private final TableEntityMapper baseMapper = new TableEntityMapper<>(baseClass());
        @Override
        public TableEntityMapper getMapperForEntity(TableEntity entity) { return baseMapper; }
        @Override
        public TableEntityMapper getMapperForValue(Object propertyValue) { return baseMapper; }
    }

    @VisibleForTesting
    class ClassNameAttributeTypeHelper implements DynamicTypeHelper {

        public static final String CLASS_ATTR_SUFFIX = ".class";
        private final String classNameAttr = getName() + CLASS_ATTR_SUFFIX;
        private final Map<Class, TableEntityMapper> mapperByClassName = new HashMap<>();

        @Override
        public TableEntityMapper getMapperForValue(Object propertyValue) throws Exception {
            return mapperByClassName.computeIfAbsent(propertyValue.getClass(), MapperWithTypeProperty::new);
        }

        @Override
        public TableEntityMapper getMapperForEntity(TableEntity entity) throws Exception {
            Class targetClass = resolveType(entity);
            return mapperByClassName.computeIfAbsent(targetClass, MapperWithTypeProperty::new);
        }

        public Class resolveType(TableEntity entity) throws ClassNotFoundException {
            Object className = entity.getProperty(classNameAttr);
            log.info("Class name from entity: {}", className);
            if (className == null) {
                // Fall back to base class
                return getter().getReturnType();
            }
            if (className instanceof String) {
                return Class.forName((String) className);
            }
            throw new RuntimeException(String.format(
                "Class name attribute '%s' has unexpected type: %s", classNameAttr, getter().getReturnType()
            ));
        }
    
    }

    private class MapperWithTypeProperty extends TableEntityMapper<E> {

        private final PropertyValue classPropValue;
        public MapperWithTypeProperty(Class<E> entityClass) {
            super(entityClass);
            classPropValue = new PropertyValue(classPropName(), null, getEntityClass().getName());
        }

        @Override
        List<PropertyValue> getAllPropertyValues(E pojo) {
            List<PropertyValue> otherPropertyValues = super.getAllPropertyValues(pojo);
            return ImmutableList.<PropertyValue>builder()
                .add(classPropValue)
                .addAll(otherPropertyValues)
                .build();
        }
    }

    private final PropertyDescriptor parentDescriptor;
    @Getter
    private final String name;
    private final DynamicTypeHelper typeHelper;

    public EmbeddedPropertyHandler(PropertyDescriptor parentDescriptor) {
        log.info("Constructing EmbeddedPropertyHandler for {}", parentDescriptor.getName());
        this.parentDescriptor = parentDescriptor;
        this.name = StringUtils.capitalize(parentDescriptor.getName());
        Embedded annotation = getter().getAnnotation(Embedded.class);
        log.info("Type policy: {}", annotation == null ? null : annotation.typePolicy());
        if (annotation == null || annotation.typePolicy() == Embedded.TypePolicy.STATIC_ONLY) {
            typeHelper = new StaticTypeHelper();
        } else if (annotation.typePolicy() == Embedded.TypePolicy.CLASSNAME_ATTRIBUTE) {
            typeHelper = new ClassNameAttributeTypeHelper();
        } else {
            throw new IllegalStateException();
        }
    }

    @Override
    public List<PropertyValue> getProperties(E pojo) {
        Object parentValue;
        try {
            parentValue = getter().invoke(pojo);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Failed to get value of property using " + getter());
        }
        if (parentValue == null) {
            return List.of();
        }
        try {
            TableEntityMapper subMapper = typeHelper.getMapperForValue(parentValue);
            return subMapper.getAllPropertyValues(parentValue);
        } catch (Exception e) {
            throw new RuntimeException("Failed to map property value of " + getName(), e);
        }
    }

    @Override
    public void setProperties(E pojo, TableEntity entity) {
        if (isReadOnly()) {
            return;
        }
        Object parentValue;
        try {
            TableEntityMapper subMapper = typeHelper.getMapperForEntity(entity);
            log.info("Using subMapper for entity class {}", subMapper.getEntityClass());
            parentValue = subMapper.fromTableEntity(entity);
        } catch(Exception e) {
            throw new RuntimeException("Failed to instantiate value for property " + getName() + " from entity.", e);
        }
        try {
            setter().invoke(pojo, parentValue);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Failed to set value of property using " + setter(), e);
        }
    }

    @Override
    public boolean isReadOnly() {
        return setter() == null;
    }

    private Method getter() {
        return parentDescriptor.getReadMethod();
    }

    private Method setter() {
        return parentDescriptor.getWriteMethod();
    }

    private Class baseClass() {
        return getter().getReturnType();
    }

    private String classPropName() {
        return StringUtils.capitalize(getName()) + ".class";
    }
}
