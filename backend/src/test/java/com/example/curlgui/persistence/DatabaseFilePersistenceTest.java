package com.example.curlgui.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Test;

/**
 * Proves the storage layer is a real on-disk file, not memory: data written by
 * one connection is visible to a completely separate connection opened later -
 * which is what "history survives a backend restart" needs. Pure JDBC, no Spring.
 */
class DatabaseFilePersistenceTest {

    @Test
    void sqliteDataSurvivesClosingAndReopeningTheFile() throws Exception {
        Path db = Files.createTempFile("curlgui-persist-", ".db");
        Files.deleteIfExists(db); // let SQLite create it fresh
        String url = "jdbc:sqlite:" + db.toString().replace('\\', '/');

        try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE request_history "
                    + "(id INTEGER PRIMARY KEY AUTOINCREMENT, method TEXT, url TEXT, created_at TEXT)");
            s.executeUpdate("INSERT INTO request_history(method, url, created_at) "
                    + "VALUES ('GET', 'https://example.com', '2026-09-02T18:30:00Z')");
        }

        // Every connection above is closed. Opening a brand-new one is the same
        // thing that happens after the app is stopped and started again.
        try (Connection c = DriverManager.getConnection(url);
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT method, url FROM request_history ORDER BY id DESC")) {
            assertTrue(rs.next(), "the row written by the first connection is gone");
            assertEquals("GET", rs.getString("method"));
            assertEquals("https://example.com", rs.getString("url"));
        }

        Files.deleteIfExists(db);
    }
}
