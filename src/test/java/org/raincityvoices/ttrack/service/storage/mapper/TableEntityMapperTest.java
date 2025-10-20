package org.raincityvoices.ttrack.service.storage.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;

import com.azure.data.tables.models.TableEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class TableEntityMapperTest {

    private static final UUID RANDOM_UUID = UUID.randomUUID();
    private static final InnerEntity INNER_ENTITY = new InnerEntity("foo", 42, ImmutableList.of("a","b","c"));
    private static final String INNER_ENTITY_JSON;

    static {
        try {
            INNER_ENTITY_JSON = new ObjectMapper().writeValueAsString(INNER_ENTITY);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Data
    private static class TestEntity {
        @Getter(onMethod = @__(@PartitionKey))
        String id;
        @Getter(onMethod = @__(@RowKey))
        String sortKey;
        int intProp;
        @Setter(AccessLevel.NONE)
        String strProp;
        @Getter(onMethod = @__(@Property(value="guid", type="Edm.Binary")))
        UUID uuidProp;
        @Getter(onMethod = @__(@Property(type="json")))
        InnerEntity inner;
        List<Integer> numbers;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class InnerEntity {
        String someString;
        int someInt;
        List<String> someList;
    }

    private static class TestEntityWithBadKeyProps {
        int id;
        int sortKey;
        @Getter
        int partitionKey;
        @Getter
        int rowKey;
        @PartitionKey
        public int getId() { return id; }
        public void setId(int id) {}
        @RowKey
        public int getSortKey() { return sortKey; }
        public void setSortKey(int sortKey) { }
    }

    @Test
    public void GIVEN_read_write_property_with_no_annotations_WHEN_createPropertyHandler_THEN_returns_standard_bean_handler() throws IntrospectionException {
        PropertyDescriptor descriptor = new PropertyDescriptor("intProp", TestEntity.class);
        PropertyHandler<TestEntity> handler = TableEntityMapper.createPropertyHandler(descriptor);
        TestEntity entity = new TestEntity();
        handler.setProperty(entity, 42);
        assertEquals(42, entity.getIntProp());
        PropertyValue pv = handler.getProperty(entity);
        assertEquals("IntProp", pv.getName());
        assertNull(pv.getOdataType());
        assertEquals(42, pv.getValue());
    }

    @Test
    public void GIVEN_read_only_property_with_no_annotations_WHEN_createPropertyHandler_THEN_returns_standard_bean_handler() throws IntrospectionException {
        PropertyDescriptor descriptor = new PropertyDescriptor("strProp", TestEntity.class, "getStrProp", null);
        PropertyHandler<TestEntity> handler = TableEntityMapper.createPropertyHandler(descriptor);
        TestEntity entity = new TestEntity();
        entity.strProp = "hello";
        PropertyValue pv = handler.getProperty(entity);
        assertEquals("StrProp", pv.getName());
        assertNull(pv.getOdataType());
        assertEquals("hello", pv.getValue());
        assert handler.isReadOnly();
    }

    @Test
    public void GIVEN_string_partition_key_property_WHEN_createPropertyHandler_THEN_returns_handler_with_pk_name() throws IntrospectionException {
        PropertyDescriptor descriptor = new PropertyDescriptor("id", TestEntity.class);
        PropertyHandler<TestEntity> handler = TableEntityMapper.createPropertyHandler(descriptor);
        TestEntity entity = new TestEntity();
        handler.setProperty(entity, "part1");
        assertEquals("part1", entity.getId());
        PropertyValue pv = handler.getProperty(entity);
        assertEquals("PartitionKey", pv.getName());
        assertNull(pv.getOdataType());
        assertEquals("part1", pv.getValue());
    }

    @Test
    public void GIVEN_string_property_with_row_key_annotation_WHEN_createPropertyHandler_THEN_returns_handler_with_rk_name() throws IntrospectionException {
        PropertyDescriptor descriptor = new PropertyDescriptor("sortKey", TestEntity.class);
        PropertyHandler<TestEntity> handler = TableEntityMapper.createPropertyHandler(descriptor);
        TestEntity entity = new TestEntity();
        handler.setProperty(entity, "row1");
        assertEquals("row1", entity.getSortKey());
        PropertyValue pv = handler.getProperty(entity);
        assertEquals("RowKey", pv.getName());
        assertNull(pv.getOdataType());
        assertEquals("row1", pv.getValue());
    }

    @Test
    public void GIVEN_uuid_property_with_property_name_annotation_WHEN_createPropertyHandler_THEN_returns_handler_with_name_from_annotation() throws IntrospectionException {
        PropertyDescriptor descriptor = BeanUtils.getPropertyDescriptor(TestEntity.class, "uuidProp");
        PropertyHandler<TestEntity> handler = TableEntityMapper.createPropertyHandler(descriptor);
        TestEntity entity = new TestEntity();
        handler.setProperty(entity, RANDOM_UUID);
        assertEquals(RANDOM_UUID, entity.getUuidProp());
        PropertyValue pv = handler.getProperty(entity);
        assertEquals("guid", pv.getName());
        assertEquals("Edm.Binary", pv.getOdataType());
        assertEquals(RANDOM_UUID, pv.getValue());
    }

    @Test
    public void GIVEN_innerclass_property_with_json_annotation_WHEN_createPropertyHandler_THEN_returns_handler_with_json_decorator() throws Exception {
        PropertyDescriptor descriptor = BeanUtils.getPropertyDescriptor(TestEntity.class, "inner");
        PropertyHandler<TestEntity> handler = TableEntityMapper.createPropertyHandler(descriptor);
        TestEntity entity = new TestEntity();
        handler.setProperty(entity, INNER_ENTITY_JSON);
        assertEquals(INNER_ENTITY, entity.getInner());
        PropertyValue pv = handler.getProperty(entity);
        assertEquals("Inner", pv.getName());
        assertNull(pv.getOdataType());
        assert pv.getValue() instanceof String;
        String actualJson = (String) pv.getValue();
        InnerEntity actualEntity = new ObjectMapper().readValue(actualJson, InnerEntity.class);
        assertEquals(INNER_ENTITY, actualEntity);
    }

    @Test
    public void GIVEN_non_string_property_with_pk_annotation_WHEN_createPropertyHandler_THEN_throws_exception() throws IntrospectionException {
        PropertyDescriptor descriptor = BeanUtils.getPropertyDescriptor(TestEntityWithBadKeyProps.class, "id");
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> TableEntityMapper.createPropertyHandler(descriptor));
        assert thrown.getMessage().contains("PartitionKey");
    }

    @Test
    public void GIVEN_non_string_property_with_pk_name_WHEN_createPropertyHandler_THEN_throws_exception() throws IntrospectionException {
        PropertyDescriptor descriptor = BeanUtils.getPropertyDescriptor(TestEntityWithBadKeyProps.class, "partitionKey");
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> TableEntityMapper.createPropertyHandler(descriptor));
        assert thrown.getMessage().contains("PartitionKey");
    }

    @Test
    public void GIVEN_non_string_property_with_rk_annotation_WHEN_createPropertyHandler_THEN_throws_exception() throws IntrospectionException {
        PropertyDescriptor descriptor = BeanUtils.getPropertyDescriptor(TestEntityWithBadKeyProps.class, "sortKey");
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> TableEntityMapper.createPropertyHandler(descriptor));
        assert thrown.getMessage().contains("RowKey");
    }

    @Test
    public void GIVEN_non_string_property_with_rk_name_WHEN_createPropertyHandler_THEN_throws_exception() throws IntrospectionException {
        PropertyDescriptor descriptor = BeanUtils.getPropertyDescriptor(TestEntityWithBadKeyProps.class, "rowKey");
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> TableEntityMapper.createPropertyHandler(descriptor));
        assert thrown.getMessage().contains("RowKey");
    }

    @Test
    void testToTableEntity() throws NoSuchMethodException, SecurityException {
        TestEntity pojo = new TestEntity();
        pojo.setId("item1");
        pojo.setIntProp(42);
        pojo.setSortKey("row1");
        pojo.strProp = "hello";
        pojo.setUuidProp(RANDOM_UUID);
        pojo.setInner(INNER_ENTITY);

        TableEntity entity = new TableEntityMapper<>(TestEntity.class).toTableEntity(pojo);
        assertEquals(ImmutableMap.<String, Object>builder()
            .put("PartitionKey", "item1")
            .put("RowKey", "row1")
            .put("IntProp", 42)
            .put("StrProp", "hello")
            .put("guid", RANDOM_UUID)
            .put("guid@odata.type", "Edm.Binary")
            .put("Inner", INNER_ENTITY_JSON)
            .build(), 
            entity.getProperties());
    }
}
