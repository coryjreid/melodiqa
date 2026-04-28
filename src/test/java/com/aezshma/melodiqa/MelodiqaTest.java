package com.aezshma.melodiqa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

public class MelodiqaTest {

    @Test
    void resolveStopFilePath_windows_usesAppData() {
        final Path result = Melodiqa.resolveStopFilePath(
            "Windows 11", "C:\\Users\\test\\AppData\\Roaming", "/home/test");
        assertEquals(
            Paths.get("C:\\Users\\test\\AppData\\Roaming", "Aezshma", "Melodiqa", ".stop"),
            result);
    }

    @Test
    void resolveStopFilePath_linux_usesUserHome() {
        final Path result = Melodiqa.resolveStopFilePath("Linux", null, "/home/testuser");
        assertEquals(
            Paths.get("/home/testuser", ".local", "share", "Aezshma", "Melodiqa", ".stop"),
            result);
    }

    @Test
    void resolveStopFilePath_windows_nullAppData_throws() {
        assertThrows(IllegalStateException.class,
            () -> Melodiqa.resolveStopFilePath("Windows 11", null, "/home/test"));
    }

    @Test
    void resolveStopFilePath_nullOsName_throws() {
        assertThrows(IllegalStateException.class,
            () -> Melodiqa.resolveStopFilePath(null, "C:\\Users\\test\\AppData\\Roaming", "/home/test"));
    }
}
