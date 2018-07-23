/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy;

/**
 * A list of available feature toggles.
 * Will be modified e.g. if something new comes up or something is removed or finally kept and enabled.
 */
enum AvailableFeatureToggles {

    /**
     ////////////////////////
    // "Permanent" toggles
    //  These toggles are meant to be somewhat permanent
    //  However, their default value might change to enable or disable them permanently on startup.
    ////////////////////////
    */
    /**
     * If Strict mode is set, a lot of checks will result to rollback or Roddy refuses to run at all.
     * As strict mode is such an important and early set feature, it should only be set via ini or cli.
     * The xml set variant can only be used, when an analysis object already exists. Pre configuration
     * issues would then not be treated strictly.
     */
    StrictMode(false, true),

    /**
     * Rollback cluster jobs on submission error. Only applies, if StrictMode is enabled.
     * Can be enabled on a per configuration level.
     */
    RollbackOnWorkflowError(true),

    /**
    ////////////////////////
    // "Semi-permanent" toggles
    //  These toggles will be most likely be removed at some point.
    ////////////////////////
    */
    @Deprecated
    ForbidSubmissionOnRunning(false),

    @Deprecated
    BreakSubmissionOnError(false),

    @Deprecated
    QuoteSomeScalarConfigValues(true),

    @Deprecated
    UseDeclareFunctionalityForBashConverter(true),

    /**
     * Enable this, to have Bash arrays in the runtime config auto quoted to something like
     *
     * declare -x BASH_ARRAY="value"
     *
     * unless they are already quoted.
     */
    AutoQuoteBashArrayVariables(true),

    /**
     * Fail, if strict mode is enabled and auto filenames would be created.
     */
    FailOnAutoFilenames(false),

    /**
     * Fail, if e.g. upon (test)rerun the first dry run QUERY_STATUS failed.
     */
    FailOnErroneousDryRuns(true )

    public final boolean defaultValue

    /**
     * Sets, if the mode is only applicable on application level.
     * Otherwise it can be set in xml configurations.
     */
    public final boolean applicationLevelOnly

    AvailableFeatureToggles(boolean defaultValue, boolean applicationLevelOnly = false) {
        this.applicationLevelOnly = applicationLevelOnly
        this.defaultValue = defaultValue
    }
}
