package com.hdg.prysm.execution;

/**
 * 描述当前上下文是否足够支撑后续审查。
 */
public class ContextStatus implements java.io.Serializable {

    private final ContextStatusCode code;
    private final String reason;

    public ContextStatus(ContextStatusCode code, String reason) {
        if (code == null) {
            throw new IllegalArgumentException("Context status code must not be null");
        }

        this.code = code;
        this.reason = reason;
    }

    public ContextStatusCode getCode() {
        return code;
    }

    public String getReason() {
        return reason;
    }

    public boolean canRunLlmReview() {
        return code == ContextStatusCode.FULL || code == ContextStatusCode.LIMITED;
    }
}
