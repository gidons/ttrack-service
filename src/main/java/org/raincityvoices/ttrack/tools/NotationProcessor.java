package org.raincityvoices.ttrack.tools;

import static java.util.stream.Collectors.toMap;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;

import org.audiveris.proxymusic.Lyric;
import org.audiveris.proxymusic.Note;
import org.audiveris.proxymusic.ScorePartwise;
import org.audiveris.proxymusic.ScorePartwise.Part;
import org.audiveris.proxymusic.ScorePartwise.Part.Measure;
import org.audiveris.proxymusic.TextElementData;
import org.audiveris.proxymusic.util.Marshalling;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

public class NotationProcessor {

    @Command(name = "lyrics", aliases = {"lyr"})
    public static class ExtractLyrics implements Callable<Integer> {
        @Option(names = { "-i", "input"}, description = "The MusicXML file to analyze")
        private File inFile;

        @Override
        public Integer call() throws Exception {
            Marshalling.getContext(ScorePartwise.class);
            ScorePartwise score = (ScorePartwise) Marshalling.unmarshal(new FileInputStream(inFile));
            score.getPart().forEach(part -> {
                System.out.printf("Part %s:\n", part.getId());
                Map<String, List<Lyric>> lyricsByVoice = getLyrics(part);
            });
            return 0;
        }

        private Map<String, List<Lyric>> getLyrics(Part part) {
            return part.getMeasure().stream()
                .map(this::getLyrics)
                .flatMap(m -> m.entrySet().stream())
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue,
                    (oldList, newList) -> { oldList.addAll(newList); return oldList; }
                ));
        }

        private Map<String, String> getLyricsText(Measure measure) {
            return measure.getNoteOrBackupOrForward().stream()
                .filter(i -> i instanceof Note)
                .map(i -> getLyrics((Note)i))
                .reduce(new HashMap<String, String>(), this::accumlateLyricsByVoice);
        }

        private Map<String, List<Lyric>> getLyrics(Measure measure) {
            return measure.getNoteOrBackupOrForward().stream()
                .filter(i -> i instanceof Note)
                .map(i -> (Note) i)
                .collect(toMap(n -> n.getVoice(), n -> n.getLyric()));
        }

        private Map<String, String> getLyrics(Note note) {
            String voice = Objects.toString(note.getVoice(), "D");
            return note.getLyric().stream()
                .flatMap(i -> i.getElisionAndSyllabicAndText().stream())
                .filter(i -> i instanceof TextElementData)
                .map(i -> Map.of(voice, ((TextElementData)i).getValue()))
                .reduce(new HashMap<String, String>(), this::accumlateLyricsByVoice);
        }

        private Map<String, String> accumlateLyricsByVoice(Map<String, String> acc, Map<String, String> add) {
            add.forEach((voice, text) -> {
                acc.compute(voice, (v, accText) -> accText == null ? text : accText + " | " + text);
            });
            return acc;
        }
    }

    @Command(subcommands = {
        ExtractLyrics.class
    })
    public static class Main {

    }

    public static void main(String[] args) {
        CommandLine cl = new CommandLine(new Main());
        System.exit(cl.execute(args));
    }

}
