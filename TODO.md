Functionality
=============
- Add "online mixer" API that takes a song ID and mix and returns an audio stream.
- Add ability to upload multiple part files simultaneously.
- Support configurable "mix packages"
- Add job that generates a full package
- Model keys as integer/enum 0..11, and pitch shift as addition/substraction mod 12.
  - E.g. if a song is originally in Bb, and we pitch-shift by -2, we now know it's in Ab.
- Use MP3 instead of WAV, at least for upload.

Bugs
====
- Incorrect filename for mixes

Tech Debt
=========
- Add uniqueness checks, including between PartTracks and MixTracks.
- UNIT TESTS
- INTEGRATION TESTS

Refactor
========
- Consider creating a set of model classes that are independent of the storage implementation.
  Alternatively, figure out how to use the "API" classes internally, while hiding unnecessary
  information (like media location) from API callers.