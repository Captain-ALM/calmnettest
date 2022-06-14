package com.captainalm.test.calmnet;

import com.captainalm.lib.calmnet.packet.*;
import com.captainalm.lib.calmnet.packet.factory.CALMNETPacketFactory;
import com.captainalm.lib.calmnet.packet.factory.IPacketFactory;

import java.nio.file.Path;

/**
 * This class creates packets.
 *
 * @author Captain ALM
 */
public final class MyPacketFactory extends CALMNETPacketFactory {
    private Path targetPath;

    /**
     * Constructs a new Instance of CALMNETPacketFactory with the specified {@link PacketLoader}.
     *
     * @param loader The packet loader to use.
     * @throws NullPointerException loader is null.
     */
    public MyPacketFactory(PacketLoader loader) {
        super(true ,loader);
    }

    /**
     * Constructs a new Instance of CALMNETPacketFactory with the specified {@link PacketLoader} and {@link IPacketFactory}.
     *
     * @param loader  The packet loader to use.
     * @param factory The packet factory to use or null (null signifies to use the same instance).
     * @throws NullPointerException loader is null.
     */
    public MyPacketFactory(PacketLoader loader, IPacketFactory factory) {
        super(true ,loader, factory);
    }

    /**
     * Constructs a {@link IPacket} of the protocol specified by the passed {@link PacketProtocolInformation} instance.
     *
     * @param information The protocol information to use.
     * @return The constructed packet or null.
     * @throws NullPointerException The information is null.
     */
    @Override
    public IPacket getPacket(PacketProtocolInformation information) {
        if (information == null) throw new NullPointerException("information is null");

        if (information.equals(AKNPacket.getTheProtocol())) return new AKNPacket();
        if (information.equals(TypePacket.getTheProtocol())) return new TypePacket(null);
        if (information.equals(DataPacket.getTheProtocol())) return (streamPreferred) ? new StreamedDataPacket(targetPath, true) : new DataPacket(targetPath);

        return super.getPacket(information);
    }

    /**
     * Gets the target path for {@link DataPacket}s.
     *
     * @return The target path or null.
     */
    public Path getTargetPath() {
        return targetPath;
    }

    /**
     * Sets the target path for {@link DataPacket}s.
     *
     * @param path The target path or null.
     */
    public void setTargetPath(Path path) {
        targetPath = path;
    }
}
