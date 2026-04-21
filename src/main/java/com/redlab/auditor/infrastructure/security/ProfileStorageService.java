package com.redlab.auditor.infrastructure.security;

import com.redlab.auditor.domain.model.Profile;
import com.redlab.auditor.infrastructure.util.StorageUtils;
import jakarta.enterprise.context.ApplicationScoped;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
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

    private static final byte[] SALT = "redlab-salt-v1".getBytes();

    private Path getStoragePath() {
        return StorageUtils.getProfilesPath().resolve(FILE_NAME);
    }

    private SecretKey getMachineKey() throws Exception {
        String fingerprint = buildMachineFingerprint();

        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");

        PBEKeySpec spec = new PBEKeySpec(
                fingerprint.toCharArray(),
                SALT,
                65536,
                128
        );

        byte[] keyBytes = factory.generateSecret(spec).getEncoded();

        return new SecretKeySpec(keyBytes, ALGORITHM);
    }

    private String buildMachineFingerprint() {
        try {
            String os = System.getProperty("os.name", "");
            String arch = System.getProperty("os.arch", "");
            String home = System.getProperty("user.home", "");
            String user = System.getProperty("user.name", "");

            String host = InetAddress.getLocalHost().getHostName();

            return (os + arch + home + user + host).toLowerCase();

        } catch (Exception e) {
            throw new RuntimeException("Failed to build machine fingerprint", e);
        }
    }

    public void saveProfiles(Map<String, Profile> profiles) {
        try {
            SecretKey key = getMachineKey();

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);

            byte[] iv = new byte[IV_SIZE];
            new SecureRandom().nextBytes(iv);

            GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);

            try (FileOutputStream fos = new FileOutputStream(getStoragePath().toFile());
                 DataOutputStream dos = new DataOutputStream(fos);
                 CipherOutputStream cos = new CipherOutputStream(dos, cipher);
                 ObjectOutputStream oos = new ObjectOutputStream(cos)) {

                dos.writeInt(iv.length);
                dos.write(iv);

                oos.writeObject(profiles);
            }

        } catch (Exception e) {
            throw new RuntimeException("Error saving profiles", e);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Profile> loadProfiles() {
        Path path = getStoragePath();
        if (!Files.exists(path)) return new HashMap<>();

        try {
            SecretKey key = getMachineKey();

            try (FileInputStream fis = new FileInputStream(path.toFile());
                 DataInputStream dis = new DataInputStream(fis)) {

                int ivLength = dis.readInt();
                byte[] iv = new byte[ivLength];
                dis.readFully(iv);

                Cipher cipher = Cipher.getInstance(TRANSFORMATION);
                cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH, iv));

                try (CipherInputStream cis = new CipherInputStream(dis, cipher);
                     ObjectInputStream ois = new ObjectInputStream(cis)) {

                    return (Map<String, Profile>) ois.readObject();
                }
            }

        } catch (Exception e) {
            System.err.println("[WARN] Could not load profiles. Possibly different machine/environment.");
            return new HashMap<>();
        }
    }
}