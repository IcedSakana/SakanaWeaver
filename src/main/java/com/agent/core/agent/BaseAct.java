package com.agent.core.agent;

import java.util.Map;

/**
 * Base abstract class for all Acts in the ReAct agent.
 * Each Act (IntentPlanner, InputPreprocessor, TaskExecutor, etc.) extends this class.
 *
 * Provides:
 * - Sleep/Stop signal handling
 * - Context dump/load for persistence
 */
public abstract class BaseAct {

    /** Name of this act */
    protected final String actName;

    /** Sleep signal */
    protected volatile boolean sleepSignal = false;

    /** Stop signal */
    protected volatile boolean stopSignal = false;

    protected BaseAct(String actName) {
        this.actName = actName;
    }

    /**
     * Execute this act's logic.
     *
     * @param input input data
     * @return output data
     */
    public abstract Object execute(Object input);

    /**
     * Dump this act's runtime context for persistence.
     *
     * @return serializable context map
     */
    public abstract Map<String, Object> dumpContext();

    /**
     * Load persisted context to restore this act's state.
     *
     * @param context previously dumped context
     */
    public abstract void loadContext(Map<String, Object> context);

    /**
     * Receive sleep signal.
     * The act should complete current loop iteration and then stop.
     */
    public void onSleepSignal() {
        this.sleepSignal = true;
    }

    /**
     * Receive stop signal.
     */
    public void onStopSignal() {
        this.stopSignal = true;
    }

    /**
     * Check if should continue execution.
     */
    protected boolean shouldContinue() {
        return !sleepSignal && !stopSignal;
    }

    /**
     * Reset signals after resume.
     */
    public void resetSignals() {
        this.sleepSignal = false;
        this.stopSignal = false;
    }

    public String getActName() {
        return actName;
    }
}
