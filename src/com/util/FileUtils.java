package com.util;

import java.io.*;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * General File utility functions
 * @version 1.0.0
 */
public class FileUtils {

    private static final Logger log = Logger.getLogger(FileUtils.class);
    
    private final static char[] HEX_DIGITS = {
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    };

    public static String getMD5Hash(File f) throws IOException {
        if (log.isTraceEnabled()) {
            log.trace("Computing MD5 hash:  file=" + f.getPath());
        }
        try {
            MessageDigest complete = MessageDigest.getInstance("MD5");

            InputStream in = null;
            try {
                in = new FileInputStream(f);
                //in = new BufferedInputStream(in); // we're already buffering with a byte array

                byte[] buf = new byte[4096];
                int numRead;
                do {
                    numRead = in.read(buf);
                    if (numRead > 0) {
                        complete.update(buf, 0, numRead);
                    }
                } while (numRead != -1);
            } finally {
                closeIfNotNull(in);
            }

            byte[] hashRaw = complete.digest();

            //create hex string from the 16-byte hash    
            StringBuilder buf = new StringBuilder(hashRaw.length * 2);
            for (int i = 0; i < hashRaw.length; i++) {
                buf.append(HEX_DIGITS[(hashRaw[i] >>> 4) & 0x0f]);
                buf.append(HEX_DIGITS[hashRaw[i] & 0x0f]);
            }
            String hashStr = buf.toString();

            if (log.isDebugEnabled()) {
                log.debug("Computed MD5 hash:  file=" + f.getPath() + "; hash=" + hashStr);
            }

            return hashStr;

        } catch (NoSuchAlgorithmException e) {
            // This should never happen
            throw new IllegalStateException(e);
        }
    }
    
    public static File createTempFile(String prefix, String suffix) throws IOException {
        File f = File.createTempFile(prefix, suffix);
        logExt(Level.DEBUG, "Created temporary file:  " + f.getPath());
        return f;
    }

    public static File createTempFile(String prefix, String suffix, File dir) throws IOException {
        createDirectory(dir);
        File f = File.createTempFile(prefix, suffix, dir);
        logExt(Level.DEBUG, "Created temporary file:  " + f.getPath());
        return f;
    }

    public static void rename(File src, File dest) throws IOException {
        createParentDirectory(dest);
        if (src.renameTo(dest)) {
            logExt(Level.DEBUG, "Renamed file:  src=" + src.getPath() + "; dest=" + dest.getPath());
        } else {
            throw new FileRenameException(src, dest);
        }
    }

    public static void copy(File src, File dest) throws IOException
    {
        createParentDirectory(dest);
        
        FileInputStream in = null;
        FileOutputStream out = null;
        FileChannel inChannel = null;
        FileChannel outChannel = null;
        try {
            in = new FileInputStream(src);
            inChannel = (in).getChannel();
            out = new FileOutputStream(dest);
            outChannel = (out).getChannel();
            inChannel.transferTo(0L, inChannel.size(), outChannel);
            logExt(Level.DEBUG, "Copied file:  src=" + src.getPath() + "; dest=" + dest.getPath());
        } finally {
            closeIfNotNull(outChannel);
            closeIfNotNull(out);
            closeIfNotNull(inChannel);
            closeIfNotNull(in);
        }
    }

    public static void move(File src, File dest) throws IOException {
        if (dest.exists()) {
            delete(dest);
        } else {
            createParentDirectory(dest);
        }
        if (src.renameTo(dest)) {
            logExt(Level.DEBUG, "Moved file (via rename):  src=" + src.getPath() + "; dest=" + dest.getPath());
        } else {
            logExt(Level.TRACE, "File move via rename failed.  Trying copy+delete...");
            try {
                copy(src, dest);
                if (delete(src)) {
                    logExt(Level.DEBUG, "Moved file (via copy+delete):  src=" + src.getPath() + "; dest=" + dest.getPath());
                } else {
                    logExt(Level.ERROR, "Error moving file (via copy+delete) - deletion failed.  Deleting destination:  src=" + src.getPath() + "; dest=" + dest.getPath());
                    delete(dest);
                }
            } catch (IOException e) {
                logExt(Level.ERROR, "Error moving file (via copy+delete).  Deleting destination:  src=" + src.getPath() + "; dest=" + dest.getPath() + "; exception=" + e);
                delete(dest);
                throw e;
            } catch (RuntimeException e) {
                logExt(Level.ERROR, "Error moving file (via copy+delete).  Deleting destination:  src=" + src.getPath() + "; dest=" + dest.getPath() + "; exception=" + e);
                delete(dest);
                throw e;
            }
        }
    }
    
    /**
     * Creates a directory (and all parent directories).
     * @param dir directory to create
     * @throws IOException if the directory could not be created
     * 
     * This method will not throw an exception if the directory already exists
     */
    public static void createDirectory(File dir) throws IOException {
        if (!dir.exists()) {
            if (dir.mkdirs()) {
                logExt(Level.DEBUG, "Created directory:  " + dir.getPath());
            } else {
                throw new DirectoryCreationException(dir);
            }
        }
    }

    /**
     * Creates a parent directory (and all parent directories).
     * @param child an object within a directory
     * @throws IOException if the parent directory could not be created
     * 
     * This method will not throw an exception if the parent directory already exists
     * or if the child has no parent.
     */
    public static void createParentDirectory (File child) throws IOException {
        File parent = child.getParentFile();
        if (parent != null) {
            createDirectory(parent);
        }
    }

    public static boolean deleteIfNotNull(File f) {
        if (f != null) {
            return delete(f);
        } else {
            return true;
        }
    }

    public static boolean delete(File f) {
        if (f.exists()) {
            boolean ok = f.delete();
            if (ok) {
                logExt(Level.DEBUG, "Deleted file:  " + f.getPath());
            } else {
                logExt(Level.WARN, "File deletion failed:  " + f.getPath());
            }
            return ok;
        } else {
            logExt(Level.DEBUG, "File deletion ignored -- file not found:  " + f.getPath());
            return true;
        }
    }

    public static void close(Closeable closeable) {
        closeIfNotNull(closeable);
    }

    /**
     * Strictly speaking, this should be in a general IO utilities class
     * @param closeable
     */
    public static void closeIfNotNull(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                logExt(Level.WARN, "Error closing closeable:  " + closeable);
            }
        }
    }
    
    protected static void logExt(Level level, Object message) {
        if (log.isTraceEnabled()) {
            log.log(level, message, new Throwable("Debug stacktrace"));
        } else {
            log.log(level, message);
        }
    }
}
