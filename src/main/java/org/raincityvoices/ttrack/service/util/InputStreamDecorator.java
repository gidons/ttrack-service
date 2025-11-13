package org.raincityvoices.ttrack.service.util;

import java.io.IOException;
import java.io.InputStream;

public abstract class InputStreamDecorator extends InputStream {

    private final InputStream decorated;
    private byte[] oneByte = new byte[1];

    protected InputStreamDecorator(InputStream decorated) {
        this.decorated = decorated;
    }

    protected InputStream decorated() { return decorated; }

    @Override
    public int read() throws IOException {
        int ret = read(oneByte, 0, 1);
        return ret < 0 ? ret : oneByte[0];
    }

    @Override
    public abstract int read(byte[] b, int off, int len) throws IOException;

    @Override
    public void mark(int readlimit) {
        decorated.mark(readlimit);
    }

    @Override
    public boolean markSupported() {
        return decorated.markSupported();
    }

    @Override
    public void reset() throws IOException {
        decorated.reset();
    }

    @Override
    public int available() throws IOException {
        return decorated.available();
    }

    @Override
    public void close() throws IOException {
        decorated.close();
    }
}
