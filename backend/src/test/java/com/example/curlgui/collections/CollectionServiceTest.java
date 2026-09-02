package com.example.curlgui.collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import com.example.curlgui.dto.CollectionDto;
import com.example.curlgui.dto.SaveRequestDto;
import com.example.curlgui.repository.CollectionRepository;
import com.example.curlgui.repository.SavedRequestRepository;
import com.example.curlgui.service.CollectionService;
import com.example.curlgui.service.ConflictException;
import com.example.curlgui.service.HeaderSanitizer;
import com.example.curlgui.service.InvalidRequestException;
import com.example.curlgui.service.NotFoundException;
import com.example.curlgui.service.SavedRequestService;
import com.example.curlgui.service.SensitiveHeaders;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Collection CRUD against a real SQLite file. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlite:build/test-collections.db",
        "spring.datasource.hikari.maximum-pool-size=1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class CollectionServiceTest {

    @Autowired
    private CollectionRepository collectionRepository;
    @Autowired
    private SavedRequestRepository savedRequestRepository;

    private CollectionService collections;
    private SavedRequestService savedRequests;

    @BeforeEach
    void setUp() {
        savedRequestRepository.deleteAll();
        collectionRepository.deleteAll();
        HeaderSanitizer sanitizer = new HeaderSanitizer(new SensitiveHeaders(), new ObjectMapper());
        collections = new CollectionService(collectionRepository, savedRequestRepository);
        savedRequests = new SavedRequestService(savedRequestRepository, collectionRepository, sanitizer);
    }

    @Test
    void test1_createCollection() {
        CollectionDto c = collections.create("Record API");
        assertEquals("Record API", c.name());
        assertTrue(c.requests().isEmpty());
        assertEquals(1, collections.listAll().size());
    }

    @Test
    void test2_rejectBlankName() {
        assertThrows(InvalidRequestException.class, () -> collections.create("   "));
        assertThrows(InvalidRequestException.class, () -> collections.create(null));
    }

    @Test
    void rejectDuplicateNameCaseInsensitive() {
        collections.create("Record API");
        assertThrows(ConflictException.class, () -> collections.create("record api"));
    }

    @Test
    void test3_renameCollection() {
        long id = collections.create("Record API").id();
        CollectionDto renamed = collections.rename(id, "Record Club API");
        assertEquals("Record Club API", renamed.name());
        assertEquals("Record Club API", collections.listAll().get(0).name());
    }

    @Test
    void renameMissingCollectionIs404() {
        assertThrows(NotFoundException.class, () -> collections.rename(9999, "X"));
    }

    @Test
    void test4_deleteCollection() {
        long id = collections.create("Temp").id();
        collections.delete(id);
        assertTrue(collections.listAll().isEmpty());
        assertThrows(NotFoundException.class, () -> collections.delete(id));
    }

    @Test
    void test5_deletingCollectionDeletesItsRequests() {
        long id = collections.create("Record API").id();
        savedRequests.create(new SaveRequestDto("Rate Release", id, "POST",
                "https://example.com/api/rate", List.of(), "{\"rating\":7}"));
        savedRequests.create(new SaveRequestDto("Get Profile", id, "GET",
                "https://example.com/api/profile", List.of(), ""));
        assertEquals(2, savedRequestRepository.count());

        collections.delete(id);

        assertEquals(0, savedRequestRepository.count(), "saved requests were orphaned");
    }

    @Test
    void ensureDefaultCollectionIsIdempotent() {
        collections.ensureDefaultCollection();
        collections.ensureDefaultCollection();
        collections.ensureDefaultCollection();
        long count = collections.listAll().stream()
                .filter(c -> c.name().equals(CollectionService.DEFAULT_COLLECTION_NAME))
                .count();
        assertEquals(1, count);
    }

    @Test
    void listAllGroupsRequestsUnderTheirCollection() {
        long a = collections.create("A").id();
        long b = collections.create("B").id();
        savedRequests.create(new SaveRequestDto("a1", a, "GET", "https://e.com", List.of(), ""));
        savedRequests.create(new SaveRequestDto("a2", a, "GET", "https://e.com", List.of(), ""));
        savedRequests.create(new SaveRequestDto("b1", b, "GET", "https://e.com", List.of(), ""));

        List<CollectionDto> all = collections.listAll();
        assertEquals(2, all.get(0).requests().size());
        assertEquals(1, all.get(1).requests().size());
        assertEquals("a1", all.get(0).requests().get(0).name());
    }
}
