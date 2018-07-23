/*
 * Copyright (c) 2017 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.execution.jobs

import de.dkfz.roddy.AvailableFeatureToggles
import de.dkfz.roddy.Constants
import de.dkfz.roddy.Roddy
import de.dkfz.roddy.config.*
import de.dkfz.roddy.config.converters.BashConverter
import de.dkfz.roddy.config.converters.ConfigurationConverter
import de.dkfz.roddy.core.ExecutionContext
import de.dkfz.roddy.core.ExecutionContextError
import de.dkfz.roddy.core.ExecutionContextLevel
import de.dkfz.roddy.execution.io.fs.FileSystemAccessProvider
import de.dkfz.roddy.execution.jobs.direct.synchronousexecution.DirectCommand
import de.dkfz.roddy.execution.jobs.direct.synchronousexecution.DirectSynchronousExecutionJobManager
import de.dkfz.roddy.knowledge.files.BaseFile
import de.dkfz.roddy.knowledge.files.FileGroup
import de.dkfz.roddy.tools.LoggerWrapper
import de.dkfz.roddy.tools.RoddyIOHelperMethods
import de.dkfz.roddy.tools.Tuple2
import groovy.transform.CompileDynamic

import static de.dkfz.roddy.Constants.*
import static de.dkfz.roddy.execution.jobs.JobConstants.*
import static de.dkfz.roddy.config.ConfigurationConstants.CVALUE_PLACEHOLDER_RODDY_JOBID
import static de.dkfz.roddy.config.ConfigurationConstants.DEBUG_WRAP_IN_SCRIPT
import static de.dkfz.roddy.config.FilenamePattern.PLACEHOLDER_JOBPARAMETER

@groovy.transform.CompileStatic
class Job extends BEJob<BEJob, BEJobResult> {

    private static final de.dkfz.roddy.tools.LoggerWrapper logger = de.dkfz.roddy.tools.LoggerWrapper.getLogger(BEJob.class.getSimpleName())

    public static final String TOOLID_WRAPIN_SCRIPT = "wrapinScript"
    public static final String PARM_WRAPPED_SCRIPT = "WRAPPED_SCRIPT"
    public static final String PARM_WRAPPED_SCRIPT_MD5 = "WRAPPED_SCRIPT_MD5"

    /**
     * The current context.
     */
    public final ExecutionContext context

    /**
     * The local tool path
     */
    private final File localToolPath

    /**
     * The tool you want to call.
     */
    private final String toolID

    /**
     * Keeps a list of all unchanged, initial parameters, including default job parameters.
     */
    protected final Map<String, Object> allRawInputParameters

    /**
     * Keeps a list of all parameters after conversion and before removing them from the final parameter list
     * (See keepOnlyEssentialParameters). The value is used for testrun and testrerun output.
     */
    final Map<String, String> reportedParameters = [:]

    /**
     * Provide a list of files if you want to generate job dependencies.
     */
    public transient final List<BaseFile> parentFiles

    private Map<String, Object> initialInputParameters = [:]

    /**
     * Command object of last execution.
     */
    protected transient Command lastCommand

    /**
     * The list contains files which should be validated for a job restart.
     */
    public final List<BaseFile> filesToVerify

    Job(ExecutionContext context, String jobName, String toolID, List<String> arrayIndices, Map<String, Object> parameters, List<BaseFile> parentFiles) {
        this(context, jobName, toolID, arrayIndices, parameters, parentFiles, null)
    }

    Job(ExecutionContext context, String jobName, String toolID, Map<String, Object> parameters, List<BaseFile> parentFiles, List<BaseFile> filesToVerify) {
        this(context, jobName, toolID, null, parameters, parentFiles, filesToVerify)
    }

    Job(ExecutionContext context, String jobName, String toolID, Map<String, Object> parameters, List<BaseFile> parentFiles) {
        this(context, jobName, toolID, null, parameters, parentFiles, null)
    }

    Job(ExecutionContext context, String jobName, String toolID, List<String> arrayIndices, List<BaseFile> filesToVerify, Map<String, Object> parameters) {
        this(context, jobName, toolID, arrayIndices, parameters, null, filesToVerify)
    }

    Job(ExecutionContext context, String jobName, List<BaseFile> filesToVerify, String toolID, Map<String, Object> parameters) {
        this(context, jobName, toolID, null, parameters, null, filesToVerify)
    }

    Job(ExecutionContext context, String jobName, String toolID, List<String> arrayIndices, Map<String, Object> parameters) {
        this(context, jobName, toolID, arrayIndices, parameters, null, null)
    }

    Job(ExecutionContext context, String jobName, String toolID, Map<String, Object> parameters) {
        this(context, jobName, toolID, null, parameters, null, null)
    }

