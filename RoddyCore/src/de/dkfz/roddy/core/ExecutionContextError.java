/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.core;

import java.io.Serializable;
import java.util.logging.Level;

/**
 * Different types of errors which can happen on workflow processing.
 */
public class ExecutionContextError implements Serializable {

    public static final ExecutionContextError EXECUTION_SETUP_INVALID = new ExecutionContextError("The workflow setup is not correct.", Level.SEVERE);

    public static final ExecutionContextError EXECUTION_SCRIPT_INVALID = new ExecutionContextError("A script file is not valid.", Level.SEVERE);

    public static final ExecutionContextError EXECUTION_SCRIPT_NOTFOUND = new ExecutionContextError("A script file could not be found in the configuration or the id is invalid.", Level.SEVERE);

    public static final ExecutionContextError EXECUTION_BASEPATH_NOTFOUND = new ExecutionContextError("A base path could not be found in the configuration or the id is invalid.", Level.SEVERE);

    public static final ExecutionContextError EXECUTION_PATH_NOTFOUND = new ExecutionContextError("A path could not be found.", Level.SEVERE);

    public static final ExecutionContextError EXECUTION_PATH_NOTFOUND_WARN = new ExecutionContextError("A path could not be found.", Level.WARNING);

    public static final ExecutionContextError EXECUTION_PATH_INACCESSIBLE = new ExecutionContextError("A path could not be accessed.", Level.SEVERE);

    public static final ExecutionContextError EXECUTION_PATH_NOTWRITABLE = new ExecutionContextError("A path is not writable.", Level.SEVERE);

    public static final ExecutionContextError EXECUTION_FILECREATION_PATH_NOTSET= new ExecutionContextError("A file object has no valid path.", Level.SEVERE);

    public static final ExecutionContextError EXECUTION_FILECREATION_NOCONSTRUCTOR= new ExecutionContextError("There is no constructor for automatic file name creation available.", Level.SEVERE);

    public static final ExecutionContextError EXECUTION_FILECREATION_FIELDINACCESSIBLE = new ExecutionContextError("A field / variable is not accessible in the target object.", Level.SEVERE);

    public static final ExecutionContextError EXECUTION_BINARY_INVALID = new ExecutionContextError("A binary file is not valid.", Level.SEVERE);

    public static final ExecutionContextError EXECUTION_JOBFAILED = new ExecutionContextError("The execution of a job failed.", Level.SEVERE);

    public static final ExecutionContextError EXECUTION_NOINPUTDATA = new ExecutionContextError("No input data was found.", Level.SEVERE);

    public static final ExecutionContextError EXECUTION_UNCAUGHTERROR = new ExecutionContextError("An uncaught error occurred during a run.", Level.SEVERE);

    public static final ExecutionContextError EXECUTION_SUBMISSION_FAILURE = new ExecutionContextError("The job submission did not work, aborting job submission.", Level.SEVERE);

    public static final ExecutionContextError READBACK_NOREALJOBCALLSFILE = new ExecutionContextError("The created calls log file is missing => No information about created jobs is available.", Level.SEVERE);

    public static final ExecutionContextError READBACK_NOJOBSTATESFILE = new ExecutionContextError("The job jobState log file is missing => No information about job states is available.", Level.WARNING);

    public static final ExecutionContextError READBACK_NOBINARYSERIALIZEDJOBS = new ExecutionContextError("The binary file with serialized job objects is missing => No extended job info is available.", Level.WARNING);

    public static final ExecutionContextError READBACK_NOEXECUTEDJOBSFILE = new ExecutionContextError("The xml document with information about started jobs is missing => No extended job info is available.", Level.WARNING);

    public static final ExecutionContextError EXECUTION_GETJOBNAME_NOT_POSSIBLE = new ExecutionContextError("Roddy was not able to apply a jobname pattern for a basefile.", Level.SEVERE);

    public static final ExecutionContextError EXECUTION_PARAMETER_ISNULL_NOTUSABLE = new ExecutionContextError("A parameter cannot be used because it has a value of null.", Level.SEVERE);


    public final String description;

    private Level errorLevel;

    private String additionalInfo;

    private ExecutionContextError parent = null;

    private transient Exception exception;

    private int id;

    protected ExecutionContextError(String description, Level errorLevel) {
        this.description = description;
        this.errorLevel = errorLevel;
        id = description.hashCode();
    }

    public Level getErrorLevel() {
        return errorLevel;
    }

    public String getDescription() {
        return description;
    }

    public String getAdditionalInfo() {
        return additionalInfo;
    }

    /**
     * Creates a copy of this object and sets its additionalInfo field.
     *
     * @param info
     * @return
     */
    public ExecutionContextError expand(String info, Level newLvl) {
        ExecutionContextError newErr = new ExecutionContextError(description, newLvl);
        newErr.additionalInfo = info;
        newErr.parent = this;
        return newErr;
    }

    public ExecutionContextError expand(String info) {
        return expand(info, Level.SEVERE);
    }

    public ExecutionContextError expand(Exception ex) {
        ExecutionContextError newErr = expand(description);
        newErr.exception = ex;
        newErr.parent = this;
        return newErr;
    }

    public ExecutionContextError getBase() {
        if(parent == null) return this;
        return parent.getBase();
    }

    public Exception getException() {
        return exception;
    }

    @Override
    public String toString() {
        return description + " " + errorLevel + (additionalInfo != null ? ": " + additionalInfo : "");
    }
}
