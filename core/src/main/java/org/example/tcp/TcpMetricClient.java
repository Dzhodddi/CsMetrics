package org.example.tcp;

import org.example.dtos.MetricDto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.example.network.PacketCodec;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.List;


public class TcpMetricClient implements AutoCloseable {

    private final String host;
    private final int port;

    private Socket socket;
    private OutputStream os;
    private final ObjectMapper mapper;

    public TcpMetricClient(String host, int port) {
        this.host = host;
        this.port = port;
        this.mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    public synchronized void send(List<MetricDto> dtos) {
        try {
            ensureConnected();
            byte[] payload = mapper.writeValueAsBytes(dtos);
            byte[] packet = PacketCodec.serialize(payload);
            os.write(packet);
            os.flush();
        } catch (IOException e) {
            System.err.println("[TcpMetricClient] Send failed, dropping metric: " + e.getMessage());
            resetConnection();
        }
    }

    private void ensureConnected() throws IOException {
        if (socket == null || socket.isClosed() || !socket.isConnected()) {
            resetConnection();
            socket = new Socket(host, port);
            os = socket.getOutputStream();
            System.out.println("TCP client connected to " + host + ":" + port);
        }
    }

    private void resetConnection() {
        try {
            if (os != null) os.close();
        } catch (IOException ignored) {}
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {}
        socket = null;
        os = null;
    }

    @Override
    public synchronized void close() {
        resetConnection();
    }
}
