package com.hdg.prysm.mcp;

public class McpToolException extends Exception {

    private final String errorCode;
    private final boolean retryable;

    private McpToolException(String errorCode, String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    public static McpToolException transientFailure(String errorCode, String message, Throwable cause) {
        return new McpToolException(errorCode, message, true, cause);
    }

    public static McpToolException permanentFailure(String errorCode, String message) {
        return new McpToolException(errorCode, message, false, null);
    }

    public String errorCode() {
        return errorCode;
    }

    public boolean retryable() {
        return retryable;
    }
}
