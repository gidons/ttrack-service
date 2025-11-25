package org.raincityvoices.ttrack.service.storage.mapper;

import com.azure.core.util.ETag;

import lombok.Data;

@Data
public class BaseDTO {

    ETag eTag;

    public boolean hasETag() {
        return eTag != null && !eTag.toString().isEmpty();
    }
}
