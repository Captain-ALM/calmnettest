package com.captainalm.test.calmnet;

/**
 * Provides a type of packet.
 *
 * @author Captain ALM
 */
public enum PacketType {
    Message(0),
    Name(1),
    Data(2);
    int value;
    PacketType(int i) {
        value = i;
    }

    /**
     * Gets the integer value of the packet type.
     *
     * @return The integer value of the packet type.
     */
    public int getValue() {
        return value;
    }

    /**
     * Gets the packet type given the integer value.
     *
     * @param i The integer value.
     * @return The packet type or null.
     */
    public static PacketType getPacketType(int i) {
        switch (i) {
            case 0:
                return Message;
            case 1:
                return Name;
            case 2:
                return Data;
            default:
                return null;
        }
    }
}
