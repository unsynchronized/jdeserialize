import java.io.*;
import java.util.*;

/**
 * An InputStream designed to record data passing through the stream after a call to
 * record() is made.  After record() is called, the results from every read will 
 * be stored in an * internal buffer.  The contents of the buffer can be 
 * retrieved by getRecordedData(); to stop recording and clear the internal 
 * buffer, call stopRecording().
 *
 * Note: calls to mark() and reset() are merely passed through to the inner stream; if
 * recording is active, the buffer won't be backtracked by reset().
 */
public class LoggerInputStream extends InputStream {
    private InputStream innerStream = null;
    private ByteArrayOutputStream baos = null;
    private boolean recording = false; 

    public LoggerInputStream(InputStream innerStream) {
        super();
        this.innerStream = innerStream;
    }
    public synchronized int read() throws IOException {
        int i = innerStream.read();
        if(recording && i != -1) {
            if(i > 255 || i < 0) {
                throw new IOException("non-byte, non--1 value read from inner stream: " + i);
            }
            baos.write((byte)i);
        }
        return i;
    }
    public synchronized int read(byte[] b) throws IOException {
        return this.read(b, 0, b.length);
    }
    public synchronized int read(byte[] b, int off, int len) throws IOException {
        int retval = innerStream.read(b, off, len);
        if(recording && retval > 0) {
            if(retval > len) {
                throw new IOException("inner stream read(byte[], int, int) violating contract; return value > len: " + retval);
            }
            baos.write(b, off, retval);
        }
        return retval;
    }
    public synchronized long skip(long n) throws IOException {
        if(n < 0) {
            throw new IOException("can't skip negative number of bytes");
        }
        if(recording == false) {
            return innerStream.skip(n);
        }
        long nskipped = 0;
        while(n > Integer.MAX_VALUE) {
            long ret = skip(Integer.MAX_VALUE);
            nskipped += ret;
            if(ret < Integer.MAX_VALUE) {
                return nskipped;
            }
            n -= ret;
        }
        int toread = (int)n, actuallyread = 0;
        byte[] buf = new byte[10240];
        while(toread > 0) {
            int r = Math.min(toread, buf.length);
            int rret = this.read(buf, 0, r);
            actuallyread += rret;
            toread -= rret;
            if(rret < r) {
                break;
            }
        }
        return actuallyread;
    }
    public synchronized int available() throws IOException {
        return innerStream.available();
    }
    public synchronized void close() throws IOException {
        innerStream.close();
    }
    public synchronized void mark(int readlimit) {
        innerStream.mark(readlimit);
    }
    public synchronized void reset() throws IOException {
        innerStream.reset();
    }
    public boolean markSupported() {
        return innerStream.markSupported();
    }
    /**
     * If not currently recording, start recording.  If the stream is currently recording,
     * the current buffer is cleared.
     */
    public synchronized void record() {
        recording = true;
        baos = new ByteArrayOutputStream();
    }
    /**
     * Stops recording and clears the internal buffer.  If recording is not active, an
     * IOException is thrown.
     *
     * @throws IOException if recording is not currently active
     */
    public synchronized void stopRecording() throws IOException {
        if(recording == false) {
            throw new IOException("recording not active");
        }
        try {
            baos.close();
        } catch (IOException ignore) {}
        baos = null;
        recording = false;
    }
    /**
     * Returns the data recorded so far; if recording is not active, an empty buffer
     * is returned.
     */
    public synchronized byte[] getRecordedData() {
        if(recording == false) {
            return new byte[0];
        }
        return baos.toByteArray();
    }
}
