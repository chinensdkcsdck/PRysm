package com.hdg.prysm.enrichment;

/**
 * Bounded cross-file context rendered into the model prompt.
 */
public record CrossFileContext(String promptFragment, int symbolCount, int snippetCount, boolean truncated) {

    public CrossFileContext {
        if (promptFragment == null) {
            throw new IllegalArgumentException("Cross-file prompt fragment must not be null");
        }
        if (symbolCount < 0 || snippetCount < 0) {
            throw new IllegalArgumentException("Cross-file context counts must not be negative");
        }
    }

    public static CrossFileContext empty() {
        return new CrossFileContext("", 0, 0, false);
    }

    public boolean hasContent() {
        return !promptFragment.isBlank();
    }
}
