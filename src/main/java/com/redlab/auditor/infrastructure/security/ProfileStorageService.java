package com.redlab.auditor.infrastructure.security;

import com.redlab.auditor.domain.model.Profile;
import com.redlab.auditor.infrastructure.util.StorageUtils;
import jakarta.enterprise.context.ApplicationScoped;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.SecretKeySpec;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class ProfileStorageService {

    private static final String FILE_NAME = "profiles.dat";
    private static final String ALGORITHM = "AES";

    private Path getStoragePath() {
        return StorageUtils.getProfilesPath().resolve(FILE_NAME);
    }

    private SecretKeySpec getMachineSpecificKey() throws Exception {
        String rawKey = System.getProperty("user.name") +
                System.getProperty("os.name") +
                System.getProperty("user.home");

        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] key = sha.digest(rawKey.getBytes(StandardCharsets.UTF_8));

        return new SecretKeySpec(Arrays.copyOf(key, 16), ALGORITHM);
    }

    public void saveProfiles(Map<String, Profile> profiles) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, getMachineSpecificKey());

            try (FileOutputStream fos = new FileOutputStream(getStoragePath().toFile());
                 CipherOutputStream cos = new CipherOutputStream(fos, cipher);
                 ObjectOutputStream oos = new ObjectOutputStream(cos)) {

                oos.writeObject(profiles);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error saving profiles with encryption: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Profile> loadProfiles() {
        Path path = getStoragePath();
        if (!Files.exists(path)) return new HashMap<>();

        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, getMachineSpecificKey());

            try (FileInputStream fis = new FileInputStream(path.toFile());
                 CipherInputStream cis = new CipherInputStream(fis, cipher);
                 ObjectInputStream ois = new ObjectInputStream(cis)) {

                return (Map<String, Profile>) ois.readObject();
            }
        } catch (Exception e) {
            System.err.println("[WARN] Could not load profiles. Security key mismatch for this environment.");
            return new HashMap<>();
        }
    }
}