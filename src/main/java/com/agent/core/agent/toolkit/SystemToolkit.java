package com.agent.core.agent.toolkit;

import java.util.Arrays;

/**
 * Built-in system toolkit that provides the Agent with self-control capabilities.
 * <p>
 * This toolkit exposes fundamental operations the agent can perform on itself or
 * its runtime environment, such as checking task status, listing skills, sleeping,
 * communicating with the user, deep thinking, and data parsing.
 */
public class SystemToolkit {

    private static final String TOOLKIT_NAME = "SystemToolkit";
    private static final String TOOLKIT_DESCRIPTION =
            "Built-in system toolkit providing the agent with self-control and meta-cognitive capabilities.";

    private SystemToolkit() {
        // utility class
    }

    /**
     * Create and return the pre-defined {@link ToolkitTemplate} containing all
     * system-level methods.
     *
     * @return a fully configured {@link ToolkitTemplate}
     */
    public static ToolkitTemplate create() {
        return ToolkitTemplate.builder()
                .name(TOOLKIT_NAME)
                .description(TOOLKIT_DESCRIPTION)
                .methods(Arrays.asList(
                        checkTaskStatus(),
                        listSkills(),
                        sleep(),
                        talkToUser(),
                        deepThink(),
                        parseData()
                ))
                .build();
    }

    // ---- method definitions ----

    private static ToolkitTemplate.ToolMethod checkTaskStatus() {
        return ToolkitTemplate.ToolMethod.builder()
                .name("check_task_status")
                .description("Check the current status of a running or completed task.")
                .params(Arrays.asList(
                        ToolkitTemplate.MethodParam.builder()
                                .name("task_id")
                                .type("str")
                                .description("The unique identifier of the task to check")
                                .required(true)
                                .build()
                ))
                .returnType("dict")
                .returnDescription("A dictionary containing the task status, progress, and any result or error information.")
                .build();
    }

    private static ToolkitTemplate.ToolMethod listSkills() {
        return ToolkitTemplate.ToolMethod.builder()
                .name("list_skills")
                .description("List all skills currently available to the agent.")
                .returnType("list[dict]")
                .returnDescription("A list of dictionaries, each describing an available skill with its name, description, and capabilities.")
                .build();
    }

    private static ToolkitTemplate.ToolMethod sleep() {
        return ToolkitTemplate.ToolMethod.builder()
                .name("sleep")
                .description("Agent voluntarily yields execution and enters a sleep state. "
                        + "Use when waiting for external events or when no immediate action is needed.")
                .returnType("None")
                .returnDescription("None. The agent resumes when woken up by an external event.")
                .build();
    }

    private static ToolkitTemplate.ToolMethod talkToUser() {
        return ToolkitTemplate.ToolMethod.builder()
                .name("talk_to_user")
                .description("Send a message to the user. Use for status updates, clarification requests, "
                        + "or delivering final results.")
                .params(Arrays.asList(
                        ToolkitTemplate.MethodParam.builder()
                                .name("message")
                                .type("str")
                                .description("The message content to send to the user")
                                .required(true)
                                .build()
                ))
                .returnType("bool")
                .returnDescription("True if the message was delivered successfully, False otherwise.")
                .build();
    }

    private static ToolkitTemplate.ToolMethod deepThink() {
        return ToolkitTemplate.ToolMethod.builder()
                .name("deep_think")
                .description("Invoke the LLM for deep, deliberate thinking about a complex question. "
                        + "Use when the problem requires multi-step reasoning or careful analysis.")
                .params(Arrays.asList(
                        ToolkitTemplate.MethodParam.builder()
                                .name("question")
                                .type("str")
                                .description("The question or problem that requires deep thinking")
                                .required(true)
                                .build()
                ))
                .returnType("str")
                .returnDescription("The LLM's detailed analysis and reasoning about the question.")
                .build();
    }

    private static ToolkitTemplate.ToolMethod parseData() {
        return ToolkitTemplate.ToolMethod.builder()
                .name("parse_data")
                .description("Use the LLM to parse, extract, or transform data according to a natural-language instruction. "
                        + "Suitable for unstructured-to-structured data conversion, information extraction, and data cleaning.")
                .params(Arrays.asList(
                        ToolkitTemplate.MethodParam.builder()
                                .name("data")
                                .type("str")
                                .description("The raw data to be parsed or transformed")
                                .required(true)
                                .build(),
                        ToolkitTemplate.MethodParam.builder()
                                .name("instruction")
                                .type("str")
                                .description("Natural-language instruction describing how to parse or transform the data")
                                .required(true)
                                .build()
                ))
                .returnType("str")
                .returnDescription("The parsed or transformed data as a string.")
                .build();
    }
}
