package com.captainalm.test.calmnet;

import com.captainalm.lib.calmnet.packet.IStreamedPacket;
import com.captainalm.lib.calmnet.packet.PacketException;
import com.captainalm.lib.calmnet.stream.LengthClampedInputStream;

import java.io.*;
import java.nio.file.Path;

/**
 * Provides the data packet to store a byte array that can be backed to a file that is stream stored.
 *
 * @author Captain ALM
 */
public class StreamedDataPacket extends DataPacket implements IStreamedPacket {
    protected static final int transferBufferSize = 2048;
    protected boolean passThroughStream = false;

    /**
     * Constructs a new StreamedDataPacket with initial data.
     *
     * @param dataIn The data to initially use.
     */
    public StreamedDataPacket(byte[] dataIn) {
        super(dataIn);
    }

    /**
     * Constructs a new stream data packet backed by a specified {@link Path}.
     *
     * @param path The path to back to.
     * @param passThroughStream If the backing array should not be used.
     */
    public StreamedDataPacket(Path path, boolean passThroughStream) {
        super(path);
        this.passThroughStream = passThroughStream;
    }

    /**
     * Gets whether the backing array is not used when using {@link IStreamedPacket} methods.
     * The backing array is always used when the store is not file backed.
     *
     * @return If the backing array is not used.
     */
    public boolean isStreamPassedThrough() {
        return passThroughStream;
    }

    /**
     * Sets whether the backing array is not used when using {@link IStreamedPacket} methods.
     * The backing array is always used when the store is not file backed.
     *
     * @param passThroughStream If the backing array should not be used.
     */
    public void setStreamPassedThrough(boolean passThroughStream) {
        synchronized (slock) {
            this.passThroughStream = passThroughStream;
        }
    }

    /**
     * Gets if the packet is valid.
     *
     * @return Is the packet valid?
     */
    @Override
    public boolean isValid() {
        boolean toret = super.isValid();
        synchronized (slock) {
            return toret || passThroughStream;
        }
    }

    /**
     * Reads payload data to an {@link OutputStream}.
     *
     * @param outputStream The output stream to read data to.
     * @throws NullPointerException outputStream is null.
     * @throws IOException          An IO Exception has occurred.
     * @throws PacketException      An Exception has occurred.
     */
    @Override
    public void readData(OutputStream outputStream) throws IOException, PacketException {
        synchronized (slock) {
            if (passThroughStream && backingFile != null) {
                try (InputStream streamIn = new FileInputStream(backingFile)) {
                    int length;
                    byte[] transferBuffer = new byte[transferBufferSize];
                    while ((length = streamIn.read(transferBuffer)) != -1)
                        outputStream.write(transferBuffer, 0, length);
                } catch (SecurityException e) {
                    throw new PacketException(e);
                }
            } else {
                outputStream.write(internalSavePayload());
            }
        }
    }

    /**
     * Writes payload data from an {@link InputStream}.
     *
     * @param inputStream The input stream to write data from.
     * @param size        The size of the input payload in bytes.
     * @throws NullPointerException     inputStream is null.
     * @throws IllegalArgumentException size is less than 0.
     * @throws IOException              An IO Exception has occurred.
     * @throws PacketException          An Exception has occurred.
     */
    @Override
    public void writeData(InputStream inputStream, int size) throws IOException, PacketException {
        synchronized (slock) {
            if (passThroughStream && backingFile != null) {
                try (OutputStream streamOut = new FileOutputStream(backingFile); LengthClampedInputStream linputStream = new LengthClampedInputStream(inputStream, size)) {
                    int length;
                    byte[] transferBuffer = new byte[transferBufferSize];
                    while ((length = linputStream.read(transferBuffer)) != -1)
                        streamOut.write(transferBuffer, 0, length);
                } catch (SecurityException e) {
                    throw new PacketException(e);
                }
            } else {
                byte[] dataIn = new byte[size];
                int offset = 0;
                int slen;
                while (offset < size) if ((slen = inputStream.read(dataIn, offset, size - offset)) != -1) offset += slen; else throw new IOException("inputStream end of stream");
                internalLoadPayload(dataIn);
            }
        }
    }

    /**
     * Gets the size of the output data.
     *
     * @return The size of the output data in bytes.
     * @throws PacketException An Exception has occurred.
     */
    @Override
    public int getSize() throws PacketException {
        synchronized (slock) {
            if (passThroughStream) {
                try {
                    return Math.toIntExact(backingFile.length());
                } catch (ArithmeticException | SecurityException e){
                    throw new PacketException(e);
                }
            } else {
                if (backedData == null) return 0; else return backedData.length;
            }
        }
    }
}
