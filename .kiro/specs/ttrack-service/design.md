# Design — ttrack-service

> Reverse-engineered from the existing codebase. Describes the architecture,
> components, data model, and key flows as currently implemented, with file
> references so future changes can be grounded in how the code actually works.

## 1. Context

`ttrack-service` is the backend of a three-part system for rehearsing choral
music:

```
┌─────────────────┐     ┌─────────────────┐     ┌──────────────────────┐
│  ttrack-fe      │     │  TTrackPlayer   │     │  (operators / CI)    │
│  (web SPA,      │     │  (Android app,  │     │                      │
│   Vite/TS)      │     │   Kotlin)       │     │                      │
└────────┬────────┘     └────────┬────────┘     └──────────┬───────────┘
         │  manage data          │  download & play         │  deploy/ops
         ▼                       ▼                           ▼
                  ┌────────────────────────────────────┐
                  │           ttrack-service            │
                  │        (Spring Boot 3.5, Java 21)   │
                  └───────────────┬────────────────────┘
                                  │
        ┌─────────────────────────┼──────────────────────────────┐
        ▼                         ▼                              ▼
  Azure Table Storage      Azure Blob Storage            Azure Key Vault
  - Songs (songs+tracks)   - song-media (audio, notation) - Clerk API key
  - AsyncTasks             - song-timed-data (lyrics…)
                           - temp-files (zips)
                          + Azure Cognitive (transcription), Azure Monitor
```

- The **front-end** manages songs, parts, mixes, lyrics, and notation.
- The **player** downloads parts/mixes and timed data for offline playback.
- Both call the same REST API; auth is via Clerk bearer tokens (localhost is
  auto-authenticated for development).

## 2. Technology and runtime

- **Language/framework:** Java 21, Spring Boot 3.5 (`spring-boot-starter-web`,
  `-security`, `-actuator`).
- **Entry point:** `org.raincityvoices.ttrack.service.ServiceApplication`.
- **Models:** Lombok (`@Value`, `@Builder`, `@SuperBuilder`, `@Jacksonized`).
- **Audio:** Java Sound (`javax.sound.sampled`), `mp3spi`/vavi for MP3, TarsosDSP
  for pitch/time manipulation, `proxymusic` for MusicXML.
- **Azure SDKs:** `azure-data-tables`, `azure-storage-blob`, `azure-identity`,
  `azure-security-keyvault-secrets`, plus Cognitive Services speech.
- **Auth:** Clerk backend SDK + Spring Security.
- **Observability:** Micrometer (Prometheus + Azure Monitor), Zalando Logbook.
- **Packaging:** Spring Boot fat JAR; multi-stage Dockerfile (build with the
  Maven wrapper, runtime image installs `ffmpeg`).
- **Deploy:** GitHub Actions (`.github/workflows/main_shavit-ttrack.yml`) builds
  and deploys to Azure Web App `shavit-ttrack` on push to `main`.

## 3. Package / layer overview

Base package: `org.raincityvoices.ttrack.service`.

| Package | Responsibility |
|---|---|
| (root) | Controllers (`SongController`, `TaskController`), app wiring (`ServiceApplication`), response link rewriting (`AddBaseUrlAdvice`), media-URL provider. |
| `api` | REST model classes (DTOs over the wire), Jackson-annotated. |
| `exceptions` | `ErrorResponseException` subclasses mapping to HTTP codes. |
| `auth` | Clerk authentication filter + Spring Security `Authentication` types. |
| `audio` | Audio stream processing: mixing, channel extraction/analysis, Tarsos adapters. |
| `audio.model` | Audio domain model: `AudioPart`, `AudioMix` (+`MonoMix`/`StereoMix`/`AllPartsMix`), `AudioFormats`. |
| `async` | Async task framework and concrete tasks. |
| `storage` | Persistence: base DAO, temp files; subpackages below. |
| `storage.mapper` | Reflection-based POJO ↔ Azure Table entity mapper. |
| `storage.songs` | Song + AudioTrack persistence (`SongStorage`). |
| `storage.media` | Media blob persistence + disk cache (`MediaStorage`). |
| `storage.timeddata` | Timed text (lyrics) persistence. |
| `storage.async` | Async task-status persistence. |
| `storage.files` | Shared file/blob abstractions, SAS URL helper, metadata. |
| `config` | Spring configuration: Azure clients, security, web. |
| `util` | `FileManager` abstraction, audio system shim, JSON, locks, temp files. |
| `..tools` | Picocli CLIs (`OpsCli`, `WavProcessor`, `NotationProcessor`) run under a separate `cli` profile. |

