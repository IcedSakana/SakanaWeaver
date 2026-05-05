package com.agent.core.agent.code;

import com.agent.common.exception.AgentException;
import com.agent.common.utils.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Java-side Py4j-style gateway server that exposes registered Java toolkit handlers
 * to a running Python process over a local TCP socket.
 *
 * <h3>Design overview</h3>
 * <ol>
 *   <li>The gateway opens a {@link ServerSocket} on a configurable port.</li>
 *   <li>Python connects and sends JSON-encoded invocation requests.</li>
 *   <li>Each request is dispatched to the matching {@link ToolkitHandler}.</li>
 *   <li>The result (or error) is serialized back to Python as JSON.</li>
 * </ol>
 *
 * <h3>Wire protocol (newline-delimited JSON)</h3>
 * <pre>
 * Request  &rarr; {"toolkit":"httpClient","method":"get","args":{"url":"..."}}
 * Response &larr; {"success":true,"result":{...}}
 *          or  {"success":false,"error":"..."}
 * </pre>
 *
 * <p>Thread-safety: handler registration is backed by a {@link ConcurrentHashMap}
 * and the server accepts connections on a dedicated daemon thread.</p>
 *
 * @author agent-server
 * @see PythonExecutor
 * @see CodeSandbox
 */
@Slf4j
public class Py4jGateway {

    /**
     * Registered toolkit handlers keyed by toolkit name.
     */
    private final Map<String, ToolkitHandler> handlers = new ConcurrentHashMap<>();

    /**
     * Ordered log of every tool invocation dispatched during the gateway's lifetime.
     * Cleared on {@link #stop()}.
     */
    private final List<CodeExecutionResult.ToolCallRecord> callRecords = new CopyOnWriteArrayList<>();

