# Requirements — ttrack-service

> Reverse-engineered from the existing codebase. This document describes the
> behavior the service **currently** implements, framed as requirements, so it
> can serve as a baseline for planned changes and improvements.

## Overview

`ttrack-service` is the backend web service for the TTrack system, a tool for
rehearsing choral (barbershop-style) music. It stores and serves per-song data:
audio tracks, timestamped text (lyrics, bookmarks, measures), notation files
(e.g. MusicXML), and song metadata (title, key, arranger, voicing).

The system has three parts, each in its own Git repository:

- **ttrack-service** (this repo, `gidons/ttrack-service`) — Spring Boot backend.
- **ttrack-fe** (`gidons/ttrack-fe`) — web front-end for managing songs and data.
- **ttrack-android / TTrackPlayer** (`gidons/ttrack-android`) — mobile player that
  downloads and plays songs.

The central audio concept: a song has mono **part** tracks (one per voice, e.g.
Bass, Tenor, Lead, Bari). From those parts the service generates **mix** tracks —
stereo (or multi-channel) audio that blends the parts with per-channel weights
(e.g. "Bass on the left, everyone else on the right"), optionally pitch-shifted
and/or speed-adjusted. The player downloads parts and mixes; the front-end
manages the underlying data.

## Glossary

- **Song** — a unit of music with metadata and associated tracks/data/notation.
- **Song metadata** — the descriptive fields of a song: full title, short title
  (used as a track-name prefix), version, arranger, key, voicing, and an archived
  flag. This is the canonical field set referenced by the requirements below.
- **Part track** — single-channel (mono) audio for one voice part. Track ID is
  the part name (e.g. `Bass`).
- **Target channel** — in a stereo upload for a single part, the channel (left =
  `0` or right = `1`) that carries that part's audio. The service extracts the
  target channel and stores it as the mono part track.
- **Mix track** — audio produced by combining part tracks per a mix definition.
  Track ID is the mix name (e.g. `Bass Left`).
- **Mix definition / `AudioMix`** — how N part inputs map to output channels with
  weights. Three kinds: `MonoMix` (N→1), `StereoMix` (N→2), `AllPartsMix` (N→N).
- **All-channel mix** — a system-managed mix (ID `All`) placing each part on its
  own output channel; kept in sync automatically as parts change.
- **Pitch shift** — transposition in half-steps (−11..+11).
- **Speed factor** — playback-rate multiplier (0.1..3.0), independent of pitch.
- **Timed text data** — time-stamped text entries per part and type (lyrics,
  bookmarks, measures).
- **Notation file** — a non-audio song file such as MusicXML, stored per song.
- **Async task** — a long-running operation (mixing, upload post-processing,
  zipping) tracked with a status record the client can poll.

---

## Requirements

### 1. Client / player: browse and download content

**User story:** As a player (mobile) or front-end client, I want to discover the
available songs and download their content for playback, so that a singer can
rehearse offline against the parts, mixes, and synchronized text for a song.

This requirement covers the read-only, consumer-facing capabilities that the
player and front-end depend on. The write-side management of this same content is
covered by the data-manager requirements that follow.

#### Acceptance criteria

1. WHEN a client lists songs THEN the service SHALL return the available songs
   with their **song metadata** (see glossary), so that the client can present a
   browsable catalog.
2. WHEN a client lists songs THEN archived songs SHALL be excluded by default, so
   that players only see songs intended for use.
3. WHEN a client requests the extended view of a song (list or single) THEN the
   response SHALL include enough information to download all of the song's
   associated content, namely: the list of part IDs, the `All`-mix media URL and
   its last-updated time, the notation URL and its last-updated time (if present),
   and the timed-text URL and its last-updated time.
4. WHEN a client lists the parts or mixes of a song THEN the service SHALL return,
   for each track, its metadata plus a download URL for the track's media, so that
   the client can fetch any individual part or mix.
