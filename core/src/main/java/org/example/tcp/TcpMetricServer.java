package org.example.tcp;

import org.example.dtos.MetricDto;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class TcpMetricServer {

    private final int port;
    private final Consumer<List<MetricDto>> handler;
    private final ExecutorService executor = Executors.newCachedThreadPool();

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
        try (ObjectInputStream ois = new ObjectInputStream(socket.getInputStream())) {
            while (running) {
                try {
                    Object obj = ois.readObject();
                    if (obj instanceof List<?> list) {
                        @SuppressWarnings("unchecked")
                        List<MetricDto> dtos = (List<MetricDto>) list;
                        handler.accept(dtos);
                    } else {
                        System.err.println("TCP Server Unexpected object type: " + obj.getClass());
                    }
                } catch (EOFException e) {
                    break;
                } catch (ClassNotFoundException e) {
                    System.err.println("TCP Server Unknown class received: " + e.getMessage());
                }
            }
        } catch (SocketException e) {
            System.out.println("TCP Server Client disconnected: " + remote);
        } catch (IOException e) {
            System.err.println("TCP Server IO error with client " + remote + ": " + e.getMessage());
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
