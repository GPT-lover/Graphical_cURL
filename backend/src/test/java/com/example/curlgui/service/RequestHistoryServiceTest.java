package com.example.curlgui.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import com.example.curlgui.dto.HeaderDto;
import com.example.curlgui.dto.RequestHistoryDto;
import com.example.curlgui.dto.SendRequestDto;
import com.example.curlgui.dto.SendResponseDto;
import com.example.curlgui.repository.RequestHistoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Integration tests for {@link RequestHistoryService} against a <b>real SQLite
 * database</b> (a temp file under {@code build/}). {@code @DataJpaTest} boots only
 * the JPA slice - no web server, no HttpClient.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlite:build/test-history.db",
        "spring.datasource.hikari.maximum-pool-size=1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class RequestHistoryServiceTest {

    @Autowired
    private RequestHistoryRepository repository;

    private RequestHistoryService service;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        service = new RequestHistoryService(repository, new SensitiveHeaders(), new ObjectMapper());
    }

    private static SendRequestDto req(String method, String url, List<HeaderDto> headers, String body) {
        return new SendRequestDto(method, url, headers, List.of(), body);
    }

    private static SendResponseDto resp(int status, long durationMs) {
        return new SendResponseDto(status, Map.of(), "", durationMs, List.of());
    }

    @Test
    void test1_historyRecordIsCreatedAfterARequest() {
        service.record(req("GET", "https://example.com", List.of(), ""), resp(200, 143));

        List<RequestHistoryDto> all = service.list();
        assertEquals(1, all.size());
        RequestHistoryDto e = all.get(0);
        assertEquals("GET", e.method());
        assertEquals("https://example.com", e.url());
        assertEquals(200, e.statusCode());
        assertEquals(143, e.durationMs());
        assertNotNull(e.id());
        assertNotNull(e.createdAt());
    }

    @Test
    void test2_sensitiveHeadersAreNotPersisted() {
        service.record(req("POST", "https://example.com", List.of(
                new HeaderDto("Authorization", "Bearer SECRET"),
                new HeaderDto("Cookie", "auth=SECRET"),
                new HeaderDto("X-Api-Key", "SECRET"),
                new HeaderDto("Content-Type", "application/json"),
                new HeaderDto("Accept", "application/json")), "{}"), resp(200, 10));

        List<HeaderDto> stored = service.list().get(0).headers();
        List<String> names = stored.stream().map(HeaderDto::key).toList();
        assertTrue(names.contains("Content-Type"));
        assertTrue(names.contains("Accept"));
        assertFalse(names.contains("Authorization"));
        assertFalse(names.contains("Cookie"));
        assertFalse(names.contains("X-Api-Key"));
        assertFalse(stored.toString().contains("SECRET"), "a secret value leaked into history");
    }

    @Test
    void test3_historyIsOrderedNewestFirst() {
        service.record(req("GET", "https://example.com/1", List.of(), ""), resp(200, 1));
        service.record(req("GET", "https://example.com/2", List.of(), ""), resp(200, 1));
        service.record(req("GET", "https://example.com/3", List.of(), ""), resp(200, 1));

        List<RequestHistoryDto> all = service.list();
        assertEquals("https://example.com/3", all.get(0).url());
        assertEquals("https://example.com/2", all.get(1).url());
        assertEquals("https://example.com/1", all.get(2).url());
    }

    @Test
    void test4_deleteRemovesOneEntry() {
        service.record(req("GET", "https://example.com", List.of(), ""), resp(200, 1));
        long id = service.list().get(0).id();

        assertTrue(service.delete(id));
        assertTrue(service.list().isEmpty());
        assertFalse(service.delete(id), "deleting a missing id should report false");
    }

    @Test
    void test5_clearRemovesEverything() {
        for (int i = 0; i < 6; i++) {
            service.record(req("GET", "https://example.com/" + i, List.of(), ""), resp(200, 1));
        }
        assertEquals(6, service.list().size());

        service.clear();
        assertEquals(0, service.list().size());
    }

    @Test
    void test6_tableIsTrimmedToTheNewest100() {
        for (int i = 0; i < 130; i++) {
            service.record(req("GET", "https://example.com/" + i, List.of(), ""), resp(200, 1));
        }
        List<RequestHistoryDto> all = service.list();
        assertEquals(100, all.size());
        assertEquals("https://example.com/129", all.get(0).url());  // newest kept
        assertEquals("https://example.com/30", all.get(99).url());  // 130 - 100
    }

    @Test
    void headersAreStoredAsJsonAndParseBackToAList() {
        service.record(req("GET", "https://example.com", List.of(
                new HeaderDto("Accept", "application/json"),
                new HeaderDto("X-Trace", "abc-123")), ""), resp(204, 2));

        List<HeaderDto> stored = service.list().get(0).headers();
        assertEquals(List.of(
                new HeaderDto("Accept", "application/json"),
                new HeaderDto("X-Trace", "abc-123")), stored);
    }

    @Test
    void nullHeadersAndBodyAreHandled() {
        service.record(new SendRequestDto("GET", "https://example.com", null, null, null), resp(200, 1));
        RequestHistoryDto e = service.list().get(0);
        assertEquals(List.of(), e.headers());
        assertEquals("", e.body());
    }
}
