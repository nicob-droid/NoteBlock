package com.example.cosmonote.Utils;


import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;


public class HashUtils {

    // --- Hash SHA-256 (retourne hex string) ---
    public static byte[] sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(data);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Convertit un byte[] en String hexadécimal
    private static String bytesToShaHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1)
                hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    // Combine les deux : hash SHA-256 + conversion en hex
    public static String sha256Hex(String input) {
        byte[] hash = sha256(input.getBytes(StandardCharsets.UTF_8));
        return bytesToShaHex(hash);
    }
}

