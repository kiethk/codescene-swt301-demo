package com.rtms.backend.common.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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

    private FileStorageService storage;

    @BeforeEach
    void setUp() {
        storage = new FileStorageService(uploadDir.toString(), "");
    }

    @Test
    void storesLoadsAndDeletesALocalFile() throws Exception {
        var file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[]{1, 2, 3});

        String key = storage.store(file, "rtms/admissions/1");

        assertTrue(key.matches("local:[a-f0-9\\-]{36}\\.jpg"));
        assertTrue(storage.isLocal(key));
        assertArrayEquals(new byte[]{1, 2, 3}, storage.load(key).getContentAsByteArray());

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
    void publicUrlUsesTheEndpointForLocalFilesAndTheKeyForExternalOnes() {
        String cloudUrl = "https://res.cloudinary.com/demo/image/upload/v1/rtms/admissions/1/abc.pdf";

        assertEquals("/api/files/1", storage.publicUrl("local:abc.pdf", "/api/files/1"));
        assertEquals(cloudUrl, storage.publicUrl(cloudUrl, "/api/files/1"));
        assertFalse(storage.isLocal(cloudUrl));
    }

    @Test
    void deleteIgnoresMissingMalformedAndExternalKeys() {
        assertDoesNotThrow(() -> storage.delete(null));
        assertDoesNotThrow(() -> storage.delete("local:not-a-valid-key"));
        assertDoesNotThrow(() -> storage.delete("https://res.cloudinary.com/demo/image/upload/v1/a/b.png"));
    }

    @Test
    void refusesToLoadExternallyHostedFiles() {
        assertThrows(ResponseStatusException.class,
                () -> storage.load("https://res.cloudinary.com/demo/image/upload/v1/a/b.png"));
    }
}
