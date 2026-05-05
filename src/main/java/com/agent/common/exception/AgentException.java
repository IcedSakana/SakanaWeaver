package com.agent.common.exception;

/**
 * Custom exception for Agent system errors.
 */
public class AgentException extends RuntimeException {

    private final String errorCode;

    public AgentException(String message) {
        super(message);
        this.errorCode = "AGENT_ERROR";
    }

    public AgentException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public AgentException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
