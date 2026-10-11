package com.rtms.backend.common.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Talks to a real Cloudinary account, so it only runs when CLOUDINARY_URL is set:
 *
 *   CLOUDINARY_URL=cloudinary://key:secret@cloud ./mvnw test -Dtest=FileStorageServiceCloudinaryTest
 *
 * Every file it uploads goes under rtms/_selftest and is deleted again.
 */
@EnabledIfEnvironmentVariable(named = "CLOUDINARY_URL", matches = "cloudinary://.+")
class FileStorageServiceCloudinaryTest {
    private static final String FOLDER = "rtms/_selftest";
    private static final byte[] PNG_1X1 = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

    @TempDir
    Path unusedLocalDir;

    private final HttpClient http = HttpClient.newHttpClient();

    private FileStorageService storage() {
        return new FileStorageService(unusedLocalDir.toString(), System.getenv("CLOUDINARY_URL"));
    }

    @Test
    void uploadsAPrivateImageThatOnlyTheBackendCanReadThenDeletesIt() throws Exception {
        FileStorageService storage = storage();
        String url = storage.store(new MockMultipartFile("file", "pixel.png", "image/png", PNG_1X1), FOLDER);
        try {
            System.out.println("[cloudinary] image stored at " + url);
            assertFalse(storage.isLocal(url));
            assertTrue(url.matches("https://res\\.cloudinary\\.com/[^/]+/image/authenticated/v\\d+/rtms/_selftest/[a-f0-9\\-]{36}\\.png"), url);
            int anonymous = status(url);
            System.out.println("[cloudinary] anonymous request for the stored key: " + anonymous);
            assertNotEquals(200, anonymous, "the stored key must not open the file without a signature");
            assertArrayEquals(PNG_1X1, storage.load(url).getContentAsByteArray(), "the backend should read it back");
        } finally {
            storage.delete(url);
        }
        // Not asserted: the CDN may keep serving a deleted file for a while after it is invalidated.
        System.out.println("[cloudinary] anonymous status after delete: " + status(url));
    }

    @Test
    void uploadsAPrivatePdfAndReadsItBack() throws Exception {
        FileStorageService storage = storage();
        String url = storage.store(new MockMultipartFile("file", "doc.pdf", "application/pdf", minimalPdf()), FOLDER);
        try {
            System.out.println("[cloudinary] pdf stored at " + url);
            assertTrue(url.matches("https://res\\.cloudinary\\.com/[^/]+/image/authenticated/v\\d+/rtms/_selftest/[a-f0-9\\-]{36}\\.pdf"), url);
            assertNotEquals(200, status(url), "the stored key must not open the file without a signature");
            // Fails with "not found" when the account blocks PDF delivery:
            // enable "Allow delivery of PDF and ZIP files" under Settings > Security.
            assertArrayEquals(minimalPdf(), storage.load(url).getContentAsByteArray());
        } finally {
            storage.delete(url);
        }
    }

    @Test
    void rejectsAFileWhoseContentIsNotWhatItClaims() {
        var fake = new MockMultipartFile("file", "fake.png", "image/png", "MZ not an image".getBytes());

        var error = assertThrows(ResponseStatusException.class, () -> storage().store(fake, FOLDER));
        assertEquals(400, error.getStatusCode().value());
    }

    private int status(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    /** A one-page blank PDF with a correct cross-reference table. */
    private static byte[] minimalPdf() {
        String[] objects = {
                "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n",
                "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n",
                "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] >>\nendobj\n",
        };
        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        int[] offsets = new int[objects.length];
        for (int i = 0; i < objects.length; i++) {
            offsets[i] = pdf.length();
            pdf.append(objects[i]);
        }
        int xref = pdf.length();
        pdf.append("xref\n0 ").append(objects.length + 1).append("\n0000000000 65535 f \n");
        for (int offset : offsets) {
            pdf.append(String.format("%010d 00000 n \n", offset));
        }
        pdf.append("trailer\n<< /Size ").append(objects.length + 1).append(" /Root 1 0 R >>\nstartxref\n")
                .append(xref).append("\n%%EOF\n");
        return pdf.toString().getBytes(StandardCharsets.US_ASCII);
    }
}
