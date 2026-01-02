package org.raincityvoices.ttrack.service.storage.mapper;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.raincityvoices.ttrack.service.util.JsonUtils;
import org.springframework.beans.BeanUtils;

import com.azure.data.tables.models.TableEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

public class TableEntityMapperTest {

    private static final UUID RANDOM_UUID = UUID.randomUUID();
    private static final InnerEntity INNER_ENTITY = new InnerEntity("foo", 42, ImmutableList.of("a","b","c"));
    private static final String INNER_ENTITY_JSON;
    private static final Date NOW_DATE = new Date();
    private static final OffsetDateTime NOW = OffsetDateTime.ofInstant(NOW_DATE.toInstant(), ZoneId.of("UTC"));
    private static final ObjectMapper MAPPER = JsonUtils.newMapper();

    static {
        try {
            INNER_ENTITY_JSON = MAPPER.writeValueAsString(INNER_ENTITY);
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
        @Getter(onMethod = @__(@Embedded(typePolicy = Embedded.TypePolicy.CLASSNAME_ATTRIBUTE)))
        EmbeddedEntity embedded;
        List<Integer> numbers;
        @Getter(onMethod = @__(@Timestamp))
        Date updated;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class InnerEntity {
        String someString;
        int someInt;
        List<String> someList;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class EmbeddedEntity {
        String embString;
        int embInt;
    }

    @NoArgsConstructor
    @Data
    @EqualsAndHashCode(callSuper = true)
    static class EmbeddedSubEntity extends EmbeddedEntity {
        EmbeddedSubEntity(String s, int i, double d) {
            super(s, i);
            this.embDouble = d;
        }
        Double embDouble;
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
        @SuppressWarnings("unused")
        public void setId(int id) {}
        @RowKey
        public int getSortKey() { return sortKey; }
        @SuppressWarnings("unused")
        public void setSortKey(int sortKey) {}
        @Timestamp
        public boolean getNotADate() { return false; }
        @SuppressWarnings("unused")
        public void setNotADate(boolean dummy) {}
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
        assertTrue(handler.isReadOnly());
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
    public void GIVEN_date_property_with_timestamp_annotation_WHEN_createPropertyHandler_THEN_returns_handler_with_ts_name() throws IntrospectionException {
        PropertyDescriptor descriptor = new PropertyDescriptor("updated", TestEntity.class);
        PropertyHandler<TestEntity> handler = TableEntityMapper.createPropertyHandler(descriptor);
        TestEntity entity = new TestEntity();
        handler.setProperty(entity, NOW);
        assertEquals(NOW_DATE, entity.getUpdated());
        PropertyValue pv = handler.getProperty(entity);
        assertEquals("Timestamp", pv.getName());
        assertNull(pv.getOdataType());
        assertEquals(NOW, pv.getValue());
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
        assertThat(pv.getValue(), instanceOf(String.class));
        String actualJson = (String) pv.getValue();
        InnerEntity actualEntity = MAPPER.readValue(actualJson, InnerEntity.class);
        assertEquals(INNER_ENTITY, actualEntity);
    }

    @Test
    public void GIVEN_non_string_property_with_pk_annotation_WHEN_createPropertyHandler_THEN_throws_exception() throws IntrospectionException {
        PropertyDescriptor descriptor = BeanUtils.getPropertyDescriptor(TestEntityWithBadKeyProps.class, "id");
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> TableEntityMapper.createPropertyHandler(descriptor));
        assertThat(thrown.getMessage(), containsString("PartitionKey"));
    }

    @Test
    public void GIVEN_non_string_property_with_pk_name_WHEN_createPropertyHandler_THEN_throws_exception() throws IntrospectionException {
        PropertyDescriptor descriptor = BeanUtils.getPropertyDescriptor(TestEntityWithBadKeyProps.class, "partitionKey");
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> TableEntityMapper.createPropertyHandler(descriptor));
        assertThat(thrown.getMessage(), containsString("PartitionKey"));
    }

    @Test
    public void GIVEN_non_string_property_with_rk_annotation_WHEN_createPropertyHandler_THEN_throws_exception() throws IntrospectionException {
        PropertyDescriptor descriptor = BeanUtils.getPropertyDescriptor(TestEntityWithBadKeyProps.class, "sortKey");
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> TableEntityMapper.createPropertyHandler(descriptor));
        assertThat(thrown.getMessage(), containsString("RowKey"));
    }

    @Test
    public void GIVEN_non_string_property_with_rk_name_WHEN_createPropertyHandler_THEN_throws_exception() throws IntrospectionException {
        PropertyDescriptor descriptor = BeanUtils.getPropertyDescriptor(TestEntityWithBadKeyProps.class, "rowKey");
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> TableEntityMapper.createPropertyHandler(descriptor));
        assertThat(thrown.getMessage(), containsString("RowKey"));
    }

