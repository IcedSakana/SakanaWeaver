package com.agent.core.agent.code;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Result model for Python code execution within the Code-Driven Execution Engine.
 *
 * <p>Captures the full lifecycle of a single code execution, including standard output,
 * error output, wall-clock duration, any variables the Python script explicitly exported,
 * and a ledger of all toolkit (tool) invocations that were dispatched through the
 * Py4j gateway during the run.</p>
 *
 * <p>Typical usage:</p>
 * <pre>{@code
 * CodeExecutionResult result = sandbox.execute(pythonCode);
 * if (result.isSuccess()) {
 *     String output = result.getOutput();
 *     Map<String, Object> vars = result.getVariables();
 * } else {
 *     log.error("Execution failed: {}", result.getError());
 * }
 * }</pre>
 *
 * @author agent-server
 * @see CodeSandbox
 * @see PythonExecutor
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeExecutionResult {

    /**
     * Whether the code executed successfully without unhandled exceptions.
     */
    private boolean success;

    /**
     * Captured standard output (stdout) from the Python process.
     */
    private String output;

    /**
     * Captured error output (stderr) from the Python process,
     * or the exception message if execution failed.
     */
    private String error;

    /**
     * Wall-clock execution time in milliseconds.
     */
    private long executionTimeMs;

    /**
     * Variables explicitly returned / exported from the Python execution context.
     * The Python script can populate this map via the {@code __result__} convention
     * so that the Java caller can inspect computed values.
     */
    @Builder.Default
    private Map<String, Object> variables = new HashMap<>();

    /**
     * Ordered list of tool invocations that occurred during execution.
     * Each entry records the toolkit method called, its arguments, the result
     * returned to Python, and whether the invocation itself succeeded.
     */
    @Builder.Default
    private List<ToolCallRecord> toolCalls = new ArrayList<>();

    // ------------------------------------------------------------------
    // Inner class
    // ------------------------------------------------------------------

    /**
     * Immutable record of a single tool invocation dispatched through the
     * {@link Py4jGateway} during Python code execution.
     *
     * <p>Tool calls are captured in order so that callers can audit exactly
     * which Java-side toolkit handlers were exercised and what data flowed
     * between Python and Java.</p>
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCallRecord {

        /**
         * Fully-qualified tool name in the form {@code toolkitName.methodName}.
         */
        private String toolName;

        /**
         * Arguments passed from Python to the Java toolkit handler.
         */
        @Builder.Default
        private Map<String, Object> arguments = new HashMap<>();

        /**
         * The result object returned by the Java toolkit handler back to Python.
         * May be {@code null} if the handler returned void.
         */
        private Object result;

        /**
         * Whether the tool invocation completed without throwing an exception.
         */
        private boolean success;
    }
}
