/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.core;

import java.io.Serializable;

/**
 * Specifies the level of an ExecutionContext object
 */
public enum ExecutionContextLevel implements Serializable {

    /**
     * Do not execute anything. Used for collecting data for previous
     * pipeline runs.
     */
    QUERY_STATUS,
    /**
     * Run everything.
     */
    RUN(true, true),
    /**
     * Only run if files are not there
     */
    RERUN(true, true),
    TESTRERUN(false,false),
    /**
     * Allowed after run/rerun
     * Tells the system that submitting jobs is not allowed any more
     */
    ABORTED(false, true),
    /**
     * Level to create test data
     */
    CREATETESTDATA,
    /**
     * Nothing special or unknown
     */
    UNSET,
    /**
     * Valid for read out jobs.
     */
    READOUT,
    CLEANUP(true,true);

    public final boolean canSubmitJobs;

    public final boolean allowedToSubmitJobs;

    private ExecutionContextLevel() {
        this.canSubmitJobs = false;
        this.allowedToSubmitJobs = false;
    }

    private ExecutionContextLevel(boolean canSubmitJobs, boolean allowedToSubmitJobs) {
        this.canSubmitJobs = canSubmitJobs;
        this.allowedToSubmitJobs = allowedToSubmitJobs;
    }
}
