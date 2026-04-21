package com.redlab.auditor.infrastructure.security;

import com.redlab.auditor.domain.model.Profile;
import com.redlab.auditor.infrastructure.util.StorageUtils;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
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

    @ConfigProperty(name = "app.secret", defaultValue = "dev-env-secret")
    private String secret;

    private Path getStoragePath() {
        return StorageUtils.getProfilesPath().resolve(FILE_NAME);
    }

    private SecretKeySpec getAppKey() throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] key = sha.digest(secret.getBytes());
        return new SecretKeySpec(key, 0, 16, ALGORITHM);
    }

    public void saveProfiles(Map<String, Profile> profiles) {
        try {
            SecretKeySpec key = getAppKey();

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
            SecretKeySpec key = getAppKey();

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
            System.err.println("[WARN] Could not load profiles. File may be corrupted or incompatible.");
            return new HashMap<>();
        }
    }
}