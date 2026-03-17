package com.redlab.auditor.infrastructure.security;

import com.redlab.auditor.domain.model.Profile;
import com.redlab.auditor.infrastructure.util.StorageUtils;
import jakarta.enterprise.context.ApplicationScoped;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.file.*;
import java.util.*;

@ApplicationScoped
public class ProfileStorageService {

    private static final String FILE_NAME = "profiles.dat";
    private static final String ALGORITHM = "AES";
    private static final byte[] KEY = "RedLabSecretKey!".getBytes();

    private Path getStoragePath() {
        return StorageUtils.getProfilesPath().resolve(FILE_NAME);
    }

    public void saveProfiles(Map<String, Profile> profiles) {
        Path path = getStoragePath();
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(KEY, ALGORITHM));

            try (FileOutputStream fos = new FileOutputStream(path.toFile());
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
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(KEY, ALGORITHM));

            try (FileInputStream fis = new FileInputStream(path.toFile());
                 CipherInputStream cis = new CipherInputStream(fis, cipher);
                 ObjectInputStream ois = new ObjectInputStream(cis)) {

                return (Map<String, Profile>) ois.readObject();
            }
        } catch (Exception e) {
            System.err.println("[WARN] Could not load profiles. The file might be corrupted or the key changed.");
            return new HashMap<>();
        }
    }
}