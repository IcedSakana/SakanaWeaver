package com.agent.core.agent.toolkit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Defines a toolkit as a Python class template.
 * <p>
 * A toolkit groups related tool methods together.  The {@link #toPythonClass()} method
 * generates a Python class stub that can be embedded into the system prompt so the
 * LLM understands available tool capabilities and calling conventions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolkitTemplate {

    /**
     * Toolkit name (used as the Python class name).
     */
    private String name;

    /**
     * Human-readable description of what this toolkit does.
     */
    private String description;

    /**
     * The methods exposed by this toolkit.
     */
    @Builder.Default
    private List<ToolMethod> methods = new ArrayList<>();

    /**
     * Arbitrary metadata attached to this toolkit.
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * Generate a Python class stub string representing this toolkit.
     *
     * @return a Python class definition as a string
     */
    public String toPythonClass() {
        StringBuilder sb = new StringBuilder();

        sb.append("class ").append(name).append(":\n");
        sb.append("    \"\"\"").append(description != null ? description : "").append("\"\"\"\n");

        if (methods == null || methods.isEmpty()) {
            sb.append("    pass\n");
            return sb.toString();
        }

        for (int i = 0; i < methods.size(); i++) {
            ToolMethod method = methods.get(i);
            sb.append("\n");
            sb.append(method.toPythonMethod());
        }

        return sb.toString();
    }

    // ---- inner classes ----

    /**
     * A single method within a toolkit.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolMethod {

        private String name;
        private String description;

        @Builder.Default
        private List<MethodParam> params = new ArrayList<>();

        private String returnType;
        private String returnDescription;

        /**
         * Render this method as a Python method stub.
         */
        public String toPythonMethod() {
            StringBuilder sb = new StringBuilder();

            // Build parameter list
            String paramList = buildParamList();

            sb.append("    def ").append(name).append("(self");
            if (!paramList.isEmpty()) {
                sb.append(", ").append(paramList);
            }
            sb.append(")");

            if (returnType != null && !returnType.isEmpty()) {
                sb.append(" -> ").append(returnType);
            }

            sb.append(":\n");

            // Docstring
            sb.append("        \"\"\"").append(description != null ? description : "").append("\n");

            if (params != null && !params.isEmpty()) {
                sb.append("\n");
                sb.append("        Args:\n");
                for (MethodParam param : params) {
                    sb.append("            ").append(param.getName());
                    if (param.getType() != null) {
                        sb.append(" (").append(param.getType()).append(")");
                    }
                    sb.append(": ").append(param.getDescription() != null ? param.getDescription() : "");
                    if (!param.isRequired()) {
                        sb.append(" (optional)");
                    }
                    sb.append("\n");
                }
            }

            if (returnDescription != null && !returnDescription.isEmpty()) {
                sb.append("\n");
                sb.append("        Returns:\n");
                sb.append("            ").append(returnDescription).append("\n");
            }

            sb.append("        \"\"\"\n");
            sb.append("        ...\n");

            return sb.toString();
        }

        private String buildParamList() {
            if (params == null || params.isEmpty()) {
                return "";
            }
            return params.stream()
                    .map(p -> {
                        StringBuilder paramSb = new StringBuilder();
                        paramSb.append(p.getName());
                        if (p.getType() != null && !p.getType().isEmpty()) {
                            paramSb.append(": ").append(p.getType());
                        }
                        if (p.getDefaultValue() != null) {
                            paramSb.append(" = ").append(p.getDefaultValue());
                        } else if (!p.isRequired()) {
                            paramSb.append(" = None");
                        }
                        return paramSb.toString();
                    })
                    .collect(Collectors.joining(", "));
        }
    }

    /**
     * A single parameter of a {@link ToolMethod}.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MethodParam {

        private String name;
        private String type;
        private String description;

        @Builder.Default
        private boolean required = true;

        private String defaultValue;
    }
}
