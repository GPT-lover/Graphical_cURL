package com.example.curlgui.collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import com.example.curlgui.dto.HeaderDto;
import com.example.curlgui.dto.SaveRequestDto;
import com.example.curlgui.dto.SavedRequestDto;
import com.example.curlgui.repository.CollectionRepository;
import com.example.curlgui.repository.SavedRequestRepository;
import com.example.curlgui.service.CollectionService;
import com.example.curlgui.service.HeaderSanitizer;
import com.example.curlgui.service.InvalidRequestException;
import com.example.curlgui.service.NotFoundException;
import com.example.curlgui.service.SavedRequestService;
import com.example.curlgui.service.SensitiveHeaders;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Saved-request CRUD + sanitisation against a real SQLite file. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlite:build/test-saved-requests.db",
        "spring.datasource.hikari.maximum-pool-size=1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class SavedRequestServiceTest {

    @Autowired
    private CollectionRepository collectionRepository;
    @Autowired
    private SavedRequestRepository savedRequestRepository;

    private CollectionService collections;
    private SavedRequestService savedRequests;
    private long collectionId;

    @BeforeEach
    void setUp() {
        savedRequestRepository.deleteAll();
        collectionRepository.deleteAll();
        HeaderSanitizer sanitizer = new HeaderSanitizer(new SensitiveHeaders(), new ObjectMapper());
        collections = new CollectionService(collectionRepository, savedRequestRepository);
        savedRequests = new SavedRequestService(savedRequestRepository, collectionRepository, sanitizer);
        collectionId = collections.create("Record API").id();
    }

    private SaveRequestDto dto(String name, String body, List<HeaderDto> headers) {
        return new SaveRequestDto(name, collectionId, "POST",
                "https://example.com/api/rate", headers, body);
    }

    @Test
    void test1_createSavedRequest() {
        SavedRequestDto saved = savedRequests.create(dto("Rate Release", "{\"rating\":7}", List.of(
                new HeaderDto("Content-Type", "application/json"))));
        assertEquals("Rate Release", saved.name());
        assertEquals(collectionId, saved.collectionId());
        assertEquals("POST", saved.method());
        assertEquals("{\"rating\":7}", saved.body());
        assertEquals(List.of(new HeaderDto("Content-Type", "application/json")), saved.headers());
        assertTrue(saved.id() != null);
    }

    @Test
    void test2_retrieveSavedRequest() {
        long id = savedRequests.create(dto("Rate Release", "{}", List.of())).id();
        SavedRequestDto got = savedRequests.get(id);
        assertEquals("Rate Release", got.name());
        assertEquals("https://example.com/api/rate", got.url());
    }

    @Test
    void retrieveMissingIs404() {
        assertThrows(NotFoundException.class, () -> savedRequests.get(9999));
    }

    @Test
    void test3_updateSavedRequest() {
        long id = savedRequests.create(dto("Rate Release", "{\"rating\":7}", List.of())).id();
        SavedRequestDto updated = savedRequests.update(id, new SaveRequestDto(
                "Rate Release", collectionId, "POST",
                "https://example.com/api/rate/123", List.of(), "{\"rating\":9}"));
        assertEquals("https://example.com/api/rate/123", updated.url());
        assertEquals("{\"rating\":9}", updated.body());
        assertEquals("{\"rating\":9}", savedRequests.get(id).body());  // persisted
    }

    @Test
    void test4_deleteSavedRequest() {
        long id = savedRequests.create(dto("Rate Release", "{}", List.of())).id();
        savedRequests.delete(id);
        assertThrows(NotFoundException.class, () -> savedRequests.get(id));
        assertThrows(NotFoundException.class, () -> savedRequests.delete(id));
    }

    @Test
    void test5_rejectNonexistentCollection() {
        assertThrows(NotFoundException.class, () -> savedRequests.create(new SaveRequestDto(
                "X", 9999L, "GET", "https://e.com", List.of(), "")));
    }

    @Test
    void test6_rejectBlankName() {
        assertThrows(InvalidRequestException.class, () -> savedRequests.create(dto("   ", "{}", List.of())));
        assertThrows(InvalidRequestException.class, () -> savedRequests.create(dto(null, "{}", List.of())));
    }

    @Test
    void rejectNullCollectionId() {
        assertThrows(InvalidRequestException.class, () -> savedRequests.create(new SaveRequestDto(
                "X", null, "GET", "https://e.com", List.of(), "")));
    }

    @Test
    void security_sensitiveHeadersAndCookiesAreNotPersisted() {
        SavedRequestDto saved = savedRequests.create(dto("With creds", "{}", List.of(
                new HeaderDto("Authorization", "Bearer SECRET"),
                new HeaderDto("Cookie", "auth=SECRET"),
                new HeaderDto("X-Auth-Token", "SECRET"),
                new HeaderDto("Content-Type", "application/json"))));

        List<String> names = saved.headers().stream().map(HeaderDto::key).toList();
        assertTrue(names.contains("Content-Type"));
        assertFalse(names.contains("Authorization"));
        assertFalse(names.contains("Cookie"));
        assertFalse(names.contains("X-Auth-Token"));
        assertFalse(saved.headers().toString().contains("SECRET"), "a secret leaked into the DB");

        // re-read from the DB to be sure it wasn't just filtered on the way out
        assertFalse(savedRequests.get(saved.id()).headers().toString().contains("SECRET"));
    }

    @Test
    void updateCanMoveRequestToAnotherCollection() {
        long other = collections.create("Testing").id();
        long id = savedRequests.create(dto("Rate Release", "{}", List.of())).id();
        SavedRequestDto moved = savedRequests.update(id, new SaveRequestDto(
                "Rate Release", other, "POST", "https://example.com/api/rate", List.of(), "{}"));
        assertEquals(other, moved.collectionId());
    }
}
