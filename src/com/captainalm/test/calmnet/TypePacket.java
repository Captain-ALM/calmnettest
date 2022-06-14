package com.captainalm.test.calmnet;

import com.captainalm.lib.calmnet.packet.IPacket;
import com.captainalm.lib.calmnet.packet.PacketException;
import com.captainalm.lib.calmnet.packet.PacketProtocolInformation;

/**
 * This packet provides the ability to send {@link PacketType}s.
 *
 * @author Captain ALM
 */
public class TypePacket implements IPacket {
    private static final PacketProtocolInformation protocol = new PacketProtocolInformation((byte) 1, (byte) 2);

    /**
     * This field holds the {@link PacketType} (Can be null).
     */
    public PacketType type;

    /**
     * Constructs a new instance of type packet using the specified {@link PacketType}.
     *
     * @param packetType The packet type or null.
     */
    public TypePacket(PacketType packetType) {
        type = packetType;
    }

    /**
     * Gets if the packet is valid.
     *
     * @return Is the packet valid?
     */
    @Override
    public boolean isValid() {
        return (type != null);
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
     * Saves the packet payload to a byte array.
     *
     * @return The packet payload data.
     * @throws PacketException An Exception has occurred.
     */
    @Override
    public byte[] savePayload() throws PacketException {
        if (type == null) throw new PacketException("no data");
        return new byte[] {(byte) type.getValue()};
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
        if (packetData == null) throw new NullPointerException("packetData is null");
        if (packetData.length != 1) throw new PacketException("packetData length is not 1");
        type = PacketType.getPacketType(packetData[0]);
    }
}
