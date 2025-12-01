package org.raincityvoices.ttrack.service.util;

import java.io.File;
import java.io.IOException;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.junit.jupiter.api.Test;

public class JsonUtilsTest {

    @Test
    public void testAudioFileFormatSerialization() throws UnsupportedAudioFileException, IOException {        
        AudioFileFormat format = AudioSystem.getAudioFileFormat(new File("src/test/resources/sunshine-lead.wav"));
        System.out.println(JsonUtils.toJson(format));
        format = AudioSystem.getAudioFileFormat(new File("src/test/resources/sunshine-lead.mp3"));
        System.out.println(JsonUtils.toJson(format));
    }
    
}
