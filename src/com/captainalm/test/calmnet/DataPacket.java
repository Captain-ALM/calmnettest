package com.captainalm.test.calmnet;

import com.captainalm.lib.calmnet.packet.IPacket;
import com.captainalm.lib.calmnet.packet.PacketException;
import com.captainalm.lib.calmnet.packet.PacketProtocolInformation;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Provides the data packet to store a byte array that can be backed to a file.
 *
 * @author Captain ALM
 */
public class DataPacket implements IPacket {
    private static final PacketProtocolInformation protocol = new PacketProtocolInformation((byte) 1, (byte) 1);

    protected File backingFile;
    protected byte[] backedData;
    protected final Object slock = new Object();

    /**
     * Constructs a new DataPacket with initial data.
     *
     * @param dataIn The data to initially use.
     */
    public DataPacket(byte[] dataIn) {
        backedData = dataIn;
    }

    /**
     * Constructs a new DataPacket backed by a specified {@link Path}.
     *
     * @param path The path to back to.
     */
    public DataPacket(Path path) {
        if (path != null) backingFile = path.toFile();
    }

    /**
     * Gets the backing file.
     *
     * @return The backing file or null.
     */
    public File getBackingFile() {
        return backingFile;
    }

    /**
     * Gets if the packet is valid.
     *
     * @return Is the packet valid?
     */
    @Override
    public boolean isValid() {
        synchronized (slock) {
            return (backingFile == null) || (backedData != null);
        }
    }

    /**
     * Gets the protocol information.
     *
     * @return The protocol information.
     */
    @Override
    public PacketProtocolInformation getProtocol() {
        return protocol;
    }

    /**
     * Gets the protocol information statically.
     *
     * @return The protocol information.
     */
    public static PacketProtocolInformation getTheProtocol() {
        return protocol;
    }

    /**
     * Unloads the stored data.
     */
    public void unload() {
        synchronized (slock) {
            backedData = null;
        }
    }

    /**
     * Saves the packet payload to a byte array.
     *
     * @return The packet payload data.
     * @throws PacketException An Exception has occurred.
     */
    @Override
    public byte[] savePayload() throws PacketException {
        synchronized (slock) {
            return internalSavePayload();
        }
    }

    protected byte[] internalSavePayload() throws PacketException {
        if (backedData == null && backingFile != null) {
            try {
                backedData = Files.readAllBytes(backingFile.toPath());
            } catch (IOException | SecurityException e) {
                throw new PacketException(e);
            }
        }
        if (backedData == null) throw new PacketException("no data");
        return backedData;
    }

    /**
     * Loads the packet payload from save data.
     *
     * @param packetData The packet payload data.
     * @throws NullPointerException The new store data is null.
     * @throws PacketException      An Exception has occurred.
     */
    @Override
    public void loadPayload(byte[] packetData) throws PacketException {
        if (packetData == null) throw new NullPointerException("dataIn is null");
        synchronized (slock) {
            internalLoadPayload(packetData);
        }
    }

    protected void internalLoadPayload(byte[] packetData) throws PacketException {
        backedData = packetData;
        if (backingFile != null) {
            try {
                Files.write(backingFile.toPath(), backedData);
            } catch (IOException | SecurityException | UnsupportedOperationException e) {
                throw new PacketException(e);
            }
        }
    }
}
