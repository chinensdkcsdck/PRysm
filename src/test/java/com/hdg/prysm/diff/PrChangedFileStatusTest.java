package com.hdg.prysm.diff;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PrChangedFileStatusTest {

    @Test
    void shouldParseGithubStatusValue() {
        assertEquals(PrChangedFileStatus.MODIFIED, PrChangedFileStatus.fromGithubValue("modified"));
        assertEquals(PrChangedFileStatus.RENAMED, PrChangedFileStatus.fromGithubValue(" RENAMED "));
    }

    @Test
    void shouldRejectUnsupportedGithubStatusValue() {
        assertThrows(IllegalArgumentException.class, () -> PrChangedFileStatus.fromGithubValue("unknown"));
    }
}
