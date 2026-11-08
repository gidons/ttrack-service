Functionality
=============
- Add "online mixer" API that takes a song ID and mix and returns an audio stream.
- Add ability to upload multiple part files simultaneously.
- Support configurable "mix packages"
- Add job that generates a full package
- Model keys as integer/enum 0..11, and pitch shift as addition/substraction mod 12.
  - E.g. if a song is originally in Bb, and we pitch-shift by -2, we now know it's in Ab.
- Use MP3 instead of WAV, at least for upload.
- Detect "part-only" stereo uploads, where the target part is on both channels. They are currently
  rejected as ambiguous, because neither channel is pitched more than the other. Recognize identical
  (or nearly identical) channels and use either one. Low priority: the uploader can pass `channel`.
- Make the reason an upload was rejected available to clients. Today a rejected part just loses its
  media. The reason is in the failed task's error details, but the upload endpoints don't return a
  task ID and the track doesn't record the error.

Bugs
====
- Incorrect filename for mixes

Tech Debt
=========
- Add uniqueness checks, including between PartTracks and MixTracks.
- UNIT TESTS
- INTEGRATION TESTS
- Block operations that read a part while an upload of it is still being processed. A mix created
  in that window fails or is built from the unprocessed media. (A multi-part upload already holds
  back its mix refresh until the uploads are processed.)
- Add timeouts and/or cancelation for async tasks, so that a stuck task can't leave a song's tracks
  deadlocked. A task holds its track's lock (`currentTaskId`) until it finishes, other tasks give up
  after 30 s, and the lock has no expiry: a hung task, or a server restart while a task is running,
  leaves the track locked permanently.
- Integration tests can't cover media download URLs, because they are signed with a User Delegation
  key, which Azurite doesn't support; the tests stub `MediaUrlProvider`. Find a way to test the real
  URLs (e.g. account-key SAS under the test profile).
- `TestRestTemplate` drops `Song.id` when it deserializes a `Song` response, although the body is
  correct and parses fine everywhere else. The integration tests parse responses with `JsonUtils`
  to avoid it (see `AzuriteIntegrationTestBase`); the cause was never found.

Refactor
========
- Consider creating a set of model classes that are independent of the storage implementation.
  Alternatively, figure out how to use the "API" classes internally, while hiding unnecessary
  information (like media location) from API callers.