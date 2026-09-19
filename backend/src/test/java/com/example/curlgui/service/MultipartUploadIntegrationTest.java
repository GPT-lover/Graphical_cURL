package com.example.curlgui.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.curlgui.dto.HeaderDto;
import com.example.curlgui.dto.MultipartFieldDto;
import com.example.curlgui.dto.SendRequestDto;
import com.example.curlgui.dto.SendResponseDto;
import com.sun.net.httpserver.HttpServer;

/**
 * End-to-end coverage for multipart / binary uploads: runs the real {@code
 * RequestService} -> {@code CurlProcessExecutor} -> real {@code curl}
 * executable pipeline against a local {@link HttpServer} (JDK built-in, no test
 * dependency needed) and inspects exactly what bytes the server received - the
 * point being to prove the file's real bytes reach the wire, not the path
 * string. No external network access; the whole exchange is 127.0.0.1.
 *
 * <p>Uses {@code RequestService.executeResolved(...)}, which - unlike {@code
 * execute(...)} - never touches {@code EnvironmentVariableService} or {@code
 * RequestHistoryService} (see its javadoc), so this needs no database/Spring
 * context, matching how {@code RunMultipleService}/{@code ChainRunner} reuse it.
 */
class MultipartUploadIntegrationTest {

    @TempDir
    Path tempDir;

    private HttpServer server;
    private RequestService requestService;
    private String baseUrl;
    private final AtomicReference<byte[]> capturedBody = new AtomicReference<>();
    private final AtomicReference<String> capturedContentType = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/echo", exchange -> {
            capturedContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            byte[] body = exchange.getRequestBody().readAllBytes();
            capturedBody.set(body);
            byte[] response = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        requestService = new RequestService(
                new CurlProcessExecutor(""), null, null,
                new EnvironmentVariableResolver(), new DynamicVariableResolver());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private static SendRequestDto multipartDto(String url, List<HeaderDto> headers,
                                               List<MultipartFieldDto> fields) {
        return new SendRequestDto("POST", url, headers, List.of(), "", null, null, "multipart", fields);
    }

    private static SendRequestDto binaryDto(String url, List<HeaderDto> headers, String path) {
        return new SendRequestDto("PUT", url, headers, List.of(), path, null, null, "binary", null);
    }

    @Test
    void multipartUploadSendsTheRealFileBytesNotThePathString() throws IOException {
        byte[] fileBytes = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3, 4, 5, 0, (byte) 0xAA};
        Path file = tempDir.resolve("photo.jpg");
        Files.write(file, fileBytes);

        SendResponseDto response = requestService.executeResolved(multipartDto(
                baseUrl + "/echo", List.of(),
                List.of(new MultipartFieldDto("file", "image", file.toString(), "image/jpeg"),
                        new MultipartFieldDto("text", "albumId", "123", null))));

        assertEquals(200, response.statusCode());
        assertTrue(capturedContentType.get().startsWith("multipart/form-data; boundary="));