//    /**
//     * This is for job implementations which do the writeConfigurationFile on their own.
//     */
//    protected BEJob(String jobName, ExecutionContext context, String toolID, Map<String, String> parameters, List<String> arrayIndices, List<BaseFile> parentFiles, List<BaseFile> filesToVerify) {
//        super(jobName
//                , context.getConfiguration().getProcessingToolPath(context, toolID)
//                , toolID != null && toolID.trim().length() > 0 ? context.getConfiguration().getProcessingToolMD5(toolID): null
//                , arrayIndices
//                , parameters
//                , collectParentJobsFromFiles(parentFiles)
//                , collectJobIDsFromFiles(parentFiles)
//                , Roddy.getJobManager())
//        this.toolID = toolID
//        this.context = context
//        this.parentFiles = parentFiles
//        this.filesToVerify = filesToVerify
//        this.context.addExecutedJob(this)
//    }

    Job(ExecutionContext context, String jobName, String toolID, List<String> arrayIndices, Map<String, Object> inputParameters, List<BaseFile> parentFiles, List<BaseFile> filesToVerify) {
        this(context, jobName, toolID, null, arrayIndices, inputParameters, parentFiles, filesToVerify)
    }

    private static List<BEJobID> jobs2jobIDs(List<BEJob> jobs) {
        if (null == jobs) {
            return new LinkedList<BEJobID>()
        } else {
            return jobs.collect { it.runResult.jobID }
        }
    }

    private static List<BEJob> reconcileParentJobInformation(List<BEJob> parentJobs, List<BEJobID> parentJobIDs, BatchEuphoriaJobManager jobManager) {
        List<BEJob> pJobs
        if ((null != parentJobIDs && !parentJobIDs.isEmpty()) &&
                (null != parentJobs && !parentJobs.isEmpty())) {
            def validJobs = jobsWithUniqueValidJobId(parentJobs)
            def validIds = uniqueValidJobIDs(parentJobIDs).collect { it.toString() }
            def idsOfValidJobs = jobs2jobIDs(validJobs).collect { it.toString() }
            if (validIds != idsOfValidJobs) {
                throw new RuntimeException("parentJobBEJob needs to be called with one of parentJobs, parentJobIDs, or parentJobsIDs and *corresponding* parentJobs.")
            }
            pJobs = validJobs
        } else if (null == parentJobIDs && null == parentJobs) {
            pJobs = new LinkedList<BEJob>()
        } else if (null != parentJobs) {
            pJobs = jobsWithUniqueValidJobId(parentJobs)
        } else {
            pJobs = uniqueValidJobIDs(parentJobIDs).collect { new BEJob(it, jobManager) }
        }
        return pJobs
    }

    Job(ExecutionContext context, String jobName, String toolID, String inlineScript, List<String> arrayIndices, Map<String, Object> inputParameters,
        List<BaseFile> parentFiles, List<BaseFile> filesToVerify) {
        super(new BEJobID()
                , jobName
                , context.getConfiguration().getProcessingToolPath(context, TOOLID_WRAPIN_SCRIPT)
                , inlineScript
                , inlineScript ? null : getToolMD5(TOOLID_WRAPIN_SCRIPT, context)
                , getResourceSetFromConfiguration(toolID, context)
                , []
                , [:]
                , Roddy.getJobManager()
                , JobLog.toOneFile(new File(context.loggingDirectory, jobName + ".o{JOB_ID}"))
                , null)
        this.localToolPath = context.getConfiguration().getSourceToolPath(toolID)
        this.addParentJobs(reconcileParentJobInformation(collectParentJobsFromFiles(parentFiles), collectJobIDsFromFiles(parentFiles), jobManager))
        this.context = context
        this.toolID = toolID

        Map<String, Object> defaultParameters = context.getDefaultJobParameters(toolID)

        if (inputParameters != null)
            defaultParameters.putAll(inputParameters)
        this.allRawInputParameters = defaultParameters

        for (String k : defaultParameters.keySet()) {
            if (this.parameters.containsKey(k)) continue
            Object _v = defaultParameters[k]
            if (_v == null) {
                context.addErrorEntry(ExecutionContextError.EXECUTION_PARAMETER_ISNULL_NOTUSABLE.
                        expand("The parameter " + k + " has no valid value and will be set to ${NO_VALUE}."))
                this.parameters[k] = NO_VALUE
            } else {
                String newParameters = parameterObjectToString(k, _v)
                this.parameters.put(k, newParameters)
            }
        }

        parameters.putAll(convertResourceSetToParameters())
        reportedParameters.putAll(parameters)

        this.parameters[PARM_WRAPPED_SCRIPT] = context.getConfiguration().getProcessingToolPath(context, toolID).getAbsolutePath()
        this.parameters[PARM_WRAPPED_SCRIPT_MD5] = getToolMD5(toolID, context)
        this.parameters[PARM_JOBCREATIONCOUNTER] = "" + jobCreationCounter

        if (inputParameters != null)
            initialInputParameters.putAll(inputParameters)

        this.parentFiles = parentFiles ?: new LinkedList<BaseFile>()

        this.filesToVerify = filesToVerify ?: new LinkedList<BaseFile>()
        this.context.addExecutedJob(this)
    }

    static ResourceSet getResourceSetFromConfiguration(String toolID, ExecutionContext context) {
        if (!toolID || toolID == Constants.UNKNOWN) {
            return new EmptyResourceSet()
        } else {
            ToolEntry te = context.getConfiguration().getTools().getValue(toolID)
            return te.getResourceSet(context.configuration) ?: new EmptyResourceSet()
        }
    }

    static String getToolMD5(String toolID, ExecutionContext context) throws ConfigurationError {
        toolID != null && toolID.trim().length() > 0 ? context.getConfiguration().getProcessingToolMD5(toolID) : null
    }

    static List<BEJob> collectParentJobsFromFiles(List<BaseFile> parentFiles) {
        if (!parentFiles) return []
        List<BEJob> parentJobs = parentFiles.collect {
            BaseFile bf -> bf?.getCreatingJobsResult()?.job
        }.findAll {
            BEJob job -> job
        } as List<BEJob>
        return parentJobs
    }

    static List<BEJobID> collectJobIDsFromFiles(List<BaseFile> parentFiles) {
        List<BEJobID> dIDs = []
        if (parentFiles != null) {
            for (BaseFile bf : parentFiles) {
                if (bf.isSourceFile() && bf.getCreatingJobsResult() == null) continue
                try {
                    BEJobID jobid = bf.getCreatingJobsResult()?.getJobID()
                    if (jobid?.isValidID()) {
                        dIDs << jobid
                    }
                } catch (Exception ex) {
                    logger.severe("No job dependency id could be retrieved from BaseFile: " + bf)
                    logger.postSometimesInfo(ex.message)
                    logger.postSometimesInfo(RoddyIOHelperMethods.getStackTraceAsString(ex))
                }
            }
        }
        return dIDs
    }

    private static String fileToParameterString(File file) {
        return file.getAbsolutePath()
    }

    private static Integer generateHashCode(String template, List<BaseFile> parentFiles) {
        if (parentFiles) {
            parentFiles.each {
                BaseFile p ->
                    if (!p instanceof BaseFile) return

                    BaseFile _bf = (BaseFile) p
                    template += ("" + _bf.getAbsolutePath())

            }
        }
        return Math.abs(template.hashCode()) as Integer
    }

    /** The auto filename is generated from the raw job parameters as background information.
     *  If feature toggle FailOnAutoFilename is set, this method will fail with a RuntimeException.
     *
     *  The auto file name will be composed from the jobName, the key value, a hash code generated
     *  from these information and the list of parent files and the .auto suffix.
     *
     * @param key
     * @param baseFile
     * @return auto filename as path string
     */
    private String generateAutoFilename(String key, BaseFile baseFile) {
        int slotPosition = allRawInputParameters.keySet().asList().indexOf(key)

        if (Roddy.isStrictModeEnabled() && context.getFeatureToggleStatus(AvailableFeatureToggles.FailOnAutoFilenames))
            throw new RuntimeException("Auto filenames are forbidden when strict mode is active.")
        else
            context.addErrorEntry(ExecutionContextError.EXECUTION_SETUP_INVALID.
                    expand("An auto filename will be used for ${jobName}:${slotPosition} / ${baseFile.class.name}"))

        Integer hashCode = generateHashCode(jobName + key + slotPosition, parentFiles)

        File autoPath = new File(context.getOutputDirectory(), [jobName, key, hashCode, slotPosition].join("_") + ".auto")
        baseFile.setPath(autoPath)
        baseFile.setAsTemporaryFile()
        return autoPath.absolutePath
    }

    /** Convert a BaseFile object into a path string. First try to replace all variables using the raw job input parameters as background variables.
     *  If that fails, generate an auto file name.
     *
     * @param key
     * @param baseFile
     * @return path to basefile
     */
    private String baseFileToParameterString(String key, BaseFile baseFile) {
        String newPath = replaceParametersInFilePath(baseFile, allRawInputParameters)
        //Explicitly query newPath for a proper value!
        if (newPath == null) {
            newPath = generateAutoFilename(key, baseFile)
        }
        return newPath
        //            newParameters[k + "_path"] = newPath;
        //TODO Create a toStringList method for filestages. The method should then be very generic.
        //                this.parameters.put(k + "_fileStage_numericIndex", "" + bf.getFileStage().getNumericIndex());
        //                this.parameters.put(k + "_fileStage_index", bf.getFileStage().getIndex());
        //                this.parameters.put(k + "_fileStage_laneID", bf.getFileStage().getLaneId());
        //                this.parameters.put(k + "_fileStage_runID", bf.getFileStage().getRunID());
    }


    private String collectionToParameterString(String key, Collection collection) {
        //TODO This is not the best way to do this, think of a better one which is more generic.
        List<Object> convertedParameters = new LinkedList<>()
        for (Object o : collection) {
            if (o instanceof BaseFile) {
                if (((BaseFile) o).getPath() != null)
                    convertedParameters.add(baseFileToParameterString(key, o as BaseFile))
            } else
                convertedParameters.add(o.toString())
        }
        return BashConverter.convertListToBashArrayString(convertedParameters)
    }


    private String parameterObjectToString(String key, Object value) {
        if (value instanceof File) {
            return fileToParameterString(value as File)
        } else if (value instanceof BaseFile) {
            return baseFileToParameterString(key, value as BaseFile)
        } else if (value instanceof FileGroup) {
            return collectionToParameterString(key, (value as FileGroup).getFilesInGroup())
        } else if (value instanceof Collection) {
            return collectionToParameterString(key, value as Collection)
        } else {
            return value.toString()
        }
    }

    Map<String, String> convertResourceSetToParameters() {
        def rs = getResourceSet()
        Map<String, String> rsParameters = [:]
        if (rs == null) return rsParameters

        if (rs.isMemSet()) rsParameters["RODDY_JOBRESOURCE_REQUEST_MEM"] = rs.mem.toString()
        if (rs.isCoresSet()) rsParameters["RODDY_JOBRESOURCE_REQUEST_CORES"] = rs.cores.toString()
        if (rs.isNodesSet()) rsParameters["RODDY_JOBRESOURCE_REQUEST_NODES"] = rs.nodes.toString()
        if (rs.isQueueSet()) rsParameters["RODDY_JOBRESOURCE_REQUEST_QUEUE"] = rs.queue.toString()
        if (rs.isWalltimeSet()) rsParameters["RODDY_JOBRESOURCE_REQUEST_WALLTIME"] = rs.walltime.toString()
        if (rs.isAdditionalNodeFlagSet()) rsParameters["RODDY_JOBRESOURCE_REQUEST_NODEFLAGS"] = rs.additionalNodeFlag.toString()
        if (rs.isStorageSet()) rsParameters["RODDY_JOBRESOURCE_REQUEST_STORAGE"] = rs.storage.toString()
        return rsParameters
    }

    static String createJobName(BaseFile bf, String toolName, boolean reduceLevel) {
        return createJobName(bf, toolName, reduceLevel, null)
    }

    static String createJobName(BaseFile bf, String toolName, boolean reduceLevel, List<BaseFile> inputFilesForSizeCalculation) {
        ExecutionContext rp = bf.getExecutionContext()
        String runtime = rp.getTimestampString()
        StringBuilder sb = new StringBuilder()
        sb.append("r").append(runtime).append("_").append(bf.getPid()).append("_").append(toolName)
        return sb.toString()
    }

    static String createJobName(ExecutionContext rp, String postfix) {
        String runtime = rp.getTimestampString()
        StringBuilder sb = new StringBuilder()
        sb.append("r").append(runtime).append("_").append(rp.getDataSet().getId()).append("_").append(postfix)
        return sb.toString()
    }

    static String createJobName(FileGroup fg, String toolName, boolean reduceLevel, List<BaseFile> inputFilesForSizeCalculation) {
        BaseFile bf = (BaseFile) fg.getFilesInGroup().get(0)
        return createJobName(bf, toolName, reduceLevel, inputFilesForSizeCalculation)
    }

    static File replaceParametersInFilePath(BaseFile bf, Map<String, Object> parameters) {
        //TODO: It can occur that the regeneration of the filename is not valid!

        // Replace $_JOBPARAMETER items in path with proper values.
        File path = bf.getPath()
        if (path == null) {
            return null
        }

        String absolutePath = path.getAbsolutePath()
        List<FilenamePatternHelper.Command> commands = FilenamePatternHelper.extractCommands(bf.getExecutionContext(), PLACEHOLDER_JOBPARAMETER, absolutePath)
        for (FilenamePatternHelper.Command command : commands) {
            FilenamePatternHelper.CommandAttribute name = command.attributes.get("name")
            if (name != null) {
                String val = parameters[name.value]
                if (val == null) {
                    val = NO_VALUE
                    bf.getExecutionContext().addErrorEntry(ExecutionContextError.EXECUTION_PARAMETER_ISNULL_NOTUSABLE.expand("A value named " + name.value + " cannot be found in the jobs parameter list for a file of ${bf.class.name}. The value is set to <NO_VALUE>"))
                }
                absolutePath = absolutePath.replace(command.fullString, val)
            }
            path = new File(absolutePath)
            bf.setPath(path)
        }
        path
    }

    private static String jobStateInfoLine(String jobId, String code, String millis, String toolID) {
        return String.format("%s:%s:%s:%s", jobId, code, millis, toolID)
    }

    /**
     * Stores a new job jobState info to an execution contexts job jobState log file.
     *
     * @param job
     */
    @CompileDynamic
    void appendToJobStateLogfile(BatchEuphoriaJobManager jobManager, ExecutionContext executionContext, BEJobResult res, OutputStream out = null) {
        if (!jobManager.isHoldJobsEnabled() && !jobManager instanceof DirectSynchronousExecutionJobManager) {
            throw new RuntimeException("Appending to JobManager ${ConfigurationConstants.RODDY_JOBSTATE_LOGFILE} not supported when JobManager does not submit on hold: '${jobManager.class.name}")
        }
        if (res.successful) {
            def job = res.command.getJob()
            String jobInfoLine
            String millis = "" + System.currentTimeMillis()
            millis = millis.substring(0, millis.length() - 3)
            String jobId = job.getJobID()
            if (jobId != null) {
                if (job.getJobState() == JobState.UNSTARTED)
                    jobInfoLine = jobStateInfoLine(jobId, "UNSTARTED", millis, toolID)
                else if (job.getJobState() == JobState.ABORTED)
                    jobInfoLine = jobStateInfoLine(jobId, "ABORTED", millis, toolID)
                // TODO Issue 222: COMPLETED_SUCCESSFUL is set by BE.ExecutionService, when the execution result is successful, i.e. the qsub, not when the job finished successfully on the cluster!
//                else if (job.getJobState() == JobState.COMPLETED_SUCCESSFUL)
//                    jobInfoLine = jobStateInfoLine(jobId, "0", millis, toolID)
                else if (job.getJobState() == JobState.FAILED)
                    jobInfoLine = jobStateInfoLine(jobId, "" + res.executionResult.exitCode, millis, toolID)
                else
                    jobInfoLine = null
            } else {
                logger.postSometimesInfo("Did not store info for job " + job.getJobName() + ", job id was null.")
                jobInfoLine = null
            }
            if (jobInfoLine != null)
                FileSystemAccessProvider.getInstance().appendLineToFile(true, executionContext.getRuntimeService().getJobStateLogFile(executionContext), jobInfoLine, false)
        }
    }

    /**
     * Stores a new job jobState info to an execution contexts job jobState log file.
     *
     * @param job
     */
    @CompileDynamic
    void appendToJobStateLogfile(DirectSynchronousExecutionJobManager jobManager, ExecutionContext executionContext, BEJobResult res, OutputStream outputStream = null) {
//        if (res.command.isBlockingCommand()) {
//            assert (null != outputStream)
//            File logFile = (res.command.getTag(COMMAND_TAG_EXECUTION_CONTEXT) as ExecutionContext).getRuntimeService().getLogFileForCommand(res.command)
//
//            // Use reflection to get access to the hidden path field :p The stream object does not natively give
//            // access to it and I do not want to create a new class just for this.
//            Field fieldOfFile = FileOutputStream.class.getDeclaredField("path")
//            fieldOfFile.setAccessible(true);
//            File tmpFile2 = new File((String) fieldOfFile.get(outputStream))
//
//            FileSystemAccessProvider.getInstance().moveFile(tmpFile2, logFile)
//        } else {
        if (res.successful) {
            String millis = "" + System.currentTimeMillis()
            millis = millis.substring(0, millis.length() - 3)
            String code = "255"
            if (res.job.getJobState() == JobState.UNSTARTED)
                code = "UNSTARTED" // N
            else if (res.job.getJobState() == JobState.ABORTED)
                code = "ABORTED" // A
            else if (res.job.getJobState() == JobState.COMPLETED_SUCCESSFUL)
                code = "SUCCESSFUL"  // C
            else if (res.job.getJobState() == JobState.FAILED)
                code = "FAILED" // E

            if (null != res.job.getJobID()) {
                // That is indeed funny here: on our cluster, the following line did not work without the forced toString(), however
                // on our local machine it always worked! Don't know why it worked for PBS... Now we force-convert the parameters.
                String jobInfoLine = jobStateInfoLine("" + res.job.getJobID(), code, millis, toolID)
                FileSystemAccessProvider.getInstance().appendLineToFile(true, executionContext.getRuntimeService().getJobStateLogFile(executionContext), jobInfoLine, false)
            } else {
                logger.postSometimesInfo("Did not store info for job " + res.job.getJobName() + ", job id was null.")
            }

        }
//        }
    }

    /** Keep only the essential command line configurations for the job, due to argument length restrictions (e.g. -env in LSF).
     *
     */
    void keepOnlyEssentialParameters() {
        Set<String> nonEssentialParameters = parameters.keySet().findAll { key ->
            ![PARAMETER_FILE, CONFIG_FILE, DEBUG_WRAP_IN_SCRIPT, APP_PROPERTY_BASE_ENVIRONMENT_SCRIPT, PRM_TOOL_ID].contains(key)
        }
        parameters.keySet().removeAll(nonEssentialParameters)
    }

    BEJobResult run(boolean appendToJobStateLogfile = true) {
        if (runResult != null)
            throw new RuntimeException(ERR_MSG_ONLY_ONE_JOB_ALLOWED)

        ExecutionContextLevel contextLevel = context.getExecutionContextLevel()
        Configuration configuration = context.getConfiguration()

        StringBuilder dbgMessage = new StringBuilder()
        StringBuilder jobDetailsLine = new StringBuilder()

        //Remove duplicate job ids as PBS qsub cannot handle duplicate keys => job will hold forever as it releases the dependency queue linearly.
        this.parameters[RODDY_PARENT_JOBS] = parameterObjectToString(RODDY_PARENT_JOBS, parentJobIDs.unique()*.id)
        this.parameters[PARAMETER_FILE] = parameterObjectToString(PARAMETER_FILE, parameterFile)
        // The CONFIG_FILE variable is set to the same value as the PARAMETER_FILE to keep older scripts working with job-specific only environments.
        this.parameters[CONFIG_FILE] = this.parameters[PARAMETER_FILE]  // TODO Deprecated. Remove this line in Roddy 4.0.

        boolean debugWrapInScript = false
        if (configuration.configurationValues.hasValue(DEBUG_WRAP_IN_SCRIPT)) {
            debugWrapInScript = configuration.configurationValues.getBoolean(DEBUG_WRAP_IN_SCRIPT)
        }
        this.parameters.put(DEBUG_WRAP_IN_SCRIPT, parameterObjectToString(DEBUG_WRAP_IN_SCRIPT, debugWrapInScript))

        if (Roddy.applicationConfiguration.containsKey(APP_PROPERTY_BASE_ENVIRONMENT_SCRIPT)) {
            this.parameters.put(APP_PROPERTY_BASE_ENVIRONMENT_SCRIPT,
                    Roddy.applicationConfiguration.getOrSetApplicationProperty(APP_PROPERTY_BASE_ENVIRONMENT_SCRIPT, ""))
        }

        // See if the job should be executed
        boolean runJob
        if (contextLevel == ExecutionContextLevel.RUN || contextLevel == ExecutionContextLevel.CLEANUP) {
            runJob = true
            jobDetailsLine << "  Running job " + jobName
        } else if (contextLevel == ExecutionContextLevel.RERUN || contextLevel == ExecutionContextLevel.TESTRERUN) {
            runJob = checkIfJobShouldRerun(dbgMessage)
            jobDetailsLine << "  Rerun job " + jobName
        }

        //Execute the job or create a dummy command.
        if (runJob) {
            storeJobConfigurationFile(createJobConfiguration())
            keepOnlyEssentialParameters()
            runResult = jobManager.submitJob(this)
            if (appendToJobStateLogfile)
                this.appendToJobStateLogfile(jobManager, executionContext, runResult, null)
            Command cmd = runResult.command
            jobDetailsLine << " => " + cmd.job.getJobID().toString().padRight(10) // If we have os process id attached, we'll need some space, so pad the output.

            // For direct execution it can be very helpful to know the id of the started process. Sometimes, sub processes
            // remain and need to be killed.
            if (cmd instanceof DirectCommand)
                jobDetailsLine << " [OS Process ID: ${runResult.executionResult.processID}]"

            System.out.println(jobDetailsLine.toString())
            if (!cmd.jobID) {
                context.addErrorEntry(ExecutionContextError.EXECUTION_SUBMISSION_FAILURE.expand("Please check your submission command manually.\n\t  Is your access group set properly? [${context.getAnalysis().getUsergroup()}]\n\t  Can the submission binary handle your binary?\n\t  Is your submission system offline?"))
                logger.postSometimesInfo("Command: ${runResult.command}\nStatus Code: ${runResult.executionResult.exitCode}, Output:\n${runResult.executionResult.resultLines.join("\n")}")
                if (Roddy.getFeatureToggleValue(AvailableFeatureToggles.BreakSubmissionOnError)) {
                    context.abortJobSubmission()
                }
            }
            lastCommand = cmd

            // For auto filenames. Get the job id and push propagate it to all filenames.
            if (runResult?.jobID?.shortID) {
                allRawInputParameters.each { String k, Object o ->
                    BaseFile bf = o instanceof BaseFile ? (BaseFile) o : null
                    if (!bf) return

                    String absolutePath = bf.getPath().getAbsolutePath()
                    if (absolutePath.contains(CVALUE_PLACEHOLDER_RODDY_JOBID)) {
                        bf.setPath(new File(absolutePath.replace(CVALUE_PLACEHOLDER_RODDY_JOBID, runResult.jobID.shortID)))
                    }
                }
            }

        } else {
            dbgMessage << "\tdummy job created." + ENV_LINESEPARATOR
            File tool = context.getConfiguration().getProcessingToolPath(context, toolID)
            resetJobID(new BEFakeJobID(BEFakeJobID.FakeJobReason.NOT_EXECUTED))
            runResult = new BEJobResult((Command) null, this, null, tool, parameters, parentFiles.collect { it.getCreatingJobsResult()?.getJob() }.findAll { it })
            jobState = JobState.DUMMY
        }

        return runResult
    }

    /**
     * There are several reasons, why we need a job configuration.
     * The most important reason is, that we need to replicate Roddys configuration mechanism with all it's functionality for
     * any target system (Bash so far). If we don't do it, we will suffer from:
     * - wrong values
     * - wrong resolved value dependencies
     * - mistakenly overriden values
     * There might be more, but these are the ones for now.
     * @return
     */
    Configuration createJobConfiguration() {
        Configuration jobConfiguration = new Configuration(null, executionContext.configuration)
        jobConfiguration.configurationValues.addAll(parameters.collect { String k, String v -> new ConfigurationValue(jobConfiguration, k, v) })
        return jobConfiguration
    }

    void storeJobConfigurationFile(Configuration cfg) {
        String configText = ConfigurationConverter.convertAutomatically(context, cfg)
        FileSystemAccessProvider.getInstance().writeTextFile(getParameterFile(), configText, context)
    }

    /**
     * Checks, if a job needs to be rerun.
     * @param dbgMessage
     * @return
     */
    private boolean checkIfJobShouldRerun(StringBuilder dbgMessage) {
        def isVerbosityHigh = logger.isVerbosityHigh()
        String sep = ENV_LINESEPARATOR
        if (isVerbosityHigh) dbgMessage << "Rerunning job " + jobName

        //Check the parents of the new files to see if one of those is invalid for the current context! A file might be validated during a dry context...
        boolean parentFileIsDirty = false

        if (isVerbosityHigh) dbgMessage << sep << "\tchecking parent files for validity"
        for (BaseFile pf : parentFiles) {
            if (!pf.isFileValid()) {
                if (isVerbosityHigh) dbgMessage << sep << "\tfile " << pf.getPath().getName() << " is dirty or does not exist." << sep
                parentFileIsDirty = true
            }
        }

        Integer knownFilesCnt = 0
        Boolean fileUnverified = false
        //Now check if the new created files are in the list of already existing files and if those files are valid.
        if (!parentFileIsDirty) {
            Tuple2 res = verifyFiles(dbgMessage)
            fileUnverified = res.x
            knownFilesCnt = res.y
        }

        boolean parentJobIsDirty = parentJobs.collect { BEJob job -> job.isDirty }.any { boolean dirty -> dirty }

        boolean knownFilesCountMismatch = knownFilesCnt != filesToVerify.size()

        boolean rerunIsNecessary = fileUnverified || parentFileIsDirty || parentJobIsDirty || knownFilesCountMismatch

        if (isVerbosityHigh && rerunIsNecessary) dbgMessage << "\tBEJob will rerun" << sep

        //If all files could be found rerun if necessary otherwise rerun it definitive.
        if (!rerunIsNecessary) {
            if (isVerbosityHigh) dbgMessage << "\tBEJob was verified and will not be rerun" << sep
            return false
        }

        //More detailed if then because of enhanced debug / breakpoint possibilities
        if (isVerbosityHigh) {
            if (parentJobIsDirty)
                dbgMessage << "\t* BEJob is set to rerun because a parent job is marked dirty" << sep
            if (knownFilesCountMismatch)
                dbgMessage << "\t* The number of existing files does not match with the number of files which should be created" << sep
            if (fileUnverified)
                dbgMessage << "\t* One or more files could not be verified" << sep
            if (parentFileIsDirty)
                dbgMessage << "\t* One or more of the jobs parent files could not be verified" << sep
        }
        isDirty = true
        return true
    }

    private Tuple2<Boolean, Integer> verifyFiles(StringBuilder dbgMessage) {
        String sep = ENV_LINESEPARATOR
        Boolean fileUnverified = false
        Integer knownFilesCnt = 0
        boolean isVerbosityHigh = LoggerWrapper.isVerbosityHigh()

        if (isVerbosityHigh) dbgMessage << "\tverifying specified files" << sep

        //TODO what about the case if no verifiable files where specified? Or if the know files count does not match
        for (BaseFile fp : filesToVerify) {
            //See if we know the file... so this way we can use the BaseFiles verification method.
            List<BaseFile> knownFiles = context.getAllFilesInRun()
            for (BaseFile bf : knownFiles) {
                for (int i = 0; i < 3; i++) {
                    if (fp == null || bf == null || fp.getAbsolutePath() == null || bf.getPath() == null)
                        try {
                            logger.severe("Taking a short nap because a file does not seem to be finished.")
                            Thread.sleep(100)
                        } catch (InterruptedException e) {
                            e.printStackTrace()
                        }
                }
                if (fp.getAbsolutePath().equals(bf.getPath().getAbsolutePath())) {
                    if (!bf.isFileValid()) {
                        fileUnverified = true
                        if (isVerbosityHigh) dbgMessage << "\tfile " << bf.getPath().getName() << " could not be verified!" << sep
                    }
                    knownFilesCnt++
                    break
                }
            }
        }
        return new Tuple2<Boolean, Integer>(fileUnverified, knownFilesCnt)
    }

    ExecutionContext getExecutionContext() {
        return context
    }

    List<BaseFile> getParentFiles() {
        if (parentFiles != null)
            return new LinkedList<>(parentFiles)
        else
            return new LinkedList<>()
    }

    private File _logFile = null
/**
 * Returns the path to an existing log file.
 * If no logfile exists this returns null.
 *
 * @return
 */
    public synchronized File getLogFile() {
        if (_logFile == null)
            _logFile = this.getExecutionContext().getRuntimeService().getLogFileForJob(this);
        return _logFile;
    }

    public boolean hasLogFile() {
        if (getJobState().isPlannedOrRunning())
            return false;
        if (_logFile == null)
            return this.getExecutionContext().getRuntimeService().hasLogFileForJob(this);
        return true;
    }

    File getParameterFile() {
        return this.context.getParameterFilename(this)
    }

    List<BaseFile> getFilesToVerify() {
        return new LinkedList<>(filesToVerify)
    }

    synchronized boolean areAllFilesValid() {
        boolean allValid = true
        for (BaseFile baseFile : filesToVerify) {
            allValid &= baseFile.isFileValid()
        }
        return allValid
    }

    String getToolID() {
        return toolID
    }

    File getLocalToolPath() {
        return localToolPath
    }

}
