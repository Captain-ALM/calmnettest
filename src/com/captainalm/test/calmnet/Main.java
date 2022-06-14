package com.captainalm.test.calmnet;

import com.captainalm.lib.calmnet.SSLUtilities;
import com.captainalm.lib.calmnet.SSLUtilityException;
import com.captainalm.lib.calmnet.packet.IPacket;
import com.captainalm.lib.calmnet.packet.PacketException;
import com.captainalm.lib.calmnet.packet.core.NetworkSSLUpgradePacket;
import com.captainalm.utils.Console;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.Queue;

public final class Main {
    private static NetworkRuntime runtime;
    private static Thread recvThread;
    private static boolean akned = false;
    private final static Queue<DataPacket> packetQueue = new LinkedList<>();

    private static SSLContext sslContext;
    private static boolean isClient;
    private static String sslHost;

    private static int sendLoopsRemainingSetting;

    private static int sendLoopWaitTime;
    private static final Object slock = new Object();

    public static void main(String[] args) {
        header();
        help();
        boolean shouldExecuting = true;
        while (shouldExecuting) {
            char opt = Console.readCharacter();
            
            switch (opt) {
                case '0':
                case 'h':
                    help();
                    break;
                case '1':
                case 'i':
                    info();
                    break;
                case '2':
                case 's':
                    start();
                    break;
                case '3':
                case 'd':
                    stop();
                    break;
                case '4':
                case 'm':
                    message();
                    break;
                case '5':
                case 'f':
                    send();
                    break;
                case '6':
                case 'p':
                    process();
                    break;
                case '7':
                case 'x':
                    sslSetup();
                    break;
                case '8':
                case 'u':
                    upgrade();
                    break;
                case '9':
                case 'a':
                    header();
                    break;
                default:
                    shouldExecuting = false;
            }
        }
        System.exit(0);
    }

