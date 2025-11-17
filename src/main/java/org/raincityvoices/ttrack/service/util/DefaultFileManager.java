package org.raincityvoices.ttrack.service.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.sound.sampled.AudioFileFormat.Type;

public class DefaultFileManager implements FileManager {
    @Override
    public InputStream getInputStream(File file) throws IOException {
        return new BufferedInputStream(new FileInputStream(file));
    }
    @Override
    public OutputStream getOutputStream(File file) throws IOException {
        return new BufferedOutputStream(new FileOutputStream(file));
    }
    @Override
    public AudioFileFormat getAudioFileFormat(File file) throws IOException, UnsupportedAudioFileException {
        return AudioSystem.getAudioFileFormat(file);
    }
    @Override
    public AudioInputStream getAudioInputStream(File file) throws IOException, UnsupportedAudioFileException {
        return AudioSystem.getAudioInputStream(file);
    }
    @Override
    public void writeWavAudio(AudioInputStream audioStream, File file) throws IOException {
        AudioSystem.write(audioStream, Type.WAVE, file);
    }
    @Override
    public boolean exists(File file) {
        return file.exists();
    }
    @Override
    public boolean delete(File file) {
        return file.delete();
    }
    @Override
    public boolean rename(File oldName, File newName) {
        return oldName.renameTo(newName);
    }
    @Override
    public Temp.File tempFile(String prefix, String suffix) throws IOException {
        return Temp.file(prefix, suffix);
    }
}
