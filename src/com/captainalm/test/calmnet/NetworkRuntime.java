package com.captainalm.test.calmnet;

import com.captainalm.lib.calmnet.SSLUtilities;
import com.captainalm.lib.calmnet.SSLUtilityException;
import com.captainalm.lib.calmnet.packet.*;
import com.captainalm.lib.calmnet.packet.core.NetworkSSLUpgradePacket;
import com.captainalm.lib.calmnet.packet.fragment.FragmentAllocationPacket;
import com.captainalm.lib.calmnet.packet.fragment.FragmentPIDPacket;
import com.captainalm.lib.calmnet.packet.fragment.FragmentSendStopPacket;
import com.captainalm.lib.calmnet.stream.NetworkInputStream;
import com.captainalm.lib.calmnet.stream.NetworkOutputStream;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.function.Function;

/**
 * This class is the network runtime class.
 *
 * @author Captain ALM
 */
public final class NetworkRuntime {
    public final MyPacketFactory factory = new MyPacketFactory(new PacketLoader());
    private NetworkInputStream inputStream;
    private NetworkOutputStream outputStream;
    private FragmentReceiver fragmentReceiver;
    private final HashMap<Integer, LocalDateTime> fragmentRMM = new HashMap<>();
    private FragmentSender fragmentSender;
    private final HashMap<Integer, LocalDateTime> fragmentSMM = new HashMap<>();
    private boolean processing = true;

    private InetAddress targetAddress;
    private int targetPort = -1;

    private final Queue<IPacket> packetQueue = new LinkedList<>();
    private Function<IPacket, Boolean> packetReceiveCallback;
    private final Object slock = new Object();
    private final Object slocksend = new Object();
    private final Object slockfmon = new Object();
    private final Object slockupg = new Object();

    /**
     * Constructs a new NetworkRuntime with the specified parameters.
     *
     * @param socketIn The socket to use.
     * @param useFragmentation If fragmentation should be used.
     * @param verifyFragmentPayloads If a fragment payload should be verified.
     */
    public NetworkRuntime(Socket socketIn, boolean useFragmentation, boolean verifyFragmentPayloads) {
        inputStream = new NetworkInputStream(socketIn);
        outputStream = new NetworkOutputStream(socketIn);
        init(useFragmentation, verifyFragmentPayloads);
    }

    /**
     * Constructs a new NetworkRuntime with the specified parameters.
     *
     * @param socketIn The datagram socket to use.
     * @param useFragmentation If fragmentation should be used.
     * @param verifyFragmentPayloads If a fragment payload should be verified.
     * @param address The target address (Can be null).
     * @param port The target port.
     */
    public NetworkRuntime(DatagramSocket socketIn, boolean useFragmentation, boolean verifyFragmentPayloads, InetAddress address, int port) {
        inputStream = new NetworkInputStream(socketIn);
        outputStream = new NetworkOutputStream(socketIn);
        targetAddress = address;
        targetPort = port;
        if (address != null && port >= 0) {
            try {
                outputStream.setDatagramTarget(address, port);
            } catch (IOException e) {
            }
        }
        try {
            outputStream.setDatagramBufferSize(65535);
        } catch (IOException e) {
        }
        init(useFragmentation, verifyFragmentPayloads);
    }

    private void init(boolean useFragmentation, boolean verifyFragmentPayloads) {
        fragmentReceiver = (useFragmentation) ? new FragmentReceiver(factory.getPacketLoader(), factory) : null;
        if (fragmentReceiver != null) fragmentReceiver.setResponseVerification(verifyFragmentPayloads);
        fragmentSender = (useFragmentation) ? new FragmentSender(factory.getPacketLoader()) : null;
        if (fragmentSender != null) fragmentSender.setResponseVerification(verifyFragmentPayloads);
        receiveThread.start();
        if (useFragmentation) {
            fragmentMonitorThread.start();
            fragmentFinishRecvMonitorThread.start();
            fragmentFinishSendMonitorThread.start();
            fragmentReceiveThread.start();
            fragmentSendThread.start();
        }
    }

    private final Thread receiveThread = new Thread(() -> {
        while (processing) {
            try {
                IPacket packet = factory.getPacketLoader().readStreamedPacket(inputStream, factory, null);
                if (packet == null) continue;
                if (inputStream.getDatagramSocket() != null && (targetAddress == null || targetPort < 0)) {
                    targetAddress = inputStream.getAddress();
                    targetPort = inputStream.getPort();
                    if (targetAddress != null && targetPort >= 0) {
                        try {
                            outputStream.setDatagramTarget(targetAddress, targetPort);
                        } catch (IOException e) {
                        }
                    }
                }
                if (fragmentReceiver != null) {
                    updateMState(fragmentRMM, packet);
                    fragmentReceiver.receivePacket(packet);
                }
                if (fragmentSender != null) {
                    updateMState(fragmentSMM, packet);
                    fragmentSender.receivePacket(packet);
                }
                synchronized (slock) {
                    if (packetReceiveCallback == null || packetReceiveCallback.apply(packet)) packetQueue.add(packet);
                }
                if (packet.isValid() && packet instanceof NetworkSSLUpgradePacket) {
                    synchronized (slockupg) {
                        int timeout = 4;
                        while (!isSSLUpgraded() && inputStream.getDatagramSocket() == null && timeout-- > 0) slockupg.wait();
                    }
                }
                synchronized (slocksend) {
                    slocksend.notifyAll();
                }
            } catch (PacketException | IOException e) {
                e.printStackTrace();
                stopProcessing();
            } catch (InterruptedException e) {
            }
        }
    }, "recv_thread");

