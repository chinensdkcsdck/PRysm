package com.hdg.prysm.github;

import com.hdg.prysm.context.PrContext;

/**
 * Confirms that review output still targets the current pull request revision.
 */
public interface PullRequestRevisionGuard {

    boolean isCurrent(PrContext context);
}
