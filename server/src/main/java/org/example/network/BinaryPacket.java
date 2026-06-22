package org.example.network;

public record BinaryPacket(
        byte[] magicBytes,
        byte version,
        int payloadLength,
        byte[] encryptedPayload
) {
    public static final byte[] EXPECTED_MAGIC = {0x4D, 0x54};
    public static final byte CURRENT_VERSION = 1;
}
