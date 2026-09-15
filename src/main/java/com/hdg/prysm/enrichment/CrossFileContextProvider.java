package com.hdg.prysm.enrichment;

import com.hdg.prysm.execution.ReviewExecutionInput;

/**
 * Supplies bounded repository context related to symbols changed by the pull request.
 */
public interface CrossFileContextProvider {

    CrossFileContext build(ReviewExecutionInput input);
}
