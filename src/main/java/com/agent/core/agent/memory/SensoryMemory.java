package com.agent.core.agent.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sensory memory -- the most ephemeral layer of the Atkinson-Shiffrin model.
 *
 * <p>Captures raw environmental perception such as the current page URL,
 * page title, structured page context and recent user actions.
 * The captured state is automatically invalidated whenever a new page
 * action occurs (i.e. a call to {@link #capture} replaces the previous
 * snapshot).
 *
 * <p>Typical lifecycle:
 * <ol>
 *   <li>Agent enters a page &rarr; {@link #capture} records the environment.</li>
 *   <li>Prompt builder calls {@link #getCurrent} to read the snapshot.</li>
 *   <li>Agent navigates away &rarr; {@link #invalidate} (or a new {@code capture})
 *       clears the previous state.</li>
 * </ol>
 *
 * @author agent-server
 * @see MemoryManager
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SensoryMemory {

    /**
     * URL of the page currently being observed.
     */
    private String currentPageUrl;

    /**
     * Human-readable title of the current page.
     */
    private String currentPageTitle;

    /**
     * Structured context extracted from the current page.
     * The exact schema is page-type dependent.
     */
    @Builder.Default
    private Map<String, Object> pageContext = new HashMap<>();

    /**
     * Ordered list of recent user actions captured on this page
     * (e.g. clicks, form inputs, scrolls).
     */
    @Builder.Default
    private List<String> userActions = new ArrayList<>();

    /**
     * Epoch-millisecond timestamp at which this snapshot was captured.
     */
    private Long capturedAt;

    /**
     * Internal validity flag. Set to {@code true} by {@link #capture}
     * and to {@code false} by {@link #invalidate}.
     */
    @Builder.Default
    private boolean valid = false;

    // ----------------------------------------------------------------
    // Operations
    // ----------------------------------------------------------------

    /**
     * Capture a new sensory snapshot, replacing any previous state.
     *
     * <p>This method invalidates the former snapshot and records the
     * new page environment. The {@code capturedAt} timestamp is set
     * to {@link System#currentTimeMillis()}.
     *
     * @param url     the URL of the page being observed
     * @param title   the human-readable page title
     * @param context structured page context (may be {@code null})
     */
    public void capture(String url, String title, Map<String, Object> context) {
        this.currentPageUrl = url;
        this.currentPageTitle = title;
        this.pageContext = context != null ? new HashMap<>(context) : new HashMap<>();
        this.userActions = new ArrayList<>();
        this.capturedAt = System.currentTimeMillis();
        this.valid = true;
    }

    /**
     * Return the current sensory snapshot if it is still valid.
     *
     * @return this instance when {@link #isValid()} is {@code true},
     *         or {@code null} otherwise
     */
    public SensoryMemory getCurrent() {
        return this.valid ? this : null;
    }

    /**
     * Explicitly invalidate the current sensory snapshot.
     * After this call, {@link #isValid()} returns {@code false}
     * and {@link #getCurrent()} returns {@code null}.
     */
    public void invalidate() {
        this.valid = false;
        this.currentPageUrl = null;
        this.currentPageTitle = null;
        this.pageContext = new HashMap<>();
        this.userActions = new ArrayList<>();
        this.capturedAt = null;
    }

    /**
     * Check whether the current sensory snapshot is still valid.
     *
     * @return {@code true} if a snapshot has been captured and has not
     *         been invalidated
     */
    public boolean isValid() {
        return this.valid;
    }
}
