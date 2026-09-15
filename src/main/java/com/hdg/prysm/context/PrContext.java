package com.hdg.prysm.context;

/**
 * 当前 GitHub Pull Request 的运行上下文。
 *
 * 只保存定位 PR 所需的非敏感信息，不持有 token 或 API key。
 */
public class PrContext {

    private final String owner;
    private final String repository;
    private final int pullRequestNumber;
    private final String targetRevision;

    /**
     * 创建一个已校验的 PR 上下文。
     */
    public PrContext(String owner, String repository, int pullRequestNumber) {
        this(owner, repository, pullRequestNumber, null);
    }

    /**
     * Creates a PR context tied to the target-side revision from the triggering event.
     */
    public PrContext(String owner, String repository, int pullRequestNumber, String targetRevision) {
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("Repository owner must not be blank");
        }
        if (repository == null || repository.isBlank()) {
            throw new IllegalArgumentException("Repository name must not be blank");
        }
        if (pullRequestNumber <= 0) {
            throw new IllegalArgumentException("Pull request number must be positive");
        }
        this.owner = owner;
        this.repository = repository;
        this.pullRequestNumber = pullRequestNumber;
        this.targetRevision = normalizeRevision(targetRevision);
    }

    /**
     * 返回仓库所属账号或组织。
     */
    public String getOwner() {
        return owner;
    }

    /**
     * 返回仓库名称。
     */
    public String getRepository() {
        return repository;
    }

    /**
     * 返回 Pull Request 编号。
     */
    public int getPullRequestNumber() {
        return pullRequestNumber;
    }

    /**
     * Returns the head revision reviewed by this task, or null for legacy/programmatic callers.
     */
    public String getTargetRevision() {
        return targetRevision;
    }

    /**
     * 返回 GitHub API 使用的 owner/repository 仓库名。
     */
    public String fullRepositoryName() {
        return owner + "/" + repository;
    }

    private static String normalizeRevision(String revision) {
        if (revision == null || revision.isBlank()) {
            return null;
        }
        String normalized = revision.trim().toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{40,64}")) {
            throw new IllegalArgumentException("Target revision must be a Git object id");
        }
        return normalized;
    }
}