Layering: **Controller → Storage interfaces + AsyncTaskManager → Azure
clients.** Controllers translate between `api.*` wire models and `storage.*`
DTOs and never touch Azure SDKs directly.

## 4. REST API surface

All paths are under `SongController` (`/songs`) and `TaskController` (`/tasks`).
Responses are JSON; `@CrossOrigin` is enabled.

### Songs
- `GET /songs?extended&includeArchived` — list songs.
- `POST /songs` — create a song (also refreshes its `All` mix).
- `GET /songs/{id}?extended` — get a song (404 if missing).
- `PUT /songs/{id}` — update (400 if URL id ≠ body id).
- `DELETE /songs/{id}` — archive (soft delete).

### Notation
- `PUT /songs/{id}/notation` (multipart `file`) — upload notation file.
- `DELETE /songs/{id}/notation` — delete notation file.

### Parts
- `GET /songs/{id}/parts` — list part tracks.
- `GET /songs/{id}/parts/{part}` — describe one part.
- `PUT /songs/{id}/parts/{part}?overwrite` (multipart `audioFile`) — upload one
  part; refreshes `All` mix; schedules `ProcessUploadedPartTask`.
- `PUT /songs/{id}/parts?overwrite` (multi-file multipart) — upload many parts;
  refreshes `All` mix; schedules `RefreshAllMixesTask`.
- `GET /songs/{id}/parts/{part}/media` — stream part audio.

### Mixes
- `GET /songs/{id}/mixes` — list mix tracks.
- `GET /songs/{id}/mixes/{mix}` — describe one mix (404 if missing).
- `POST /songs/{id}/mixes?overwrite` — create single mix or mix package
  (polymorphic body; schedules `CreateMixTrackTask`).
- `POST /songs/{id}/mixes/{mix}/refresh` — re-render a mix
  (schedules `RefreshMixTrackTask`).
- `DELETE /songs/{id}/mixes/{mix}` — delete mix record + media.
- `GET /songs/{id}/mixes/{mix}/media` — stream mix audio.
- `GET /songs/{id}/defaultMixes` — generated catalog of standard mixes.

### Timed text data
- `GET /songs/{id}/text` — all timed data grouped by part/type.
- `PUT /songs/{id}/text` — upsert full timed-text structure.
- `GET /songs/{id}/parts/{part}/text?includeShared` — a part's timed data.
- `GET /songs/{id}/parts/{part}/text/{type}` — one part+type's entries.
- `PUT /songs/{id}/parts/{part}/text/{type}` — upsert one part+type's entries.

### Zip
- `POST /songs/{id}/zip` (body: list of track IDs) — schedule `ZipTracksTask`;
  returns task ID.

### Tasks
- `GET /tasks/{id}` — task status/result (404 if missing).

**Error handling:** controllers throw `BadRequestException` (400),
`NotFoundException` (404), `ConflictException` (409),
`UnauthorizedException` (401) — all `ErrorResponseException` subclasses with the
corresponding HTTP status.

**Link rewriting:** `AddBaseUrlAdvice` (a `ResponseBodyAdvice`) rewrites URLs in
any response body implementing `UriContainer` to the caller's base URL, since the
container cannot know the external URL. Media URLs come from
`MediaUrlProviderImpl`, which produces 60-minute signed download URLs.

## 5. API vs. storage models

Two parallel model families, bridged by `Conversions`:

- **API (`api.*`):** `Song`, `AudioTrack` (abstract) → `PartTrack` / `MixTrack`,
  `MixInfo`, `TimedTextData` (+`Entry`, `DataType`), `Task`,
  `CreateMixRequestBase` → `CreateMixTrackRequest` / `CreateMixTrackPackageRequest`,
  `SongId`, `UriContainer`. These are fluent, immutable, Jackson-serialized.
- **Storage DTOs (`storage.*`):** `SongDTO`, `AudioTrackDTO`, `TimedTextDTO`,
  `AsyncTaskDTO`, all extending `BaseDTO` (which carries the `eTag`).

`MixInfo` captures a mix definition: `name`, `parts`, `mix` (`AudioMix`),
`pitchShift` (half-steps), `speedFactor`. `CreateMixRequestBase` validates
`speedFactor ∈ [0.1, 3.0]` and `pitchShift ∈ [-11, 11]`; the package request
additionally requires a non-empty `mixDescriptions` list and expands into one
`CreateMixTrackRequest` per description.

