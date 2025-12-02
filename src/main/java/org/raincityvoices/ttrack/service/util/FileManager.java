package org.raincityvoices.ttrack.service.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.sound.sampled.AudioFileFormat.Type;

import javazoom.spi.mpeg.sampled.file.MpegFileFormatType;

/**
 * A representation of the operations the service needs to perform on files on disk.
 * This serves two purposes:
 * <ul>
 * <li>Abstract some of the file system operations so they can be tested and/or implemented in different ways
 * <li>Provide a useful place to implement some useful functionality, like exception and timeout handling.
 * </ul>
 */
public interface FileManager {
    AudioInputStream getAudioInputStream(File file) throws IOException, UnsupportedAudioFileException;
    InputStream getInputStream(File file) throws IOException;
    OutputStream getOutputStream(File file) throws IOException;
    AudioFileFormat getAudioFileFormat(File file) throws IOException, UnsupportedAudioFileException;
    void writeAudio(AudioInputStream audioStream, AudioFileFormat.Type fileType, File file) throws IOException;
    default void writeWavAudio(AudioInputStream audioStream, File file) throws IOException {
        writeAudio(audioStream, Type.WAVE, file);
    }
    default void writeMp3Audio(AudioInputStream audioStream, File file) throws IOException {
        writeAudio(audioStream, MpegFileFormatType.MP3, file);
    }
    boolean exists(File file);
    boolean delete(File file);
    boolean rename(File oldName, File newName);
    Temp.File tempFile(String prefix, String suffix) throws IOException;
}
