package org.example.utility;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class PasswordUtil {

    // Кількість ітерацій та довжина ключа для PBKDF2
    private static final int ITERATIONS = 10000;
    private static final int KEY_LENGTH = 512; // 512 біт (64 байти) для SHA-512
    private static final String ALGORITHM = "PBKDF2WithHmacSHA512";

    /**
     * Генерує випадкову криптографічно стійку сіль (Salt) для нового користувача.
     */
    public static String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] saltBytes = new byte[16]; // 128 біт солі цілком достатньо
        random.nextBytes(saltBytes);
        return Base64.getEncoder().encodeToString(saltBytes);
    }

    /**
     * Хешує пароль разом із сіллю за допомогою алгоритму PBKDF2.
     */
    /**
     * Хешує пароль разом із сіллю за допомогою алгоритму PBKDF2.
     */
    public static String hashPassword(String password, String salt) {
        char[] passwordChars = password.toCharArray();
        byte[] saltBytes = Base64.getDecoder().decode(salt);

        PBEKeySpec spec = new PBEKeySpec(passwordChars, saltBytes, ITERATIONS, KEY_LENGTH);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            // ВИПРАВЛЕНО: замість getRemaining() використовуємо getEncoded()
            byte[] hashedBytes = factory.generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(hashedBytes);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("Помилка під час хешування пароля", e);
        } finally {
            spec.clearPassword(); // Очищаємо пароль з пам'яті
        }
    }
}
