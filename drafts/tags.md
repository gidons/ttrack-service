Song Sets/Tags
==============

As an admin, I want map songs to tags. This is a many-to-many mapping: each song can have any number of tags.

Details:

1. Tags should have a human-readable name.
2. Tags have a type. Initially, this is one of:
  1. Set: songs intended to be performed together
  2. Collection: songs that have a common theme, e.g. holiday songs or love songs
  3. Genre
3. To clarify, a song can have multiple tags of the same type; e.g. a single song can be in multiple sets, collections, and/or genres.
4. The admin can create a tag with a type, add it to or remove it from a song, rename it, and delete it (even if non-empty). Changing types is not supported.
5. Users can list tag types, list tags by types, and list songs by tag.

General implementaiton guidelines:

- The API should be backwards-compatible unless this creates significant complications (don't hestitate to ask if so). Existing frontend and client code should keep working.
- Maintaining the data model or even the persistent data is not as important. *If it makes sense*, I'm OK with having to download and re-upload data (songs, media, text).