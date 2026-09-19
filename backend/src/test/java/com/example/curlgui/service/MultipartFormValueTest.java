package com.example.curlgui.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Pure tests for curl's {@code -F} micro-syntax value building. */
class MultipartFormValueTest {

    // ---- forExecFile: unquoted, for direct ProcessBuilder execution ---

    @Test
    void execFilePlainNoContentType() {
        assertEquals("image=@photo.jpg", MultipartFormValue.forExecFile("image", "photo.jpg", null));
    }

    @Test
    void execFileWithContentType() {
        assertEquals("image=@photo.jpg;type=image/jpeg",
                MultipartFormValue.forExecFile("image", "photo.jpg", "image/jpeg"));
    }

    @Test
    void execFileWindowsPathIsPassedThroughUntouched() {
        // No escaping at all: ProcessBuilder already delivers this argv entry
        // to curl.exe intact, backslashes and spaces included. Escaping it
        // ourselves is what caused the real corruption bug this design fixes.
        assertEquals("image=@C:\\Users\\Alex Smith\\photo.jpg",
                MultipartFormValue.forExecFile("image", "C:\\Users\\Alex Smith\\photo.jpg", null));
    }

    @Test
    void execFileBlankContentTypeIsOmitted() {
        assertEquals("f=@a.jpg", MultipartFormValue.forExecFile("f", "a.jpg", "  "));
    }

    // ---- forGeneratedFile: curl-quoted, for "Copy as cURL" shell text -

    @Test
    void generatedFilePlainNoContentType() {
        assertEquals("image=@\"photo.jpg\"", MultipartFormValue.forGeneratedFile("image", "photo.jpg", null));
    }

    @Test
    void generatedFileWithContentType() {
        assertEquals("image=@\"photo.jpg\";type=image/jpeg",
                MultipartFormValue.forGeneratedFile("image", "photo.jpg", "image/jpeg"));
    }

    @Test
    void generatedFileWindowsPathBackslashesAreDoubled() {
        // Safe here (unlike forExecFile): a real POSIX shell later hands curl
        // this quoted text unchanged, and curl's own quoted-@filename escaping
        // unescapes \\ -> \ and \" -> ", so doubling round-trips correctly.
        String value = MultipartFormValue.forGeneratedFile("image", "C:\\Users\\Alex\\photo.jpg", null);
        assertEquals("image=@\"C:\\\\Users\\\\Alex\\\\photo.jpg\"", value);
    }

    @Test
    void generatedFilePathWithSemicolonIsProtectedByQuoting() {
        String value = MultipartFormValue.forGeneratedFile("f", "a;b.jpg", "text/plain");
        assertEquals("f=@\"a;b.jpg\";type=text/plain", value);
    }

    @Test
    void generatedFilePathContainingADoubleQuoteIsEscaped() {
        String value = MultipartFormValue.forGeneratedFile("f", "weird\"name.jpg", null);
        assertEquals("f=@\"weird\\\"name.jpg\"", value);
    }

    @Test
    void generatedFileBlankContentTypeIsOmitted() {
        assertEquals("f=@\"a.jpg\"", MultipartFormValue.forGeneratedFile("f", "a.jpg", "  "));
    }

    // ---- forText: shared by both call sites ----------------------------

    @Test
    void textFieldIsJustNameEqualsValue() {
        assertEquals("albumId=123", MultipartFormValue.forText("albumId", "123"));
    }

    @Test
    void textFieldNullValueBecomesEmptyString() {
        assertEquals("albumId=", MultipartFormValue.forText("albumId", null));
    }
}
