# org.raincityvoices.ttrack.service.storage.mapper

This package provides a mapping layer between annotated Java POJOs and Azure Table Storage `TableEntity` instances, similar to DynamoDB's `DynamoDBMapper`. It handles serialization, deserialization, type conversion, and property name mapping.

## Usage Guide

### Basic Mapping

The entry point is [`TableEntityMapper`](TableEntityMapper.java). Create an instance with your DTO class:

```java
TableEntityMapper<SongDTO> mapper = new TableEntityMapper<>(SongDTO.class);

// Convert POJO to TableEntity
SongDTO song = new SongDTO();
song.setId("song123");
TableEntity entity = mapper.toTableEntity(song);
tableClient.updateEntity(entity);

// Convert TableEntity back to POJO
TableEntity loaded = tableClient.getEntity("song123", "");
SongDTO restored = mapper.fromTableEntity(loaded);
```

### Annotations

Annotate your POJO properties to control mapping behavior:

| Annotation | Use | Notes |
|-----------|-----|-------|
| `@PartitionKey` | Primary key | Must be `String`; maps to `PartitionKey` |
| `@RowKey` | Sort key (track ID, etc.) | Must be `String`; maps to `RowKey`; defaults to `""` if missing |
| `@Timestamp` | DateTime fields | Supported types: `Instant`, `OffsetDateTime`, `Date`, `LocalDateTime`; maps to `Timestamp` |
| `@Property(value="Name", type="json")` | Custom name or JSON serialization | `type="json"` serializes to JSON string; `type="Edm.Binary"` for binary types |
| `@Embedded` | Composite objects | Maps nested properties recursively (see below) |
| `@ETag` | Optimistic locking | Maps to OData ETag; type can be `String` or `com.azure.core.util.ETag` |

**⚠️ Property naming convention:** Properties without annotations are mapped using PascalCase (e.g., `intProp` → `IntProp`). This is intentional to match Azure Tables conventions.

### Embedded Objects

Use `@Embedded` to flatten composite objects into multiple table properties:

```java
@Data
public class Song {
    @PartitionKey
    String id;
    
    @Embedded
    Metadata metadata;  // All properties of Metadata are mapped at the Song level
}

@Data
public class Metadata {
    String title;
    String arranger;
}
```

This produces the table properties: `PartitionKey`, `Title`, and `Arranger`.

**Type handling for @Embedded:**
- By default (`typePolicy = STATIC_ONLY`), the embedded type is assumed to be a specific class.
- With `typePolicy = CLASSNAME_ATTRIBUTE`, a `_class` attribute will automatically be added, storing the runtime type, to support polymorphic deserialization (e.g., `input` and `output` fields in [`AsyncTaskDTO`](../AsyncTaskDTO.java)).

### Special Cases

**Read-only properties:** Properties with only a getter (no setter) are serialized but not deserialized.

```java
@Getter
String computedField;  // Will be written to table but ignored on read
```

**Transient properties:** Mark properties to skip mapping:

```java
@Transient
String notMapped;  // Completely ignored
```

**Missing RowKey:** If a property that would map to `RowKey` is absent, it defaults to an empty string (as per Azure Tables convention).

**⚠️ ETag surprises:**
- Azure Table Storage sometimes returns ETags missing the final `"` character. The mapper compensates for this automatically.
- ETags should always be preserved and passed back on updates for optimistic concurrency control (see [`BaseTablesDAO.put()`](../BaseTablesDAO.java)).

---

## Implementation Guide

### Architecture

The mapper uses a **property handler chain** pattern:

1. **`TableEntityMapper`** — orchestrator; discovers properties and delegates to handlers.
2. **`PropertyHandler<E>`** — interface for converting a single POJO property to and from table representations. Note that this one POJO property might map to multiple table properties.
3. **`BeanUtilsPropertyHandler<E>`** — base handler; uses reflection to get/set POJO properties.
4. **`PropertyHandlerDecorator<E>`** — abstract decorator that wraps a base handler and adds conversion logic.
5. **Concrete decorators:**
   - `TimestampPropertyHandlerDecorator` — converts between Java time types and `OffsetDateTime` (Azure's native type).
   - `ETagPropertyHandlerDecorator` — converts `String` ↔ `ETag`; handles the missing-quote bug.
   - `JsonPropertyHandlerDecorator` — serializes/deserializes objects to/from JSON strings.
   - `EmbeddedPropertyHandler` — recursively maps nested objects.

### How Properties Are Discovered

[`TableEntityMapper.findPropertyProviders()`](TableEntityMapper.java) introspects the POJO class using `BeanUtils.getPropertyDescriptors()`, and for each readable property (excluding `class`) calls `createPropertyHandler()` to create an appropriate handler based on the type, name, and/or annotation.

**⚠️ Ordering:** The order of properties in `findPropertyProviders()` is not guaranteed. Don't rely on iteration order.

### Property Name Resolution

[`createPropertyHandler()`](TableEntityMapper.java) determines a table property name via this precedence:

1. `@PartitionKey` → `"PartitionKey"`
2. `@RowKey` → `"RowKey"`
3. `@Timestamp` → `"Timestamp"`
4. `@Property(value="CustomName")` → `"CustomName"`
5. ETag annotation or `ETag` return type → `"odata.etag"`
6. Otherwise: PascalCase of property name (e.g., `myField` → `"MyField"`)

**⚠️ Confusion risk:** A property named `"partitionKey"` (lowercase) will be mapped to `"PartitionKey"` (step 6), **not** treated as the PartitionKey unless explicitly annotated with `@PartitionKey`. Use explicit annotations for clarity.

### Serialization (POJO → TableEntity)

[`toTableEntity()`](TableEntityMapper.java):

1. Calls `getAllPropertyValues()` to gather `PropertyValue` objects from all handlers.
2. Extracts `PartitionKey` and `RowKey` from the property map (required; throws if missing).
3. Creates a `TableEntity` with those keys.
4. Adds remaining properties to the entity, including `@odata.type` hints for binary/special types.

**⚠️ Missing PartitionKey:** Always ensure your POJO has a `@PartitionKey` property or a `PartitionKey`-named property. The mapper will fail at serialization time if it's absent.

### Deserialization (TableEntity → POJO)

[`fromTableEntity()`](TableEntityMapper.java):

1. Instantiates the POJO via no-arg constructor (required; throws if missing).
2. Calls `setProperties()` on each handler, passing the table entity.
3. Each handler extracts and converts properties as needed.

**⚠️ Property mismatch:** If the table has extra properties not defined in the POJO, they are silently ignored. If the POJO expects a property that's missing from the table, it retains its default value (usually `null`).

### Type Conversion Pipeline

For properties that need conversion (e.g., timestamps, JSON), the pipeline is:

```
Table value → PropertyHandlerDecorator.convertToPojoType() → POJO value
POJO value → PropertyHandlerDecorator.convertToTableType() → Table value
```

**Example:** `TimestampPropertyHandlerDecorator`
- POJO side: any supported time type (`Instant`, `Date`, `OffsetDateTime`, etc.)
- Table side: always `OffsetDateTime` (normalized to UTC)
- Conversion uses [`DateHelper`](DateHelper.java), which handles the various time types.

### Embedded Object Handling

[`EmbeddedPropertyHandler`](EmbeddedPropertyHandler.java) is complex because it handles polymorphism:

- **`StaticTypeHelper` (default):** Assumes the embedded property is always the same type. Nested properties are mapped directly to the parent level.
- **`ClassNameAttributeTypeHelper` (with `typePolicy = CLASSNAME_ATTRIBUTE`):** Stores the runtime class name in a hidden attribute (e.g., `Input_class`). On deserialization, reads this attribute to determine the actual type and instantiate the correct class. This is essential for fields like [`AsyncTaskDTO.input`](../AsyncTaskDTO.java) where the type varies (`AudioTrackTask.Input`, `CreateMixTrackTask.Input`, etc.).

**⚠️ Hidden attributes:** When using `CLASSNAME_ATTRIBUTE`, a property `input` produces an extra table attribute `input_class`. This is transparent during normal usage but visible in the Azure Tables UI.

### DateHelper Utility

[`DateHelper`](DateHelper.java) provides conversion between Java time types and `OffsetDateTime`:

- **Supported POJO types:** `Instant`, `Date`, `LocalDateTime`, `OffsetDateTime`
- **Table type:** `OffsetDateTime` (always UTC)
- All conversions normalize to UTC (`ZoneId.UTC`)

**⚠️ Data loss:** If you use `LocalDateTime` in your POJO (which has no timezone), it will be interpreted as UTC. Be cautious.

### Error Handling

Errors are generally wrapped in `RuntimeException` or `IllegalArgumentException`:

- **No no-arg constructor:** `IllegalArgumentException` at mapper creation time.
- **Invalid property types:** `IllegalArgumentException` at handler creation (e.g., `@RowKey` on a non-`String`).
- **Serialization/deserialization failure:** `RuntimeException` at runtime with context (property name, class name).

**⚠️ Early vs. late validation:** Some errors (e.g., missing `@PartitionKey`) are caught at serialization time, not at mapper creation. Test your round-trip serialization early.

### Testing Patterns

See [`TableEntityMapperTest`](../../test/java/org/raincityvoices/ttrack/service/storage/mapper/TableEntityMapperTest.java) for comprehensive examples:

- Round-trip tests (POJO → Table → POJO) verify bidirectional fidelity.
- Annotation validation tests ensure proper error handling.
- Embedded object and polymorphic type tests validate complex scenarios.