    private final Thread fragmentReceiveThread = new Thread(() -> {
        while (processing) {
            try {
                IPacket packet = fragmentReceiver.receivePacket();
                if (packet == null) continue;
                synchronized (slock) {
                    if (packetReceiveCallback == null || packetReceiveCallback.apply(packet)) packetQueue.add(packet);
                }
            } catch (InterruptedException e) {
            }
        }
    }, "frag_recv_thread");

    private final Thread fragmentSendThread = new Thread(() -> {
        while (processing) {
            try {
                synchronized (slocksend) {
                    slocksend.wait();
                    IPacket[] packets = fragmentSender.sendPacket();
                    for (IPacket c : packets) if (c != null) {
                        updateMState(fragmentSMM, c);
                        factory.getPacketLoader().writePacket(outputStream, c, true);
                    }
                    packets = fragmentReceiver.sendPacket();
                    for (IPacket c : packets) if (c != null) {
                        updateMState(fragmentRMM, c);
                        factory.getPacketLoader().writePacket(outputStream, c, true);
                    }
                }
            } catch (PacketException | IOException e) {
                e.printStackTrace();
                stopProcessing();
            } catch (InterruptedException e) {
            }
        }
    }, "frag_send_thread");

    private final Thread fragmentMonitorThread = new Thread(() -> {
        while (processing) {
            int id = -1;
            synchronized (slockfmon) {
                for (int c : fragmentRMM.keySet()) {
                    if (!fragmentRMM.get(c).plusSeconds(29).isAfter(LocalDateTime.now())) {
                        fragmentRMM.remove(id);
                        fragmentReceiver.deletePacketFromRegistry(c);
                    }
                }
                for (int c : fragmentSMM.keySet()) {
                    if (!fragmentSMM.get(c).plusSeconds(29).isAfter(LocalDateTime.now())) {
                        fragmentSMM.remove(id);
                        fragmentSender.deletePacketFromRegistry(c);
                    }
                }
            }
            try {
                Thread.sleep(30000);
            } catch (InterruptedException e) {
            }
        }
    }, "frag_mntr_thread");

    private final Thread fragmentFinishRecvMonitorThread = new Thread(() -> {
        while (processing) {
            int id = -1;
            try {
                while ((id = fragmentReceiver.getLastIDFinished()) != -1) synchronized (slockfmon) {
                    fragmentRMM.remove(id);
                }
            } catch (InterruptedException e) {
            }
        }
    }, "frag_fin_recv_mntr_thread");

    private final Thread fragmentFinishSendMonitorThread = new Thread(() -> {
        while (processing) {
            int id = -1;
            try {
                while ((id = fragmentSender.getLastIDFinished()) != -1) synchronized (slockfmon) {
                    fragmentSMM.remove(id);
                }
            } catch (InterruptedException e) {
            }
        }
    }, "frag_fin_send_mntr_thread");

    private void updateMState(HashMap<Integer, LocalDateTime> mm, IPacket packet) {
        if (packet == null || !packet.isValid()) return;
        synchronized (slockfmon) {
            if (packet instanceof FragmentAllocationPacket) {
                mm.put(((FragmentAllocationPacket) packet).getPacketID(), LocalDateTime.now());
            } else if (packet instanceof FragmentPIDPacket && !(packet instanceof FragmentSendStopPacket)) {
                if (mm.containsKey(((FragmentPIDPacket) packet).getPacketID()))
                    mm.put(((FragmentPIDPacket) packet).getPacketID(), LocalDateTime.now());
            }
        }
    }

    /**
     * Gets the current remote endpoint address or null.
     *
     * @return The remote address or null.
     */
    public InetAddress getTargetAddress() {
        if (!processing) return null;
        return (targetAddress == null) ? inputStream.getAddress() : targetAddress;
    }

    /**
     * Gets the current target port or -1.
     *
     * @return The target port or -1.
     */
    public int getTargetPort() {
        if (!processing) return -1;
        return (targetPort < 0) ? inputStream.getPort() : targetPort;
    }

    /**
     * Gets the current remote endpoint address or null.
     *
     * @return The remote address or null.
     */
    public InetAddress getLocalAddress() {
        if (!processing) return null;
        return inputStream.getLocalAddress();
    }

    /**
     * Gets the current local port or -1.
     *
     * @return The local port or -1.
     */
    public int getLocalPort() {
        if (!processing) return -1;
        return inputStream.getLocalPort();
    }

