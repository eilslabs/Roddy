/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.config;

public class ConfigurationConstants {
    public static final String CFG_INPUT_BASE_DIRECTORY = "inputBaseDirectory";
    public static final String CFG_OUTPUT_BASE_DIRECTORY = "outputBaseDirectory";
    public static final String CFG_OUTPUT_ANALYSIS_BASE_DIRECTORY = "outputAnalysisBaseDirectory";
    public static final String CFG_INPUT_ANALYSIS_BASE_DIRECTORY = "inputAnalysisBaseDirectory";
    public static final String RODDY_EXEC_DIR_PREFIX = "exec_";
    public static final String RODDY_EXEC_CACHE_FILE = ".roddyExecCache.txt";
    public static final String RODDY_JOBSTATE_LOGFILE = "jobStateLogfile.txt";


    // Turn off debugging of wrapped script. This is mainly used for direct job execution during submission time.
    public static final String DISABLE_DEBUG_OPTIONS_FOR_TOOLSCRIPT = "disableDebugOptionsForToolscript";

    // set -o pipefail
    public static final String DEBUG_OPTIONS_USE_PIPEFAIL = "debugOptionsUsePipefail";

    // set -o verbose; set -v
    public static final String DEBUG_OPTIONS_USE_VERBOSE_OUTPUT = "debugOptionsUseVerboseOutput";

    // set -o errtrace; set -x
    public static final String DEBUG_OPTIONS_USE_EXECUTE_OUTPUT = "debugOptionsUseExecuteOutput";

    // This additionally puts debug information in the front of each line.
    public static final String DEBUG_OPTIONS_USE_EXTENDED_EXECUTE_OUTPUT = "debugOptionsUseExtendedExecuteOutput";

    // set -o nounset; set -u
    public static final String DEBUG_OPTIONS_USE_UNDEFINED_VARIABLE_BREAK = "debugOptionsUseUndefinedVariableBreak";

    // set -o errexit; set -e
    public static final String DEBUG_OPTIONS_USE_EXIT_ON_ERROR = "debugOptionsUseExitOnError";

    // set -o noexec; set -n; do not execute anything (dry-run); for syntax checking
    public static final String DEBUG_OPTIONS_PARSE_SCRIPTS = "debugOptionsParseScripts";

    // Toggle on/off debugging of the wrap in script itself.
    public static final String DEBUG_WRAP_IN_SCRIPT = "debugWrapInScript";

    public static final String CVALUE_PLACEHOLDER_EXECUTION_DIRECTORY = "$PWD";
    public static final String CVALUE_PLACEHOLDER_RODDY_JOBID = "${RODDY_JOBID}";
    public static final String CVALUE_PLACEHOLDER_RODDY_JOBID_RAW = "RODDY_JOBID";
    public static final String CVALUE_PLACEHOLDER_RODDY_JOBNAME_RAW = "RODDY_JOBNAME";
    public static final String CVALUE_PLACEHOLDER_RODDY_QUEUE_RAW = "RODDY_QUEUE";
    public static final String CVALUE_PLACEHOLDER_RODDY_SCRATCH_RAW = "RODDY_SCRATCH";
    public static final String CVALUE_DEFAULT_SCRATCH_DIR = "defaultScratchDir";
    public static final String CVALUE_TYPE_BASH_ARRAY = "bashArray";
    public static final String CVALUE_TYPE_PATH = "path";
    public static final String CVALUE_TYPE_STRING = "string";
    public static final String CVALUE_TYPE_BOOLEAN = "boolean";

    public static final String CVALUE_PREFIX_BASEPATH = "BASEPATH_";
    public static final String CVALUE_PREFIX_TOOL = "TOOL_";
    public static final String CVALUE_SUFFIX_BINARY = "_BINARY";
    public static final String CVALUE_SUFFIX_BINARY_SHORT = "_BIN";

    public static final String CVALUE_PROCESS_OPTIONS_SETUSERGROUP = "processOptionsSetUserGroup";
    public static final String CVALUE_PROCESS_OPTIONS_SETUSERMASK = "processOptionsSetUserMask";
    public static final String CVALUE_PROCESS_OPTIONS_QUERY_ENV = "processOptionsQueryEnv";
    public static final String CVALUE_PROCESS_OPTIONS_QUERY_ID = "processOptionsQueryID";

    public static final String CFG_ALLOW_ACCESS_RIGHTS_MODIFICATION = "outputAllowAccessRightsModification";
    public static final String CFG_OUTPUT_ACCESS_RIGHTS_FOR_DIRECTORIES = "outputAccessRightsForDirectories";
    public static final String CFG_OUTPUT_ACCESS_RIGHTS = "outputAccessRights";
    public static final String CFG_OUTPUT_FILE_GROUP = "outputFileGroup";

    public static final String CFG_OUTPUT_UMASK = "outputUMask";

    public static final String CFG_USED_RESOURCES_SIZE = "usedResourcesSize";

}