    private ServerSocket serverSocket;
    private ExecutorService acceptorPool;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private int port;

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    /**
     * Start the gateway server on the specified port.
     *
     * <p>The method is idempotent; calling it while the gateway is already
     * running has no effect.</p>
     *
     * @param port TCP port to listen on (use {@code 0} for an ephemeral port)
     * @throws AgentException if the server socket cannot be opened
     */
    public void start(int port) {
        if (running.get()) {
            log.warn("Py4jGateway is already running on port {}", this.port);
            return;
        }

        try {
            this.serverSocket = new ServerSocket(port);
            this.port = serverSocket.getLocalPort(); // resolve ephemeral port
            this.acceptorPool = Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "py4j-gateway-" + this.port);
                t.setDaemon(true);
                return t;
            });
            running.set(true);
            acceptorPool.submit(this::acceptLoop);
            log.info("Py4jGateway started on port {}", this.port);
        } catch (IOException e) {
            throw new AgentException("PY4J_START_FAILED",
                    "Failed to start Py4j gateway on port " + port, e);
        }
    }

    /**
     * Stop the gateway server and release all resources.
     *
     * <p>All pending client connections are terminated and the call-record
     * ledger is cleared.</p>
     */
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            log.warn("Error closing Py4j gateway server socket", e);
        }

        if (acceptorPool != null) {
            acceptorPool.shutdownNow();
            try {
                if (!acceptorPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("Py4jGateway acceptor pool did not terminate in time");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        callRecords.clear();
        log.info("Py4jGateway stopped (port {})", port);
    }

    // ------------------------------------------------------------------
    // Handler registration
    // ------------------------------------------------------------------

    /**
     * Register a toolkit handler under the given name.
     *
     * <p>If a handler with the same name was previously registered it is
     * silently replaced.</p>
     *
     * @param toolkitName unique name used by Python to address this toolkit
     * @param handler     the handler implementation
     */
    public void registerHandler(String toolkitName, ToolkitHandler handler) {
        handlers.put(toolkitName, handler);
        log.debug("Registered toolkit handler: {}", toolkitName);
    }

    // ------------------------------------------------------------------
    // Invocation entry-point (may also be called programmatically)
    // ------------------------------------------------------------------

    /**
     * Dispatch a method invocation to the named toolkit handler.
     *
     * <p>This is the primary entry-point called (indirectly) from Python.
     * Arguments arrive as a JSON string and are deserialized into a
     * {@code Map<String, Object>} before being forwarded to the handler.</p>
     *
     * @param toolkitName the registered toolkit name
     * @param methodName  the method to invoke on the toolkit
     * @param argsJson    JSON-encoded argument map (may be {@code null} or empty)
     * @return JSON-encoded result string
     * @throws AgentException if the toolkit is not registered
     */
    public String invokeMethod(String toolkitName, String methodName, String argsJson) {
        ToolkitHandler handler = handlers.get(toolkitName);
        if (handler == null) {
            String errorMsg = "No toolkit handler registered for: " + toolkitName;
            log.error(errorMsg);
            recordCall(toolkitName + "." + methodName, Collections.emptyMap(), null, false);
            throw new AgentException("TOOLKIT_NOT_FOUND", errorMsg);
        }

        Map<String, Object> args = parseArgs(argsJson);

        try {
            Object result = handler.invoke(methodName, args);
            recordCall(toolkitName + "." + methodName, args, result, true);
            log.debug("Toolkit invocation succeeded: {}.{}", toolkitName, methodName);
            return JsonUtils.toJson(Map.of("success", true, "result", result != null ? result : ""));
        } catch (Exception e) {
            log.error("Toolkit invocation failed: {}.{}", toolkitName, methodName, e);
            recordCall(toolkitName + "." + methodName, args, e.getMessage(), false);
            return JsonUtils.toJson(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ------------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------------

    /**
     * Return an unmodifiable snapshot of all tool call records collected so far.
     *
     * @return list of tool call records
     */
    public List<CodeExecutionResult.ToolCallRecord> getCallRecords() {
        return List.copyOf(callRecords);
    }

    /**
     * Clear the accumulated call records (e.g. between successive executions).
     */
    public void clearCallRecords() {
        callRecords.clear();
    }

    /**
     * Return the actual port the gateway is listening on.
     *
     * @return TCP port number
     */
    public int getPort() {
        return port;
    }

    /**
     * Check whether the gateway is currently running.
     *
     * @return {@code true} if the server socket is open and accepting connections
     */
    public boolean isRunning() {
        return running.get();
    }

    // ------------------------------------------------------------------
    // Inner interface
    // ------------------------------------------------------------------

    /**
     * Handler interface that toolkit implementations must satisfy.
     *
     * <p>A single handler instance is registered per toolkit name.  The handler
     * is responsible for routing the {@code methodName} to the correct
     * business-logic implementation.</p>
     */
    public interface ToolkitHandler {

        /**
         * Invoke a named method with the supplied argument map.
         *
         * @param methodName method identifier
         * @param args       deserialized argument map (never {@code null})
         * @return result object; will be serialized to JSON for the Python caller
         */
        Object invoke(String methodName, Map<String, Object> args);
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /**
     * Background loop that accepts incoming Python connections and handles
     * each one on a pooled thread.
     */
    private void acceptLoop() {
        log.debug("Py4jGateway accept loop started");
        while (running.get()) {
            try {
                Socket clientSocket = serverSocket.accept();
                acceptorPool.submit(() -> handleClient(clientSocket));
            } catch (IOException e) {
                if (running.get()) {
                    log.error("Error accepting Py4j client connection", e);
                }
                // If not running, the socket was closed intentionally -- exit quietly.
            }
        }
    }

    /**
     * Handle a single client connection.  Reads newline-delimited JSON
     * invocation requests and writes back JSON responses.
     */
    private void handleClient(Socket socket) {
        try (socket;
             var reader = new java.io.BufferedReader(
                     new java.io.InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             var writer = new java.io.BufferedWriter(
                     new java.io.OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                String response = processRequest(line);
                writer.write(response);
                writer.newLine();
                writer.flush();
            }
        } catch (IOException e) {
            if (running.get()) {
                log.warn("Error handling Py4j client connection", e);
            }
        }
    }

    /**
     * Parse a single JSON request and dispatch to {@link #invokeMethod}.
     */
    private String processRequest(String requestJson) {
        try {
            Map<String, Object> request = JsonUtils.fromJson(requestJson,
                    new TypeReference<Map<String, Object>>() {});

            String toolkit = (String) request.get("toolkit");
            String method = (String) request.get("method");
            Object argsObj = request.get("args");
            String argsJson = argsObj != null ? JsonUtils.toJson(argsObj) : "{}";

            return invokeMethod(toolkit, method, argsJson);
        } catch (Exception e) {
            log.error("Failed to process Py4j request: {}", requestJson, e);
            return JsonUtils.toJson(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Deserialize an argument JSON string into a {@code Map}.
     */
    private Map<String, Object> parseArgs(String argsJson) {
        if (argsJson == null || argsJson.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return JsonUtils.fromJson(argsJson, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse args JSON, treating as empty: {}", argsJson, e);
            return Collections.emptyMap();
        }
    }

    /**
     * Record a tool call for later inclusion in {@link CodeExecutionResult}.
     */
    private void recordCall(String toolName, Map<String, Object> args, Object result, boolean success) {
        callRecords.add(CodeExecutionResult.ToolCallRecord.builder()
                .toolName(toolName)
                .arguments(args)
                .result(result)
                .success(success)
                .build());
    }
}
