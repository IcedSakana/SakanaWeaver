package com.agent.model.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Artifact structure aligned with A2A protocol.
 * Contains the actual payload of an event, supporting text, cards, and custom types.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Artifact {

    /**
     * Extended metadata (from A2A protocol).
     * e.g., type field for frontend rendering different card types.
     */
    private Map<String, Object> metadata;

    /**
     * Parts of the artifact (from A2A protocol).
     * Each part contains actual message content in JSON structure.
     */
    private java.util.List<ArtifactPart> parts;

    /**
     * Index for identifying reply round.
     * Combined with eventSource + taskId to uniquely identify a chat bubble.
     */
    private Integer index;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ArtifactPart {

        /** Content type: text, data, file, etc. */
        private String type;

        /** Actual data payload (JSON structure) */
        private Object data;

        /** MIME type of the content */
        private String mimeType;
    }
}