    private static void start() {
        if (runtime != null && runtime.isProcessing()) return;
        Console.writeLine("Socket Setup:");
        Console.writeLine("IP Address:");
        InetAddress address = null;
        try {
            address = InetAddress.getByName(Console.readString());
            Console.writeLine("Done   ! ; Setting To: " + address.getHostAddress());
        } catch (UnknownHostException e) {
            Console.writeLine("Ignored! ; Setting To: null");
        }
        Console.writeLine("Port:");
        Integer port = Console.readInt();
        if (port == null || port < 0 || port > 65535) {
            port = 0;
            Console.writeLine("Ignored! ; Setting To: 0");
        }
        Console.writeLine("Use Fragmentation (Y/OTHER):");
        char fopt = Console.readCharacter();
        
        boolean fragmentation = (fopt == 'Y' || fopt == 'y');
        boolean fverifyp = false;
        if (fragmentation) {
            Console.writeLine("Verify Fragment Payloads (Y/OTHER)");
            fopt = Console.readCharacter();
            
            fverifyp = (fopt == 'Y' || fopt == 'y');
        }
        Console.writeLine("Select Socket Mode:");
        Console.writeLine("0) TCP Listen");
        Console.writeLine("1) TCP Client");
        Console.writeLine("2) UDP Listen");
        Console.writeLine("3) UDP Client");
        Console.writeLine("4) UDP Broadcast");
        Console.writeLine("5) UDP Loopback Client");
        Console.writeLine("6) UDP Loopback Broadcast");
        Console.writeLine("7) SSL TCP Listen");
        Console.writeLine("8) SSL TCP Client");
        Console.writeLine("OTHER) Cancel");
        char opt = Console.readCharacter();
        
        Console.writeLine("Starting Socket...");
        runtime = null;
        switch (opt) {
            case '0':
                sendLoopsRemainingSetting = 1;
                sendLoopWaitTime = 50;
                try (ServerSocket serverSocket = new ServerSocket(port, 1, address)) {
                    Socket socket = serverSocket.accept();
                    runtime = new NetworkRuntime(socket, fragmentation, fverifyp);
                    isClient = false;
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            case '1':
                sendLoopsRemainingSetting = 1;
                sendLoopWaitTime = 50;
                try {
                    Socket socket = new Socket(address, port);
                    runtime = new NetworkRuntime(socket, fragmentation, fverifyp);
                    isClient = true;
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            case '2':
                requestSendSettings();
                try {
                    DatagramSocket socket = new DatagramSocket(port, address);
                    runtime = new NetworkRuntime(socket, fragmentation, fverifyp, null, -1);
                    isClient = false;
                } catch (SocketException e) {
                    e.printStackTrace();
                }
                break;
            case '3':
                requestSendSettings();
                try {
                    DatagramSocket socket = new DatagramSocket();
                    runtime = new NetworkRuntime(socket, fragmentation, fverifyp, address, port);
                    isClient = true;
                } catch (SocketException e) {
                    e.printStackTrace();
                }
                break;
            case '4':
                requestSendSettings();
                try {
                    MulticastSocket socket = new MulticastSocket(port);
                    if (!socket.getLoopbackMode()) socket.setLoopbackMode(true);
                    socket.joinGroup(address);
                    runtime = new NetworkRuntime(socket, fragmentation, fverifyp, address, port);
                    isClient = false;
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            case '5':
                requestSendSettings();
                try {
                    DatagramSocket socket = new DatagramSocket(port, address);
                    runtime = new NetworkRuntime(socket, fragmentation, fverifyp, address, port);
                    isClient = true;
                } catch (SocketException e) {
                    e.printStackTrace();
                }
                break;
            case '6':
                requestSendSettings();
                try {
                    MulticastSocket socket = new MulticastSocket(port);
                    if (socket.getLoopbackMode()) socket.setLoopbackMode(false);
                    socket.joinGroup(address);
                    runtime = new NetworkRuntime(socket, fragmentation, fverifyp, address, port);
                    isClient = false;
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            case '7':
                sendLoopsRemainingSetting = 1;
                sendLoopWaitTime = 50;
                if (sslContext == null) break;
                try (SSLServerSocket serverSocket = SSLUtilities.getSSLServerSocket(sslContext, port, 1, address)) {
                    Socket socket = serverSocket.accept();
                    runtime = new NetworkRuntime(socket, fragmentation, fverifyp);
                    isClient = false;
                } catch (IOException | SSLUtilityException e) {
                    e.printStackTrace();
                }
                break;
            case '8':
                sendLoopsRemainingSetting = 1;
                sendLoopWaitTime = 50;
                if (sslContext == null) break;
                try {
                    Socket socket = SSLUtilities.getSSLClientSocket(sslContext, sslHost, port);
                    runtime = new NetworkRuntime(socket, fragmentation, fverifyp);
                    isClient = true;
                } catch (SSLUtilityException e) {
                    e.printStackTrace();
                }
                break;
        }
        if (runtime == null) {
            Console.writeLine("!FAILED TO START!");
        } else {
            while (runtime.notReadyToSend()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                }
            }
            createAndStartRecvThread();
            Console.writeLine("Socket Started.");
        }
    }

    private static void requestSendSettings() {
        Console.writeLine("Enter number of send retries:");
        Integer num = Console.readInt();
        if (num == null || num < 0) num = 0;
        Console.writeLine("Send Retires set to: " + num);
        sendLoopsRemainingSetting = num + 1;
        Console.writeLine("Enter timeout before send retry (Milliseconds) :");
        num = Console.readInt();
        if (num == null || num < 50) num = 50;
        Console.writeLine("Retry Send Timeout set to: " + num);
        sendLoopWaitTime = num;
    }

    private static void createAndStartRecvThread() {
        recvThread = new Thread(() -> {
            PacketType nextType = null;
            Path nextPath = null;
            while (runtime != null && runtime.isProcessing()) {
                IPacket packet;
                while ((packet = runtime.receiveLastPacket()) != null) {
                    if (!packet.isValid()) continue;
                    if (packet instanceof TypePacket && nextType == null) {
                        nextType = ((TypePacket) packet).type;
                        runtime.sendPacket(new AKNPacket(), false);
                    }
                    if (packet instanceof DataPacket && nextType == PacketType.Message) {
                        nextType = null;
                        synchronized (slock) {
                            packetQueue.add((DataPacket) packet);
                        }
                        runtime.sendPacket(new AKNPacket(), false);
                    }
                    if (packet instanceof DataPacket && nextType == PacketType.Name) {
                        nextType = null;
                        try {
                            nextPath = new File(new String(packet.savePayload(), StandardCharsets.UTF_8)).toPath();
                        } catch (PacketException e) {
                            e.printStackTrace();
                        }
                        runtime.sendPacket(new AKNPacket(), false);
                    }
                    if (packet instanceof StreamedDataPacket && nextType == PacketType.Data && nextPath != null) {
                        nextType = null;
                        try (FileOutputStream outputStream = new FileOutputStream(nextPath.toFile())) {
                            ((StreamedDataPacket) packet).readData(outputStream);
                            synchronized (slock) {
                                DataPacket p = new DataPacket(("Received File: " + nextPath.toAbsolutePath()).getBytes(StandardCharsets.UTF_8));
                                packetQueue.add(p);
                            }
                        } catch (IOException | PacketException e) {
                            e.printStackTrace();
                        } finally {
                            nextPath = null;
                        }
                    }
                    if (packet instanceof NetworkSSLUpgradePacket && sslContext != null) {
                        if (((NetworkSSLUpgradePacket) packet).isAcknowledgement()) {
                            runtime.sslUpgrade(sslContext, sslHost, isClient);
                        } else {
                            runtime.sendPacket(new NetworkSSLUpgradePacket(true), true);
                            runtime.sslUpgrade(sslContext, sslHost, isClient);
                        }
                    }
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                }
            }
        }, "main_recv_thread");
        recvThread.start();
    }

    private static void stop() {
        if (runtime == null || !runtime.isProcessing()) return;
        Console.writeLine("Socket Stopping...");
        runtime.stopProcessing();
        try {
            recvThread.join();
        } catch (InterruptedException e) {
        }
        runtime = null;
        isClient = false;
        Console.writeLine("Socket Stopped.");
    }

    private static boolean waitForAKN(IPacket packet) {
        if (packet instanceof AKNPacket && packet.isValid()) {
            akned = true;
            return false;
        }
        return true;
    }

    private static void doAKNWait(IPacket packet) {
        akned = false;
        runtime.setPacketReceiveCallback(Main::waitForAKN);
        int i = 0;
        while (!akned) {
            runtime.sendPacket(packet, false);
            try {
                Thread.sleep(sendLoopWaitTime);
            } catch (InterruptedException e) {
            }
            if (++i >= sendLoopsRemainingSetting) akned = true;
        }
        runtime.setPacketReceiveCallback(null);
    }

    private static void message() {
        if (runtime == null || !runtime.isProcessing()) return;
        Console.writeLine("Message To Send:");
        String message = Console.readString();
        doAKNWait(new TypePacket(PacketType.Message));
        DataPacket packet = new DataPacket(message.getBytes(StandardCharsets.UTF_8));
        doAKNWait(packet);
        Console.writeLine("!Message Sent!");
    }

    private static void send() {
        if (runtime == null || !runtime.isProcessing()) return;
        Console.writeLine("Path of File To Send:");
        File file = new File(Console.readString());
        if (file.exists()) {
            doAKNWait(new TypePacket(PacketType.Name));
            DataPacket packet = new DataPacket(file.getName().getBytes(StandardCharsets.UTF_8));
            doAKNWait(packet);
            Console.writeLine("!Name Sent!");
            doAKNWait(new TypePacket(PacketType.Data));
            packet = new StreamedDataPacket(file.toPath(), true);
            doAKNWait(packet);
            Console.writeLine("!File Sent!");
        } else {
            Console.writeLine("!File does not Exist!");
        }
    }

    private static void process() {
        Console.writeLine("Received Messages:");
        synchronized (slock) {
            int i = 1;
            while (packetQueue.size() > 0) {
                DataPacket packet = packetQueue.poll();
                Console.writeLine("Message ("+ i++ +"):");
                try {
                    Console.writeLine(new String(packet.savePayload(), StandardCharsets.UTF_8));
                } catch (PacketException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static void sslSetup() {
        if (runtime != null && runtime.isSSLUpgraded()) return;
        Console.writeLine("SSL Setup:");
        Console.writeLine("SSL Host Name (Enter empty to set to null):");
        sslHost = Console.readString();
        if (sslHost.equals("")) sslHost = null;
        Console.writeLine("SSL Keystore Path:");
        String kpath = Console.readString();
        Console.writeLine("SSL Keystore Password:");
        String kpass = Console.readString();
        if (kpass.equals("")) kpass = "changeit";
        try {
            sslContext = SSLUtilities.getSSLContext(null, SSLUtilities.loadKeyStore(null, new File(kpath), kpass), kpass.toCharArray());
            Console.writeLine("SSL Setup Complete!");
        } catch (SSLUtilityException e) {
            e.printStackTrace();
            Console.writeLine("SSL Setup Failed!");
        }
    }

    private static void upgrade() {
        if (runtime == null || !runtime.isProcessing() || sslContext == null) return;
        Console.writeLine("Upgrading Connection to SSL...");
        runtime.sendPacket(new NetworkSSLUpgradePacket(false), true);
    }

    private static void info() {
        Console.writeLine("INFORMATION:");
        Console.writeLine("Local Socket: " + ((runtime != null && runtime.isProcessing()) ? runtime.getLocalAddress() + ":" + runtime.getLocalPort() : ":"));
        Console.writeLine("Remote Socket: " + ((runtime != null && runtime.isProcessing()) ? runtime.getTargetAddress() + ":" + runtime.getTargetPort() : ":"));
        Console.writeLine("Is Active: " + ((runtime != null && runtime.isProcessing()) ? "Yes" : "No"));
        Console.writeLine("Number Of Packets To Process: " + (((runtime == null) ? 0 : runtime.numberOfQueuedReceivedPackets()) + packetQueue.size()));
        Console.writeLine("SSL Upgrade Status: " + ((runtime != null && runtime.isSSLUpgraded()) ? "Upgraded" : "Not Upgraded"));
        Console.writeLine("SSL Host Name: " + ((sslHost == null) ? "<null>" : sslHost));
        Console.writeLine("SSL Context Status: " + ((sslContext == null) ? "Unavailable" : "Available"));
    }

    private static void header() {
        Console.writeLine("C-ALM Net Test (C) Captain ALM 2022");
        Console.writeLine("Under The BSD 3-Clause License");
    }

    private static void help() {
        Console.writeLine("HELP:");
        Console.writeLine("KEY) Action");
        Console.writeLine("0/h) This Help Message");
        Console.writeLine("1/i) Information State");
        Console.writeLine("2/s) Start Connection");
        Console.writeLine("3/d) Stop Connection");
        Console.writeLine("4/m) Send Message");
        Console.writeLine("5/f) Send File");
        Console.writeLine("6/p) Process Incoming Packets");
        Console.writeLine("7/x) SSL Settings");
        Console.writeLine("8/u) SSL Upgrade");
        Console.writeLine("9/a) Show the About Header");
        Console.writeLine("OTHER) Quit");
    }
}
