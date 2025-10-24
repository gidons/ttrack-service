# Guidance for AI coding agents working on ttrack-service

This file captures concise, concrete knowledge an AI coding assistant needs to be immediately productive in this repository.

1. Big-picture
   - This is a Spring Boot 3.5 web service (entry: `ServiceApplication`).
   - Primary domain: storing, serving and mixing song audio parts. Key packages:
     - `org.raincityvoices.ttrack.service` — REST controllers and application wiring (`SongController`, `AudioMixer`, `AzureClients`).
     - `org.raincityvoices.ttrack.service.audio` — audio processing utilities (mixing, streams).
     - `org.raincityvoices.ttrack.service.audio.model` — audio domain models (`AudioPart`, `AudioMix`, formats).
     - `org.raincityvoices.ttrack.service.storage` — persistence DTOs and `SongStorage` abstraction used for Azure Table + Blob storage.

2. Build / run / test
   - Use Maven wrapper in repo root: `./mvnw clean package` to build and `./mvnw test` to run tests.
   - The app runs as a Spring Boot app; use `./mvnw spring-boot:run` or run `ServiceApplication` from IDE.
   - Tests are standard JUnit (see `src/test/...`). Running full build is fast enough for CI; prefer running focused test classes when iterating.

3. Azure integrations / credentials
   - Azure clients are created in `AzureClients` and use `AzureCliCredential` (so developers can use `az login` locally).
   - Configuration properties live under `azure.*` (see `AzureClients.AzureServiceConfig`). The code expects Table endpoint and Blob endpoint URLs.
   - Blob container name: `song-media`; Table name: `Songs` (hard-coded in `AzureClients`).

4. Important patterns & conventions
   - DTOs vs API models: persistence objects live under `service.storage.*` (e.g., `AudioTrackDTO`, `SongDTO`) and convert to API models under `service.api.*`.
   - Audio files are streamed via `MediaContent` wrappers and passed to `AudioSystem` for format detection and mixing.
   - Mixing is performed asynchronously: controller submits `AudioMixer` to a fixed thread pool (see `SongController.executorService`). Keep thread-safety and blocking IO in mind when editing.
   - Audio parts are represented by `AudioPart` (extends `StringId`). Each part should be a mono channel.

5. Error handling & HTTP behavior
   - Controllers use `ErrorResponseException` with HTTP status codes for client errors (404, 409, 400, 500). When adding new endpoints, follow the same pattern.
   - Downloads set Content-Disposition and content type from `MediaContent.metadata()` in `SongController.downloadTrack`.

6. Where to change behavior
   - To alter storage behavior, modify implementations of `SongStorage` (search for implementations under `service.storage`).
   - To change how Azure credentials are resolved, update `AzureClients.cliCredential()` — current approach expects local `az` authentication.
   - To add new audio processing features, look at `AudioMixingStream`, `MixUtils`, and `AudioMixer` for examples of audio IO and temporary-file usage.

7. Quick examples (copyable references)
   - Persist a new mix: `songStorage.writeTrack(AudioTrackDTO.fromMixTrack(mixTrack))` (used in `SongController.createMixTrack`).
   - Read part media for mixing: `MediaContent content = songStorage.readMedia(partTrack.blobName()); InputStream in = content.stream();` (used in `AudioMixer`).

8. Tests & fixtures
   - Unit tests for audio mixing live under `src/test/java/org/raincityvoices/ttrack/service/audio` and `audio.model` (see `StereoMixTest`, `MonoMixTest`). Use those as templates for audio-related tests.

9. Non-obvious gotchas discovered in code
   - Many audio operations use temporary files and delete them on exit — be careful when refactoring to avoid leaking temp files or keeping streams open.
   - The project uses Lombok heavily; generated methods are assumed in code and tests. Keep annotation processor config in `pom.xml` when changing model classes.

If anything is unclear or you want more examples (storage implementation, exact DTO mappings, or CI steps), tell me which area to expand. I'll iterate on this file.

10. Terraform / infrastructure (local infra config)
    - There is a `terraform/` folder: `terraform/infra.tf` defines Azure resources and `terraform/terraform.tfstate` is checked in (contains resource metadata and sensitive keys).
    - Resources created by the config: `azurerm_resource_group` named `TTrack`, a Storage Account `shavitttrackwestus`, a Blob container `song-media`, and a Storage Table `Songs`.
    - Important: `terraform.tfstate` contains secrets (storage account keys, connection strings). Treat it as sensitive. Avoid printing/committing state or exposing it in PRs. If you must touch it, do not include secrets in diffs and consult the repo owner about rotating keys.
    - How the app uses the infra: the app expects Table and Blob endpoints configured under `azure.*` (see `AzureClients`). Typical values are the storage account endpoints, e.g.:

```yaml
azure:
   blobs:
      endpoint: https://shavitttrackwestus.blob.core.windows.net/
   tables:
      endpoint: https://shavitttrackwestus.table.core.windows.net/
```

    - Basic local workflow for infra changes (do this only if you are authorized to manage resources):
       1. Authenticate: `az login` (or configure a service principal and export ARM_* env vars).
       2. In `terraform/` run: `terraform init` then `terraform plan` then `terraform apply`.
       3. After apply, copy endpoints/connection strings into application config (or set via env vars) — do not hardcode secrets in the repo.

    - If you need the storage endpoints for local development and a state file is present, derive them from the storage account name (found in `infra.tf` or `terraform.tfstate`) rather than copying keys. For example the blob endpoint is `https://<storage-account>.blob.core.windows.net/` and the table endpoint is `https://<storage-account>.table.core.windows.net/`.

    - If you find `terraform.tfstate` in the repo and it contains production secrets, notify the repository owner. Consider adding `terraform/terraform.tfstate` to `.gitignore` and moving state to remote backends (e.g., Azure Storage) — discuss with maintainers first.
