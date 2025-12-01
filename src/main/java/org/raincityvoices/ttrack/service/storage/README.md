Storage Model
=============

Table: Songs
------------
This table contains two kinds of rows: songs and tracks.

- Songs
  - **PartitionKey**: song ID.
  - **RowKey**: empty.
  - **DTO**: SongDTO

  This contains metadata about the song, like title, arranger, etc.

- Tracks
  - **PartitionKey**: song ID.
  - **RowKey**: track ID (either the part name, for part-tracks, or the mix name, for mix-tracks).
  - **DTO**: AudioTrackDTO

  This contains metadata about each audio track, including the audio mix used to create it, and the name of the blob that contains the actual media.

Container: song-media
---------------------
Contains the blobs that have the audio media for every track.

Blob name format: <songId>/<trackId>