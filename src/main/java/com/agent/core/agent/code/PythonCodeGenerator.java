package com.agent.core.agent.code;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Helper class that generates Python code scaffolding for the LLM.
 *
 * <p>The Code-Driven Execution Engine asks an LLM to write Python code that
 * will be executed inside a {@link CodeSandbox}.  This generator provides
 * ready-made code fragments so the LLM can focus on business logic rather
 * than boilerplate:</p>
 * <ul>
 *   <li>Toolkit import stubs so the LLM knows which tools are available.</li>
 *   <li>Fill-In-the-Middle (FIM) prompt format for code-completion models.</li>
 *   <li>Error-handling wrappers for safe execution.</li>
 *   <li>{@code __main__} blocks with result-capture conventions.</li>
 * </ul>
 *
 * <p>All methods are stateless and static; the class is a pure utility.</p>
 *
 * @author agent-server
 * @see CodeSandbox
 * @see PythonExecutor
 */
public final class PythonCodeGenerator {

    private PythonCodeGenerator() {
        // Utility class -- no instantiation
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Generate Python import and class-stub code for a list of available
     * toolkits so the LLM can see what tools are at its disposal.
     *
     * <p>Example output:</p>
     * <pre>{@code
     * # ---- Available Toolkits ----
     *
     * class HttpClient:
     *     """HTTP client toolkit for making web requests."""
     *
     *     @staticmethod
     *     def get(url: str, headers: dict = None) -> dict:
     *         """Send an HTTP GET request.
     *
     *         Args:
     *             url: Target URL.
     *             headers: Optional request headers.
     *         Returns:
     *             Response dict with 'status', 'body', 'headers'.
     *         """
     *         return gateway.invoke('httpClient', 'get', url=url, headers=headers)
     * }</pre>
     *
     * @param toolkits the toolkit templates describing available tools
     * @return Python source code string
     */
    public static String generateToolkitImports(List<ToolkitTemplate> toolkits) {
        if (toolkits == null || toolkits.isEmpty()) {
            return "# No toolkits available\n";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# ---- Available Toolkits ----\n");
        sb.append("# These classes delegate to Java via the Py4j gateway.\n");
        sb.append("# Call methods directly; the gateway connection is pre-configured.\n\n");

        for (ToolkitTemplate toolkit : toolkits) {
            sb.append(generateToolkitClass(toolkit));
            sb.append("\n\n");
        }

        return sb.toString();
    }

    /**
     * Generate a Fill-In-the-Middle (FIM) prompt for code-completion LLMs
     * (e.g., StarCoder, Code Llama).
     *
     * <p>The FIM format uses special tokens to mark where the model should
     * insert code:</p>
     * <pre>
     * &lt;fim_prefix&gt;{prefix}&lt;fim_suffix&gt;{suffix}&lt;fim_middle&gt;
     * </pre>
     *
     * @param prefix code that appears before the insertion point
     * @param suffix code that appears after the insertion point
     * @return the complete FIM prompt string
     */
    public static String generateFIMPrompt(String prefix, String suffix) {
        return "<fim_prefix>"
                + (prefix != null ? prefix : "")
                + "<fim_suffix>"
                + (suffix != null ? suffix : "")
                + "<fim_middle>";
    }

    /**
     * Wrap the given Python code in a {@code try / except} block that catches
     * all exceptions, prints the traceback to stderr, and sets
     * {@code __result__} to an error dict so the Java side always receives
     * structured output.
     *
     * @param code raw Python source code
     * @return code wrapped with error handling
     */
    public static String wrapWithErrorHandling(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }

        String indentedCode = indentCode(code, "    ");

        return """
                import traceback as __tb__
                import sys as __sys__
                
                try:
                %s
                except Exception as __e__:
                    __tb__.print_exc(file=__sys__.stderr)
                    __result__ = {
                        'success': False,
                        'error': str(__e__),
                        'error_type': type(__e__).__name__
                    }
                """.formatted(indentedCode);
    }

    /**
     * Wrap the given code in a {@code if __name__ == '__main__':} block
     * and append result-capture logic.
     *
     * <p>After the user code executes, any local variable named
     * {@code result} is automatically promoted to {@code __result__} so that
     * the sandbox can extract it.</p>
     *
     * @param code the user / LLM-generated Python code
     * @return code wrapped in a {@code __main__} block with result capture
     */
    public static String generateMainBlock(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }

        String indentedCode = indentCode(code, "    ");

        return """
                import json as __json__
                import traceback as __tb__
                import sys as __sys__
                
                def __main__():
                %s
                    # --- Auto result capture ---
                    _locals = dict(locals())
                    if 'result' in _locals:
                        return _locals['result']
                    return {k: v for k, v in _locals.items()
                            if not k.startswith('_') and not callable(v)}
                
                if __name__ == '__main__' or True:
                    try:
                        __result__ = __main__()
                        if __result__ is not None:
                            print(__json__.dumps(__result__, default=str), flush=True)
                    except Exception as __e__:
                        __tb__.print_exc(file=__sys__.stderr)
                        __result__ = {
                            'success': False,
                            'error': str(__e__),
                            'error_type': type(__e__).__name__
                        }
                """.formatted(indentedCode);
    }

