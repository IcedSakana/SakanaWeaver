package com.agent.model.task;

/**
 * Task status enumeration.
 */
public enum TaskStatus {

    /** Pending - waiting to be executed */
    PENDING,

    /** Running - currently being executed */
    RUNNING,

    /** Completed - successfully finished */
    COMPLETED,

    /** Failed - execution failed */
    FAILED,

    /** Cancelled - cancelled by user or system */
    CANCELLED
}