5. WHEN a client downloads a track's media (part or mix) THEN the service SHALL
   stream the audio with a correct content type and a filename, so that the
   downloaded file is usable and identifiable offline.
6. WHEN a client requests a song's timed text data (all, per part, or per
   part+type) THEN the service SHALL return the lyrics, bookmarks, and measure
   markers with their time offsets, so that the client can display content
   synchronized to playback.
7. WHEN a client requests notation for a song that has a notation file THEN the
   service SHALL make the notation file (e.g. MusicXML) downloadable.
8. The service SHALL enable clients to detect changes without downloading full
   content, by exposing last-updated times for a song's media, timed text, and
   notation in the extended song view (per criterion 3).
9. Responses that contain links back to the service SHALL have those links
   rewritten to the client-facing base URL of the incoming request, so that a
   client can follow returned URLs without constructing them itself.
10. Media and file download URLs returned to clients SHALL be stable, working,
    time-limited signed URLs (currently valid for 60 minutes), so that a client
    can download content directly from storage within that window.

> Detailed behavior of the underlying list/describe/download endpoints (status
> codes, filename derivation, `includeShared`, etc.) is specified in the
> management requirements below (§2 Song metadata, §3 Part tracks, §4 Mix tracks,
> §5 Timed text, §6 Notation). This requirement states the client/player's
> consumer-facing expectations across those capabilities.

### 2. Song metadata management

**User story:** As a data manager, I want to create, view, update, list, and
remove songs, so that the catalog reflects the choir's repertoire.

#### Acceptance criteria

1. WHEN a client POSTs a song THEN the service SHALL persist it and, IF the song
   has no ID, SHALL assign a generated identifier (8 hex digits).
2. WHEN a song is created THEN the service SHALL create/refresh its `All`
   (all-channel) mix.
3. WHEN a client GETs a song by ID THEN the service SHALL return its **song
   metadata** (see glossary), OR return 404 IF no such song exists.
4. WHEN a client GETs a song with `extended=true` THEN the response SHALL also
   include the list of part IDs, the media URL and last-updated time of the
   `All` mix, the notation URL/updated time (if a notation file exists), and the
   text-data URL/updated time.
5. WHEN a client PUTs a song whose URL ID does not match the body ID THEN the
   service SHALL reject the request with 400.
6. WHEN a client lists songs THEN the service SHALL return all non-archived
   songs by default, OR include archived songs WHEN `includeArchived=true`.
7. WHEN listing songs with `extended=true` THEN each song SHALL include the
   extended fields described in criterion 4.
8. WHEN a client DELETEs a song THEN the service SHALL archive it (soft delete),
   not hard-delete it.
9. The service SHALL persist and return the full **song metadata** field set
   (see glossary).

### 3. Part track management

**User story:** As a data manager, I want to upload and manage audio for each
voice part — supplied either as mono, or as stereo with the part isolated on one
channel — so that the service stores a clean mono part from which mixes can be
generated and which the player can download.

A single voice part is always stored as mono audio. Uploads may be supplied in
two forms:
- **mono** — stored as the part directly; or
- **stereo** — the part is isolated on a **target channel** (see glossary); the
  service extracts that channel and stores it as the mono part.

#### Acceptance criteria

1. WHEN a client uploads audio for a single part (`PUT /songs/{id}/parts/{part}`)
   THEN the service SHALL store the uploaded media, create the part track if
   needed, and schedule post-processing of the uploaded audio.
2. A single-part upload MAY include an optional `channel` query parameter (`0`
   for left, `1` for right) naming the target channel of a stereo upload. Any
   other value SHALL be rejected with 400, before anything is stored.
3. IF a part already exists AND `overwrite` is not true THEN the upload SHALL be
   rejected with 409; WHEN `overwrite=true` THEN the existing part SHALL be
   replaced.