    // ------------------------------------------------------------------
    // Inner model
    // ------------------------------------------------------------------

    /**
     * Describes a toolkit that can be exposed to the LLM as a Python class
     * stub.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolkitTemplate {

        /** Toolkit name (used as the Python class name). */
        private String name;

        /** Human-readable description shown as the class docstring. */
        private String description;

        /** Methods exposed by this toolkit. */
        private List<MethodTemplate> methods;
    }

    /**
     * Describes a single method within a toolkit.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MethodTemplate {

        /** Method name. */
        private String name;

        /** Human-readable description shown as the method docstring. */
        private String description;

        /** Parameter definitions. */
        private List<ParamTemplate> parameters;

        /** Return type description (e.g., "dict", "str", "list[dict]"). */
        private String returnType;
    }

    /**
     * Describes a single parameter of a toolkit method.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParamTemplate {

        /** Parameter name. */
        private String name;

        /** Python type hint (e.g., "str", "int", "dict"). */
        private String type;

        /** Human-readable description. */
        private String description;

        /** Whether this parameter is required. */
        @Builder.Default
        private boolean required = true;

        /** Default value expression (Python literal), or {@code null}. */
        private String defaultValue;
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Generate a Python class stub for a single toolkit.
     */
    private static String generateToolkitClass(ToolkitTemplate toolkit) {
        StringBuilder sb = new StringBuilder();

        String className = toPascalCase(toolkit.getName());
        String toolkitId = toolkit.getName();

        sb.append("class ").append(className).append(":\n");
        sb.append("    \"\"\"").append(sanitizeDocstring(toolkit.getDescription())).append("\"\"\"\n");

        if (toolkit.getMethods() == null || toolkit.getMethods().isEmpty()) {
            sb.append("    pass\n");
            return sb.toString();
        }

        for (MethodTemplate method : toolkit.getMethods()) {
            sb.append("\n");
            sb.append(generateMethodStub(toolkitId, method));
        }

        return sb.toString();
    }

    /**
     * Generate a Python static method stub for a single toolkit method.
     */
    private static String generateMethodStub(String toolkitId, MethodTemplate method) {
        StringBuilder sb = new StringBuilder();

        // --- Signature ---
        String params = buildParamSignature(method.getParameters());
        String returnHint = method.getReturnType() != null
                ? " -> " + method.getReturnType() : "";
        sb.append("    @staticmethod\n");
        sb.append("    def ").append(method.getName())
                .append("(").append(params).append(")")
                .append(returnHint).append(":\n");

        // --- Docstring ---
        sb.append("        \"\"\"").append(sanitizeDocstring(method.getDescription())).append("\n");
        if (method.getParameters() != null && !method.getParameters().isEmpty()) {
            sb.append("\n        Args:\n");
            for (ParamTemplate p : method.getParameters()) {
                sb.append("            ").append(p.getName()).append(": ")
                        .append(sanitizeDocstring(p.getDescription())).append("\n");
            }
        }
        if (method.getReturnType() != null) {
            sb.append("\n        Returns:\n");
            sb.append("            ").append(method.getReturnType()).append("\n");
        }
        sb.append("        \"\"\"\n");

        // --- Body: delegate to gateway ---
        String kwargs = buildKwargs(method.getParameters());
        sb.append("        return gateway.invoke('").append(toolkitId)
                .append("', '").append(method.getName()).append("'")
                .append(kwargs.isEmpty() ? "" : ", " + kwargs)
                .append(")\n");

        return sb.toString();
    }

    /**
     * Build a Python parameter signature string from parameter templates.
     */
    private static String buildParamSignature(List<ParamTemplate> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        return params.stream()
                .map(p -> {
                    String s = p.getName();
                    if (p.getType() != null) {
                        s += ": " + p.getType();
                    }
                    if (!p.isRequired() && p.getDefaultValue() != null) {
                        s += " = " + p.getDefaultValue();
                    } else if (!p.isRequired()) {
                        s += " = None";
                    }
                    return s;
                })
                .collect(Collectors.joining(", "));
    }

    /**
     * Build Python keyword arguments for a gateway.invoke() call.
     */
    private static String buildKwargs(List<ParamTemplate> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        return params.stream()
                .map(p -> p.getName() + "=" + p.getName())
                .collect(Collectors.joining(", "));
    }

    /**
     * Convert a camelCase or snake_case string to PascalCase for use as a
     * Python class name.
     */
    private static String toPascalCase(String input) {
        if (input == null || input.isEmpty()) {
            return "UnnamedToolkit";
        }
        StringBuilder sb = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : input.toCharArray()) {
            if (c == '_' || c == '-') {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                sb.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Sanitize a string for safe embedding inside a Python docstring.
     */
    private static String sanitizeDocstring(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\"\"\"", "\\\"\\\"\\\"");
    }

    /**
     * Indent every line of the given code block with the specified prefix.
     */
    private static String indentCode(String code, String indent) {
        if (code == null) {
            return "";
        }
        return code.lines()
                .map(line -> indent + line)
                .collect(Collectors.joining("\n"));
    }
}
