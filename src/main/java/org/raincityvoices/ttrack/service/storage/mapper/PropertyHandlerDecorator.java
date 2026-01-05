package org.raincityvoices.ttrack.service.storage.mapper;

public abstract class PropertyHandlerDecorator<E> implements PropertyHandler<E> {

    private final PropertyHandler<E> base;

    public PropertyHandlerDecorator(PropertyHandler<E> base) {
        this.base = base;
    }

    @Override
    public String getName() { return base.getName(); }

    /** Convert the given value, which came from the POJO property, to the type used in the table. */
    public abstract Object convertToTableType(Object pojoValue);
    /** Convert the given value, which came from the table, to the type used in the POJO. */
    public abstract Object convertToPojoType(Object tableValue);

    @Override
    public PropertyValue getProperty(E pojo) {
        final PropertyValue baseValue = base.getProperty(pojo);
        if (baseValue == null) {
            return null;
        }
        final Object convertedValue = convertToTableType(baseValue.getValue());
        return new PropertyValue(getName(), null, convertedValue);
    }

    @Override
    public void setProperty(E pojo, Object tableValue) {
        if (tableValue == null) {
            return;
        }
        Object convertedValue = convertToPojoType(tableValue);
        base.setProperty(pojo, convertedValue);
    }

    @Override
    public boolean isReadOnly() {
        return base.isReadOnly();
    }

}
