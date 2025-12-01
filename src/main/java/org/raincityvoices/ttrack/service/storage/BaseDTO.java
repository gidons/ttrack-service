package org.raincityvoices.ttrack.service.storage;

import org.apache.commons.lang3.StringUtils;
import org.raincityvoices.ttrack.service.storage.mapper.ETag;

import lombok.Data;
import lombok.Getter;

@Data
public class BaseDTO {

    @Getter(onMethod = @__(@ETag))
    String eTag;

    public boolean hasETag() {
        return !StringUtils.isEmpty(eTag);
    }
}
