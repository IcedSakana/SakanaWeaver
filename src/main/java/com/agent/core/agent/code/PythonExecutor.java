package com.agent.core.agent.code;

import com.agent.common.exception.AgentException;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Manages the lifecycle of a local Python process used by the Code-Driven
 * Execution Engine.
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Start a long-lived Python interpreter with a Py4j bootstrap script.</li>
 *   <li>Send arbitrary Python code to the running process for execution.</li>
 *   <li>Capture {@code stdout} / {@code stderr} and wrap them in a
 *       {@link CodeExecutionResult}.</li>
 *   <li>Enforce a configurable execution timeout (default 60 seconds).</li>
 *   <li>Gracefully or forcibly tear down the process on shutdown.</li>
 * </ul>
 *
 * <h3>Process communication</h3>
 * Code is written to a temporary {@code .py} file and the file path is sent to
 * the running interpreter via stdin.  A sentinel marker on stdout signals
 * completion so that the reader can distinguish execution output from the
 * Python REPL prompt.
 *
 * @author agent-server
 * @see CodeSandbox
 * @see Py4jGateway
 */
@Slf4j
public class PythonExecutor {

    // ------------------------------------------------------------------
    // Constants
    // ------------------------------------------------------------------

    /** Default execution timeout in seconds. */
    private static final long DEFAULT_TIMEOUT_SECONDS = 60;

    /** Sentinel printed by the wrapper script after each execution. */
    private static final String EXECUTION_COMPLETE_MARKER = "@@__EXEC_COMPLETE__@@";

    /** Sentinel printed on stderr to separate error output per execution. */
    private static final String ERROR_COMPLETE_MARKER = "@@__ERR_COMPLETE__@@";

    /** Python command to use. Can be overridden via system property {@code python.command}. */
    private static final String PYTHON_CMD =
            System.getProperty("python.command", "python3");

    // ------------------------------------------------------------------
    // Fields
    // ------------------------------------------------------------------

    /** The underlying OS process running the Python interpreter. */
    private Process pythonProcess;

    /** Whether the process is running and accepting code. */
    private volatile boolean running;

    /** Port of the companion Py4j gateway, injected into the Python environment. */
    private final int gatewayPort;

    /** Execution timeout in seconds. */
    private long timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;

    // ------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------