4. WHEN a client uploads audio for multiple parts in one request
   (`PUT /songs/{id}/parts`) THEN the service SHALL store each file as the named
   part, SHALL reject the request with 400 IF the number of files and part names
   differ, and after upload SHALL refresh the `All` mix and schedule a refresh of
   all existing mixes. The refresh of the existing mixes SHALL NOT start until
   post-processing of all the uploaded files has finished, because mixes are built
   from the processed parts. A target channel MAY be given per file via parallel
   `channel0`, `channel1`, … form fields (alongside the existing per-file part
   name fields); an invalid value SHALL cause the whole request to be rejected
   with 400, before anything is stored. Each file SHALL be post-processed
   independently (per criteria 5–6); files SHALL NOT be assumed to share a form or
   target channel.
5. WHEN uploaded part audio is post-processed THEN the service SHALL validate the
   media and produce a mono part as follows:
   - the media MUST be in a supported format (currently WAV or MP3) and have
     either one or two channels; otherwise processing SHALL fail with a clear
     error (see criterion 7);
   - IF the media is mono (one channel) THEN it SHALL be stored as the part as-is
     (updating duration/metadata);
   - IF the media is stereo (two channels) THEN the service SHALL isolate the
     target channel and store it as the mono part.
6. WHEN post-processing a stereo upload THEN the service SHALL determine the
   target channel as follows:
   - IF the client specified a `channel` THEN that channel SHALL be used;
   - OTHERWISE the service SHALL attempt to identify the target channel
     automatically, and SHALL fail with a clear error (see criterion 7) IF it
     cannot do so with high confidence.
7. Validation and extraction (criteria 5–6) are performed in the asynchronous
   post-processing task, not synchronously during the HTTP upload (see the design
   note below). WHEN such processing fails (unsupported format, wrong channel
   count, or low-confidence channel identification) THEN the task SHALL fail with
   descriptive error details and the part SHALL be left without usable media.
8. WHEN a client lists parts for a song THEN the service SHALL return each part
   track with its metadata, a track URL, and a time-limited media download URL.
9. WHEN a client requests a specific part THEN the service SHALL return its
   details, OR behave as not-found IF the part does not exist.
10. WHEN a client downloads part media THEN the service SHALL stream the audio
    with an appropriate content type and a `Content-Disposition` filename derived
    from the song title and part name, OR return 404 IF the part or its media is
    missing.

> **Design note — validation is asynchronous (revisit later).** Format and
> channel validation and channel extraction happen in the asynchronous
> post-processing task rather than synchronously during the upload request. This
> is intentional: the upload handler streams the media straight to cloud storage
> rather than buffering it locally, which keeps the request lightweight and
> allows the post-processing task to run on a separate host. The tradeoff is that
> a client does not get a synchronous error for a malformed or wrong-shape
> upload; it must observe the task outcome instead. Moving the cheap checks
> (format, channel count) earlier — ideally to a synchronous 400 — is a planned
> improvement.

> **Note — target-channel auto-identification.** When the client does not supply
> `channel`, the service identifies the target channel from the audio content: in
> a stereo upload the target channel carries a single voice, while the other
> channel carries the same voice or a blend that includes other parts. A file in
> which neither channel is clearly the target (for example, both channels are
> part-predominant, or the channels are identical) is not identifiable and is
> handled per criterion 7; the client can resubmit with an explicit `channel`.
> The algorithm is described in the design (§7.5); this requirement only
> specifies the observable behavior (use the specified channel, else
> auto-identify, else fail clearly).

### 4. Mix track generation and management

**User story:** As a data manager, I want to define and generate mixes that
blend parts with per-channel weighting, pitch, and speed, so that singers can
rehearse against configurable backing tracks.

#### Acceptance criteria

1. WHEN a client requests mix creation (`POST /songs/{id}/mixes`) THEN the
   service SHALL verify the song exists (404 otherwise) and that every requested
   part exists (400 otherwise).
2. The create-mix request SHALL support two shapes:
   - a **single mix** request (name, parts, mix description, pitch shift, speed
     factor); and
   - a **mix package** request (parts, a list of mix descriptions, optional
     package description suffix, shared pitch/speed), which SHALL expand into one
     mix per description.