    /**
     * Performs an SSL Upgrade on a TCP Connection.
     *
     * @param context The SSL Context to use.
     * @param host The host to check the remote certificate using (Set to null on server).
     * @param isClientSide If the caller is directly connected (Not using an accepted socket).
     */
    public void sslUpgrade(SSLContext context, String host, boolean isClientSide) {
        if (!processing || inputStream.getSocket() == null || inputStream.getSocket() instanceof SSLSocket) return;
        synchronized (slock) {
            synchronized (slocksend) {
                synchronized (slockupg) {
                    Socket original = inputStream.getSocket();
                    try {
                        inputStream.setSocket(SSLUtilities.upgradeClientSocketToSSL(context, inputStream.getSocket(), host, inputStream.getPort(), true, isClientSide));
                        outputStream.setSocket(inputStream.getSocket());
                    } catch (SSLUtilityException | IOException e) {
                        e.printStackTrace();
                        try {
                            inputStream.setSocket(original);
                            outputStream.setSocket(original);
                        } catch (IOException ex) {
                        }
                    }
                    slockupg.notifyAll();
                }
            }
        }
    }

    /**
     * Is the runtime SSL Upgraded.
     *
     * @return If the runtime is upgraded.
     */
    public boolean isSSLUpgraded() {
        if (!processing) return false;
        return inputStream.getSocket() instanceof SSLSocket;
    }

    /**
     * Sends a packet.
     *
     * @param packet The packet to send.
     * @param forceNormalSend Forces a normal send operation without fragmentation.
     */
    public void sendPacket(IPacket packet, boolean forceNormalSend) {
        if (packet == null || notReadyToSend()) return;
        synchronized (slocksend) {
            if (fragmentSender == null || forceNormalSend) {
                try {
                    factory.getPacketLoader().writePacket(outputStream, packet, true);
                    slocksend.notifyAll();
                } catch (IOException | PacketException e) {
                    e.printStackTrace();
                    stopProcessing();
                }
            } else {
                fragmentSender.sendPacket(packet);
                slocksend.notifyAll();
            }
        }
    }

    /**
     * Receives the last packet.
     *
     * @return Receives the last packet.
     */
    public IPacket receiveLastPacket() {
        if (!processing) return null;
        synchronized (slock) {
            return packetQueue.poll();
        }
    }

    /**
     * Gets the number of queued received packets.
     *
     * @return The number of queued received packets.
     */
    public int numberOfQueuedReceivedPackets() {
        synchronized (slock) {
            return packetQueue.size();
        }
    }

    /**
     * Gets the packet receive callback.
     * The return value of the passed function is whether to add the packet to the receive queue.
     *
     * @return The packet receive callback.
     */
    public Function<IPacket, Boolean> getPacketReceiveCallback() {
        return packetReceiveCallback;
    }

    /**
     * Sets the packet receive callback.
     * The return value of the passed function is whether to add the packet to the receive queue.
     *
     * @param callback The new packet receive callback.
     */
    public void setPacketReceiveCallback(Function<IPacket, Boolean> callback) {
        synchronized (slock) {
            packetReceiveCallback = callback;
        }
    }

    /**
     * Is the runtime not ready to send.
     *
     * @return Cannot send.
     */
    public boolean notReadyToSend() {
        return !processing || ((outputStream.getSocket() == null) && (outputStream.getDatagramSocket() == null || targetAddress == null || targetPort < 0));
    }

    /**
     * Gets if the runtime is processing.
     *
     * @return Is the runtime processing.
     */
    public boolean isProcessing() {
        return processing;
    }

    /**
     * Stops processing.
     */
    public void stopProcessing() {
        if (processing) {
            processing = false;
            if (Thread.currentThread() != fragmentSendThread && fragmentSendThread.isAlive()) fragmentSendThread.interrupt();
            if (Thread.currentThread() != fragmentReceiveThread && fragmentReceiveThread.isAlive()) fragmentReceiveThread.interrupt();
            if (Thread.currentThread() != fragmentMonitorThread && fragmentMonitorThread.isAlive()) fragmentMonitorThread.interrupt();
            if (Thread.currentThread() != fragmentFinishRecvMonitorThread && fragmentFinishRecvMonitorThread.isAlive()) fragmentFinishRecvMonitorThread.interrupt();
            if (Thread.currentThread() != fragmentFinishSendMonitorThread && fragmentFinishSendMonitorThread.isAlive()) fragmentFinishSendMonitorThread.interrupt();
            try {
                inputStream.close();
            } catch (IOException e) {
            }
            inputStream = null;
            try {
                outputStream.close();
            } catch (IOException e) {
            }
            outputStream = null;
            if (fragmentSender != null) {
                fragmentSender.clearLastIDFinished();
                fragmentSender.clearRegistry();
                fragmentSender.clearWaitingPackets();
                fragmentSender = null;
                fragmentSMM.clear();
            }
            if (fragmentReceiver != null) {
                fragmentReceiver.clearLastIDFinished();
                fragmentReceiver.clearRegistry();
                fragmentReceiver.clearWaitingPackets();
                fragmentReceiver = null;
                fragmentRMM.clear();
            }
        }
    }
}
