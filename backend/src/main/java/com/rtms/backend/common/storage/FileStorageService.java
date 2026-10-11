package com.rtms.backend.common.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The one place uploaded files are written, read and removed, for every feature
 * (admission documents, incident images, and anything added later).
 *
 * Two backends, chosen by configuration:
 * - local disk (default): the stored key is "local:<uuid>.<ext>" and the file is
 *   served by the owning feature's own endpoint;
 * - Cloudinary (when rtms.storage.cloudinary-url is set): the stored key is the
 *   public https URL and clients load it directly.
 *
 * Callers persist the returned key as-is and never need to know which backend is active.
 */
@Component
public class FileStorageService {
    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);
    private static final String LOCAL_PREFIX = "local:";
    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final Map<String, String> EXTENSIONS = Map.of(
            "application/pdf", ".pdf",
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp");
    /** https://res.cloudinary.com/<cloud>/image/upload/v<version>/<public_id>.<ext> */
    private static final Pattern CLOUDINARY_FILE_URL = Pattern.compile(
            "^https://res\\.cloudinary\\.com/[^/]+/image/upload/(?:v\\d+/)?(.+)\\.[a-z0-9]+$");

    private final Path uploadRoot;
    /** Null when no Cloudinary URL is configured: files then stay on local disk. */
    private final Cloudinary cloudinary;

    public FileStorageService(
            @Value("${rtms.storage.upload-dir:./uploads/admissions}") String directory,
            @Value("${rtms.storage.cloudinary-url:}") String cloudinaryUrl) {
        this.uploadRoot = Path.of(directory).toAbsolutePath().normalize();
        this.cloudinary = cloudinaryUrl == null || cloudinaryUrl.isBlank() ? null : new Cloudinary(cloudinaryUrl);
        if (cloudinary == null) {
            log.info("File storage: local disk at {}", uploadRoot);
        } else {
            log.info("File storage: Cloudinary (cloud name: {})", cloudinary.config.cloudName);
        }
    }

    /** Rejects anything that is not a PDF, JPEG, PNG or WebP of at most 10 MB. */
    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document must be between 1 byte and 10 MB");
        }

        String mediaType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);

        if (!EXTENSIONS.containsKey(mediaType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PDF, JPEG, PNG and WebP are accepted");
        }
    }

    /**
     * Validates and stores a file.
     *
     * @param folder groups files of one record, e.g. "rtms/admissions/12". Used by Cloudinary; ignored on local disk.
     * @return the key to persist: "local:<uuid>.<ext>" or the Cloudinary https URL.
     */
    public String store(MultipartFile file, String folder) {
        validate(file);
        return cloudinary == null ? storeLocally(file) : storeOnCloudinary(file, folder);
    }

    /** Removes a stored file from whichever backend holds it. Never throws: a failed clean-up must not fail the request. */
    public void delete(String key) {
        if (key == null) return;
        if (isLocal(key)) {
            try {
                Files.deleteIfExists(resolve(key));
            } catch (IOException | RuntimeException ignored) {
            }
            return;
        }
        Matcher matcher = CLOUDINARY_FILE_URL.matcher(key);
        if (cloudinary == null || !matcher.matches()) return;
        try {
            cloudinary.uploader().destroy(matcher.group(1), ObjectUtils.asMap("invalidate", true));
        } catch (IOException | RuntimeException ignored) {
        }
    }

    /** Opens a file kept on local disk. Externally hosted files are loaded by the client from their URL instead. */
    public Resource load(String key) {
        if (!isLocal(key)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "This document is hosted externally");
        }
        Path path = resolve(key);
        if (!Files.isRegularFile(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document file not found");
        }
        return new FileSystemResource(path);
    }

    /** True when the key points at local disk, i.e. the file must be served through an API endpoint. */
    public boolean isLocal(String key) {
        return key != null && key.startsWith(LOCAL_PREFIX);
    }

    /**
     * The URL a client should use for a stored file.
     *
     * @param localEndpoint the feature's own download endpoint, used when the file is on local disk.
     */
    public String publicUrl(String key, String localEndpoint) {
        return isLocal(key) ? localEndpoint : key;
    }

    private String storeLocally(MultipartFile file) {
        String extension = EXTENSIONS.get(file.getContentType().toLowerCase(Locale.ROOT));
        String storageKey = UUID.randomUUID() + extension;
        String key = LOCAL_PREFIX + storageKey;
        try {
            Files.createDirectories(uploadRoot);
            Path destination = uploadRoot.resolve(storageKey);
            try (var stream = file.getInputStream()) {
                Files.copy(stream, destination);
            }
            return key;
        } catch (IOException ex) {
            delete(key);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to store the uploaded file", ex);
        } catch (RuntimeException ex) {
            delete(key);
            throw ex;
        }
    }

    private String storeOnCloudinary(MultipartFile file, String folder) {
        String cleanFolder = folder == null || folder.isBlank() ? "rtms/misc" : folder.replaceAll("^/+|/+$", "");
        try {
            Map<?, ?> result = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    // The folder is part of the public id (fixed-folder accounts) and also set as the
                    // asset folder (dynamic-folder accounts), so the Media Library shows it either way.
                    "public_id", cleanFolder + "/" + UUID.randomUUID(),
                    "asset_folder", cleanFolder,
                    // PDFs are delivered through the "image" type too, so every stored URL has one shape.
                    "resource_type", "image"));
            return (String) result.get("secure_url");
        } catch (IOException | RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Unable to upload the file to cloud storage. Please try again.", ex);
        }
    }

    private Path resolve(String key) {
        String filename = key.substring(LOCAL_PREFIX.length());
        if (!filename.matches("[a-f0-9\\-]{36}\\.(pdf|jpg|png|webp)")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid document key");
        }
        Path result = uploadRoot.resolve(filename).normalize();
        if (!result.startsWith(uploadRoot)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid document path");
        }
        return result;
    }
}
