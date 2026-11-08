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
  part; refreshes `All` mix; schedules `ProcessUploadedPartTask`. Accepts an
  optional `channel` query parameter (`0`/`1`) naming the target channel of a
  stereo upload (see §7.4); any other value is rejected with 400.
- `PUT /songs/{id}/parts?overwrite` (multi-file multipart) — upload many parts;
  refreshes `All` mix; schedules `RefreshAllMixesTask`. Per-file part names come
  from `part0`, `part1`, … form fields; per-file target channels come from
  parallel `channel0`, `channel1`, … fields (see §7.4); an invalid value rejects the
  whole request with 400 before anything is stored.
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
`AudioMix` (see requirements §4.4 for the grammar). Weight helpers
(`targetPartFactors`, `dominantPartFactors`, `equalMixFactors`) compute the L/R
factor arrays; `PREDOMINANT_WEIGHT = 4.0`. `getParseableMixes` generates the
default catalog (Full Mix; per-part Solo/Left/Dominant/Missing; all Duets).
`allChannelMix(n)` builds an `AllPartsMix`.

### 7.3 Streaming and DSP (`audio`, `util`)

- `CustomAudioSystem` — a `javax.sound.sampled.AudioSystem` replacement that
  enumerates SPI providers but filters out a buggy Tritonus WAV reader.
- `ChannelExtractingStream` — pulls one channel out of a multichannel stream
  (used to isolate the target channel of a stereo part upload; see §7.4).
- `TargetChannelDetector` — identifies which channel of a stereo part-predominant
  track carries the part, by comparing pitch-detection confidence per channel
  (§7.5).
- `AudioMixingStream` — the workhorse: combines N mono 16-bit inputs into one
  output per an `AudioMix`, converting PCM↔float via TarsosDSP. (Reads exactly
  the requested bytes per input, which can block — noted TODO.)
- `TarsosUtils` + `TarsosStreamAdapter` — pitch/speed change: builds a Tarsos
  `AudioDispatcher` (WSOLA time-stretch + `RateTransposer`) where
  `pitchFactor = 2^(-pitchShift/12)`, and bridges its push model to a pull-based
  `AudioInputStream` via a background thread and a bounded queue (backpressure).

### 7.4 Part upload processing

A part track is always stored as mono audio. The upload handler
(`SongController.uploadOnePart`) streams the uploaded media straight to blob
storage and records the track, then schedules `ProcessUploadedPartTask`; it does
not buffer or inspect the media locally. Validation and any channel extraction
happen in that async task. This split is intentional: streaming straight to the
cloud keeps the request lightweight and lets the post-processing task run on a
separate host. The tradeoff — no synchronous error for a malformed upload, and a
plan to move the cheap checks earlier — is recorded in requirements §3. The
handler does validate the cheap, request-level inputs synchronously: a `channel`
that is not `0` or `1` is rejected with 400 before anything is stored.

`ProcessUploadedPartTask` (input: the track and an optional target channel):
1. Sniffs the stored media and requires a supported format (WAV or MP3) and one
   or two channels. Format readers fail in inconsistent ways on arbitrary bytes,
   so any failure of this step means "not a recognizable audio file".
2. For mono media, keeps it as is and refreshes the track's metadata. A `channel`
   given for mono media is ignored.
3. For stereo media, determines the **target channel** — the `channel` the client
   supplied (`0`/`1`; per-file `channelN` form fields in a multi-upload), or, if
   none, the one identified by `TargetChannelDetector` (§7.5) — then extracts that
   channel to mono with `ChannelExtractingStream` and stores it in the same
   container format as the upload (MP3 in, MP3 out; WAV in, WAV out) through
   `AudioTrackTask.uploadStream`. Extraction from WAV is lossless; MP3 is
   re-encoded.
4. If the media can't be used (unrecognizable, unsupported format, wrong channel
   count, damaged, or no confidently identifiable target channel) the task fails
   with a message addressed to the uploader, clears the track's media location and
   duration, and deletes the uploaded blob. The message for an unidentifiable
   channel tells the uploader to resubmit with an explicit `channel`.

Each pass over the media (sniffing, detection, extraction) opens the stored media
afresh from the local disk cache, so nothing is buffered in memory beyond one
analysis window.

