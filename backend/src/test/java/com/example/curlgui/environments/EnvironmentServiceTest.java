package com.example.curlgui.environments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import com.example.curlgui.dto.SaveVariableDto;
import com.example.curlgui.repository.EnvironmentRepository;
import com.example.curlgui.repository.EnvironmentVariableRepository;
import com.example.curlgui.service.ConflictException;
import com.example.curlgui.service.EnvironmentService;
import com.example.curlgui.service.EnvironmentVariableService;
import com.example.curlgui.service.InvalidRequestException;
import com.example.curlgui.service.NotFoundException;

/** Environment CRUD against a real SQLite file. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlite:build/test-environments.db",
        "spring.datasource.hikari.maximum-pool-size=1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class EnvironmentServiceTest {

    @Autowired
    private EnvironmentRepository environmentRepository;
    @Autowired
    private EnvironmentVariableRepository variableRepository;

    private EnvironmentService environments;
    private EnvironmentVariableService variables;

    @BeforeEach
    void setUp() {
        variableRepository.deleteAll();
        environmentRepository.deleteAll();
        environments = new EnvironmentService(environmentRepository, variableRepository);
        variables = new EnvironmentVariableService(variableRepository, environmentRepository);
    }

    @Test
    void test1_createEnvironment() {
        var dev = environments.create("Development");
        assertEquals("Development", dev.name());
        assertEquals(1, environments.listAll().size());
    }

    @Test
    void test2_rejectBlankName() {
        assertThrows(InvalidRequestException.class, () -> environments.create("  "));
        assertThrows(InvalidRequestException.class, () -> environments.create(null));
    }

    @Test
    void rejectDuplicateNameCaseInsensitive() {
        environments.create("Development");
        assertThrows(ConflictException.class, () -> environments.create("development"));
    }

    @Test
    void test3_renameEnvironment() {
        long id = environments.create("Development").id();
        assertEquals("Production", environments.rename(id, "Production").name());
        assertEquals("Production", environments.listAll().get(0).name());
    }

    @Test
    void test4_deleteEnvironment() {
        long a = environments.create("A").id();
        environments.create("B"); // so A is not the only one
        environments.delete(a);
        assertEquals(1, environments.listAll().size());
        assertThrows(NotFoundException.class, () -> environments.delete(a));
    }

    @Test
    void test5_deletingEnvironmentDeletesItsVariables() {
        long keepId = environments.create("Keep").id();
        long id = environments.create("Development").id();
        variables.create(id, new SaveVariableDto("BASE_URL", "https://api.example.com"));
        variables.create(id, new SaveVariableDto("TOKEN", "secret"));
        assertEquals(2, variableRepository.count());

        environments.delete(id);

        assertEquals(0, variableRepository.count(), "variables were orphaned");
        assertEquals(1, environments.listAll().size());
        assertEquals(keepId, environments.listAll().get(0).id());
    }

    @Test
    void cannotDeleteTheOnlyEnvironment() {
        long id = environments.create("Only").id();
        assertThrows(ConflictException.class, () -> environments.delete(id));
        assertEquals(1, environments.listAll().size());
    }

    @Test
    void ensureDefaultEnvironmentIsIdempotent() {
        environments.ensureDefaultEnvironment();
        environments.ensureDefaultEnvironment();
        environments.ensureDefaultEnvironment();
        long count = environments.listAll().stream()
                .filter(e -> e.name().equals(EnvironmentService.DEFAULT_ENVIRONMENT_NAME))
                .count();
        assertEquals(1, count);
    }

    @Test
    void getReturnsVariablesWithValues() {
        long id = environments.create("Development").id();
        variables.create(id, new SaveVariableDto("BASE_URL", "http://localhost:8081"));
        var dto = environments.get(id);
        assertEquals("Development", dto.name());
        assertEquals(1, dto.variables().size());
        assertEquals("BASE_URL", dto.variables().get(0).key());
        assertEquals("http://localhost:8081", dto.variables().get(0).value());
    }

    @Test
    void getMissingEnvironmentIs404() {
        assertThrows(NotFoundException.class, () -> environments.get(9999));
    }

    @Test
    void listEndpointDoesNotExposeValues() {
        long id = environments.create("Development").id();
        variables.create(id, new SaveVariableDto("TOKEN", "super-secret"));
        String rendered = environments.listAll().toString();
        assertTrue(!rendered.contains("super-secret"));
    }
}
