package com.agent.model.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Agent runtime context.
 * Holds the runtime data of an agent instance including conversation history,
 * current task state, and act contexts for dump/load.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentContext {

    /** Session ID */
    private String sessionId;

    /** Short-term memory: recent conversation messages */
    private List<ConversationMessage> shortTermMemory;

    /** Current task ID being executed */
    private String currentTaskId;

    /** Act contexts for persistence (dump/load) */
    private Map<String, Object> actContexts;

    /** System prompt */
    private String systemPrompt;

    /** Available MCP tools for this agent */
    private List<String> availableTools;

    /** Available sub-agents */
    private List<String> availableSubAgents;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConversationMessage {
        /** Role: user / assistant / system / tool */
        private String role;
        /** Message content */
        private String content;
        /** Timestamp */
        private Long timestamp;
        /** Associated tool call ID (if role is tool) */
        private String toolCallId;
    }
}
