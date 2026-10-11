package com.rtms.backend.common.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Local-disk backend. The Cloudinary backend needs real credentials and is checked manually. */
class FileStorageServiceTest {

    @TempDir
    Path uploadDir;

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3};

    private FileStorageService storage;

    @BeforeEach
    void setUp() {
        storage = new FileStorageService(uploadDir.toString(), "");
    }

    @Test
    void storesLoadsAndDeletesALocalFile() throws Exception {
        var file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", JPEG);

        String key = storage.store(file, "rtms/admissions/1");

        assertTrue(key.matches("local:[a-f0-9\\-]{36}\\.jpg"));
        assertTrue(storage.isLocal(key));
        assertArrayEquals(JPEG, storage.load(key).getContentAsByteArray());
        assertEquals(MediaType.IMAGE_JPEG, storage.mediaType(key));

        storage.delete(key);

        try (var remaining = Files.list(uploadDir)) {
            assertEquals(0, remaining.count());
        }
        assertThrows(ResponseStatusException.class, () -> storage.load(key));
    }

    @Test
    void rejectsUnsupportedTypesAndEmptyFiles() {
        var text = new MockMultipartFile("file", "notes.txt", "text/plain", new byte[]{1});
        var empty = new MockMultipartFile("file", "photo.png", "image/png", new byte[0]);

        assertThrows(ResponseStatusException.class, () -> storage.store(text, "rtms/misc"));
        assertThrows(ResponseStatusException.class, () -> storage.store(empty, "rtms/misc"));
    }

    @Test
    void rejectsContentThatDoesNotMatchTheDeclaredType() {
        var executable = new MockMultipartFile("file", "photo.png", "image/png", "MZ not an image".getBytes());
        var textAsPdf = new MockMultipartFile("file", "doc.pdf", "application/pdf", "hello".getBytes());
        var jpegAsPng = new MockMultipartFile("file", "photo.png", "image/png", JPEG);

        assertThrows(ResponseStatusException.class, () -> storage.store(executable, "rtms/misc"));
        assertThrows(ResponseStatusException.class, () -> storage.store(textAsPdf, "rtms/misc"));
        assertThrows(ResponseStatusException.class, () -> storage.store(jpegAsPng, "rtms/misc"));
    }

    @Test
    void acceptsEverySupportedFormat() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0};
        byte[] webp = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '};

        assertTrue(storage.store(new MockMultipartFile("f", "a.png", "image/png", png), null).endsWith(".png"));
        assertTrue(storage.store(new MockMultipartFile("f", "a.webp", "image/webp", webp), null).endsWith(".webp"));
        assertTrue(storage.store(new MockMultipartFile("f", "a.pdf", "application/pdf", "%PDF-1.4".getBytes()), null).endsWith(".pdf"));
        assertEquals(MediaType.APPLICATION_PDF, storage.mediaType("local:x.pdf"));
    }

    @Test
    void deleteIgnoresMissingMalformedAndExternalKeys() {
        assertDoesNotThrow(() -> storage.delete(null));
        assertDoesNotThrow(() -> storage.delete("local:not-a-valid-key"));
        assertDoesNotThrow(() -> storage.delete("https://res.cloudinary.com/demo/image/upload/v1/a/b.png"));
    }

    @Test
    void refusesToLoadAPrivateCloudFileWhenCloudStorageIsNotConfigured() {
        assertThrows(ResponseStatusException.class,
                () -> storage.load("https://res.cloudinary.com/demo/image/authenticated/v1/a/b.png"));
        assertThrows(ResponseStatusException.class, () -> storage.load("/test-data/registration.pdf"));
    }
}
