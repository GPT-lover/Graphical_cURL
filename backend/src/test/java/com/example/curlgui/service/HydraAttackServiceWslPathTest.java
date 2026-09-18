package com.example.curlgui.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pure tests for the "does this wordlist path look like a Windows path (and
 * therefore need converting for WSL), or is it already a WSL/Linux path"
 * heuristic. No process is spawned - {@link HydraAttackService#looksLikeWindowsPath}
 * is a plain string check.
 */
class HydraAttackServiceWslPathTest {

    @Test
    void windowsAbsolutePathsAreDetected() {
        assertTrue(HydraAttackService.looksLikeWindowsPath("C:\\Users\\Yajun Wu\\Downloads\\passwords.txt"));
        assertTrue(HydraAttackService.looksLikeWindowsPath("C:/Users/Yajun Wu/Downloads/passwords.txt"));
        assertTrue(HydraAttackService.looksLikeWindowsPath("D:\\wordlists\\rockyou.txt"));
        assertTrue(HydraAttackService.looksLikeWindowsPath("d:/wordlists/rockyou.txt")); // lower-case drive letter
    }

    @Test
    void wslAndLinuxPathsAreNotConverted() {
        assertFalse(HydraAttackService.looksLikeWindowsPath("/usr/share/wordlists/rockyou.txt"));
        assertFalse(HydraAttackService.looksLikeWindowsPath("/mnt/c/Users/Yajun Wu/Downloads/passwords.txt"));
        assertFalse(HydraAttackService.looksLikeWindowsPath("/home/kali/wordlist.txt"));
    }

    @Test
    void nullIsNotAWindowsPath() {
        assertFalse(HydraAttackService.looksLikeWindowsPath(null));
    }
}
