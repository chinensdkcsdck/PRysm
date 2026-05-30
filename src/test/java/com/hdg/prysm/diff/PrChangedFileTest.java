package com.hdg.prysm.diff;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PrChangedFileTest {

    @Test
    void shouldCreateChangedFileWithDiffMetadata() {
        PrChangedFile file = new PrChangedFile(
                "src/main/java/App.java",
                PrChangedFileStatus.MODIFIED,
                10,
                3,
                "@@ -1 +1 @@"
        );

        assertEquals("src/main/java/App.java", file.getFilename());
        assertEquals(PrChangedFileStatus.MODIFIED, file.getStatus());
        assertEquals(10, file.getAdditions());
        assertEquals(3, file.getDeletions());
        assertEquals("@@ -1 +1 @@", file.getPatch());
    }

    @Test
    void shouldAllowMissingPatch() {
        PrChangedFile file = new PrChangedFile(
                "README.md",
                PrChangedFileStatus.ADDED,
                4,
                0,
                null
        );

        assertNull(file.getPatch());
    }

    @Test
    void shouldRejectInvalidChangedFileMetadata() {
        assertThrows(IllegalArgumentException.class, () -> new PrChangedFile(" ", PrChangedFileStatus.MODIFIED, 1, 0, null));
        assertThrows(IllegalArgumentException.class, () -> new PrChangedFile("README.md", null, 1, 0, null));
        assertThrows(IllegalArgumentException.class, () -> new PrChangedFile("README.md", PrChangedFileStatus.MODIFIED, -1, 0, null));
        assertThrows(IllegalArgumentException.class, () -> new PrChangedFile("README.md", PrChangedFileStatus.MODIFIED, 1, -1, null));
    }
}
