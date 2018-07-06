Roddy Configuration Options
===========================

The following variables are used by Roddy itself:

Path Configuration
------------------

The following variables describe paths and may contain metadata-variable references such as '${dataset}' that are filled in during the identification
of input data during start up or filled with metadata values during submission.

* inputBaseDirectory:  This option is also set by the --useiodir=$outputBaseDirectory,$inputBaseDirectory command-line option.
* outputBaseDirectory: This option is also set by the --useiodir=$outputBaseDirectory[,$inputBaseDirectory] command-line option. Usually, this directory contains
    * one subdirectory per dataset (e.g. patient).
    * the "execution cache file" (.roddyExecCache.txt) containing the list of executed runs
    * the global "execution store" (.roddyExecutionStore) with the decompressed analysis tools from the plugins
* outputDirectory: Usually set to "$outputBaseDirectory/${dataset}".
* outputAnalysisBaseDirectory: This is usually a subdirectory of the outputDirectory and contains the actual output of the workflow. Usually, the filename patterns in the plugins use the outputAnalysisBaseDirectory as base directory for the full paths to output files. This directory also contains the "roddyExecutionStore" with the logs of the workflow execution.



Debugging Options
-----------------

* debugWrapInScript: Turn on extended debugging of the wrap in script that sets up the stage for the actual top-level job/tool script.
* disableDebugOptionsForToolscript: Mainly for internal usage. Turn off all debugging for the top-level job/tool script.

The following options set specific debugging options for Bash

* debugOptionsUsePipefail: `set -o pipefail`
* debugOptionsUseVerboseOutput: `set -v`
* debugOptionsUseExecuteOutput: `set -x`
* debugOptionsUseUndefinedVariableBreak: `set -u`
* debugOptionsUseExitOnError: `set -e`
* debugOptionsParseScripts: `set -n`
* debugOptionsUseExtendedExecuteOutput: This turns on additional debugging in Bash by setting the `PS4` variable. Each line starts with information about the executed script, the line-number in the script, and the function calls. `export PS4='+(\${BASH_SOURCE}:\${LINENO}): \${FUNCNAME[0]: +\$ { FUNCNAME[0] }():}'"`

Exposed to Jobs
---------------

The following variables are exposed to plugins by default. Specific plugins can add additional variables to configure the executed scripts.

* RODDY_SCRATCH: Affected by `baseScratchDirectory` applicationProperties.ini option.
* RODDY_JOBID: The job-identifier (PBS_JOBID, LSB_JOBID, etc.)
* RODDY_JOBNAME: The job-name (PBS_JOBNAME, etc.)
* RODDY_QUEUE: The queue to which the job runs (PBS_QUEUE, etc.)

Access Rights
-------------

* processOptionsSetUserGroup
* processOptionsSetUserMask
* processOptionsQueryEnv
* processOptionsQueryID
* outputAccessRightsForDirectories
* outputAllowAccessRightsModification";
* outputAccessRights
* outputFileGroup
* outputUMask

Other Variables
---------------

* usedResourcesSize
