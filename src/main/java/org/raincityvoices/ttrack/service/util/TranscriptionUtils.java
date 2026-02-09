package org.raincityvoices.ttrack.service.util;

import java.util.List;
import java.util.stream.Stream;

import org.raincityvoices.ttrack.service.storage.timeddata.TimedTextDTO.Entry;

import com.azure.ai.speech.transcription.models.TranscribedPhrase;
import com.azure.ai.speech.transcription.models.TranscriptionResult;

public class TranscriptionUtils {

    /**
     * Convert Azure transcription results to a sequence of word entries.
     * After the last word of each phrase, insert an entry with the text "\n".
     * @param transcriptionResult
     * @return
     */
    public static List<Entry> getLyrics(TranscriptionResult transcriptionResult) {
        return transcriptionResult.getPhrases().stream().flatMap(p -> getLyricsForPhrase(p)).toList();
    }

    private static Stream<Entry> getLyricsForPhrase(TranscribedPhrase p) {
        return Stream.concat(
            // Word entries
            p.getWords().stream().map(w -> new Entry(w.getOffset(), w.getText())),
            // Newline to indicate the end of the phrase
            Stream.of(new Entry(p.getOffset() + p.getDuration().toMillis(), "\n")));
    }
}
