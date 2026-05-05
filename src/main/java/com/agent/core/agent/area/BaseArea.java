package com.agent.core.agent.area;

import com.agent.core.agent.BaseAct;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Abstract base class for all Areas in the brain-inspired agent architecture.
 *
 * <p>An Area represents a functional region of the agent's "brain", analogous to
 * cortical areas in neuroscience. Each Area encapsulates a cohesive set of Acts
 * that together implement a specific cognitive capability (e.g., perception,
 * cognition, motor control, expression, self-evaluation).</p>
 *
 * <p>Areas are created per agent instance and are NOT Spring-managed beans.
 * They own and coordinate their child Acts, handle lifecycle signals
 * (sleep/stop), and support context persistence for session hibernation.</p>
 *
 * <h3>Lifecycle</h3>
 * <ul>
 *   <li>{@link #registerAct(BaseAct)} -- register child acts during construction</li>
 *   <li>{@link #execute(Object)} -- main entry point for area processing</li>
 *   <li>{@link #onSleep()} -- propagate sleep signal to all acts, then dump context</li>
 *   <li>{@link #onStop()} -- propagate stop signal to all acts immediately</li>
 *   <li>{@link #dumpContext()} / {@link #loadContext(Map)} -- persistence support</li>
 * </ul>
 *
 * @author agent-framework
 */
@Slf4j
public abstract class BaseArea {

    /** Ordered map of acts belonging to this area, keyed by act name. */
    private final Map<String, BaseAct> acts = new LinkedHashMap<>();

    /** Sleep signal flag. */
    protected volatile boolean sleepSignal = false;

    /** Stop signal flag. */
    protected volatile boolean stopSignal = false;

    // -------------------------------------------------
    // Act management
    // -------------------------------------------------

    /**
     * Register an Act into this Area.
     *
     * <p>The act is stored by its {@link BaseAct#getActName()} as the key.
     * Duplicate names will overwrite the previous registration with a warning.</p>
     *
     * @param act the act to register; must not be {@code null}
     * @throws IllegalArgumentException if {@code act} is {@code null}
     */
    public void registerAct(BaseAct act) {
        if (act == null) {
            throw new IllegalArgumentException("Cannot register a null act in area: " + getAreaName());
        }
        String name = act.getActName();
        if (acts.containsKey(name)) {
            log.warn("Area [{}]: overwriting existing act with name '{}'", getAreaName(), name);
        }
        acts.put(name, act);
        log.debug("Area [{}]: registered act '{}'", getAreaName(), name);
    }

    /**
     * Retrieve a registered Act by name.
     *
     * @param actName the name of the act
     * @return the act instance, or {@code null} if not found
     */
    public BaseAct getAct(String actName) {
        return acts.get(actName);
    }

    /**
     * Return an unmodifiable view of all registered acts.
     *
     * @return map of act-name to act-instance
     */
    public Map<String, BaseAct> getAllActs() {
        return Collections.unmodifiableMap(acts);
    }

    // -------------------------------------------------
    // Execution
    // -------------------------------------------------

    /**
     * Execute the main processing logic of this Area.
     *
     * <p>Subclasses implement domain-specific pipelines in this method.
     * Before performing work, implementations should check {@link #shouldContinue()}
     * to honour sleep/stop signals.</p>
     *
     * @param input the input data for this area (type varies by subclass)
     * @return the output produced by this area (type varies by subclass)
     */
    public abstract Object execute(Object input);

    // -------------------------------------------------
    // Lifecycle signals
    // -------------------------------------------------

    /**
     * Handle the sleep signal.
     *
     * <p>Propagates {@link BaseAct#onSleepSignal()} to every registered act,
     * allowing each act to finish its current iteration gracefully.</p>
     */
    public void onSleep() {
        log.info("Area [{}]: received sleep signal, propagating to {} acts",
                getAreaName(), acts.size());
        this.sleepSignal = true;
        for (BaseAct act : acts.values()) {
            act.onSleepSignal();
        }
    }

    /**
     * Handle the stop signal.
     *
     * <p>Propagates {@link BaseAct#onStopSignal()} to every registered act,
     * requesting immediate termination.</p>
     */
    public void onStop() {
        log.info("Area [{}]: received stop signal, propagating to {} acts",
                getAreaName(), acts.size());
        this.stopSignal = true;
        for (BaseAct act : acts.values()) {
            act.onStopSignal();
        }
    }

    /**
     * Check whether this area should continue executing.
     *
     * @return {@code true} if neither sleep nor stop has been signalled
     */
    protected boolean shouldContinue() {
        return !sleepSignal && !stopSignal;
    }

    /**
     * Reset sleep/stop signals after an area is resumed from hibernation.
     */
    public void resetSignals() {
        this.sleepSignal = false;
        this.stopSignal = false;
        for (BaseAct act : acts.values()) {
            act.resetSignals();
        }
    }

    // -------------------------------------------------
    // Persistence
    // -------------------------------------------------

    /**
     * Dump this Area's runtime context for persistence.
     *
     * <p>The default implementation collects each act's context via
     * {@link BaseAct#dumpContext()} and stores them under the key
     * {@code "acts"}. Subclasses may override to add area-level state
     * but should call {@code super.dumpContext()} to include act contexts.</p>
     *
     * @return a serializable map representing this area's state
     */
    public Map<String, Object> dumpContext() {
        Map<String, Object> context = new HashMap<>();

        // Dump all act contexts
        Map<String, Object> actContexts = new HashMap<>();
        for (Map.Entry<String, BaseAct> entry : acts.entrySet()) {
            Map<String, Object> actCtx = entry.getValue().dumpContext();
            if (actCtx != null && !actCtx.isEmpty()) {
                actContexts.put(entry.getKey(), actCtx);
            }
        }
        context.put("acts", actContexts);
        context.put("areaName", getAreaName());

        return context;
    }

    /**
     * Load persisted context to restore this Area's state.
     *
     * <p>The default implementation restores each act's context from the
     * {@code "acts"} entry. Subclasses may override to restore area-level
     * state but should call {@code super.loadContext(context)} to restore
     * act contexts.</p>
     *
     * @param context previously dumped context map
     */
    @SuppressWarnings("unchecked")
    public void loadContext(Map<String, Object> context) {
        if (context == null) {
            return;
        }

        Object actsObj = context.get("acts");
        if (actsObj instanceof Map) {
            Map<String, Object> actContexts = (Map<String, Object>) actsObj;
            for (Map.Entry<String, BaseAct> entry : acts.entrySet()) {
                Object actCtx = actContexts.get(entry.getKey());
                if (actCtx instanceof Map) {
                    entry.getValue().loadContext((Map<String, Object>) actCtx);
                    entry.getValue().resetSignals();
                }
            }
        }

        log.debug("Area [{}]: context loaded", getAreaName());
    }

    // -------------------------------------------------
    // Identity
    // -------------------------------------------------

    /**
     * Return the unique name of this Area.
     *
     * <p>Used as the key when persisting area contexts and for logging.</p>
     *
     * @return a non-null area name
     */
    public abstract String getAreaName();

    @Override
    public String toString() {
        return getAreaName() + "[acts=" + acts.keySet() + "]";
    }
}