    /**
     * Create a new executor bound to the given Py4j gateway port.
     *
     * @param gatewayPort the TCP port on which the {@link Py4jGateway} is listening
     */
    public PythonExecutor(int gatewayPort) {
        this.gatewayPort = gatewayPort;
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    /**
     * Start the Python process with a bootstrap wrapper that:
     * <ol>
     *   <li>Connects to the Java-side Py4j gateway.</li>
     *   <li>Enters a read-eval-print loop, reading file paths from stdin.</li>
     *   <li>Executes each file and prints sentinel markers on completion.</li>
     * </ol>
     *
     * @throws AgentException if the process cannot be started
     */
    public void start() {
        if (running) {
            log.warn("PythonExecutor is already running");
            return;
        }

        try {
            Path bootstrapScript = createBootstrapScript();

            ProcessBuilder pb = new ProcessBuilder(PYTHON_CMD, "-u", bootstrapScript.toString())
                    .redirectErrorStream(false);
            pb.environment().put("PY4J_GATEWAY_PORT", String.valueOf(gatewayPort));
            pb.environment().put("PYTHONUNBUFFERED", "1");

            pythonProcess = pb.start();
            running = true;
            log.info("PythonExecutor started (pid={}, gateway port={})",
                    pythonProcess.pid(), gatewayPort);
        } catch (IOException e) {
            throw new AgentException("PYTHON_START_FAILED",
                    "Failed to start Python process", e);
        }
    }

    /**
     * Send a block of Python code to the running interpreter for execution.
     *
     * <p>The code is written to a temporary file whose path is piped to the
     * interpreter's stdin.  Stdout and stderr are captured until the sentinel
     * markers appear, or until the configured timeout is exceeded.</p>
     *
     * @param pythonCode the Python source code to execute
     * @return a {@link CodeExecutionResult} describing the outcome
     * @throws AgentException if the process is not running, or if a fatal I/O
     *                        error occurs
     */
    public CodeExecutionResult executeCode(String pythonCode) {
        if (!running || pythonProcess == null || !pythonProcess.isAlive()) {
            throw new AgentException("PYTHON_NOT_RUNNING",
                    "Python process is not running. Call start() first.");
        }

        long startTime = System.currentTimeMillis();

        try {
            // Write code to a temp file
            Path codeFile = Files.createTempFile("agent_code_", ".py");
            Files.writeString(codeFile, pythonCode, StandardCharsets.UTF_8);

            // Send the file path to the Python process
            OutputStream stdin = pythonProcess.getOutputStream();
            stdin.write((codeFile.toAbsolutePath() + "\n").getBytes(StandardCharsets.UTF_8));
            stdin.flush();

            // Read stdout until sentinel
            String stdout = readUntilMarker(
                    new BufferedReader(new InputStreamReader(
                            pythonProcess.getInputStream(), StandardCharsets.UTF_8)),
                    EXECUTION_COMPLETE_MARKER);

            // Read stderr until sentinel
            String stderr = readUntilMarker(
                    new BufferedReader(new InputStreamReader(
                            pythonProcess.getErrorStream(), StandardCharsets.UTF_8)),
                    ERROR_COMPLETE_MARKER);

            long elapsed = System.currentTimeMillis() - startTime;

            // Clean up temp file
            Files.deleteIfExists(codeFile);

            boolean success = stderr == null || stderr.isBlank();
            return CodeExecutionResult.builder()
                    .success(success)
                    .output(stdout != null ? stdout.strip() : "")
                    .error(stderr != null ? stderr.strip() : "")
                    .executionTimeMs(elapsed)
                    .build();

        } catch (IOException e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("I/O error during Python code execution", e);
            return CodeExecutionResult.builder()
                    .success(false)
                    .output("")
                    .error("I/O error: " + e.getMessage())
                    .executionTimeMs(elapsed)
                    .build();
        }
    }

    /**
     * Stop the Python process.  Attempts a graceful shutdown first; if the
     * process does not exit within 5 seconds it is forcibly destroyed.
     */
    public void stop() {
        if (!running || pythonProcess == null) {
            return;
        }

        running = false;

        try {
            // Send exit command
            OutputStream stdin = pythonProcess.getOutputStream();
            stdin.write("__EXIT__\n".getBytes(StandardCharsets.UTF_8));
            stdin.flush();
            stdin.close();
        } catch (IOException e) {
            log.warn("Error sending exit command to Python process", e);
        }

        try {
            if (!pythonProcess.waitFor(5, TimeUnit.SECONDS)) {
                log.warn("Python process did not exit gracefully; destroying forcibly");
                pythonProcess.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pythonProcess.destroyForcibly();
        }

        log.info("PythonExecutor stopped");
    }

    /**
     * Check whether the Python process is alive and accepting code.
     *
     * @return {@code true} if the process is running
     */
    public boolean isRunning() {
        return running && pythonProcess != null && pythonProcess.isAlive();
    }

    // ------------------------------------------------------------------
    // Configuration
    // ------------------------------------------------------------------

    /**
     * Set the execution timeout.
     *
     * @param timeoutSeconds maximum seconds a single code execution may take
     */
    public void setTimeoutSeconds(long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * Return the configured execution timeout.
     *
     * @return timeout in seconds
     */
    public long getTimeoutSeconds() {
        return timeoutSeconds;
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /**
     * Create the Python bootstrap script that acts as a persistent REPL
     * connected to the Py4j gateway.
     *
     * @return path to the temporary bootstrap script
     * @throws IOException if the file cannot be written
     */
    private Path createBootstrapScript() throws IOException {
        Path script = Files.createTempFile("agent_bootstrap_", ".py");

        String content = """
                import os
                import sys
                import json
                import socket
                import traceback
                
                GATEWAY_PORT = int(os.environ.get('PY4J_GATEWAY_PORT', '25333'))
                EXEC_MARKER = '@@__EXEC_COMPLETE__@@'
                ERR_MARKER = '@@__ERR_COMPLETE__@@'
                
                class JavaGateway:
                    \"\"\"Minimal client that calls back to the Java Py4jGateway.\"\"\"
                
                    def __init__(self, port):
                        self._port = port
                
                    def invoke(self, toolkit, method, **kwargs):
                        request = json.dumps({
                            'toolkit': toolkit,
                            'method': method,
                            'args': kwargs
                        })
                        with socket.create_connection(('127.0.0.1', self._port), timeout=60) as sock:
                            sock.sendall((request + '\\n').encode('utf-8'))
                            sock.shutdown(socket.SHUT_WR)
                            data = sock.recv(65536).decode('utf-8')
                        return json.loads(data)
                
                gateway = JavaGateway(GATEWAY_PORT)
                __builtins_dict = {'gateway': gateway, '__builtins__': __builtins__}
                
                # Main REPL loop
                for line in sys.stdin:
                    file_path = line.strip()
                    if file_path == '__EXIT__':
                        break
                    if not file_path:
                        continue
                    try:
                        with open(file_path, 'r', encoding='utf-8') as f:
                            code = f.read()
                        exec_globals = dict(__builtins_dict)
                        exec(compile(code, file_path, 'exec'), exec_globals)
                        # Export __result__ variable if set
                        if '__result__' in exec_globals:
                            print(json.dumps(exec_globals['__result__']), flush=True)
                    except Exception:
                        traceback.print_exc(file=sys.stderr)
                    finally:
                        print(EXEC_MARKER, flush=True)
                        print(ERR_MARKER, file=sys.stderr, flush=True)
                """;

        Files.writeString(script, content, StandardCharsets.UTF_8);
        return script;
    }

    /**
     * Read lines from the given reader until the sentinel marker is encountered
     * or the timeout expires.
     *
     * @param reader the buffered reader to consume
     * @param marker the sentinel string signalling completion
     * @return concatenated output lines (excluding the marker)
     */
    private String readUntilMarker(BufferedReader reader, String marker) {
        StringBuilder sb = new StringBuilder();
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000;

        try {
            while (System.currentTimeMillis() < deadline) {
                if (!reader.ready()) {
                    // Brief sleep to avoid busy-wait; check again
                    Thread.sleep(50);
                    continue;
                }
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                if (line.contains(marker)) {
                    break;
                }
                sb.append(line).append(System.lineSeparator());
            }
        } catch (IOException e) {
            log.warn("I/O error reading Python output", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while reading Python output");
        }

        return sb.toString();
    }
}
