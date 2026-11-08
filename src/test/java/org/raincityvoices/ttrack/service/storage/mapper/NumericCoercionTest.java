package org.raincityvoices.ttrack.service.storage.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.azure.data.tables.models.TableEntity;

import lombok.Data;
import lombok.Getter;

/**
 * Table backends don't consistently preserve the JSON type of a number: a whole-number double (such as
 * 1.0) may be read back as an integer. The mapper converts such values to the property's type.
 */
public class NumericCoercionTest {

    @Data
    private static class NumbersEntity {
        @Getter(onMethod = @__(@PartitionKey))
        String id;
        Double boxedDouble;
        double primitiveDouble;
        Float boxedFloat;
        Long boxedLong;
        long primitiveLong;
        Integer boxedInt;
        String text;
    }

    private final TableEntityMapper<NumbersEntity> mapper = new TableEntityMapper<>(NumbersEntity.class);

    @Test
    public void integerValuesAreConvertedToFloatingPointProperties() {
        TableEntity entity = new TableEntity("pk", "")
            .addProperty("BoxedDouble", 1)
            .addProperty("PrimitiveDouble", 2)
            .addProperty("BoxedFloat", 3)
            .addProperty("BoxedLong", 4L);

        NumbersEntity actual = mapper.fromTableEntity(entity);

        assertEquals(1.0, actual.getBoxedDouble());
        assertEquals(2.0, actual.getPrimitiveDouble());
        assertEquals(3.0f, actual.getBoxedFloat());
        assertEquals(4L, actual.getBoxedLong());
    }

    @Test
    public void smallerIntegersAreWidenedToLongProperties() {
        TableEntity entity = new TableEntity("pk", "")
            .addProperty("BoxedLong", 5)
            .addProperty("PrimitiveLong", 6);

        NumbersEntity actual = mapper.fromTableEntity(entity);

        assertEquals(5L, actual.getBoxedLong());
        assertEquals(6L, actual.getPrimitiveLong());
    }

    @Test
    public void longValuesThatFitAreNarrowedToIntegerProperties() {
        NumbersEntity actual = mapper.fromTableEntity(new TableEntity("pk", "").addProperty("BoxedInt", 7L));

        assertEquals(7, actual.getBoxedInt());
    }

    @Test
    public void valuesThatWouldLoseInformationAreNotConverted() {
        // A long too big for an int, and a fractional number for an integer property.
        assertThrows(RuntimeException.class,
            () -> mapper.fromTableEntity(new TableEntity("pk", "").addProperty("BoxedInt", 1L << 40)));
        assertThrows(RuntimeException.class,
            () -> mapper.fromTableEntity(new TableEntity("pk", "").addProperty("BoxedInt", 1.5)));
    }

    @Test
    public void valuesOfTheRightTypeAreUnchanged() {
        TableEntity entity = new TableEntity("pk", "")
            .addProperty("BoxedDouble", 1.25)
            .addProperty("BoxedInt", 8)
            .addProperty("Text", "hello");

        NumbersEntity actual = mapper.fromTableEntity(entity);

        assertEquals(1.25, actual.getBoxedDouble());
        assertEquals(8, actual.getBoxedInt());
        assertEquals("hello", actual.getText());
    }
}
