package com.hdg.prysm.diff;

/**
 * Pull Request diff 中的单个变更文件。
 *
 * PR4 负责从 GitHub changed files 数据生成该对象，PR5 可以基于它继续补充完整文件内容。
 */
public class PrChangedFile {

    private final String filename;
    private final PrChangedFileStatus status;
    private final int additions;
    private final int deletions;
    private final String patch;

    /**
     * 创建变更文件元数据，并校验 GitHub 返回的核心字段。
     */
    public PrChangedFile(
            String filename,
            PrChangedFileStatus status,
            int additions,
            int deletions,
            String patch
    ) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Changed file filename must not be blank");
        }
        if (status == null) {
            throw new IllegalArgumentException("Changed file status must not be null");
        }
        if (additions < 0) {
            throw new IllegalArgumentException("Changed file additions must not be negative");
        }
        if (deletions < 0) {
            throw new IllegalArgumentException("Changed file deletions must not be negative");
        }

        this.filename = filename;
        this.status = status;
        this.additions = additions;
        this.deletions = deletions;
        this.patch = patch;
    }

    /**
     * 返回变更文件在仓库中的相对路径。
     */
    public String getFilename() {
        return filename;
    }

    /**
     * 返回 GitHub 标记的文件变更状态。
     */
    public PrChangedFileStatus getStatus() {
        return status;
    }

    /**
     * 返回该文件新增的行数。
     */
    public int getAdditions() {
        return additions;
    }

    /**
     * 返回该文件删除的行数。
     */
    public int getDeletions() {
        return deletions;
    }

    /**
     * 返回 GitHub changed files API 给出的 patch；二进制文件或超出限制时可能为空。
     */
    public String getPatch() {
        return patch;
    }
}
