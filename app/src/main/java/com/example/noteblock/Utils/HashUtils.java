package com.example.noteblock.Utils;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;


import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class HashUtils {

    private static final String AES_MODE = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128; // bits
    private static final int IV_LENGTH = 12; // bytes

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
    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    // --- Encrypt AES-GCM ---
    public static String encrypt(String plainText, byte[] key) throws Exception {
        byte[] iv = new byte[IV_LENGTH];
        SecureRandom random = new SecureRandom();
        random.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(AES_MODE);
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

        cipher.init(Cipher.ENCRYPT_MODE, keySpec, spec);
        byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

        // Concatène IV + encrypted (IV nécessaire pour déchiffrement)
        byte[] encryptedIvAndText = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, encryptedIvAndText, 0, iv.length);
        System.arraycopy(encrypted, 0, encryptedIvAndText, iv.length, encrypted.length);

        return Base64.encodeToString(encryptedIvAndText, Base64.NO_WRAP);
    }

    // --- Decrypt AES-GCM ---
    public static String decrypt(String encryptedBase64, byte[] key) throws Exception {
        byte[] encryptedIvTextBytes = Base64.decode(encryptedBase64, Base64.NO_WRAP);

        // Extraire IV
        byte[] iv = new byte[IV_LENGTH];
        System.arraycopy(encryptedIvTextBytes, 0, iv, 0, IV_LENGTH);

        // Extraire ciphertext
        int encryptedSize = encryptedIvTextBytes.length - IV_LENGTH;
        byte[] encryptedBytes = new byte[encryptedSize];
        System.arraycopy(encryptedIvTextBytes, IV_LENGTH, encryptedBytes, 0, encryptedSize);

        Cipher cipher = Cipher.getInstance(AES_MODE);
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

        cipher.init(Cipher.DECRYPT_MODE, keySpec, spec);
        byte[] decrypted = cipher.doFinal(encryptedBytes);

        return new String(decrypted, StandardCharsets.UTF_8);
    }

    // --- Helper: convertit bytes en hex ---
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}

