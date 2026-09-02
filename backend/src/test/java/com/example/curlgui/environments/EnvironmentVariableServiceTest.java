package com.example.curlgui.environments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import com.example.curlgui.dto.EnvironmentVariableDto;
import com.example.curlgui.dto.SaveVariableDto;
import com.example.curlgui.repository.EnvironmentRepository;
import com.example.curlgui.repository.EnvironmentVariableRepository;
import com.example.curlgui.service.ConflictException;
import com.example.curlgui.service.EnvironmentService;
import com.example.curlgui.service.EnvironmentVariableService;
import com.example.curlgui.service.InvalidRequestException;
import com.example.curlgui.service.NotFoundException;

/** Environment-variable CRUD + variablesFor() against a real SQLite file. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlite:build/test-env-vars.db",
        "spring.datasource.hikari.maximum-pool-size=1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class EnvironmentVariableServiceTest {

    @Autowired
    private EnvironmentRepository environmentRepository;
    @Autowired
    private EnvironmentVariableRepository variableRepository;

    private EnvironmentService environments;
    private EnvironmentVariableService variables;
    private long envId;

    @BeforeEach
    void setUp() {
        variableRepository.deleteAll();
        environmentRepository.deleteAll();
        environments = new EnvironmentService(environmentRepository, variableRepository);
        variables = new EnvironmentVariableService(variableRepository, environmentRepository);
        envId = environments.create("Development").id();
    }

    @Test
    void test1_addVariable() {
        EnvironmentVariableDto v = variables.create(envId,
                new SaveVariableDto("BASE_URL", "https://api.example.com"));
        assertEquals("BASE_URL", v.key());
        assertEquals("https://api.example.com", v.value());
    }

    @Test
    void test2_updateVariable() {
        long id = variables.create(envId, new SaveVariableDto("BASE_URL", "https://old")).id();
        EnvironmentVariableDto updated = variables.update(envId, id,
                new SaveVariableDto("BASE_URL", "https://new"));
        assertEquals("https://new", updated.value());
        assertEquals("https://new", environments.get(envId).variables().get(0).value());
    }

    @Test
    void test3_deleteVariable() {
        long id = variables.create(envId, new SaveVariableDto("BASE_URL", "x")).id();
        variables.delete(envId, id);
        assertEquals(0, environments.get(envId).variables().size());
        assertThrows(NotFoundException.class, () -> variables.delete(envId, id));
    }

    @Test
    void test4_rejectBlankKey() {
        assertThrows(InvalidRequestException.class,
                () -> variables.create(envId, new SaveVariableDto("  ", "x")));
        assertThrows(InvalidRequestException.class,
                () -> variables.create(envId, new SaveVariableDto(null, "x")));
    }

    @Test
    void rejectKeyWithInvalidPlaceholderSyntax() {
        assertThrows(InvalidRequestException.class,
                () -> variables.create(envId, new SaveVariableDto("{{BASE_URL}}", "x")));
        assertThrows(InvalidRequestException.class,
                () -> variables.create(envId, new SaveVariableDto("BASE URL", "x")));
    }

    @Test
    void test5_allowEmptyValue() {
        EnvironmentVariableDto v = variables.create(envId, new SaveVariableDto("OPTIONAL", ""));
        assertEquals("", v.value());
        assertEquals("", environments.get(envId).variables().get(0).value());
    }

    @Test
    void test6_rejectDuplicateKeyInSameEnvironment() {
        variables.create(envId, new SaveVariableDto("BASE_URL", "one"));
        assertThrows(ConflictException.class,
                () -> variables.create(envId, new SaveVariableDto("BASE_URL", "two")));
    }

    @Test
    void test7_allowSameKeyInDifferentEnvironments() {
        long prodId = environments.create("Production").id();
        variables.create(envId, new SaveVariableDto("BASE_URL", "http://localhost"));
        variables.create(prodId, new SaveVariableDto("BASE_URL", "https://api.example.com"));

        assertEquals("http://localhost", environments.get(envId).variables().get(0).value());
        assertEquals("https://api.example.com", environments.get(prodId).variables().get(0).value());
    }

    @Test
    void createVariableForMissingEnvironmentIs404() {
        assertThrows(NotFoundException.class,
                () -> variables.create(9999, new SaveVariableDto("K", "v")));
    }

    @Test
    void variablesForReturnsAMapAndTreatsEmptyValueAsEmptyString() {
        variables.create(envId, new SaveVariableDto("BASE_URL", "https://api.example.com"));
        variables.create(envId, new SaveVariableDto("OPTIONAL", ""));

        Map<String, String> map = variables.variablesFor(envId);
        assertEquals("https://api.example.com", map.get("BASE_URL"));
        assertEquals("", map.get("OPTIONAL"));
        assertEquals(2, map.size());
    }

    @Test
    void variablesForNullEnvironmentIsEmpty_andUnknownIdIs404() {
        assertEquals(0, variables.variablesFor(null).size());
        assertThrows(NotFoundException.class, () -> variables.variablesFor(9999L));
    }
}
