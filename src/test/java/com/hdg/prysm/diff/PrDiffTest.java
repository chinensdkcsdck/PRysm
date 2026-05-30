package com.hdg.prysm.diff;

import com.hdg.prysm.context.PrContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PrDiffTest {

    @Test
    void shouldCreatePrDiffWithDefensiveChangedFilesCopy() {
        PrContext context = new PrContext("chinensdkcsdck", "PRysm", 4);
        PrChangedFile firstFile = new PrChangedFile("src/App.java", PrChangedFileStatus.MODIFIED, 5, 2, "patch");
        PrChangedFile secondFile = new PrChangedFile("README.md", PrChangedFileStatus.ADDED, 3, 0, "patch");
        List<PrChangedFile> changedFiles = new ArrayList<>(List.of(firstFile, secondFile));

        PrDiff diff = new PrDiff(context, changedFiles);
        changedFiles.clear();

        assertSame(context, diff.getContext());
        assertEquals(2, diff.getFileCount());
        assertEquals(8, diff.getTotalAdditions());
        assertEquals(2, diff.getTotalDeletions());
    }

    @Test
    void shouldExposeImmutableChangedFiles() {
        PrContext context = new PrContext("chinensdkcsdck", "PRysm", 4);
        PrChangedFile file = new PrChangedFile("README.md", PrChangedFileStatus.ADDED, 1, 0, null);
        PrDiff diff = new PrDiff(context, List.of(file));

        assertThrows(UnsupportedOperationException.class, () -> diff.getChangedFiles().clear());
    }

    @Test
    void shouldRejectInvalidPrDiffValues() {
        PrContext context = new PrContext("chinensdkcsdck", "PRysm", 4);

        assertThrows(IllegalArgumentException.class, () -> new PrDiff(null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new PrDiff(context, null));
        List<PrChangedFile> changedFilesWithNull = new ArrayList<>();
        changedFilesWithNull.add(null);
        assertThrows(IllegalArgumentException.class, () -> new PrDiff(context, changedFilesWithNull));
    }
}
