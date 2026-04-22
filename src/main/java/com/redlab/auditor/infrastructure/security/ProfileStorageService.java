package com.redlab.auditor.infrastructure.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redlab.auditor.domain.model.Profile;
import com.redlab.auditor.infrastructure.util.StorageUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class ProfileStorageService {

    private static final String FILE_NAME = "profiles.dat";

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private static final int IV_SIZE = 12;
    private static final int TAG_LENGTH = 128;

    @ConfigProperty(name = "app.secret")
    private String secret;

    @Inject
    ObjectMapper mapper;

    private Path getStoragePath() {
        return StorageUtils.getProfilesPath().resolve(FILE_NAME);
    }

    private SecretKeySpec getAppKey() throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] key = sha.digest(secret.getBytes());
        System.out.println("[DEBUG] Secret used: '" + secret + "'");
        System.out.println("[DEBUG] Key hash (first 4 bytes): " + key[0] + "," + key[1] + "," + key[2] + "," + key[3]);
        return new SecretKeySpec(key, 0, 16, ALGORITHM);
    }

    public void saveProfiles(Map<String, Profile> profiles) {
        try {
            byte[] plaintext = mapper.writeValueAsBytes(profiles);
            System.out.println("[DEBUG] saveProfiles - plaintext size: " + plaintext.length + " bytes");

            SecretKeySpec key = getAppKey();
            byte[] iv = new byte[IV_SIZE];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);
            System.out.println("[DEBUG] saveProfiles - ciphertext size: " + ciphertext.length + " bytes");

            Path storagePath = getStoragePath();
            System.out.println("[DEBUG] saveProfiles - writing to: " + storagePath.toAbsolutePath());

            try (FileOutputStream fos = new FileOutputStream(storagePath.toFile());
                 DataOutputStream dos = new DataOutputStream(fos)) {
                dos.writeInt(iv.length);
                dos.write(iv);
                dos.writeInt(ciphertext.length);
                dos.write(ciphertext);
            }

            System.out.println("[DEBUG] saveProfiles - file size after write: " + Files.size(storagePath) + " bytes");

        } catch (Exception e) {
            throw new RuntimeException("Error saving profiles", e);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Profile> loadProfiles() {
        Path path = getStoragePath();
        System.out.println("[DEBUG] loadProfiles - looking for file: " + path.toAbsolutePath());

        if (!Files.exists(path)) {
            System.out.println("[DEBUG] loadProfiles - file not found, returning empty map");
            return new HashMap<>();
        }

        try {
            System.out.println("[DEBUG] loadProfiles - file size: " + Files.size(path) + " bytes");

            SecretKeySpec key = getAppKey();

            byte[] iv;
            byte[] ciphertext;

            try (FileInputStream fis = new FileInputStream(path.toFile());
                 DataInputStream dis = new DataInputStream(fis)) {
                int ivLength = dis.readInt();
                System.out.println("[DEBUG] loadProfiles - IV length read: " + ivLength);
                iv = new byte[ivLength];
                dis.readFully(iv);

                int ciphertextLength = dis.readInt();
                System.out.println("[DEBUG] loadProfiles - ciphertext length read: " + ciphertextLength);
                ciphertext = new byte[ciphertextLength];
                dis.readFully(ciphertext);
            }

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);
            System.out.println("[DEBUG] loadProfiles - decryption successful, plaintext size: " + plaintext.length + " bytes");

            Map<String, Profile> profiles = mapper.readValue(plaintext, new TypeReference<Map<String, Profile>>() {});
            System.out.println("[DEBUG] loadProfiles - profiles loaded: " + profiles.keySet());
            return profiles;

        } catch (Exception e) {
            System.err.println("[WARN] Could not load profiles. File may be corrupted or incompatible.");
            System.err.println("[DEBUG] loadProfiles - exception: " + e.getClass().getName() + ": " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("[DEBUG] loadProfiles - cause: " + e.getCause().getClass().getName() + ": " + e.getCause().getMessage());
            }
            return new HashMap<>();
        }
    }
}