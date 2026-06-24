package org.example.tcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.example.dtos.MetricDto;
import org.example.network.BinaryPacket;
import org.example.network.PacketCodec;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class TcpMetricServer {

    private final int port;
    private final Consumer<List<MetricDto>> handler;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private volatile boolean running = false;
    private ServerSocket serverSocket;

    public TcpMetricServer(int port, Consumer<List<MetricDto>> handler) {
        this.port = port;
        this.handler = handler;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        System.out.println("TCP Server listening on port " + port);

        Thread acceptThread = new Thread(() -> {
            while (running) {
                try {
                    Socket client = serverSocket.accept();
                    executor.submit(() -> handleClient(client));
                } catch (SocketException e) {
                    if (running) System.err.println("TCP Server Accept error: " + e.getMessage());
                } catch (IOException e) {
                    System.err.println("TCP Server Accept error: " + e.getMessage());
                }
            }
        }, "tcp-metric-server-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    private void handleClient(Socket socket) {
        String remote = socket.getRemoteSocketAddress().toString();
        System.out.println("TCP Server Client connected: " + remote);
        try (DataInputStream dis = new DataInputStream(socket.getInputStream())) {
            while (running) {
                try {
                    byte[] header = new byte[7];
                    dis.readFully(header);
                    
                    int payloadLength = ByteBuffer.wrap(header, 3, 4).getInt();
                    if (payloadLength < 0 || payloadLength > 10 * 1024 * 1024) {
                        System.err.println("TCP Server Invalid payload length: " + payloadLength);
                        break;
                    }
                    
                    byte[] payload = new byte[payloadLength];
                    dis.readFully(payload);
                    
                    byte[] fullPacket = new byte[7 + payloadLength];
                    System.arraycopy(header, 0, fullPacket, 0, 7);
                    System.arraycopy(payload, 0, fullPacket, 7, payloadLength);
                    
                    BinaryPacket packet = PacketCodec.parse(fullPacket);
                    List<MetricDto> dtos = mapper.readValue(packet.encryptedPayload(), new TypeReference<>() {});
                    handler.accept(dtos);
                } catch (EOFException e) {
                    break;
                } catch (Exception e) {
                    System.err.println("TCP Server error processing packet: " + e.getMessage());
                    break;
                }
            }
        } catch (SocketException e) {
            System.out.println("TCP Server Client disconnected: " + remote);
        } catch (IOException e) {
            System.err.println("TCP Server IO error with client " + remote + ": " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {}
        executor.shutdownNow();
    }
}