3. The service SHALL validate that `speedFactor` is within 0.1..3.0 and
   `pitchShift` is within −11..+11, rejecting invalid requests with 400.
4. The service SHALL parse mix descriptions supporting at least:
   - `full` / `balanced` — all parts equally on both channels;
   - `<part> solo` / `only` — that part only, both channels;
   - `<part> left` / `stereo` — that part on the left, muted on the right;
   - `<part> right` — that part on the right, muted on the left;
   - `<part> dominant` / `predominant` — that part weighted above the others;
   - `<part> missing` / `no` — that part muted, others mixed;
   - `<part1> <part2>` (duet) — part 1 left, part 2 right;
   - a raw JSON `StereoMix` object (left/right factor arrays);
   - `all` — each part on its own channel (all-channel mix).
   An unparseable description SHALL be rejected with 400.
5. WHEN a mix name already exists AND `overwrite` is not true THEN creation SHALL
   be rejected with 409; WHEN `overwrite=true` THEN the existing mix definition
   SHALL be replaced and re-rendered.
6. WHEN a mix is created THEN the service SHALL persist the mix definition and
   schedule an asynchronous task to render and store the mixed audio.
7. The rendered mix SHALL be encoded as MP3 when it has ≤2 output channels, and
   as WAV otherwise.
8. WHEN rendering a mix THEN the service SHALL apply the stored pitch shift and
   speed factor if either differs from the default (0 / 1.0).
9. WHEN a client requests a mix refresh (`POST /songs/{id}/mixes/{mix}/refresh`)
   THEN the service SHALL re-render it from its stored definition, OR return 404.
10. WHEN a client lists or describes mixes THEN the service SHALL return mix
    metadata including the mix definition, parts, pitch/speed, a track URL, and a
    time-limited media download URL; a missing mix SHALL yield 404.
11. WHEN a client downloads mix media THEN the service SHALL stream it with the
    right content type and a filename derived from the song title and mix name,
    OR 404 IF the mix or its media is missing.
12. WHEN a client DELETEs a mix THEN the service SHALL remove the mix track
    record and its stored media, OR return 404 IF the mix does not exist.
13. WHEN a client requests the default mixes for a song
    (`GET /songs/{id}/defaultMixes`) THEN the service SHALL return a generated
    catalog of standard mix definitions based on the song's parts (Full Mix;
    per-part Solo/Left/Dominant/Missing; all two-part Duets).
14. The service SHALL maintain an `All` (all-channel) mix per song whose parts
    track the current set of part tracks, refreshing it when parts change.

### 5. Timed text data (lyrics, bookmarks, measures)

**User story:** As a data manager, I want to attach time-stamped text to a song
and its parts, so that the player can display synchronized lyrics and markers.

#### Acceptance criteria

1. Timed text data SHALL be organized by part and by type, where types include
   at least `LYRICS`, `BOOKMARKS`, and `MEASURES`, and a special `ALL` part
   denotes data shared across all parts.
2. Each data entry SHALL have a time offset in milliseconds and a text value.
3. WHEN a client GETs all text data for a song THEN the service SHALL return the
   data grouped by part and type, with entries sorted by time.
4. WHEN a client GETs text data for a specific part THEN the service SHALL return
   that part's data, and SHALL additionally include shared (`ALL`) data WHEN
   `includeShared=true`.
5. WHEN a client GETs a specific part+type THEN the service SHALL return that
   list of entries (empty list if none).
6. WHEN a client PUTs entries for a part+type, or PUTs a full timed-text
   structure for a song THEN the service SHALL persist the provided data.

### 6. Notation files

**User story:** As a data manager, I want to attach a notation file (e.g.
MusicXML) to a song, so that the player can display the score.

#### Acceptance criteria

1. WHEN a client uploads a notation file (`PUT /songs/{id}/notation`) THEN the
   service SHALL store it as the song's notation file.
