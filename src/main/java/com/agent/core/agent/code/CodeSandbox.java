package com.agent.core.agent.code;

import com.agent.common.exception.AgentException;
import com.agent.common.utils.JsonUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A safe execution sandbox that combines a {@link PythonExecutor} with a
 * {@link Py4jGateway} to provide an isolated environment for running
 * LLM-generated Python code.
 *
 * <h3>Architecture</h3>
 * <pre>
 *   +-----------------+         +------------------+
 *   | PythonExecutor  | &lt;----&gt; |   Py4jGateway    |
 *   | (child process) |  TCP   | (toolkit server) |
 *   +-----------------+         +------------------+
 *           |                           |
 *           v                           v
 *   +-----------------------------------------------+
 *   |               CodeSandbox                      |
 *   | - lifecycle management                         |
 *   | - variable injection / extraction              |
 *   | - error isolation                              |
 *   +-----------------------------------------------+
 * </pre>
 *
 * <h3>Error isolation</h3>
 * <ul>
 *   <li>All Python exceptions are caught and surfaced via
 *       {@link CodeExecutionResult#getError()}, never propagated to the JVM.</li>
 *   <li>Imports of dangerous modules ({@code os.system}, {@code subprocess},
 *       {@code shutil.rmtree}, etc.) are stripped from the code before
 *       execution.</li>
 *   <li>The sandbox enforces an execution timeout (delegated to
 *       {@link PythonExecutor}).</li>
 * </ul>
 *
 * <p>Each sandbox has a unique {@link #sandboxId} and maintains a map of
 * {@link #globalVariables} that persist across successive executions within
 * the same sandbox instance.</p>
 *
 * @author agent-server
 * @see PythonExecutor
 * @see Py4jGateway
 * @see CodeExecutionResult
 */
@Slf4j
public class CodeSandbox {

    // ------------------------------------------------------------------
    // Constants
    // ------------------------------------------------------------------

    /** Default Py4j gateway port (ephemeral). */
    private static final int DEFAULT_GATEWAY_PORT = 0;

    /** Dangerous patterns that are stripped from submitted code. */
    private static final String[] BLOCKED_PATTERNS = {
            "os.system(",
            "subprocess.",
            "shutil.rmtree(",
            "__import__('os')",
            "__import__('subprocess')",
            "eval(",
            "exec(",
            "open('/etc",
            "open('/proc",
            "open('/sys"
    };

    // ------------------------------------------------------------------
    // Fields
    // ------------------------------------------------------------------

    /** The Python process executor. */
    private final PythonExecutor executor;

    /** The Py4j callback gateway. */
    private final Py4jGateway gateway;

    /** Unique identifier for this sandbox instance. */
    @Getter
    private final String sandboxId;

    /**
     * Global variables that persist across executions within this sandbox.
     * Values are injected into the Python namespace before each run and
     * updated with any new variables exported by the script.
     */
    private final Map<String, Object> globalVariables = new ConcurrentHashMap<>();

    // ------------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------------

    /**
     * Create a sandbox with default settings and a random ID.
     */
    public CodeSandbox() {
        this(UUID.randomUUID().toString());
    }

    /**
     * Create a sandbox with the given identifier.
     *
     * @param sandboxId unique sandbox ID
     */
    public CodeSandbox(String sandboxId) {
        this.sandboxId = sandboxId;
        this.gateway = new Py4jGateway();
        // Port is resolved after gateway.start()
        this.executor = new PythonExecutor(DEFAULT_GATEWAY_PORT);
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    /**
     * Initialize the sandbox by starting both the Py4j gateway and the
     * Python process.
     *
     * <p>Must be called before any {@link #execute} invocation.</p>
     *
     * @throws AgentException if either component fails to start
     */
    public void initialize() {
        log.info("Initializing CodeSandbox [{}]", sandboxId);

        // 1. Start the gateway on an ephemeral port
        gateway.start(DEFAULT_GATEWAY_PORT);

        // 2. Start the Python process, pointing it at the gateway
        PythonExecutor resolvedExecutor = new PythonExecutor(gateway.getPort());
        resolvedExecutor.start();

        // Swap the internal reference (the field is final for the initial
        // placeholder; we store the resolved one in a mutable holder below).
        executorHolder = resolvedExecutor;

        log.info("CodeSandbox [{}] ready (gateway port={})", sandboxId, gateway.getPort());
    }

    /**
     * Mutable holder for the executor whose gateway port is only known after
     * {@link #initialize()}.
     */
    private PythonExecutor executorHolder;

    /**
     * Execute Python code inside the sandbox.
     *
     * <p>Before execution the code is sanitized via {@link #sanitize(String)}.
     * After execution, tool-call records from the gateway are attached to the
     * result.</p>
     *
     * @param code raw Python source code
     * @return execution result
     */
    public CodeExecutionResult execute(String code) {
        return execute(code, Collections.emptyMap());
    }

    /**
     * Execute Python code inside the sandbox with pre-set variables injected
     * into the Python namespace.
     *
     * @param code      raw Python source code
     * @param variables variables to inject (merged with {@link #globalVariables})
     * @return execution result
     */
    public CodeExecutionResult execute(String code, Map<String, Object> variables) {
        ensureReady();

        // Merge caller-supplied variables with globals
        Map<String, Object> effectiveVars = new ConcurrentHashMap<>(globalVariables);
        if (variables != null && !variables.isEmpty()) {
            effectiveVars.putAll(variables);
        }

        // Sanitize the code
        String safeCode = sanitize(code);

        // Prepend variable injection preamble
        String fullCode = buildInjectionPreamble(effectiveVars) + safeCode;

        // Clear previous call records
        gateway.clearCallRecords();

        // Execute
        CodeExecutionResult result = getActiveExecutor().executeCode(fullCode);

        // Attach tool-call records
        result.setToolCalls(gateway.getCallRecords());

        // Update global variables with any exported __result__
        if (result.isSuccess() && result.getVariables() != null) {
            globalVariables.putAll(result.getVariables());
        }

        return result;
    }

    /**
     * Shut down the sandbox, stopping the Python process and the gateway.
     *
     * <p>After this method returns the sandbox cannot be reused; create a
     * new instance if further execution is needed.</p>
     */
    public void shutdown() {
        log.info("Shutting down CodeSandbox [{}]", sandboxId);

        try {
            PythonExecutor active = getActiveExecutorOrNull();
            if (active != null) {
                active.stop();
            }
        } catch (Exception e) {
            log.warn("Error stopping PythonExecutor in sandbox [{}]", sandboxId, e);
        }

        try {
            gateway.stop();
        } catch (Exception e) {
            log.warn("Error stopping Py4jGateway in sandbox [{}]", sandboxId, e);
        }

        globalVariables.clear();
        log.info("CodeSandbox [{}] shut down", sandboxId);
    }

    /**
     * Check whether the sandbox is initialized and both the Python process and
     * the gateway are healthy.
     *
     * @return {@code true} if the sandbox is ready to accept code
     */
    public boolean isReady() {
        PythonExecutor active = getActiveExecutorOrNull();
        return active != null && active.isRunning() && gateway.isRunning();
    }

    // ------------------------------------------------------------------
    // Handler registration (delegate)
    // ------------------------------------------------------------------

    /**
     * Register a toolkit handler on the underlying gateway.
     *
     * @param toolkitName handler name
     * @param handler     handler implementation
     * @see Py4jGateway#registerHandler(String, Py4jGateway.ToolkitHandler)
     */
    public void registerHandler(String toolkitName, Py4jGateway.ToolkitHandler handler) {
        gateway.registerHandler(toolkitName, handler);
    }

    // ------------------------------------------------------------------
    // Global variable access
    // ------------------------------------------------------------------

    /**
     * Return an unmodifiable view of the current global variables.
     *
     * @return global variable map
     */
    public Map<String, Object> getGlobalVariables() {
        return Collections.unmodifiableMap(globalVariables);
    }

    /**
     * Set a global variable that will be available to all subsequent executions.
     *
     * @param key   variable name
     * @param value variable value (must be JSON-serializable)
     */
    public void setGlobalVariable(String key, Object value) {
        globalVariables.put(key, value);
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /**
     * Ensure the sandbox has been initialized.
     *
     * @throws AgentException if not initialized
     */
    private void ensureReady() {
        if (!isReady()) {
            throw new AgentException("SANDBOX_NOT_READY",
                    "CodeSandbox [" + sandboxId + "] is not initialized. Call initialize() first.");
        }
    }

    /**
     * Return the active executor, preferring the one created during
     * {@link #initialize()}.
     */
    private PythonExecutor getActiveExecutor() {
        return executorHolder != null ? executorHolder : executor;
    }

    /**
     * Null-safe variant of {@link #getActiveExecutor()}.
     */
    private PythonExecutor getActiveExecutorOrNull() {
        return executorHolder != null ? executorHolder : (executor.isRunning() ? executor : null);
    }

    /**
     * Strip dangerous patterns from the submitted code.
     *
     * <p>This is a best-effort safeguard, <strong>not</strong> a security
     * boundary.  For production use the Python process should run inside a
     * true container sandbox (e.g., gVisor, nsjail, or Docker with a
     * restricted seccomp profile).</p>
     *
     * @param code raw Python code
     * @return sanitized code
     */
    private String sanitize(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        String sanitized = code;
        for (String pattern : BLOCKED_PATTERNS) {
            if (sanitized.contains(pattern)) {
                log.warn("Blocked dangerous pattern in sandbox [{}]: {}", sandboxId, pattern);
                sanitized = sanitized.replace(pattern,
                        "# BLOCKED: " + pattern);
            }
        }
        return sanitized;
    }

    /**
     * Build a Python preamble that injects the supplied variables into the
     * execution namespace via {@code json.loads}.
     *
     * @param variables variables to inject
     * @return Python source fragment (may be empty if no variables)
     */
    private String buildInjectionPreamble(Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return "";
        }

        String varsJson = JsonUtils.toJson(variables)
                .replace("\\", "\\\\")
                .replace("'", "\\'");

        return "import json as __json__\n"
                + "__injected_vars__ = __json__.loads('" + varsJson + "')\n"
                + "globals().update(__injected_vars__)\n"
                + "del __injected_vars__, __json__\n\n";
    }
}
