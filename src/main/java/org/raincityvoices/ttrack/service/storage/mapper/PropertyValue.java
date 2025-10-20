package org.raincityvoices.ttrack.service.storage.mapper;

import java.util.Map;

import lombok.Value;

@Value
public class PropertyValue {
    String name;
    String odataType;
    Object value;

    public void addToMap(Map<String, Object> properties) {
        if (value != null) {
            properties.put(name, value);
            if (odataType != null && !odataType.isEmpty()) {
                properties.put(name + "@odata.type", odataType);
            }
        }
    }
}