Because a part's media is only final once this task has run, anything built from
the parts must wait for it. The multi-part upload therefore schedules
`RefreshAllMixesTask` with `AsyncTaskManager.scheduleAfter`, which holds the task
back until the upload tasks have finished (whether or not they succeeded). Waiting
occupies a pool thread, which cannot deadlock: the pool's queue is FIFO and the
prerequisites were submitted first, so they have already started by the time the
waiting task gets a thread. A mix requested while an upload of one of its parts is
still being processed is not guarded against and will fail or use the unprocessed
media.

### 7.5 Target-channel identification (`TargetChannelDetector`)

In a stereo part-predominant track the target channel carries one voice
(monophonic), while the other channel carries either the same voice or a blend in
which the other parts are present (polyphonic, at a similar or lower level).
Pitch detectors report a confident pitch on monophonic material and fail on
polyphonic material, so the channel in which a pitch is detected in markedly more
frames is the target channel.

Algorithm (one streaming pass over 16-bit PCM, two `FastYin` detectors):
1. Split the audio into non-overlapping windows of 2048 samples per channel.
2. A window is *active* in a channel if its RMS is at least 0.003 (full scale
   1.0). A window counts toward the comparison if it is active in either channel.
3. A channel is *pitched* in a window if it is active and `FastYin` reports a
   pitch of 60–1400 Hz. `FastYin`'s default absolute threshold (0.20, i.e.
   confidence ≥ 0.80) is used as is; no separate threshold is tuned.
4. Let `L` and `R` be the fractions of counted windows in which each channel is
   pitched, and `margin = (L − R) / (L + R)`. The target is the channel with the
   larger fraction, provided:
   - at least 50 windows were counted (about 2.3 s of audio at 44.1 kHz),
   - `|margin| ≥ 0.20`, and
   - the winning channel is pitched in at least 50% of counted windows.

   Otherwise identification fails (insufficient audio, or no channel is clearly
   the target — e.g. both channels part-predominant, a full mix, or identical
   channels).

Evidence behind the choices (experiment `experiments/ChannelPitchExperiment.java`,
run on 5 songs × 4 parts of real stereo tracks from the choir library — target
left in four songs and right in one, with varying amounts of bleed from the other
parts — plus 30 synthetic "both channels part-predominant" cases made by pairing
two parts' target channels, plus one real full-mix file):
- Every real file selected the correct channel, with margins from 0.34 to 0.66;
  every negative case was rejected, with margins of at most 0.05. The 0.20 limit
  sits well inside that gap.
- Results are identical for any confidence threshold from 0.50 to 0.80 (the
  detector's own threshold dominates) and degrade above 0.85, which is why the
  default is used untouched. They are also identical across energy gates from
  0.001 to 0.03 and window sizes from 1024 to 4096. The window is 2048 because
  it can resolve pitches down to about 43 Hz, leaving headroom for bass voices.
- `FastYin` is used rather than `McLeodPitchMethod`: MPM had comparable margins
  but lower pitched fractions (falsely rejecting two real files against the 50%
  guard) and was about 25× slower (157 s versus 6 s for the whole data set),
  since its cost per window is quadratic.
- The whole file is analyzed rather than an excerpt. Short excerpts never chose
  the wrong channel but rejected valid files (18 of 20 real files at 5 s, 4 at
  10 s, 0 at 60 s with the weakest margin falling to 0.27), because intros are
  often silent or instrumental. Analysis costs about 0.15 s per channel for a
  3-minute track.
- The 50% winner guard is a sanity check that no real file came close to
  tripping (the weakest winner was pitched in 75% of windows); it has not been
  calibrated against adversarial input and should be revisited with more data.

Known limitations: the evidence covers five songs, so the thresholds should be
re-checked as more material is uploaded. A "solo" stereo file with the target
voice identical on both channels has no distinguishing margin and is rejected;
callers can supply `channel` explicitly for such files.

## 8. Asynchronous task framework (`async`)

- `AsyncTaskManager` — `@Component` with a fixed pool (10 threads).
  `schedule(TaskClass, ctorArgs...)` obtains a **prototype-scoped** task bean from
  the Spring context (so collaborators are injected), calls `initialize()`
  synchronously (validation + persist a SCHEDULED record), then submits
  `execute()` to the pool, returning a `TaskExec(task, Future)`. `scheduleAfter`
  does the same but holds `execute()` back until the given prerequisite futures
  have completed (see §7.4).
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
- `ProcessUploadedPartTask` — validate an uploaded part's media and turn it into
  the part's mono audio, extracting the target channel of a stereo upload (§7.4).
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
