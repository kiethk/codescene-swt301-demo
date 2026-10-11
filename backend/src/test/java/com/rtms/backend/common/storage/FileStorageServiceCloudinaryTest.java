package com.rtms.backend.common.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void uploadsAnImageThatIsPubliclyReadableThenDeletesIt() throws Exception {
        FileStorageService storage = storage();
        String url = storage.store(new MockMultipartFile("file", "pixel.png", "image/png", PNG_1X1), FOLDER);
        try {
            System.out.println("[cloudinary] image stored at " + url);
            assertFalse(storage.isLocal(url));
            assertTrue(url.matches("https://res\\.cloudinary\\.com/[^/]+/image/upload/v\\d+/rtms/_selftest/[a-f0-9\\-]{36}\\.png"), url);
            assertEquals(200, status(url), "image should be downloadable");
        } finally {
            storage.delete(url);
        }
        System.out.println("[cloudinary] image status after delete: " + status(url));
    }

    @Test
    void uploadsAPdfAndReportsWhetherTheAccountDeliversIt() throws Exception {
        FileStorageService storage = storage();
        String url = storage.store(new MockMultipartFile("file", "doc.pdf", "application/pdf", minimalPdf()), FOLDER);
        try {
            System.out.println("[cloudinary] pdf stored at " + url);
            assertTrue(url.matches("https://res\\.cloudinary\\.com/[^/]+/image/upload/v\\d+/rtms/_selftest/[a-f0-9\\-]{36}\\.pdf"), url);
            int status = status(url);
            System.out.println("[cloudinary] pdf delivery status: " + status);
            assertEquals(200, status,
                    "PDF uploaded but is not delivered: enable \"Allow delivery of PDF and ZIP files\" under Settings > Security");
        } finally {
            storage.delete(url);
        }
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