## 6. Data model and persistence

### 6.1 Azure Tables mapper (`storage.mapper`)

A small reflection-based ORM (DynamoDBMapper-style) centered on
`TableEntityMapper<E>`. For each JavaBean property it builds a `PropertyHandler`,
choosing the table column by getter annotation precedence:

1. `@Embedded` → `EmbeddedPropertyHandler` (flattens a nested POJO's columns into
   the parent row; `CLASSNAME_ATTRIBUTE` mode adds a `<prop>_class` column so
   polymorphic types can be reconstructed).
2. `@PartitionKey` → `PartitionKey` column (must be `String`).
3. `@RowKey` → `RowKey` column (must be `String`).
4. `@Timestamp` → `Timestamp` column (date/time type).
5. `@Property("Name")` → explicit column name; `@Property(type=...)` selects an
   EDM type (`Edm.*`) or `"json"` (Jackson-serialize the value to a string).
6. `ETag`-typed or `@ETag` → OData ETag column (decorator re-appends a missing
   trailing quote to work around an Azure quirk).
7. Fallback → PascalCase of the property name (`title` → `Title`).

Null POJO fields are simply omitted from the row (and left at defaults on read).

`BasicTablesDAO<DTO>` wraps a `TableClient` + mapper: `query(filter[,top])`,
`get(pk,rk)` (404 → null), `put(dto)` (optimistic concurrency: create if no
ETag, else conditional replace `ifUnchanged`; 409 → `ConflictException`),
`delete(pk,rk)`.

### 6.2 Songs and tracks — one table, discriminated by RowKey

`AzureTablesSongStorage` uses **one** table (`Songs`) with two DAOs sharing the
same client. **PartitionKey = songId** for everything belonging to a song.

- **`SongDTO`** — `@PartitionKey id`, RowKey fixed to `""` (so the song's own row
  is `(songId, "")`). Fields: title, shortTitle, version, arranger, key, voicing,
  durationSec, `archived`. `getTrackPrefix()`/`getShortTitleOrTitle()` fall back
  shortTitle → title.
- **`AudioTrackDTO`** — `@PartitionKey songId`, `@RowKey id` (track id). Track
  rows are `(songId, trackId)`. Mix fields `parts` and `audioMix` are stored as
  JSON; `pitchShift`, `speedFactor`, `durationSec`, `created`, `@Timestamp
  updated`, `mediaLocation`, `currentTaskId` (the lock field).

**Part vs. mix discrimination is structural:** a track with `parts == null &&
audioMix == null` is a part; one with both non-null is a mix. Table queries
exploit this: parts = `RowKey ne '' and not (Parts ne '')`; mixes =
`Parts ne ''`. The mix definition is stored *on the track* (not referenced) so it
reflects the mix as of the time the audio was rendered.

**Two-phase track lifecycle:** a track row is written first (metadata), then an
async task renders/uploads the audio and sets `mediaLocation`
(`hasMedia() == mediaLocation != null`).

**Archive vs. delete:** `archiveSong`/`unarchiveSong` flip the `archived` flag;
`deleteSong` hard-deletes but refuses if the song still has any track.

### 6.3 Media (`storage.media`)

`MediaStorage` abstracts media: `exists/getMedia/putMedia/getMediaMetadata/
deleteMedia/getDownloadUrl`, plus `locationFor(songId, fileId)` →
`"<songId>/<fileId>"` (the blob key; fileId is the track id, or `__notation__`
for notation files).

- `BlobMediaClient` implements the low-level `RemoteFileStorage` over the
  `song-media` Blob container. Metadata is stored as blob HTTP headers
  (content-type, `Content-Disposition`). Downloads use conditional GET
  (`If-None-Match`): 304 → unchanged, 404 → null.
- `DiskCachingMediaStorage` is the active `MediaStorage` bean: a per-location
  caching layer (Guava `LoadingCache`, one locked `CachingMediaClient` per
  location). Local cache files are named by the remote **ETag**, so a changed
  blob produces a new file; identical ETag ⇒ skip download. Uploads write a temp
  file, push to blob, then re-fetch the authoritative ETag.
- `MediaContent` = `InputStream` + `FileMetadata`; `FileMetadata` carries
  filename, content type, byte length, duration, updated time, and ETag, with
  factory/merge helpers (infers duration/content type from audio where possible).

