package org.example.utility.cryptography;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.spec.MGF1ParameterSpec;
import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

public class RsaUtil {
    private static final String ALGORITHM = "RSA";
    private static final String CIPHER_TRANSFORMATION = "RSA/ECB/OAEPPadding";

    public static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance(ALGORITHM);
        keyGen.initialize(2048);
        return keyGen.generateKeyPair();
    }

    public static byte[] decrypt(byte[] encryptedData, PrivateKey privateKey) throws Exception {
        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);

        OAEPParameterSpec oaepParams = new OAEPParameterSpec(
                "SHA-512",
                "MGF1",
                new MGF1ParameterSpec("SHA-512"),
                PSource.PSpecified.DEFAULT
        );

        cipher.init(Cipher.DECRYPT_MODE, privateKey, oaepParams);
        return cipher.doFinal(encryptedData);
    }
}
