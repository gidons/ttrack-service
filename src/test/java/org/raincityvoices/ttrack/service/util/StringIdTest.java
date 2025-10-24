package org.raincityvoices.ttrack.service.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class StringIdTest {

    private static final ObjectMapper MAPPER = JsonUtils.newMapper();

    private static class IdA extends StringId {
        public IdA(String value) { super(value); }
    }

    private static class IdB extends StringId {
        public IdB(String value) { super(value); }
    }

    @Test
    void GIVEN_ids_with_same_class_and_value_WHEN_equals_THEN_true() {
        IdA id1 = new IdA("a");
        IdA id2 = new IdA("a");
        assertEquals(id1, id2);
    }

    @Test
    void GIVEN_ids_with_same_class_and_different_values_WHEN_equals_THEN_false() {
        IdA id1 = new IdA("a1");
        IdA id2 = new IdA("a2");
        assertNotEquals(id1, id2);
    }

    @Test
    void GIVEN_ids_with_same_class_and_value_WHEN_hashCode_THEN_same() {
        IdA id1 = new IdA("a");
        IdA id2 = new IdA("a");
        assertEquals(id1.hashCode(), id2.hashCode());
    }

    @Test
    void GIVEN_ids_with_same_class_and_different_values_WHEN_hashCode_THEN_different() {
        IdA id1 = new IdA("a1");
        IdA id2 = new IdA("a2");
        assertNotEquals(id1.hashCode(), id2.hashCode());
    }

    @Test
    void GIVEN_ids_with_different_class_and_same_value_WHEN_equals_THEN_false() {
        IdA id1 = new IdA("a");
        IdB id2 = new IdB("a");
        assertNotEquals(id1, id2);
    }

    @Test
    void GIVEN_id_WHEN_toString_THEN_returns_class_colon_value() {
        assertEquals("IdA:a1", new IdA("a1").toString());
        assertEquals("IdB:foo", new IdB("foo").toString());
    }

    @Test
    void GIVEN_id_WHEN_map_to_json_THEN_returns_value_as_json_string() throws JsonProcessingException {
        assertEquals("\"foo\"", MAPPER.writeValueAsString(new IdA("foo")));
        assertEquals("\"foo\"", MAPPER.writeValueAsString(new IdB("foo")));
    }

    @Test
    void GIVEN_json_string_value_WHEN_map_from_json_THEN_returns_id_object() throws JsonMappingException, JsonProcessingException {
        assertEquals(new IdA("foo"), MAPPER.readValue("\"foo\"", IdA.class));
        assertEquals(new IdB("foo"), MAPPER.readValue("\"foo\"", IdB.class));
    }
}