### 6.4 Timed text (`storage.timeddata`)

`BlobTimedDataStorage` stores each part+type's entries as a JSON blob in the
`song-timed-data` container, keyed `"<songId>/<part>/<type>.json"`. The DTO
(`TimedTextDTO`) holds `type`, `part`, and a list of `{t (ms), v (text)}`
entries. Listing by song/part uses blob name prefixes.

### 6.5 Async task status (`storage.async`)

`AzureTablesAsyncTaskStorage` uses the `AsyncTasks` table; `AsyncTaskDTO` has
`@PartitionKey taskId` (RowKey `""`), `status`, `taskType`, scheduled/start/end
times, `errorDetails`, `@Timestamp updated`, and the polymorphic `@Embedded(
CLASSNAME_ATTRIBUTE)` `Input`/`Output` objects (each task subtype's payload is
flattened into the row with a `_class` discriminator). Status constants:
SCHEDULED, PENDING, RUNNING, SUCCEEDED, FAILED (CANCELING/CANCELED/TIMEDOUT are
defined but not driven in the current code). `createTask`/`updateTask` enforce
optimistic concurrency via ETag.

### 6.6 Temp files (`storage.TempFileStorage`)

A separate `temp-files` Blob container for ephemeral artifacts (zip downloads),
with SAS download URLs minted by `DownloadUrlHelper` using an Azure AD User
Delegation Key (read-only, HTTPS-only, bounded validity).

## 7. Audio subsystem

### 7.1 Domain model (`audio.model`)

- `AudioPart` (extends `StringId`) — one mono voice part, identity by name;
  singletons `NONE` and `ALL` (`"*"`).
- `AudioMix` — polymorphic (Jackson `_type`): `numInputs()`, `numOutputs()`,
  `mix(FloatBuffer[] ins, FloatBuffer out)`.
  - `MonoMix` — N→1, per-input weights in [0,1] summing to ~1.0.
  - `StereoMix` — N→2, separate left/right factor arrays; emits interleaved
    L,R per frame. This encodes "part on left / right / weighted".
  - `AllPartsMix` — N→N identity passthrough (one part per channel).
- `AudioFormats` — canonical 16-bit PCM formats and conversions to PCM / MP3 /
  target type; derives a same-characteristics format for a different channel
  count.

### 7.2 Mix description grammar (`MixUtils`)

`parseMix` / `parseStereoMix` turn a human description (or raw JSON) into an
`AudioMix` (see requirements §3.4 for the grammar). Weight helpers
(`targetPartFactors`, `dominantPartFactors`, `equalMixFactors`) compute the L/R
factor arrays; `PREDOMINANT_WEIGHT = 4.0`. `getParseableMixes` generates the
default catalog (Full Mix; per-part Solo/Left/Dominant/Missing; all Duets).
`allChannelMix(n)` builds an `AllPartsMix`.

### 7.3 Streaming and DSP (`audio`, `util`)

- `CustomAudioSystem` — a `javax.sound.sampled.AudioSystem` replacement that
  enumerates SPI providers but filters out a buggy Tritonus WAV reader.
- `ChannelExtractingStream` — pulls one channel out of a multichannel stream.
- `ChannelAnalyzer` — de-interleaves a stream; `isDuplicateStereo()` detects
  "fake stereo" (both channels ~identical) so it can be collapsed to mono.
- `AudioMixingStream` — the workhorse: combines N mono 16-bit inputs into one
  output per an `AudioMix`, converting PCM↔float via TarsosDSP. (Reads exactly
  the requested bytes per input, which can block — noted TODO.)
- `TarsosUtils` + `TarsosStreamAdapter` — pitch/speed change: builds a Tarsos
  `AudioDispatcher` (WSOLA time-stretch + `RateTransposer`) where
  `pitchFactor = 2^(-pitchShift/12)`, and bridges its push model to a pull-based
  `AudioInputStream` via a background thread and a bounded queue (backpressure).

## 8. Asynchronous task framework (`async`)

- `AsyncTaskManager` — `@Component` with a fixed pool (10 threads).
  `schedule(TaskClass, ctorArgs...)` obtains a **prototype-scoped** task bean from
  the Spring context (so collaborators are injected), calls `initialize()`
  synchronously (validation + persist a SCHEDULED record), then submits
  `execute()` to the pool, returning a `TaskExec(task, Future)`.
