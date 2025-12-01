package org.raincityvoices.ttrack.service.storage;

import java.util.List;

import org.raincityvoices.ttrack.service.exceptions.ConflictException;
import org.raincityvoices.ttrack.service.storage.mapper.TableEntityMapper;
import org.springframework.http.HttpStatus;

import com.azure.core.http.HttpHeaderName;
import com.azure.core.http.rest.PagedIterable;
import com.azure.core.http.rest.Response;
import com.azure.cosmos.implementation.guava25.base.Preconditions;
import com.azure.data.tables.TableClient;
import com.azure.data.tables.models.ListEntitiesOptions;
import com.azure.data.tables.models.TableEntity;
import com.azure.data.tables.models.TableEntityUpdateMode;
import com.azure.data.tables.models.TableServiceException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BaseTablesDAO<DTO extends BaseDTO> {

    private final TableEntityMapper<DTO> mapper;
    private TableClient client;

    public BaseTablesDAO(Class<DTO> dtoClass, TableClient client) {
        this.client = client;
        this.mapper = new TableEntityMapper<>(dtoClass);
    }

    public List<DTO> query(String filter) {
        log.info("Listing all entities matching query: {}", filter);
        PagedIterable<TableEntity> results = client.listEntities(new ListEntitiesOptions().setFilter(filter),
            null, null);
        return results.stream().map(mapper::fromTableEntity).toList();
    }

    public DTO get(String partitionKey, String rowKey) {
        Preconditions.checkNotNull(partitionKey);
        Preconditions.checkNotNull(rowKey);
        try {
            TableEntity entity = client.getEntity(partitionKey, rowKey);
            log.debug("Table entity: {}", entity.getProperties());
            return mapper.fromTableEntity(entity);
        } catch (TableServiceException e) {
            if (e.getResponse().getStatusCode() == HttpStatus.NOT_FOUND.value()) {
                log.info("No entity found with key '{}'/'{}'; returning null.", partitionKey, rowKey);
                return null;
            }
            throw new RuntimeException("Exception getting entity with partition key '" + partitionKey + "' and row key '" + rowKey + "'", e);
        } catch(Exception e) {
            throw new RuntimeException("Exception getting entity with partition key '" + partitionKey + "' and row key '" + rowKey + "'", e);
        }
    }

    public void put(DTO dto) {
        TableEntity entity;
        try {
            entity = mapper.toTableEntity(dto);
        } catch(Exception e) {
            throw new RuntimeException("Failed to convert " + dto + " to Tables entity.", e);
        }
        String fullKey = entity.getPartitionKey() + "/" + entity.getRowKey();
        try {
            log.debug("Table entity: {}", entity.getProperties());
            final Response<Void> response;
            if (dto.hasETag()) {
                response = client.updateEntityWithResponse(entity, TableEntityUpdateMode.REPLACE, true, null, null);
            } else {
                response = client.createEntityWithResponse(entity, null, null);
            }
            log.debug("ETag header: '{}'", response.getHeaders().getValue(HttpHeaderName.ETAG));
            dto.setETag(response.getHeaders().getValue(HttpHeaderName.ETAG));
        } catch(TableServiceException e) {
            if (e.getResponse().getStatusCode() == 409) {
                throw new ConflictException("Entity " + fullKey + " has been updated since last read.");
            }
            throw new RuntimeException("Failed to write " + fullKey + " to table.", e);
        } catch(Exception e) {
            throw new RuntimeException("Failed to write " + fullKey + " to table.", e);
        }

    }

    public boolean delete(String partitionKey, String rowKey) {
        Preconditions.checkNotNull(partitionKey);
        Preconditions.checkNotNull(rowKey);

        try {
            client.deleteEntity(partitionKey, rowKey);
            return true;
        } catch(TableServiceException e) {
            if (e.getResponse().getStatusCode() == 404) {
                return false;
            }
            throw new RuntimeException("Exception while trying to delete entity with partition key '" + partitionKey + "' and row key '" + rowKey + "'", e);
        }
    }

}
