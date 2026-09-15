package com.hdg.prysm.diff;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UnifiedDiffParserTest {

    @Test
    void shouldMapOnlyAddedLinesToTargetFilePositions() {
        List<UnifiedDiffParser.AddedLine> lines = UnifiedDiffParser.addedLines("""
                @@ -10,3 +20,4 @@
                 context
                -removed
                +firstAdded
                \\ No newline at end of file
                +secondAdded
                 tail
                """);

        assertEquals(2, lines.size());
        assertEquals(21, lines.get(0).lineNumber());
        assertEquals("firstAdded", lines.get(0).content());
        assertEquals(22, lines.get(1).lineNumber());
    }
}