    @Test
    public void GIVEN_non_datetime_property_with_ts_annotation_WHEN_createPropertyHandler_THEN_throws_exception() throws IntrospectionException {
        PropertyDescriptor descriptor = BeanUtils.getPropertyDescriptor(TestEntityWithBadKeyProps.class, "notADate");
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> TableEntityMapper.createPropertyHandler(descriptor));
        assertThat(thrown.getMessage(), containsString("Timestamp"));
    }

    @Test
    public void GIVEN_non_datetime_property_with_ts_name_WHEN_createPropertyHandler_THEN_throws_exception() throws IntrospectionException {
        PropertyDescriptor descriptor = BeanUtils.getPropertyDescriptor(TestEntityWithBadKeyProps.class, "notADate");
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> TableEntityMapper.createPropertyHandler(descriptor));
        assertThat(thrown.getMessage(), containsString("Timestamp"));
    }

    @Test
    void testToTableEntity() throws NoSuchMethodException, SecurityException {
        EmbeddedEntity embEnt = new EmbeddedSubEntity("foo", 987, 3.14);
        TestEntity pojo = new TestEntity();
        pojo.setId("item1");
        pojo.setIntProp(42);
        pojo.setSortKey("row1");
        pojo.strProp = "hello";
        pojo.setUuidProp(RANDOM_UUID);
        pojo.setInner(INNER_ENTITY);
        pojo.setUpdated(NOW_DATE);
        pojo.setEmbedded(embEnt);

        TableEntity entity = new TableEntityMapper<>(TestEntity.class).toTableEntity(pojo);
        
        ImmutableMap<String, Object> expected = ImmutableMap.<String, Object>builder()
            .put("PartitionKey", "item1")
            .put("RowKey", "row1")
            .put("IntProp", 42)
            .put("StrProp", "hello")
            .put("guid", RANDOM_UUID)
            .put("guid@odata.type", "Edm.Binary")
            .put("Inner", INNER_ENTITY_JSON)
            .put("EmbString", "foo")
            .put("EmbInt", 987)
            .put("EmbDouble", 3.14)
            .put("Embedded" + EmbeddedPropertyHandler.ClassNameAttributeTypeHelper.CLASS_ATTR_SUFFIX, EmbeddedSubEntity.class.getName())
            .put("Timestamp", NOW)
            .build();
        expected.keySet().forEach(propName -> 
            assertEquals(expected.get(propName), entity.getProperties().get(propName), propName));
        entity.getProperties().keySet().forEach(propName -> 
            assertThat(expected, hasKey(propName)));
    }


    @Test
    void testFromTableEntity() throws NoSuchMethodException, SecurityException {
        
        ImmutableMap<String, Object> props = ImmutableMap.<String, Object>builder()
            .put("IntProp", 42)
            .put("StrProp", "hello")
            .put("guid", RANDOM_UUID)
            .put("guid@odata.type", "Edm.Binary")
            .put("Inner", INNER_ENTITY_JSON)
            .put("EmbString", "foo")
            .put("EmbDouble", 3.14)
            .put("Embedded" + EmbeddedPropertyHandler.ClassNameAttributeTypeHelper.CLASS_ATTR_SUFFIX, EmbeddedSubEntity.class.getName())
            .put("EmbInt", 987)
            .put("Timestamp", NOW)
            .build();
        
        TableEntity entity = new TableEntity("item1", "row1");
        entity.setProperties(props);

        TestEntity actual = new TableEntityMapper<>(TestEntity.class).fromTableEntity(entity);
        TestEntity expected = new TestEntity();
        expected.setId("item1");
        expected.setIntProp(42);
        expected.setSortKey("row1");
        // We don't set strProp, which is read-only
        expected.setUuidProp(RANDOM_UUID);
        expected.setInner(INNER_ENTITY);
        expected.setUpdated(NOW_DATE);
        expected.setEmbedded(new EmbeddedSubEntity("foo", 987, 3.14));

        assertEquals(expected, actual);
    }

}
