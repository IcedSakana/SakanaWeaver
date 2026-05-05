package com.agent.core.agent.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Memory message carrying a structured entity extracted during agent execution.
 *
 * <p>Entity memories represent domain objects (e.g. a customer, an order,
 * a configuration item) that the agent has identified, created, updated
 * or deleted. Each entity is described by its type, a JSON-like schema,
 * its current data snapshot, the last operation performed on it, and
 * a set of free-form tags for retrieval.
 *
 * @author agent-server
 * @see MemoryMessage
 * @see ShortTermMemory
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class EntityMemoryMessage extends MemoryMessage {

    /**
     * Logical type of the entity, e.g. {@code "Customer"},
     * {@code "Order"}, {@code "ConfigItem"}.
     */
    private String entityType;

    /**
     * Optional schema definition for the entity.
     * Keys are field names; values describe types, constraints, etc.
     */
    @Builder.Default
    private Map<String, Object> schema = new HashMap<>();

    /**
     * The current data snapshot of the entity, keyed by field name.
     */
    @Builder.Default
    private Map<String, Object> data = new HashMap<>();

    /**
     * The most recent operation performed on this entity.
     * Common values: {@code "CREATE"}, {@code "UPDATE"},
     * {@code "DELETE"}, {@code "READ"}.
     */
    private String operation;

    /**
     * Free-form tags associated with the entity for categorisation
     * and retrieval purposes.
     */
    @Builder.Default
    private List<String> tags = new ArrayList<>();
}
