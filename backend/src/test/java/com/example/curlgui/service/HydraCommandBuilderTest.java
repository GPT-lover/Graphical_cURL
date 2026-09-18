package com.example.curlgui.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.curlgui.dto.HydraAttackConfigDto;

/**
 * Pure tests for the Hydra argument-list builder. No process is spawned and no
 * filesystem paths are checked here - these assert the exact argv
 * {@link HydraAttackService} would hand to {@link ProcessBuilder}.
 */
class HydraCommandBuilderTest {

    private static HydraAttackConfigDto config(String host, Integer port, String protocol,
                                               String username, String wordlist, String path,
                                               String formParams, String failureCondition) {
        return new HydraAttackConfigDto(
                host, port, protocol, username, wordlist, path, formParams, failureCondition);
    }

    private static HydraAttackConfigDto validConfig() {
        return config("10.82.166.33", 80, "http", "admin", "/path/to/wordlist",
                "/", "username=^USER^&password=^PASS^", "incorrect");
    }

    @Test
    void buildsTheExpectedArgv() {
        List<String> argv = HydraCommandBuilder.build("/usr/bin/hydra", validConfig());

        assertEquals(List.of(
                "/usr/bin/hydra",
                "-l", "admin",
                "-P", "/path/to/wordlist",
                "-s", "80",
                "10.82.166.33",
                "http-post-form",
                "/:username=^USER^&password=^PASS^:F=incorrect",
                "-V"
        ), argv);
    }

    @Test
    void httpsProtocolSelectsHttpsModule() {
        HydraAttackConfigDto cfg = config("example.com", 443, "HTTPS", "admin", "/wl",
                "/login", "user=^USER^&pass=^PASS^", "failed");
        List<String> argv = HydraCommandBuilder.build("hydra", cfg);
        assertTrue(argv.contains("https-post-form"));
        assertEquals("443", argv.get(argv.indexOf("-s") + 1));
    }

    @Test
    void protocolDefaultsToHttpWhenBlank() {
        HydraAttackConfigDto cfg = config("host", 80, "  ", "u", "/wl", "/", "a=^USER^&b=^PASS^", "f");
        List<String> argv = HydraCommandBuilder.build("hydra", cfg);
        assertTrue(argv.contains("http-post-form"));
    }

    @Test
    void pathsWithSpacesStayAsSingleArgvElements() {
        HydraAttackConfigDto cfg = config("host", 80, "http", "admin",
                "C:\\Program Files\\wordlists\\rockyou.txt", "/", "user=^USER^&pass=^PASS^", "bad");
        List<String> argv = HydraCommandBuilder.build("C:\\Program Files\\Hydra\\hydra.exe", cfg);
        assertEquals("C:\\Program Files\\Hydra\\hydra.exe", argv.get(0));
        assertEquals("C:\\Program Files\\wordlists\\rockyou.txt", argv.get(argv.indexOf("-P") + 1));
    }

    @Test
    void rejectsMissingPlaceholders() {
        HydraAttackConfigDto cfg = config("host", 80, "http", "admin", "/wl",
                "/", "username=admin&password=test", "incorrect");
        assertThrows(InvalidRequestException.class, () -> HydraCommandBuilder.build("hydra", cfg));
    }

    @Test
    void rejectsBadPort() {
        assertThrows(InvalidRequestException.class, () -> HydraCommandBuilder.build("hydra",
                config("host", 0, "http", "u", "/wl", "/", "a=^USER^&b=^PASS^", "f")));
        assertThrows(InvalidRequestException.class, () -> HydraCommandBuilder.build("hydra",
                config("host", 70000, "http", "u", "/wl", "/", "a=^USER^&b=^PASS^", "f")));
        assertThrows(InvalidRequestException.class, () -> HydraCommandBuilder.build("hydra",
                config("host", null, "http", "u", "/wl", "/", "a=^USER^&b=^PASS^", "f")));
    }

    @Test
    void rejectsBadProtocol() {
        assertThrows(InvalidRequestException.class, () -> HydraCommandBuilder.build("hydra",
                config("host", 80, "ftp", "u", "/wl", "/", "a=^USER^&b=^PASS^", "f")));
    }

    @Test
    void rejectsPathWithoutLeadingSlash() {
        assertThrows(InvalidRequestException.class, () -> HydraCommandBuilder.build("hydra",
                config("host", 80, "http", "u", "/wl", "login", "a=^USER^&b=^PASS^", "f")));
    }

    @Test
    void rejectsColonInModuleFields() {
        assertThrows(InvalidRequestException.class, () -> HydraCommandBuilder.build("hydra",
                config("host", 80, "http", "u", "/wl", "/a:b", "x=^USER^&y=^PASS^", "f")));
        assertThrows(InvalidRequestException.class, () -> HydraCommandBuilder.build("hydra",
                config("host", 80, "http", "u", "/wl", "/", "x=^USER^&y=^PASS^:z", "f")));
        assertThrows(InvalidRequestException.class, () -> HydraCommandBuilder.build("hydra",
                config("host", 80, "http", "u", "/wl", "/", "x=^USER^&y=^PASS^", "fa:il")));
    }

    @Test
    void rejectsBlankRequiredFields() {
        assertThrows(InvalidRequestException.class, () -> HydraCommandBuilder.build("hydra",
                config(" ", 80, "http", "u", "/wl", "/", "x=^USER^&y=^PASS^", "f")));
        assertThrows(InvalidRequestException.class, () -> HydraCommandBuilder.build("hydra",
                config("host", 80, "http", "", "/wl", "/", "x=^USER^&y=^PASS^", "f")));
        assertThrows(InvalidRequestException.class, () -> HydraCommandBuilder.build("hydra",
                config("host", 80, "http", "u", "", "/", "x=^USER^&y=^PASS^", "f")));
    }

    @Test
    void rejectsNullConfig() {
        assertThrows(InvalidRequestException.class, () -> HydraCommandBuilder.build("hydra", null));
    }

    // ---- WSL invocation prefix -----------------------------------------

    @Test
    void wslInvocationPrefixIsPrependedBeforeHydraArgs() {
        List<String> argv = HydraCommandBuilder.build(
                List.of("wsl.exe", "-d", "kali-linux", "--exec", "/usr/bin/hydra"), validConfig());

        assertEquals(List.of(
                "wsl.exe", "-d", "kali-linux", "--exec", "/usr/bin/hydra",
                "-l", "admin",
                "-P", "/path/to/wordlist",
                "-s", "80",
                "10.82.166.33",
                "http-post-form",
                "/:username=^USER^&password=^PASS^:F=incorrect",
                "-V"
        ), argv);
    }

    @Test
    void wslWordlistPathWithSpacesStaysAsSingleArgvElement() {
        HydraAttackConfigDto cfg = config("host", 80, "http", "admin",
                "/mnt/c/Users/Yajun Wu/Downloads/passwords.txt", "/", "user=^USER^&pass=^PASS^", "bad");
        List<String> argv = HydraCommandBuilder.build(
                List.of("wsl.exe", "-d", "kali-linux", "--exec", "/usr/bin/hydra"), cfg);
        assertEquals("/mnt/c/Users/Yajun Wu/Downloads/passwords.txt", argv.get(argv.indexOf("-P") + 1));
    }

    @Test
    void rejectsEmptyInvocationPrefix() {
        assertThrows(InvalidRequestException.class,
                () -> HydraCommandBuilder.build(List.<String>of(), validConfig()));
        assertThrows(InvalidRequestException.class,
                () -> HydraCommandBuilder.build((List<String>) null, validConfig()));
    }
}