- `AsyncTask<I extends Input, O extends Output>` — lifecycle base. Each task has a
  random UUID id. `execute()`: PENDING → acquire lock → RUNNING → `process()` →
  SUCCEEDED (store output) / FAILED (store error) with lock released in `finally`.
- `AudioTrackTask<I,O>` — base for track-targeted tasks. Implements a
  **distributed lock on the track row** via `AudioTrackDTO.currentTaskId` plus the
  DAO's ETag optimistic concurrency: poll until unlocked (≤30s), claim by writing
  `currentTaskId`, retry on `ConflictException`. Provides
  `uploadStream`/`uploadFile` helpers that render to a temp file, push to
  `MediaStorage`, and refresh track metadata.
- `MixTrackTaskBase` — shared mix rendering (`performMix`): resolve part tracks,
  open each as mono PCM, optionally wrap in a Tarsos pitch/speed adapter, build an
  `AudioMixingStream` from the parts + the track's `AudioMix`, and upload as MP3
  (≤2 output channels) or WAV.

Concrete tasks:
- `CreateMixTrackTask` — apply the requested `MixInfo` to the track, then render.
- `RefreshMixTrackTask` — re-render from the track's stored mix definition.
- `RefreshAllMixesTask` — list a song's mixes and schedule a create task for each
  (no lock of its own; spawned tasks lock themselves).
- `ProcessUploadedPartTask` — post-process an uploaded part: keep mono as-is,
  collapse duplicate-stereo to mono, or mark genuinely-stereo audio unusable.
- `ZipTracksTask` — stream selected track media into a ZIP, upload to
  `temp-files`, return a 60-minute SAS URL (read-only, no lock).

## 9. Security (`auth`, `config.SecurityConfig`)

- `SecurityConfig` sets up a stateless filter chain: CORS on, CSRF off,
  `"/public/**"` + `/error` + `OPTIONS` permitted, everything else authenticated.
- `ClerkAuthFilter` (before `UsernamePasswordAuthenticationFilter`): localhost
  requests get a `SpoofAuthentication` admin identity; otherwise it validates the
  Clerk bearer token (`AuthenticateRequest`), loads the Clerk `User` (cached 60
  min), and sets a `ClerkAuthentication`. It skips `ERROR` dispatches so Spring's
  error handling returns correct status codes.
- The Clerk API key is read from Azure Key Vault (`ClerkAPIKeyTest`) in
  `SecurityConfig`.

## 10. Configuration and infrastructure (`config`, `terraform`)

- `AzureClients` (`@ConfigurationProperties("azure")`) builds all Azure clients
  with `DefaultAzureCredential`: Table clients `Songs` and `AsyncTasks`; Blob
  containers `song-media`, `song-timed-data`, `temp-files`; a `BlobServiceClient`
  (for user-delegation SAS); a Key Vault `SecretClient`
  (`ttracksecrets.vault.azure.net`); a Cognitive Services transcription client.
- `application.yml`: port 8080, 100 MB multipart limit, actuator endpoints,
  Azure Table/Blob endpoints (storage account `shavitttrackwestus`), local media
  cache dir `/tmp/ttrack-service/cache`.
- `terraform/` provisions the Azure resources (resource group `TTrack`, storage
  account, `song-media` container, `Songs` table). **Note:** the checked-in
  `terraform.tfstate` contains secrets and should be treated as sensitive.

## 11. Tooling (`..tools`)

Picocli CLIs run under the `cli` Spring profile (`application-cli.yml`), sharing
the service's beans:
- `OpsCli get-token` — mint a Clerk testing JWT for a user.
- `WavProcessor`, `NotationProcessor` — offline audio/notation utilities.

## 12. Cross-cutting notes and risks (for future changes)

- **Track locking** relies on ETag optimistic concurrency on the track row; a
  task that can't claim the lock within 30s fails.
- **`isValid()` precedence quirk** in `AudioTrackDTO` lets a mix track validate
  with null ids — worth fixing before tightening validation.
- **Blocking reads** in `AudioMixingStream` can stall if one input lags.
- **All-channel mix** is regenerated whenever parts change; multi-part uploads
  also trigger `RefreshAllMixesTask`.
- **Mix definition is denormalized** onto each track row (intentional), so
  changing a mix means re-rendering rather than just updating a reference.
- **ID generation** for songs is a 32-bit random hex with a small collision risk
  and no retry.
- See `TODO.md` for the owner's planned functionality, known bugs, tech debt, and
  refactors.