2. WHEN a client deletes the notation file THEN the service SHALL remove it.
3. WHEN a song is fetched in extended mode AND a notation file exists THEN the
   response SHALL include the notation URL and its last-updated time.

### 7. Bulk download (zip)

**User story:** As a user, I want to download several tracks of a song as one
archive, so that I can grab everything at once.

#### Acceptance criteria

1. WHEN a client POSTs a list of track IDs to `POST /songs/{id}/zip` THEN the
   service SHALL validate that the list is present (400 otherwise) and that every
   track exists and has media (400 otherwise).
2. The service SHALL perform the zipping asynchronously and return the task ID
   for status polling.
3. WHEN the zip task completes THEN its output SHALL include a time-limited
   download URL for the archive, the archive filename, and an expiry time.

### 8. Asynchronous task tracking

**User story:** As a client, I want to poll the status of long-running
operations, so that I can show progress and fetch results.

#### Acceptance criteria

1. Long-running operations (mix creation, mix refresh, refresh-all-mixes, upload
   post-processing, zipping) SHALL run as asynchronous tasks with a persisted
   status record.
2. A task record SHALL progress through states: scheduled → pending → running →
   succeeded, or → failed on error (with error details).
3. WHEN a client GETs a task by ID (`GET /tasks/{id}`) THEN the service SHALL
   return its ID, status, input, output, scheduled time, and end time, OR 404 IF
   no such task exists.
4. Tasks that modify a specific track SHALL acquire a lock on that track before
   processing and release it afterward, so that concurrent tasks do not corrupt
   the same track; a task SHALL fail IF it cannot acquire the lock within a
   bounded time.

### 9. Authentication and authorization

**User story:** As the system owner, I want API access protected, so that only
authenticated users can read or modify data.

#### Acceptance criteria

1. All endpoints except `"/public/**"`, the error path, and HTTP `OPTIONS`
   (CORS preflight) SHALL require authentication.
2. The service SHALL authenticate requests using Clerk-issued bearer tokens.
3. Requests originating from localhost SHALL be automatically authenticated with
   an administrative identity (to support local development).
4. Sessions SHALL be stateless; CSRF protection is disabled because the API uses
   bearer tokens.
5. CORS SHALL be enabled so the browser-based front-end can call the API.

### 10. Operational concerns

**User story:** As an operator, I want the service observable and deployable, so
that I can run it reliably in production.

#### Acceptance criteria

1. The service SHALL expose health, info, metrics, Prometheus, loggers, and
   logfile actuator endpoints.
2. The service SHALL support exporting metrics to Azure Monitor when configured.
3. The service SHALL log HTTP request/response traffic.
4. The service SHALL accept multipart uploads up to at least 100 MB.
5. The service SHALL be buildable and deployable as a Spring Boot fat JAR and as
   a Docker image (the image includes `ffmpeg`), and SHALL deploy to Azure App
   Service via CI on pushes to `main`.

---

## Non-functional notes

- **Platform:** Java 21, Spring Boot 3.5.
- **State:** All durable state lives in Azure (Table Storage + Blob Storage +
  Key Vault); the service holds only a local disk cache for media.
- **Credentials:** Azure access uses `DefaultAzureCredential` (supports
  `az login` locally and managed identity in Azure). Clerk API key comes from
  Azure Key Vault.

## Known issues / TODO carried from the codebase

These are recorded as context for future work, not as implemented behavior:

- Incorrect filename generated for some mixes.
- No uniqueness checks across part and mix tracks.
- Sparse unit and integration test coverage.
- Planned: online (on-demand) mixer endpoint; configurable mix packages; model
  keys as integers 0..11 with pitch-shift as modular arithmetic; prefer MP3 over
  WAV for uploads.
- `AudioTrackDTO.isValid()` has an operator-precedence quirk that lets a mix
  track pass validation even with a null song/track ID.
- Mix rendering reads part streams with potentially blocking I/O; the all-channel
  mix is regenerated on part changes.
