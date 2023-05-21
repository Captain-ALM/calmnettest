package com.captainalm.test.calmnet;

import com.captainalm.lib.calmnet.marshal.FragmentationOptions;
import com.captainalm.lib.calmnet.marshal.NetMarshalClient;
import com.captainalm.lib.calmnet.marshal.NetMarshalServer;
import com.captainalm.lib.calmnet.packet.PacketLoader;
import com.captainalm.lib.calmnet.ssl.*;
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
import java.util.*;

public final class Main {
    public static final MyPacketFactory factory = new MyPacketFactory(new PacketLoader());
    private static NetMarshalServer server;
    private static NetMarshalClient client;
    private static final Map<NetMarshalClient, Thread> recvThreads = Collections.synchronizedMap(new HashMap<>());

    private final static Queue<DataPacket> packetQueue = new LinkedList<>();

    private static SSLContext sslContext;
    private static boolean isClient;
    private static String sslHost;
    private static boolean sslUpgraded;

    private static final Object slockAckned = new Object();
    private static boolean ackn;
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
        if (server != null && server.isRunning()) return;
        if (client != null && client.isRunning()) return;
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
        FragmentationOptions fragOpts;
        if (fragmentation) {
            fragOpts = new FragmentationOptions();
            fragOpts.verifyFragments = fverifyp;
            fragOpts.equalityVerifyFragments = fverifyp;
        } else {
            fragOpts = null;
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
        server = null;
        client = null;
        sslUpgraded = false;
        switch (opt) {
            case '0':
                sendLoopsRemainingSetting = 1;
                sendLoopWaitTime = 50;
                try {
                    ServerSocket serverSocket = new ServerSocket(port, 1, address);
                    server = new NetMarshalServer(serverSocket, factory, factory.getPacketLoader(), fragOpts);
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
                    client = new NetMarshalClient(socket, factory, factory.getPacketLoader(), fragOpts);
                    isClient = true;
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            case '2':
                requestSendSettings();
                try {
                    DatagramSocket socket = new DatagramSocket(port, address);
                    server = new NetMarshalServer(socket, factory, factory.getPacketLoader(), fragOpts);
                    isClient = false;
                } catch (SocketException e) {
                    e.printStackTrace();
                }
                break;
            case '3':
                requestSendSettings();
                try {
                    DatagramSocket socket = new DatagramSocket();
                    client = new NetMarshalClient(socket, address, port, factory, factory.getPacketLoader(), fragOpts);
                    isClient = true;
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            case '4':
                requestSendSettings();
                try {
                    MulticastSocket socket = new MulticastSocket(port);
                    if (!socket.getLoopbackMode()) socket.setLoopbackMode(true);
                    client = new NetMarshalClient(socket, address, port, factory, factory.getPacketLoader(), fragOpts);
                    isClient = false;
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            case '5':
                requestSendSettings();
                try {
                    DatagramSocket socket = new DatagramSocket(port, address);
                    client = new NetMarshalClient(socket, address, port, factory, factory.getPacketLoader(), fragOpts);
                    isClient = true;
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            case '6':
                requestSendSettings();
                try {
                    MulticastSocket socket = new MulticastSocket(port);
                    if (socket.getLoopbackMode()) socket.setLoopbackMode(false);
                    client = new NetMarshalClient(socket, address, port, factory, factory.getPacketLoader(), fragOpts);
                    isClient = false;
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            case '7':
                sendLoopsRemainingSetting = 1;
                sendLoopWaitTime = 50;
                if (sslContext == null) break;
                try {
                    SSLServerSocket serverSocket = SSLUtilities.getSSLServerSocket(sslContext, port, 1, address);
                    server = new NetMarshalServer(serverSocket, factory, factory.getPacketLoader(), fragOpts);
                    isClient = false;
                    sslUpgraded = true;
                } catch (SSLUtilityException e) {
                    e.printStackTrace();
                }
                break;
            case '8':
                sendLoopsRemainingSetting = 1;
                sendLoopWaitTime = 50;
                if (sslContext == null) break;
                try {
                    Socket socket = SSLUtilities.getSSLClientSocket(sslContext, sslHost, port);
                    client = new NetMarshalClient(socket, factory, factory.getPacketLoader(), fragOpts);
                    isClient = true;
                    sslUpgraded = true;
                } catch (SSLUtilityException e) {
                    e.printStackTrace();
                }
                break;
        }
        if (client == null && server == null) {
            Console.writeLine("!FAILED TO START!");
        } else {
            if (server != null) {
                server.setAcceptExceptionBiConsumer(Main::errH);
                server.setReceiveExceptionBiConsumer(Main::errH);
                server.setReceiveBiConsumer(Main::sslUpgUnit);
                server.setOpenedConsumer(Main::connectH);
                server.setClosedConsumer(Main::closeH);
                server.open();
            }
            if (client != null) {
                client.setReceiveExceptionBiConsumer(Main::errH);
                client.setReceiveBiConsumer(Main::sslUpgUnit);
                client.setClosedConsumer(Main::closeH);
                client.open();
                connectH(client);
            }
            Console.writeLine("Socket Started.");
        }
    }

    private static void connectH(NetMarshalClient client) {
        Thread recvThread = new Thread(() -> {
            PacketType nextType = null;
            Path nextPath = null;
            if (sslUpgraded) {
                try {
                    client.sendPacket(new NetworkSSLUpgradePacket(false), true);
                } catch (IOException | PacketException e) {
                    e.printStackTrace();
                }
            }
            while (client.isRunning()) {
                try {
                    IPacket packet;
                    while ((packet = client.receivePacket()) != null) {
                        if (!packet.isValid()) continue;
                        if (packet instanceof AKNPacket) {
                            synchronized (slockAckned) {
                                ackn = true;
                                slockAckned.notifyAll();
                            }
                        }
                        if (packet instanceof TypePacket && nextType == null) {
                            nextType = ((TypePacket) packet).type;
                            client.sendPacket(new AKNPacket(), false);
                        }
                        if (packet instanceof DataPacket && nextType == PacketType.Message) {
                            nextType = null;
                            synchronized (slock) {
                                packetQueue.add((DataPacket) packet);
                            }
                            client.sendPacket(new AKNPacket(), false);
                        }
                        if (packet instanceof DataPacket && nextType == PacketType.Name) {
                            nextType = null;
                            try {
                                nextPath = new File(new String(packet.savePayload(), StandardCharsets.UTF_8)).toPath();
                            } catch (PacketException e) {
                                e.printStackTrace();
                            }
                            client.sendPacket(new AKNPacket(), false);
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
                    }
                } catch (InterruptedException e) {
                } catch (IOException | PacketException e) {
                    e.printStackTrace();
                }

            }
        }, "recv_thread_"+client.remoteAddress()+":"+client.remotePort());
        recvThread.start();
        recvThreads.put(client, recvThread);
    }

    private static void closeH(NetMarshalClient client) {
        Thread waitOn = recvThreads.remove(client);
        if (waitOn != null) {
            try {
                waitOn.join();
            } catch (InterruptedException e) {
            }
        }
    }

    private static void sslUpgUnit(IPacket packet, NetMarshalClient client) {
        try {
            if (packet instanceof NetworkSSLUpgradePacket && sslContext != null) {
                if (!((NetworkSSLUpgradePacket) packet).isAcknowledgement()) {
                    client.sendPacket(new NetworkSSLUpgradePacket(true), true);
                }
                if (isClient) client.sslUpgradeClientSide(sslContext, sslHost);
                else client.sslUpgradeServerSide(sslContext);
                sslUpgraded = true;
            }
        } catch (PacketException | IOException | SSLUtilityException e) {
            e.printStackTrace();
            try {
                client.close();
            } catch (IOException ex) {
                e.printStackTrace();
            }
        }
    }

    private static void errH(Exception ex, NetMarshalServer server) {
        ex.printStackTrace();
    }

    private static void errH(Exception ex, NetMarshalClient client) {
        ex.printStackTrace();
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

    private static void stop() {
        if ((server == null || !server.isRunning()) && (client == null || !client.isRunning())) return;
        Console.writeLine("Socket Stopping...");
        if (server != null) {
            try {
                server.close();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                server = null;
            }
        }
        if (client != null) {
            try {
                client.close();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                client = null;
            }
        }
        isClient = false;
        sslUpgraded = false;
        Console.writeLine("Socket Stopped.");
    }

    private static void doAKNWait(IPacket packet) {
        ackn = false;
        int i = 0;
        while (++i <= sendLoopsRemainingSetting && !ackn) {
            try {
                if (server != null) {
                    server.broadcastPacket(packet, false);
                }
                if (client != null) {
                    client.sendPacket(packet, false);
                }
            } catch (IOException | PacketException e) {
                e.printStackTrace();
            }
            try {
                synchronized (slockAckned) {
                    slockAckned.wait(sendLoopWaitTime);
                }
            } catch (InterruptedException e) {
            }
        }
    }

    private static void message() {
        if ((server == null || !server.isRunning()) && (client == null || !client.isRunning())) return;
        Console.writeLine("Message To Send:");
        String message = Console.readString();
        doAKNWait(new TypePacket(PacketType.Message));
        DataPacket packet = new DataPacket(message.getBytes(StandardCharsets.UTF_8));
        doAKNWait(packet);
        Console.writeLine("!Message Sent!");
    }

    private static void send() {
        if ((server == null || !server.isRunning()) && (client == null || !client.isRunning())) return;
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
        if (sslUpgraded) return;
        Console.writeLine("SSL Setup:");
        Console.writeLine("SSL Host Name (Enter empty to set to null):");
        sslHost = Console.readString();
        if (sslHost.equals("")) sslHost = null;
        Console.writeLine("SSL Keystore Path:");
        String kpath = Console.readString();
        if (!kpath.equals("")) {
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
        } else {
            sslContext = null;
            Console.writeLine("SSL Setup Cleared!");
        }
    }

    private static void upgrade() {
        if (sslContext == null) return;
        if (server != null) {
            Console.writeLine("Upgrading Connections to SSL...");
            try {
                server.broadcastPacket(new NetworkSSLUpgradePacket(false), true);
            } catch (IOException | PacketException e) {
                e.printStackTrace();
            }
        }
        if (client != null) {
            Console.writeLine("Upgrading Connection to SSL...");
            try {
                client.sendPacket(new NetworkSSLUpgradePacket(false), true);
            } catch (IOException | PacketException e) {
                e.printStackTrace();
            }
        }
    }

    private static void info() {
        Console.writeLine("INFORMATION:");
        if (server != null) {
            Console.writeLine("Local Socket: " + ((server.isRunning()) ? server.localAddress() + ":" + server.localPort() : ":"));
            Console.writeLine("Client Count: " + server.getConnectedClients().length);
            Console.writeLine("Is Active: Yes");
        }
        if (client != null) {
            Console.writeLine("Local Socket: " + ((client.isRunning()) ? client.localAddress() + ":" + client.localPort() : ":"));
            Console.writeLine("Remote Socket: " + ((client.isRunning()) ? client.remoteAddress() + ":" + client.remotePort() : ":"));
            Console.writeLine("Is Active: Yes");
        }
        if (client == null && server == null) Console.writeLine("Is Active: No");
        Console.writeLine("Number Of Packets To Process: " + packetQueue.size());
        Console.writeLine("SSL Upgrade Status: " + ((sslUpgraded) ? "Upgraded" : "Not Upgraded"));
        Console.writeLine("SSL Host Name: " + ((sslHost == null) ? "<null>" : sslHost));
        Console.writeLine("SSL Context Status: " + ((sslContext == null) ? "Unavailable" : "Available"));
    }

    private static void header() {
        Console.writeLine("C-ALM Net Test (C) Captain ALM 2023");
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
