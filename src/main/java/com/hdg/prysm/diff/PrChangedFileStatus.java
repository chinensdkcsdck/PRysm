package com.hdg.prysm.diff;

import java.util.Locale;

/**
 * GitHub Pull Request changed files API 返回的文件变更状态。
 */
public enum PrChangedFileStatus {

    ADDED("added"),
    REMOVED("removed"),
    MODIFIED("modified"),
    RENAMED("renamed");

    private final String githubValue;

    /**
     * 保存 GitHub API 中对应的原始字符串值。
     */
    PrChangedFileStatus(String githubValue) {
        this.githubValue = githubValue;
    }

    /**
     * 将 GitHub API 返回的状态字符串转换为内部枚举。
     */
    public static PrChangedFileStatus fromGithubValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Changed file status must not be blank");
        }

        String normalizedValue = value.trim().toLowerCase(Locale.ROOT);
        for (PrChangedFileStatus status : values()) {
            if (status.githubValue.equals(normalizedValue)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unsupported changed file status: " + value);
    }
}
