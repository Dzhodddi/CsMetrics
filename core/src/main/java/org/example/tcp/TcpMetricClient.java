package org.example.tcp;

import org.example.dtos.MetricDto;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;


public class TcpMetricClient implements AutoCloseable {

    private final String host;
    private final int port;

    private Socket socket;
    private ObjectOutputStream oos;

    public TcpMetricClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public synchronized void send(MetricDto dto) {
        try {
            ensureConnected();
            oos.writeObject(dto);
            oos.flush();
        } catch (IOException e) {
            System.err.println("[TcpMetricClient] Send failed, dropping metric: " + e.getMessage());
            resetConnection();
        }
    }

    private void ensureConnected() throws IOException {
        if (socket == null || socket.isClosed() || !socket.isConnected()) {
            resetConnection();
            socket = new Socket(host, port);
            oos = new ObjectOutputStream(socket.getOutputStream());
            System.out.println("TCP client connected to " + host + ":" + port);
        }
    }

    private void resetConnection() {
        try {
            if (oos != null) oos.close();
        } catch (IOException ignored) {}
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {}
        socket = null;
        oos = null;
    }

    @Override
    public synchronized void close() {
        resetConnection();
    }
}
