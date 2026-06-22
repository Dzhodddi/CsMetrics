package org.example.network;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class PacketCodec {

    private static final int HEADER_SIZE = 7;

    public static BinaryPacket parse(byte[] rawData) {
        if (rawData.length < HEADER_SIZE) {
            throw new IllegalArgumentException("Packet length too short.");
        }

        ByteBuffer buffer = ByteBuffer.wrap(rawData);

        byte[] magic = new byte[2];
        buffer.get(magic);
        if (!Arrays.equals(magic, BinaryPacket.EXPECTED_MAGIC)) {
            throw new IllegalArgumentException("Packet magic incorrect.");
        }

        byte version = buffer.get();
        if (version != BinaryPacket.CURRENT_VERSION) {
            throw new IllegalArgumentException("Packet version incorrect.");
        }

        int payloadLength = buffer.getInt();

        if (buffer.remaining() < payloadLength) {
            throw new IllegalArgumentException("Payload length too short.");
        }

        byte[] encryptedPayload = new byte[payloadLength];
        buffer.get(encryptedPayload);

        return new BinaryPacket(magic, version, payloadLength, encryptedPayload);
    }

    public static byte[] serialize(byte[] encryptedPayload) {
        int totalSize = HEADER_SIZE + encryptedPayload.length;
        ByteBuffer buffer = ByteBuffer.allocate(totalSize);

        buffer.put(BinaryPacket.EXPECTED_MAGIC);
        buffer.put(BinaryPacket.CURRENT_VERSION);
        buffer.putInt(encryptedPayload.length);
        buffer.put(encryptedPayload);

        return buffer.array();
    }
}