        String received = new String(capturedBody.get(), StandardCharsets.ISO_8859_1);
        // The literal path string must NOT appear as the field's content - only
        // the real bytes (round-tripped through ISO-8859-1 so every byte value,
        // including 0x00 and 0xFF, compares exactly).
        assertTrue(received.contains(new String(fileBytes, StandardCharsets.ISO_8859_1)),
                "multipart body must contain the file's real bytes");
        assertTrue(received.contains("name=\"albumId\""));
        assertTrue(received.contains("123"));
        assertTrue(received.contains("filename=\"photo.jpg\""));
        assertTrue(received.contains("Content-Type: image/jpeg"));
    }

    /**
     * Regression test for a real bug found in manual testing: quoting the file
     * path (curl's own {@code @"path"} escape) works when curl is invoked
     * through a POSIX shell, but on Windows, {@code ProcessBuilder} re-quotes
     * any argv entry containing a space or {@code "} to build the Win32
     * command-line string - colliding with quoting {@link CurlCommandBuilder}
     * added itself, silently corrupting the path (curl ended up reading a
     * different, unrelated file). Any directory name containing a space -
     * exactly what "AppData\Local\Temp" often does NOT have, so this test
     * creates one deliberately - must still upload correctly.
     */
    @Test
    void multipartUploadSucceedsWhenTheFilePathContainsASpace() throws IOException {
        Path dirWithSpace = tempDir.resolve("a directory with spaces");
        Files.createDirectory(dirWithSpace);
        byte[] fileBytes = "distinctive marker content 12345".getBytes(StandardCharsets.UTF_8);
        Path file = dirWithSpace.resolve("my photo.jpg");
        Files.write(file, fileBytes);

        SendResponseDto response = requestService.executeResolved(multipartDto(
                baseUrl + "/echo", List.of(),
                List.of(new MultipartFieldDto("file", "image", file.toString(), null))));

        assertEquals(200, response.statusCode());
        String received = new String(capturedBody.get(), StandardCharsets.UTF_8);
        assertTrue(received.contains("filename=\"my photo.jpg\""));
        assertTrue(received.contains("distinctive marker content 12345"),
                "must contain the real file's bytes, not a different file's content");
    }

    @Test
    void multipartFilePathContainingSemicolonIsRejectedRatherThanRiskingCorruption() throws IOException {
        Path file = tempDir.resolve("a;b.jpg");
        Files.write(file, new byte[]{1, 2, 3});

        InvalidRequestException ex = assertThrows(InvalidRequestException.class,
                () -> requestService.executeResolved(multipartDto(baseUrl + "/echo", List.of(),
                        List.of(new MultipartFieldDto("file", "image", file.toString(), null)))));
        assertTrue(ex.getMessage().contains("image"));
    }

    @Test
    void multipartTextFieldNeverBecomesAFileAttachment() throws IOException {
        SendResponseDto response = requestService.executeResolved(multipartDto(
                baseUrl + "/echo", List.of(),
                List.of(new MultipartFieldDto("text", "note", "hello world", null))));

        assertEquals(200, response.statusCode());
        String received = new String(capturedBody.get(), StandardCharsets.UTF_8);
        assertTrue(received.contains("name=\"note\""));
        assertTrue(received.contains("hello world"));
        assertTrue(!received.contains("filename="), "a text field must never carry a filename= part");
    }

    @Test
    void binaryUploadStreamsTheRealFileBytes() throws IOException {
        byte[] fileBytes = new byte[500];
        for (int i = 0; i < fileBytes.length; i++) {
            fileBytes[i] = (byte) (i % 256);
        }
        Path file = tempDir.resolve("image.bin");
        Files.write(file, fileBytes);

        SendResponseDto response = requestService.executeResolved(binaryDto(
                baseUrl + "/echo", List.of(new HeaderDto("Content-Type", "application/octet-stream")),
                file.toString()));

        assertEquals(200, response.statusCode());
        assertEquals("application/octet-stream", capturedContentType.get());
        org.junit.jupiter.api.Assertions.assertArrayEquals(fileBytes, capturedBody.get());
    }

    // ---- Error handling ------------------------------------------------

    @Test
    void missingFileFailsWithAClearErrorBeforeCurlRuns() {
        Path missing = tempDir.resolve("does-not-exist.jpg");
        InvalidRequestException ex = assertThrows(InvalidRequestException.class,
                () -> requestService.executeResolved(multipartDto(baseUrl + "/echo", List.of(),
                        List.of(new MultipartFieldDto("file", "image", missing.toString(), null)))));
        assertTrue(ex.getMessage().contains("image"));
        assertTrue(ex.getMessage().toLowerCase().contains("no longer exist"));
    }

    @Test
    void deletedFileFailsTheSameWayAsMissing() throws IOException {
        Path file = tempDir.resolve("temporary.jpg");
        Files.write(file, new byte[]{1, 2, 3});
        Files.delete(file);

        InvalidRequestException ex = assertThrows(InvalidRequestException.class,
                () -> requestService.executeResolved(binaryDto(baseUrl + "/echo", List.of(), file.toString())));
        assertTrue(ex.getMessage().toLowerCase().contains("no longer exist"));
    }

    @Test
    void invalidPathFailsWithAClearError() {
        // NUL is illegal in a path on every OS Java targets here.
        InvalidRequestException ex = assertThrows(InvalidRequestException.class,
                () -> requestService.executeResolved(multipartDto(baseUrl + "/echo", List.of(),
                        List.of(new MultipartFieldDto("file", "image", "bad\0path.jpg", null)))));
        assertTrue(ex.getMessage().contains("image"));
    }

    @Test
    void aDirectoryInsteadOfAFileIsRejected() {
        InvalidRequestException ex = assertThrows(InvalidRequestException.class,
                () -> requestService.executeResolved(multipartDto(baseUrl + "/echo", List.of(),
                        List.of(new MultipartFieldDto("file", "image", tempDir.toString(), null)))));
        assertTrue(ex.getMessage().contains("image"));
    }

    @Test
    void emptyMultipartFieldListIsRejected() {
        assertThrows(InvalidRequestException.class,
                () -> requestService.executeResolved(multipartDto(baseUrl + "/echo", List.of(), List.of())));
    }

    @Test
    void blankFilePathIsRejected() {
        InvalidRequestException ex = assertThrows(InvalidRequestException.class,
                () -> requestService.executeResolved(multipartDto(baseUrl + "/echo", List.of(),
                        List.of(new MultipartFieldDto("file", "image", "  ", null)))));
        assertTrue(ex.getMessage().contains("image"));
    }
}
