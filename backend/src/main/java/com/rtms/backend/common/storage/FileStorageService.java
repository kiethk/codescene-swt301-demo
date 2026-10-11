package com.rtms.backend.common.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
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
 * - local disk (default): the stored key is "local:<uuid>.<ext>";
 * - Cloudinary (when rtms.storage.cloudinary-url is set): the stored key is the asset's https URL.
 *
 * Files are private on both backends. A client never loads a stored key directly: the owning
 * feature exposes its own endpoint, checks the caller's permission there, and streams {@link #load}.
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
    /**
     * https://res.cloudinary.com/<cloud>/image/<type>/[s--signature--/][v<version>/]<public_id>.<ext>
     * Type "upload" is a public asset stored before files became private; it is still readable and removable.
     */
    private static final Pattern CLOUDINARY_FILE_URL = Pattern.compile(
            "^https://res\\.cloudinary\\.com/[^/]+/image/(upload|authenticated)/(?:s--[^/]+--/)?(?:v(\\d+)/)?(.+)\\.([a-z0-9]+)$");
    private static final String PRIVATE_TYPE = "authenticated";

    private final Path uploadRoot;
    /** Null when no Cloudinary URL is configured: files then stay on local disk. */
    private final Cloudinary cloudinary;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public FileStorageService(
            @Value("${rtms.storage.upload-dir:./uploads/admissions}") String directory,
            @Value("${rtms.storage.cloudinary-url:}") String cloudinaryUrl) {
        this.uploadRoot = Path.of(directory).toAbsolutePath().normalize();
        this.cloudinary = cloudinaryUrl == null || cloudinaryUrl.isBlank() ? null : new Cloudinary(cloudinaryUrl);
        if (cloudinary == null) {
            log.info("File storage: local disk at {}", uploadRoot);
        } else {
            log.info("File storage: Cloudinary (cloud name: {})", cloudinary.config.cloudName);
            warnWhenCredentialsAreRejected();
        }
    }

    /** Rejects anything that is not a real PDF, JPEG, PNG or WebP of at most 10 MB. */
    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document must be between 1 byte and 10 MB");
        }

        String mediaType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);

        if (!EXTENSIONS.containsKey(mediaType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PDF, JPEG, PNG and WebP are accepted");
        }

        // The declared type comes from the client, so the first bytes must agree with it.
        if (!contentMatches(mediaType, leadingBytes(file))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The file content does not match its type. Upload a real PDF, JPEG, PNG or WebP file.");
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
            cloudinary.uploader().destroy(matcher.group(3),
                    ObjectUtils.asMap("type", matcher.group(1), "invalidate", true));
        } catch (IOException | RuntimeException ex) {
            log.warn("Could not remove {} from Cloudinary: {}", matcher.group(3), ex.getMessage());
        }
    }

    /**
     * Opens a stored file from whichever backend holds it. The caller must already have checked
     * that the current user may read the record this file belongs to.
     */
    public Resource load(String key) {
        if (isLocal(key)) {
            Path path = resolve(key);
            if (!Files.isRegularFile(path)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document file not found");
            }
            return new FileSystemResource(path);
        }
        Matcher matcher = key == null ? null : CLOUDINARY_FILE_URL.matcher(key);
        if (matcher == null || !matcher.matches()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document file not found");
        }
        return new ByteArrayResource(download(deliveryUrl(key, matcher)));
    }

    /** Content type of a stored file, taken from the extension its key was given at upload. */
    public MediaType mediaType(String key) {
        String lower = key == null ? "" : key.toLowerCase(Locale.ROOT);
        return EXTENSIONS.entrySet().stream()
                .filter(entry -> lower.endsWith(entry.getValue()))
                .map(entry -> MediaType.parseMediaType(entry.getKey()))
                .findFirst()
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
    }

    /** True when the key points at local disk. */
    public boolean isLocal(String key) {
        return key != null && key.startsWith(LOCAL_PREFIX);
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
                    "resource_type", "image",
                    // Private: the asset is only delivered through a URL signed with the API secret.
                    "type", PRIVATE_TYPE));
            // Stored without a signature, so the key alone opens nothing if it ever leaks.
            return "https://res.cloudinary.com/" + cloudinary.config.cloudName + "/image/" + PRIVATE_TYPE
                    + "/v" + result.get("version") + "/" + result.get("public_id") + "." + result.get("format");
        } catch (IOException | RuntimeException ex) {
            log.warn("Cloudinary upload failed: {}", ex.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Unable to upload the file to cloud storage. Please try again.", ex);
        }
    }

    /** The URL the backend itself fetches: signed for private assets, the key itself for older public ones. */
    private String deliveryUrl(String key, Matcher matcher) {
        if (!PRIVATE_TYPE.equals(matcher.group(1))) return key;
        if (cloudinary == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "This document is in cloud storage, which is not configured on this server");
        }
        var url = cloudinary.url().secure(true).resourceType("image").type(PRIVATE_TYPE)
                .format(matcher.group(4)).signed(true);
        if (matcher.group(2) != null) url = url.version(matcher.group(2));
        return url.generate(matcher.group(3));
    }

    private byte[] download(String url) {
        try {
            HttpResponse<byte[]> response = http.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 200) return response.body();
            log.warn("Cloud storage answered {} for a stored file", response.statusCode());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document file not found");
        } catch (IOException ex) {
            log.warn("Cloud storage could not be reached: {}", ex.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Unable to read the file from cloud storage. Please try again.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Unable to read the file from cloud storage. Please try again.", ex);
        }
    }

    /** A wrong key would otherwise only show up when someone uploads, so say it at start-up. Never blocks the start. */
    private void warnWhenCredentialsAreRejected() {
        try {
            cloudinary.api().ping(ObjectUtils.emptyMap());
        } catch (Exception ex) {
            log.warn("Cloudinary rejected the configured CLOUDINARY_URL ({}). Uploads will fail until it is corrected.",
                    ex.getMessage());
        }
    }

    private static byte[] leadingBytes(MultipartFile file) {
        try (InputStream stream = file.getInputStream()) {
            return stream.readNBytes(12);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded file could not be read", ex);
        }
    }

    private static boolean contentMatches(String mediaType, byte[] head) {
        return switch (mediaType) {
            case "application/pdf" -> startsWith(head, "%PDF".getBytes(StandardCharsets.US_ASCII));
            case "image/jpeg" -> startsWith(head, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
            case "image/png" -> startsWith(head, new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
            case "image/webp" -> head.length >= 12
                    && startsWith(head, "RIFF".getBytes(StandardCharsets.US_ASCII))
                    && Arrays.equals(Arrays.copyOfRange(head, 8, 12), "WEBP".getBytes(StandardCharsets.US_ASCII));
            default -> false;
        };
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        return data.length >= prefix.length && Arrays.equals(Arrays.copyOf(data, prefix.length), prefix);
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
