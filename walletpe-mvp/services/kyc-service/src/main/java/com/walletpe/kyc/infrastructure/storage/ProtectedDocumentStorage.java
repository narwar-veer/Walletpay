package com.walletpe.kyc.infrastructure.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Stores encrypted KYC documents with server-side content verification. */
@Service
public class ProtectedDocumentStorage {

    private static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024;
    private static final int GCM_IV_BYTES = 12;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "application/pdf");
    private final Path rootStorageDir;
    private final SecretKey encryptionKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public ProtectedDocumentStorage(@Value("${storage.upload-dir}") String uploadDir,
                                    @Value("${storage.encryption-key}") String encryptionKeyMaterial) {
        if (encryptionKeyMaterial == null || encryptionKeyMaterial.trim().length() < 32) {
            throw new IllegalStateException("storage.encryption-key must contain at least 32 characters");
        }
        this.rootStorageDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.encryptionKey = new SecretKeySpec(sha256(encryptionKeyMaterial), "AES");
        try { Files.createDirectories(this.rootStorageDir); }
        catch (IOException e) { throw new IllegalStateException("Failed to initialize KYC storage directory", e); }
    }

    public record StoredDocument(String storagePath, String contentType, long fileSize) {}

    /** Validates content before any sensitive file is persisted. */
    public void validate(byte[] content, String contentType) {
        String normalizedType = normalizeContentType(contentType);
        if (content == null || content.length == 0) throw new IllegalArgumentException("Document content cannot be empty");
        if (content.length > MAX_FILE_SIZE_BYTES) throw new IllegalArgumentException("File size exceeds 5MB limit");
        if (!hasExpectedSignature(content, normalizedType)) {
            throw new IllegalArgumentException("Document content does not match its declared type");
        }
    }

    public StoredDocument store(byte[] content, String contentType) {
        String normalizedType = normalizeContentType(contentType);
        validate(content, normalizedType);
        String filename = UUID.randomUUID() + ".enc";
        try {
            Files.write(resolveSafe(filename), encrypt(content));
            return new StoredDocument(filename, normalizedType, content.length);
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Failed to persist protected KYC document", e);
        }
    }

    public StoredDocument store(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Uploaded file cannot be empty");
        try { return store(file.getBytes(), file.getContentType()); }
        catch (IOException e) { throw new IllegalArgumentException("Unable to read uploaded document", e); }
    }

    public byte[] load(String storagePath) {
        try { return decrypt(Files.readAllBytes(resolveExistingSafe(storagePath))); }
        catch (IOException | GeneralSecurityException e) { throw new IllegalStateException("Failed to read protected KYC document", e); }
    }

    public void delete(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) return;
        try { Files.deleteIfExists(resolveSafe(storagePath)); }
        catch (IOException ignored) { /* cleanup is retried by operations tooling */ }
    }

    private Path resolveSafe(String storagePath) {
        Path filePath = rootStorageDir.resolve(storagePath).normalize();
        if (!filePath.startsWith(rootStorageDir)) throw new SecurityException("Invalid document path");
        return filePath;
    }

    private Path resolveExistingSafe(String storagePath) {
        Path filePath = resolveSafe(storagePath);
        if (!Files.exists(filePath)) throw new IllegalArgumentException("Document not found");
        return filePath;
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Unsupported document type. Allowed types: JPEG, PNG, PDF");
        }
        return contentType.toLowerCase(Locale.ROOT);
    }

    private boolean hasExpectedSignature(byte[] content, String contentType) {
        if ("application/pdf".equals(contentType)) return startsWith(content, new byte[]{'%', 'P', 'D', 'F', '-'});
        if ("image/png".equals(contentType)) return startsWith(content, new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        return startsWith(content, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
    }

    private boolean startsWith(byte[] content, byte[] prefix) {
        return content.length >= prefix.length && Arrays.equals(Arrays.copyOf(content, prefix.length), prefix);
    }

    private byte[] encrypt(byte[] content) throws GeneralSecurityException {
        byte[] iv = new byte[GCM_IV_BYTES]; secureRandom.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(128, iv));
        byte[] encrypted = cipher.doFinal(content);
        byte[] result = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, result, 0, iv.length); System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
        return result;
    }

    private byte[] decrypt(byte[] encrypted) throws GeneralSecurityException {
        if (encrypted.length <= GCM_IV_BYTES) throw new IllegalArgumentException("Invalid encrypted document");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(128, encrypted, 0, GCM_IV_BYTES));
        return cipher.doFinal(encrypted, GCM_IV_BYTES, encrypted.length - GCM_IV_BYTES);
    }

    private byte[] sha256(String value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
        catch (GeneralSecurityException e) { throw new IllegalStateException("SHA-256 is unavailable", e); }
    }
}
