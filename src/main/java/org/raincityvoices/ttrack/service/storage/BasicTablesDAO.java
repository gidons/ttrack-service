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

/**
 * A simple DAO that uses {@link TableEntityMapper} to persist one type of POJO 
 * data in an Azure Table Storage table. The DAO implements basic CRUD, as well
 * as filtered queries.
 * 
 * @param <DTO> The POJO class to be persisted, which must extend {@link BaseDTO},
 * and be properly annotated for use with {@link TableEntityMapper}. 
 */
@Slf4j
public class BasicTablesDAO<DTO extends BaseDTO> {

    private final TableEntityMapper<DTO> mapper;
    private TableClient client;

    public BasicTablesDAO(Class<DTO> dtoClass, TableClient client) {
        this.client = client;
        this.mapper = new TableEntityMapper<>(dtoClass);
    }

    /**
     * @return all objects whose table rows match the given filter (empty list if none).
     */
    public List<DTO> query(String filter) {
        return query(filter, null);
    }

    /**
     * Queries the table for all entities matching the given filter expression.
     * 
     * @param filter the OData filter expression to apply when querying entities
     * @param maxResults the maximum number of entities to return, or null for no limit
     * @return a list of DTOs representing the entities that match the filter criteria;
     * may be empty.
     */
    public List<DTO> query(String filter, Integer maxResults) {
        log.info("Listing all entities matching query: {}", filter);
        PagedIterable<TableEntity> results = client.listEntities(
            new ListEntitiesOptions()
                .setFilter(filter)
                .setTop(maxResults),
            null, null);
        return results.stream().map(mapper::fromTableEntity).toList();
    }

    /**
     * @return the unique entity with the given partition and row keys, or null if none.
     */
    public DTO get(String partitionKey, String rowKey) {
        Preconditions.checkNotNull(partitionKey);
        Preconditions.checkNotNull(rowKey);
        try {
            TableEntity entity = client.getEntity(partitionKey, rowKey);
            log.debug("Read table entity: {}", entity.getProperties());
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

    /**
     * Create or update the table row for the given entity, and update its eTag.
     * If the input DTO has a non-empty {@code eTag}, try to update an existing
     * row; otherwise, try to create a new row. Either way, the eTag of the input DTO
     * will be updated once the create/updated is successfully completed.
     * @param dto
     */
    public void put(DTO dto) {
        TableEntity entity;
        try {
            entity = mapper.toTableEntity(dto);
        } catch(Exception e) {
            throw new RuntimeException("Failed to convert " + dto + " to Tables entity.", e);
        }
        String fullKey = entity.getPartitionKey() + "/" + entity.getRowKey();
        try {
            log.debug("Writing table entity: {}", entity.getProperties());
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

    /**
     * Delete the entity with the given partition and row keys from the table.
     * @return {@code true} if the entity existed (and is now deleted), {@code false}
     * if it did not exist.
     */
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
