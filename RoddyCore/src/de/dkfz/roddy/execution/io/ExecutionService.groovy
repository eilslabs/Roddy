/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.execution.io

import de.dkfz.roddy.config.loader.ConfigurationLoaderException
import de.dkfz.roddy.execution.BEExecutionService
import de.dkfz.roddy.execution.jobs.BEJobResult
import de.dkfz.roddy.execution.jobs.Command
import de.dkfz.roddy.execution.jobs.Job
import de.dkfz.roddy.execution.jobs.JobManagerOptions
import de.dkfz.roddy.execution.jobs.JobState
import de.dkfz.roddy.AvailableFeatureToggles
import de.dkfz.roddy.Constants
import de.dkfz.roddy.Roddy
import de.dkfz.roddy.RunMode
import de.dkfz.roddy.StringConstants
import de.dkfz.roddy.client.cliclient.RoddyCLIClient
import de.dkfz.roddy.config.Configuration
import de.dkfz.roddy.config.ConfigurationConstants
import de.dkfz.roddy.config.loader.ConfigurationFactory
import de.dkfz.roddy.config.ConfigurationValue
import de.dkfz.roddy.config.ToolEntry
import de.dkfz.roddy.config.converters.ConfigurationConverter
import de.dkfz.roddy.config.converters.XMLConverter
import de.dkfz.roddy.core.*
import de.dkfz.roddy.execution.io.fs.FileSystemAccessProvider
import de.dkfz.roddy.execution.jobs.BEJobID
import de.dkfz.roddy.execution.jobs.cluster.lsf.LSFCommand
import de.dkfz.roddy.execution.jobs.cluster.pbs.PBSCommand
import de.dkfz.roddy.execution.jobs.cluster.sge.SGECommand
import de.dkfz.roddy.execution.jobs.direct.synchronousexecution.DirectSynchronousExecutionJobManager
import de.dkfz.roddy.plugins.LibrariesFactory
import de.dkfz.roddy.plugins.PluginInfo
import de.dkfz.roddy.tools.LoggerWrapper
import de.dkfz.roddy.tools.RoddyIOHelperMethods
import groovy.transform.CompileStatic

import java.text.ParseException
import java.util.concurrent.ExecutionException
import java.util.logging.Level

import static de.dkfz.roddy.StringConstants.TILDE
import static de.dkfz.roddy.config.ConfigurationConstants.*

/**
 * Execution services commands locally or remotely. Some specific commands (copy file or directory) are also available as those can (hopefully) be created by all service implementations.
 * i.e. local file handling is done by Java itself wheres remote handling with ssh is done via the ssh library
 *
 */
@CompileStatic
abstract class ExecutionService implements BEExecutionService {
    private static final LoggerWrapper logger = LoggerWrapper.getLogger(ExecutionService.class.name)
    private static ExecutionService executionService

    public static final String RODDY_CVALUE_DIRECTORY_LOCKFILES = "DIR_LOCKFILES"
    public static final String RODDY_CVALUE_DIRECTORY_TEMP = "DIR_TEMP"
    public static final String RODDY_CVALUE_DIRECTORY_EXECUTION = "DIR_EXECUTION"
    public static final String RODDY_CVALUE_DIRECTORY_EXECUTION_COMMON = "DIR_EXECUTION_COMMON"
    public static final String RODDY_CVALUE_DIRECTORY_RODDY_APPLICATION = "DIR_RODDY"
    public static final String RODDY_CVALUE_DIRECTORY_BUNDLED_FILES = "DIR_BUNDLED_FILES"
    public static final String RODDY_CVALUE_DIRECTORY_ANALYSIS_TOOLS = "DIR_ANALYSIS_TOOLS"
    public static final String RODDY_CVALUE_JOBSTATE_LOGFILE = "jobStateLogFile"

    static void initializeService(Class executionServiceClass, RunMode runMode) {
        executionService = (ExecutionService) executionServiceClass.getConstructors()[0].newInstance()
        if (runMode == RunMode.CLI) {
            boolean isConnected = executionService.tryInitialize(true)
            if (!isConnected) {
                int queryCount = 0
                //If password is not stored, ask once for the password only, increase queryCount.
                if (!Boolean.parseBoolean(Roddy.applicationConfiguration.getOrSetApplicationProperty(runMode, Constants.APP_PROPERTY_EXECUTION_SERVICE_STORE_PWD, Boolean.FALSE.toString()))) {
                    Roddy.applicationConfiguration.setApplicationProperty(runMode, Constants.APP_PROPERTY_EXECUTION_SERVICE_AUTH_METHOD, Constants.APP_PROPERTY_EXECUTION_SERVICE_AUTH_METHOD_PWD)
                    RoddyCLIClient.askForPassword()
                    isConnected = executionService.tryInitialize(true)
                    queryCount++
                }
                for (; queryCount < 3 && !isConnected; queryCount++) {
                    //Wait for correct password, count to three?
                    RoddyCLIClient.performCommandLineSetup()
                    isConnected = executionService.tryInitialize(true)
                }
            }
        } else {
            executionService.initialize()
        }
    }

    static void initializeService(boolean fullSetup) {
        logger.postSometimesInfo("public static void initializeService(boolean fullSetup)")

        if (!fullSetup) {
            executionService = new NoNoExecutionService()
            return
        }

        ClassLoader classLoader = LibrariesFactory.getGroovyClassLoader()

        RunMode runMode = Roddy.getRunMode()
        String executionServiceClassID = Roddy.applicationConfiguration.getOrSetApplicationProperty(runMode, Constants.APP_PROPERTY_EXECUTION_SERVICE_CLASS, SSHExecutionService.class.getName())
        try {
            Class executionServiceClass = classLoader.loadClass(executionServiceClassID)
            initializeService(executionServiceClass, runMode)
        } catch (ClassNotFoundException) {
            throw new ConfigurationLoaderException("Could not load JobManager class ${executionServiceClassID}")
        }
    }

    ExecutionService() {
    }

    static ExecutionService getInstance() {
        return executionService
    }

    void addSpecificSettingsToConfiguration(Configuration configuration) {

    }

    boolean isLocalService() {
        return true
    }

    boolean initialize() {
    }

    boolean tryInitialize(boolean waitFor) {
        try {
            long t1 = System.nanoTime()
            logger.postSometimesInfo(RoddyIOHelperMethods.printTimingInfo("initialize exec service", t1, System.nanoTime()))
            return true
        } catch (Exception ex) {
            return false
        }
    }

    boolean tryInitialize() {
        try {
            return initialize()
        } catch (Exception ex) {
            return false
        }
    }

    void destroy() {
//        this.listOfListeners.clear();
    }

    protected abstract ExecutionResult _execute(String string, boolean waitFor, boolean ignoreErrors, OutputStream outputStream)

    /**
     * Directly pass parameters to a tool script to be executed remotely. Use this method if you have a script in your plugin
     * whose execution is required during the workflow set-up. E.g. if you need the number of read-groups in the input BAM
     * during submission time.
     *
     * The tool is executed just like a normal tool but using the DirectSynchronousExecutionJobManager (so no PBS, GE, or LSF).
     *
     * Note that this tool may be executed both in the initial phase and the final phase during a testrerun. If you want to avoid
     * double execution, use some sort of caching in the calling code.
     *
     * @param context
     * @param toolID
     * @param parameters
     * @param jobNameExtension
     * @return
     */
    List<String> runDirect(ExecutionContext context, String toolID, Map<String, Object> parameters = null) {

        if (!parameters) parameters = [:]
        parameters[DISABLE_DEBUG_OPTIONS_FOR_TOOLSCRIPT] = "true"
        parameters[CVALUE_PLACEHOLDER_RODDY_SCRATCH_RAW] = context.getTemporaryDirectory()

        Job wrapperJob = new Job(context, context.getTimestampString() + "_directTool:" + toolID, toolID, parameters)
        DirectSynchronousExecutionJobManager jobManager =
                new DirectSynchronousExecutionJobManager(getInstance(), JobManagerOptions.create().setStrictMode(false).build())
        wrapperJob.setJobManager(jobManager)
        BEJobResult result = wrapperJob.run(false)

        // Getting complicated from here on. We need to replicate some code.

        // If QUERY_STATUS is set as the context status, some parts of the above .run() will not be executed and need to be mimicked (for now).
        if (!context.executionContextLevel.allowedToSubmitJobs) {
            wrapperJob.storeJobConfigurationFile(wrapperJob.createJobConfiguration())
            wrapperJob.keepOnlyEssentialParameters()

            result = jobManager.submitJob(wrapperJob)
        }

        File jobLog = new File(wrapperJob.jobLog.getOut(wrapperJob.jobID.toString()))
        if (!FileSystemAccessProvider.instance.fileExists(jobLog))
            throw new IOException("Job log file ${jobLog} does not exist")

        Long jobLogSize = FileSystemAccessProvider.instance.fileSize(jobLog)
        Long maximumJobLogSize = context.configurationValues.get("maximumJobLogSize", (1024*1024*10).toString()).toLong()
        if (jobLogSize > maximumJobLogSize)
            throw new IOException("Job log file of ${jobLogSize} byte is larger than permitted size ${maximumJobLogSize}")


        def lines = FileSystemAccessProvider.instance.loadTextFile(jobLog)

        if (lines != null) {

            if (!result.successful)
                throw new ExecutionException("Execution failed. Output was: '" + result.resultLines.orElse([]).join("\n") + "'", null)

            // Depending on which debug options are set, it is possible (set -v), that "Wrapped script ended"
            // is found before "Starting wrapped script". It is however safe to compare the whole "Starting wrapped script"
            // string to find the beginning of the output block of the executed wrapped script. Therefore we
            // use this to cut away misleading trailing output and then find the end block afterwards.
            // The end block can contain a "+ " at the beginning (again depending on the debug options), so we use
            // the find method to identify the first upcoming line.
            int index = lines.findIndexOf {
                it == "######################################################### Starting wrapped script ###########################################################"
            }
            if (index < 0)
                new ParseException("Could not find '#+ Starting wrapped script #+' in job log '${jobLog}' during synchronous script execution", -1)
            lines = lines[1 + index..-1]
            index = lines.findIndexOf { String it ->
                it.contains("######################################################### Wrapped script ended ##############################################################")
            }
            if (index < 0)
                new ParseException("Could not find '#+ Wrapped script ended #+' in job log '${jobLog}' during synchronous script execution", -1)
            lines = lines[0..index - 1]

            return lines as List<String>
        } else {
            return []
        }

    }

    List<String> executeTool(ExecutionContext context, String toolID, String jobNameExtension = "_executedScript:") {
        File path = context.getConfiguration().getProcessingToolPath(context, toolID)
        String cmd = FileSystemAccessProvider.getInstance().commandSet.getExecuteScriptCommand(path)
        return execute(cmd).resultLines
    }

    ExecutionResult execute(String string, boolean waitFor = true, OutputStream outputStream = null) {
        if (string) {
            return _execute(string, waitFor, true, outputStream)
        } else {
            return new ExecutionResult(false, -1, Arrays.asList("Command not valid. String is empty."), "")
        }
    }

    /** Create somehow valid submission IDs.
     *
     * @param command
     * @return
     */
    String validSubmissionCommandOutputWithRandomJobId(Command command) {
        if (command instanceof LSFCommand) {
            return String.format("<0%04d>", System.nanoTime() % 10000)
        } else if (command instanceof PBSCommand) {
            return String.format("0x%04X", System.nanoTime() % 10000)
        } else if (command instanceof SGECommand) {
            return String.format("dummy dummy 0%04d", System.nanoTime() % 10000)
        } else {
            throw new RuntimeException("Don't know how to create a valid dummy output for a '${command.getClass()}'")
        }
    }

    @Override
    ExecutionResult execute(Command command, boolean waitFor = true) {
        ExecutionContext context = ((Job) command.getJob()).getExecutionContext()
        ExecutionResult res

        String cmdString
        try {
            cmdString = command.toBashCommandString()

            OutputStream outputStream = createServiceBasedOutputStream(command, waitFor)

            if (context.getExecutionContextLevel() == ExecutionContextLevel.TESTRERUN) {
                String pid = validSubmissionCommandOutputWithRandomJobId(command)
                res = new ExecutionResult(true, 0, [pid], pid)
            } else {
                res = execute(cmdString, waitFor, outputStream)
            }
            command.getJob().setJobState(!res.successful ? JobState.FAILED : JobState.COMPLETED_SUCCESSFUL)

            if (outputStream)
                finalizeServiceBasedOutputStream(command, outputStream)

            context.addCalledCommand(command)
        } catch (Exception ex) {
            logger.log(Level.SEVERE, ex.toString())
        }
        return res
    }

    protected FileOutputStream createServiceBasedOutputStream(Command command, boolean waitFor) { return null }

    protected void finalizeServiceBasedOutputStream(Command command, OutputStream outputStream) {}

    static long measureStart() { return System.nanoTime() }

    static double measureStop(long startValue, String measurementID, int verbosityLevel = LoggerWrapper.VERBOSITY_RARE) {
        double t = (System.nanoTime() - startValue) / 1000000.0
        if (measurementID != null)
            logger.log(verbosityLevel, "Execution of ${measurementID} took ${t} ms.")
        return t
    }

    /**
     * Check, if access rights setting is enabled and also, if the user and the group exist.
     *
     * @param context
     * @return
     */
    boolean checkAccessRightsSettings(ExecutionContext context) {
        boolean valid = true
        if (context.isAccessRightsModificationAllowed()) {
//            context.getExecutingUser()

            def userGroup = context.getOutputGroupString()
            boolean isGroupAvailable = FileSystemAccessProvider.getInstance().isGroupAvailable(userGroup)
            if (!isGroupAvailable) {
                context.addErrorEntry(ExecutionContextError.EXECUTION_SETUP_INVALID.expand("The requested user group ${userGroup} is not available on the target system.\n\tDisable Roddys access rights managemd by setting outputAllowAccessRightsModification to true or\n\tSelect a proper group by setting outputFileGroup."))
                valid = false
            }
        }
        return valid
    }

    /**
     * Check different directories and files to see, if files are accessible.
     * Will not check everything but at least some of the well-known sources of errors.
     * The method also check for some files, if they exist. If so, the read / write access will be checked.
     * If a file does not exist yet, it will not be checked!
     * @param context The context object which needs to be checked.
     * @return
     */
    boolean checkContextDirectoriesAndFiles(ExecutionContext context) {
        FileSystemAccessProvider fsap = FileSystemAccessProvider.getInstance()
        Analysis analysis = context.getAnalysis()

        // First check in and output directories for accessibility

        // Project input directory with i.e. ../view-by-pid
        File inputBaseDirectory = analysis.getInputBaseDirectory()
        Boolean inputIsReadable = fsap.isReadable(inputBaseDirectory)
        if (!inputIsReadable)
            context.addErrorEntry(ExecutionContextError.EXECUTION_PATH_NOTFOUND_WARN.
                    expand("The input base directory is not readable: ${inputBaseDirectory}, please check access rights and ownership.", Level.SEVERE))

        // Project output base directory with i.e. ../results_per_pid
        File outputBaseDirectory = analysis.getOutputBaseDirectory()
        Boolean outputIsWriteable =
                fsap.directoryExists(outputBaseDirectory) ? fsap.isReadable(outputBaseDirectory) && fsap.isWritable(outputBaseDirectory) : null
        if (outputIsWriteable == null)
            context.addErrorEntry(ExecutionContextError.EXECUTION_PATH_NOTFOUND_WARN.
                    expand("Output base directory is missing: ${outputBaseDirectory}, please create with proper access rights and ownership.", Level.SEVERE))
        else if (outputIsWriteable == Boolean.FALSE) //Do an else if because groovy might evaluate null to false.
            context.addErrorEntry(ExecutionContextError.EXECUTION_PATH_NOTWRITABLE.
                    expand("Output base directory is not writable: ${outputBaseDirectory}, please change access rights and ownership."))

        // Output with dataset id
        File outputDirectory = context.getOutputDirectory()
        Boolean datasetDirIsWritable =
                fsap.directoryExists(outputDirectory) ? fsap.isReadable(outputDirectory) && fsap.isWritable(outputDirectory) : null
        if (datasetDirIsWritable == null)
            context.addErrorEntry(ExecutionContextError.EXECUTION_PATH_NOTFOUND_WARN.
                    expand("Creating output directory: ${outputDirectory}", Level.INFO))
        else if (datasetDirIsWritable == Boolean.FALSE) //Do an else if because groovy might evalute null to false.
            context.addErrorEntry(ExecutionContextError.EXECUTION_PATH_NOTWRITABLE.
                    expand("Output directory is not writable: ${outputDirectory}"))

        // roddyExecutionStore in the dataset folder
        File baseContextExecutionDirectory = context.getRuntimeService().getBaseExecutionDirectory(context)
        Boolean baseContextDirIsWritable =
                fsap.directoryExists(baseContextExecutionDirectory) ? fsap.isReadable(baseContextExecutionDirectory) && fsap.isWritable(baseContextExecutionDirectory) : null
        if (baseContextDirIsWritable == Boolean.FALSE) //Do an else if because groovy might evaluate null to false.
            context.addErrorEntry(ExecutionContextError.EXECUTION_PATH_NOTWRITABLE.
                    expand("The datasets execution storage folder is not writable: ${baseContextExecutionDirectory}"))

        // the exec_... folder in the base context exec dir. (NOT CHECKED, created later!)
        File contextExecutionDirectory = context.getExecutionDirectory()

        // .roddyExecutionStore in outputBaseDirectory
        File projectExecutionDirectory = context.getCommonExecutionDirectory()
        Boolean projectExecutionContextDirIsWritable =
                fsap.directoryExists(projectExecutionDirectory) ? fsap.isReadable(projectExecutionDirectory) && fsap.directoryExists(projectExecutionDirectory) : null
        if (projectExecutionContextDirIsWritable == Boolean.FALSE)
            context.addErrorEntry(ExecutionContextError.EXECUTION_PATH_NOTWRITABLE.
                    expand("The project execution store is not writable: ${projectExecutionDirectory}"))

        // .roddyExecCache.txt containing the list of executed runs in the project output folder
        File projectExecCacheFile = context.getRuntimeService().getExecCacheFile(context.getAnalysis())
        Boolean projectExecCacheFileIsWritable =
                outputIsWriteable && fsap.fileExists(projectExecCacheFile) ? fsap.isReadable(projectExecCacheFile) && fsap.isWritable(projectExecCacheFile) : null
        if (projectExecCacheFileIsWritable == Boolean.FALSE)
            context.addErrorEntry(ExecutionContextError.EXECUTION_PATH_NOTWRITABLE.
                    expand("The projects execution cache file is not writable: ${projectExecCacheFile}"))

        // The md5 sum file in .roddyExecutionStore
        File projectToolsMD5SumFile = context.getFileForAnalysisToolsArchiveOverview()
        Boolean projectToolsMD5SumFileIsWritable =
                fsap.fileExists(projectToolsMD5SumFile) ? fsap.isReadable(projectToolsMD5SumFile) && fsap.isWritable(projectToolsMD5SumFile) : projectExecutionContextDirIsWritable
        if (projectToolsMD5SumFileIsWritable == Boolean.FALSE)
            context.addErrorEntry(ExecutionContextError.EXECUTION_PATH_NOTWRITABLE.
                    expand("The project md5sum file is not writable: ${projectToolsMD5SumFile}"))

        //Just check, if there were new errors.
        int countErrors = context.getErrors().sum(0) { ExecutionContextError ece -> ece.getErrorLevel() == Level.SEVERE ? 1 : 0 } as Integer
        return (context.getErrors().sum(0) { ExecutionContextError ece -> ece.getErrorLevel() == Level.SEVERE ? 1 : 0 } as Integer) - countErrors == 0
    }

    private class CompressedArchiveInfo {
        CompressedArchiveInfo(File localArchive, String md5, File folder) {
            this.localArchive = localArchive
            this.md5 = md5
            this.folder = folder
        }

        boolean validOnRemoteSite
        String md5
        File folder
        File localArchive
        File removeArchive
    }

    private long lastExecutionContextCheckpoint = -1100
    Map<File, CompressedArchiveInfo> mapOfPreviouslyCompressedArchivesByFolder = [:]

    /**
     * Writes files like the scripts in the analysisTools directory, the current configuration (shellscript and xml) and files with debug information to the runs logging directory.
     *
     * @param context
     */
    void writeFilesForExecution(ExecutionContext context) {
        context.setDetailedExecutionContextLevel(ExecutionContextSubLevel.RUN_SETUP_INIT)

        FileSystemAccessProvider provider = FileSystemAccessProvider.getInstance()
        ConfigurationFactory configurationFactory = ConfigurationFactory.getInstance()

        String analysisID = context.getAnalysis().getName()

        File execCacheFile = context.getRuntimeService().getExecCacheFile(context.getAnalysis())
        String execCacheFileLock = new File(execCacheFile.getAbsolutePath() + TILDE).getAbsolutePath()
        File executionBaseDirectory = context.getRuntimeService().getBaseExecutionDirectory(context)
        File executionDirectory = context.getExecutionDirectory()
        File analysisToolsDirectory = context.getAnalysisToolsDirectory()
        File temporaryDirectory = context.getTemporaryDirectory()
        File lockFilesDirectory = context.getLockFilesDirectory()
        File commonExecutionDirectory = context.getCommonExecutionDirectory()
        File roddyApplicationDirectory = Roddy.getApplicationDirectory()

        //Base path for the application. This path might not be available on the target system (i.e. because of ssh calls).
        File roddyBundledFilesDirectory = Roddy.getBundledFilesDirectory()

        provider.checkDirectories([executionBaseDirectory, executionDirectory, temporaryDirectory, lockFilesDirectory], context, true)
        if(context.executionContextLevel.allowedToSubmitJobs){
            logger.always("Creating the following execution directory to store information about this process:")
            logger.always("\t${executionDirectory.getAbsolutePath()}")
        }

        Configuration cfg = context.getConfiguration()
        def configurationValues = cfg.getConfigurationValues()

        getInstance().addSpecificSettingsToConfiguration(cfg)

        //Add feature toggles to configuration
        AvailableFeatureToggles.values().each {
            AvailableFeatureToggles toggle ->
                configurationValues.put(toggle.name(), ((Boolean) Roddy.getFeatureToggleValue(toggle)).toString(), CVALUE_TYPE_BOOLEAN)
        }

        configurationValues.put(RODDY_CVALUE_DIRECTORY_LOCKFILES, lockFilesDirectory.getAbsolutePath(), CVALUE_TYPE_PATH)
        configurationValues.put(RODDY_CVALUE_DIRECTORY_TEMP, temporaryDirectory.getAbsolutePath(), CVALUE_TYPE_PATH)
        configurationValues.put(RODDY_CVALUE_DIRECTORY_EXECUTION, executionDirectory.getAbsolutePath(), CVALUE_TYPE_PATH)
        configurationValues.put(RODDY_CVALUE_DIRECTORY_EXECUTION_COMMON, commonExecutionDirectory.getAbsolutePath(), CVALUE_TYPE_PATH)
        configurationValues.put(RODDY_CVALUE_DIRECTORY_RODDY_APPLICATION, roddyApplicationDirectory.getAbsolutePath(), CVALUE_TYPE_PATH)
        configurationValues.put(RODDY_CVALUE_DIRECTORY_BUNDLED_FILES, roddyBundledFilesDirectory.getAbsolutePath(), CVALUE_TYPE_PATH)
        configurationValues.put(RODDY_CVALUE_DIRECTORY_ANALYSIS_TOOLS, analysisToolsDirectory.getAbsolutePath(), CVALUE_TYPE_PATH)
        configurationValues.put(RODDY_CVALUE_JOBSTATE_LOGFILE, executionDirectory.getAbsolutePath() + FileSystemAccessProvider.getInstance().getPathSeparator() + RODDY_JOBSTATE_LOGFILE, CVALUE_TYPE_STRING)

        final boolean ATOMIC = true
        final boolean BLOCKING = true
        final boolean NON_BLOCKING = false

        provider.createFileWithDefaultAccessRights(ATOMIC, context.getRuntimeService().getJobStateLogFile(context), context, BLOCKING)
        provider.checkFile(execCacheFile, true, context)
        provider.appendLineToFile(ATOMIC, execCacheFile, String.format("%s,%s,%s", executionDirectory.getAbsolutePath(), analysisID, provider.callWhoAmI()), NON_BLOCKING)

        context.setDetailedExecutionContextLevel(ExecutionContextSubLevel.RUN_SETUP_COPY_TOOLS)
        copyAnalysisToolsForContext(context)

        context.setDetailedExecutionContextLevel(ExecutionContextSubLevel.RUN_SETUP_COPY_CONFIG)

        //Current version info strings.
        String versionInfo = "Roddy version: " + Roddy.getUsedRoddyVersion() + "\nLibrary info:\n" + LibrariesFactory.getInstance().getLoadedLibrariesInfoList().join("\n") + "\n"
        provider.writeTextFile(context.getRuntimeService().getRuntimeFile(context), versionInfo, context)

        //Current config
        String configText = ConfigurationConverter.convertAutomatically(context, cfg)
        provider.writeTextFile(context.getRuntimeService().getConfigurationFile(context), configText, context)

        //The application ini
        provider.copyFile(Roddy.getPropertiesFilePath(), new File(executionDirectory, Constants.APP_PROPERTIES_FILENAME), context)
        provider.writeTextFile(new File(executionDirectory, "roddyCall.sh"), Roddy.getApplicationDirectory().getAbsolutePath() + "/roddy.sh " + Roddy.getCommandLineCall().getArguments().join(StringConstants.WHITESPACE) + "\n", context)

        //Current configs xml files (default, user, pipeline config file)
        String configXML = new XMLConverter().convert(context, cfg)
        provider.writeTextFile(context.getRuntimeService().getXMLConfigurationFile(context), configXML, context)
        context.setDetailedExecutionContextLevel(ExecutionContextSubLevel.RUN_RUN)
    }

    /**
     * Checks all the files created in writeFilesForExecution
     * If strict mode is enabled, all missing files will lead to false.
     * If strict mode is disabled, only neccessary files are checked.
     * @return A descriptive list of missing files
     */
    List<String> checkForInaccessiblePreparedFiles(ExecutionContext context) {
        if (!context.getExecutionContextLevel().allowedToSubmitJobs) return []
        boolean strict = Roddy.isStrictModeEnabled()
        def runtimeService = context.getRuntimeService()

        List<String> inaccessibleNecessaryFiles = []
        List<String> inaccessibleIgnorableFiles = []

        // Use the context check methods, so we automatically get an error message
        [
                runtimeService.getBaseExecutionDirectory(context),
                context.getExecutionDirectory(),
                context.getAnalysisToolsDirectory(),
                context.getTemporaryDirectory(),
                context.getLockFilesDirectory(),
                context.getCommonExecutionDirectory(),
        ].each {
            if (!context.directoryIsAccessible(it)) inaccessibleNecessaryFiles << it.absolutePath
        }

        if (!context.fileIsAccessible(runtimeService.getJobStateLogFile(context))) inaccessibleNecessaryFiles << "JobState logfile"
        if (!context.fileIsAccessible(runtimeService.getConfigurationFile(context))) inaccessibleNecessaryFiles << "Runtime configuration file"

        // Check the ignorable files. It is still nice to see whether they are there
        if (!context.fileIsAccessible(runtimeService.getExecCacheFile(context.getAnalysis()))) inaccessibleIgnorableFiles << "Execution cache file"
        if (!context.fileIsAccessible(runtimeService.getRuntimeFile(context))) inaccessibleIgnorableFiles << "Runtime information file"
        if (!context.fileIsAccessible(new File(context.getExecutionDirectory(), Constants.APP_PROPERTIES_FILENAME))) inaccessibleIgnorableFiles << "Copy of application.ini file"
        if (!context.fileIsAccessible(runtimeService.getXMLConfigurationFile(context))) inaccessibleIgnorableFiles << "XML configuration file"

        // Return true, if the neccessary files are there and if strict mode is enabled and in this case all ignorable files exist
        return inaccessibleNecessaryFiles + (strict ? [] as List<String> : inaccessibleIgnorableFiles)
    }

    /**
     * Which tools should be checked?
     * My best guess is to check all the defined tools.
     * @return
     */
    boolean checkCopiedAnalysisTools(ExecutionContext context) {
        boolean checked = true
        for (ToolEntry tool in context.getConfiguration().getTools().getAllValuesAsList()) {
            File toolPath = context.configuration.getProcessingToolPath(context, tool.id)
            checked &= context.fileIsExecutable(toolPath)
        }
        return checked
    }

    void markConfiguredToolsAsExecutable(ExecutionContext context) {
        logger.postSometimesInfo("BEExecutionService.markConfiguredToolsAsExecutable is not implemented yet! Only checks for executability are available.")
//        context.getConfiguration().getTools().each {
//            ToolEntry tool ->
//                File toolPath = context.configuration.getProcessingToolPath(context, tool.id)
//
//                def instance = FileSystemAccessProvider.getInstance()
//
//                instance.setDefaultAccessRights()
//
//        }
    }

    /**
     * Copy and link analysis tools folders to the target analysis tools directory. Analysis tools folder names must be unique over all plugins.
     * However this is not enforced.
     * @param context
     */
    void copyAnalysisToolsForContext(ExecutionContext context) {
        FileSystemAccessProvider provider = FileSystemAccessProvider.getInstance()
        Configuration cfg = context.getConfiguration()
        File dstExecutionDirectory = context.getExecutionDirectory()
        File dstAnalysisToolsDirectory = context.getAnalysisToolsDirectory()

        //Current analysisTools directory (they are also used for execution)
        Map<File, PluginInfo> sourcePaths = [:]
        for (PluginInfo pluginInfo : LibrariesFactory.getInstance().getLoadedPlugins()) {
            pluginInfo.getToolsDirectories().values().each { sourcePaths[it] = pluginInfo }
        }

        provider.checkDirectory(dstExecutionDirectory, context, true)

        String[] existingArchives = provider.loadTextFile(context.getFileForAnalysisToolsArchiveOverview())
        if (existingArchives == null)
            existingArchives = new String[0]
        Roddy.getCompressedAnalysisToolsDirectory().mkdir()

        Map<File, PluginInfo> listOfFolders = sourcePaths.findAll { File it, PluginInfo pInfo -> !it.getName().contains(".svn") }

        //Add used base paths to configuration.
        listOfFolders.each {
            File folder, PluginInfo pInfo ->
                def bPathID = folder.getName()
                String basepathConfigurationID = ConfigurationConverter.createVariableName(ConfigurationConstants.CVALUE_PREFIX_BASEPATH, bPathID)
                cfg.getConfigurationValues().add(new ConfigurationValue(basepathConfigurationID, RoddyIOHelperMethods.assembleLocalPath(dstExecutionDirectory, RuntimeService.DIRNAME_ANALYSIS_TOOLS, bPathID).getAbsolutePath(), "string"))
        }

        Map<String, List<Map<String, String>>> mapOfInlineScripts = [:]

        for (ToolEntry tool in cfg.getTools().allValuesAsList) {
            if (tool.hasInlineScript()) {
                mapOfInlineScripts.get(tool.basePathId, []) << ["inlineScript": tool.getInlineScript(), "inlineScriptName": tool.getInlineScriptName()]
            }
        }

        long startParallelCompression = System.nanoTime()

        // Check and override the listOfFolders, eventually create new temporary folders, if inline scripts are used
        listOfFolders = writeInlineScriptsAndCorrectListOfFolders(listOfFolders, mapOfInlineScripts)

        // Compress the new (or old) folder list.
        compressToolFolders(listOfFolders, mapOfInlineScripts)
        logger.postRareInfo("Overall tool compression took ${(System.nanoTime() - startParallelCompression) / 1000000} ms.")

        // Now check if the local file with its md5 sum exists on the remote site.
        moveCompressedToolFilesToRemoteLocation(listOfFolders, existingArchives, provider, context)

        markConfiguredToolsAsExecutable(context)
    }

    /**
     * Add inline scripts and compress existing tool folders to a central location and generate some md5 sums for them.
     *
     * 1. Create a new temp folder
     * 2.a If there are inline scripts in the folder:
     *  3. copy the script subfolder to the newly created temp folder AND
     *  4. Copy all inline scripts to files.
     *  5. Create a corrected entry in the new map
     * 2.b If not, put the original folder entry to the map
     *
     * @param listOfFolders
     * @param mapOfInlineScriptsBySubfolder - Map<SubfolderName,ScriptName>
     */
    Map<File, PluginInfo> writeInlineScriptsAndCorrectListOfFolders(Map<File, PluginInfo> listOfFolders, Map<String, List<Map<String, String>>> mapOfInlineScriptsBySubfolder) {

        Map<File, PluginInfo> correctedListOfFolders = [:]

        listOfFolders.keySet().parallelStream().each {
            File subFolder ->
                if (!subFolder.isDirectory()) return
                PluginInfo pInfo = listOfFolders[subFolder]

                if (!mapOfInlineScriptsBySubfolder.containsKey(subFolder.getName())) {
                    correctedListOfFolders[subFolder] = pInfo
                    return
                }

                // Create the temp folder
                File tempFolder = File.createTempDir()
                tempFolder.deleteOnExit()
                tempFolder = RoddyIOHelperMethods.assembleLocalPath(tempFolder, subFolder.getName())

                // Copy the original scripts to the new folder
                RoddyIOHelperMethods.copyDirectory(subFolder, tempFolder)
                logger.postSometimesInfo("Folder ${subFolder.getName()} copied to ${tempFolder.getAbsolutePath()}")

                // Create inline script files in new folder
                mapOfInlineScriptsBySubfolder[subFolder.getName()].each {
                    scriptEntry ->
                        new File(tempFolder, scriptEntry["inlineScriptName"]) << scriptEntry["inlineScript"]
                }
                correctedListOfFolders[tempFolder] = pInfo
        }
        return correctedListOfFolders
    }

    /**
     * Compress all folders in the given list of folders.
     * Also create some sort of (md5 based) checksum for this.
     * @param listOfFolders
     * @param mapOfInlineScriptsBySubfolder
     */
    void compressToolFolders(Map<File, PluginInfo> listOfFolders, Map<String, List<Map<String, String>>> mapOfInlineScriptsBySubfolder) {
        listOfFolders.keySet().parallelStream().each {
            File subFolder ->
                long startSingleCompression = System.nanoTime()

                PluginInfo pInfo = listOfFolders[subFolder]
                // Md5sum from tempFolder
                String md5sum = RoddyIOHelperMethods.getSingleMD5OfFilesInDirectoryIncludingDirectoryNamesAndPermissions(subFolder)
                String zipFilename = "cTools_${pInfo.getName()}:${pInfo.getProdVersion()}_${subFolder.getName()}.zip"
                String zipMD5Filename = zipFilename + "_contentmd5"
                File tempFile = new File(Roddy.getCompressedAnalysisToolsDirectory(), zipFilename)
                File zipMD5File = new File(Roddy.getCompressedAnalysisToolsDirectory(), zipMD5Filename)
                boolean createNew = false
                if (!tempFile.exists())
                    createNew = true

                if (!zipMD5File.exists() || zipMD5File.text.trim() != md5sum)
                    createNew = true

                if (createNew) {
                    RoddyIOHelperMethods.compressDirectory(subFolder, tempFile)
                    zipMD5File << md5sum
                }

                String newArchiveMD5 = md5sum
                if (tempFile.size() == 0)
                    logger.severe("The size of archive ${tempFile.getName()} is 0!")
                synchronized (mapOfPreviouslyCompressedArchivesByFolder) {
                    mapOfPreviouslyCompressedArchivesByFolder[subFolder] = new CompressedArchiveInfo(tempFile, newArchiveMD5, subFolder)
                }
                logger.postSometimesInfo("Compression of ${zipFilename} took ${(System.nanoTime() - startSingleCompression) / 1000000} ms.")
        }
    }

    /**
     * Check if the local file with its md5 sum exists on the remote site otherwise move those files from local to remote site.
     * @param listOfFolders
     * @param existingArchives
     * @param provider
     * @param context
     */
    void moveCompressedToolFilesToRemoteLocation(Map<File, PluginInfo> listOfFolders, String[] existingArchives, FileSystemAccessProvider provider, ExecutionContext context) {
        File dstCommonExecutionDirectory = context.getCommonExecutionDirectory()
        File dstAnalysisToolsDirectory = context.getAnalysisToolsDirectory()

        listOfFolders.each {
            File subFolder, PluginInfo pInfo ->
                if (!subFolder.isDirectory())
                    return
                File localFile = mapOfPreviouslyCompressedArchivesByFolder[subFolder].localArchive
                File remoteFile = new File(mapOfPreviouslyCompressedArchivesByFolder[subFolder].localArchive.getName()[0..-5] + "_" + context.getTimestampString() + ".zip")
                String archiveMD5 = mapOfPreviouslyCompressedArchivesByFolder[subFolder].md5

                String foundExisting = null
                String subFolderOnRemote = subFolder.getName()
                for (String line : existingArchives) {
                    String[] split = line.split(StringConstants.SPLIT_COLON)
                    String existingFilePath = split[0]
                    String existingFileMD5 = split[1]
                    if (split.length == 2) {
                        existingFilePath = split[0]
                        existingFileMD5 = split[1]
                    } else if (split.length == 3) {                   // Newer Roddy version create directories containing version strings (separated by ":")
                        existingFilePath = split[0] + ":" + split[1]
                        existingFileMD5 = split[2]
                    } else {
                        continue
                    }

                    if (existingFileMD5.equals(archiveMD5)) {
                        foundExisting = existingFilePath
                        remoteFile = new File(remoteFile.getParentFile(), existingFilePath)
                        subFolderOnRemote = remoteFile.getName().split(StringConstants.SPLIT_UNDERSCORE)[-3]
                        //TODO This is seriously a hack.
                        break
                    }
                }

                File analysisToolsServerDir

                // Check, if there is a zip file available and if the zip file is uncompressed.
                if (foundExisting) {
                    analysisToolsServerDir = new File(dstCommonExecutionDirectory, "/dir_" + foundExisting)
                    File remoteZipFile = new File(dstCommonExecutionDirectory, remoteFile.getName())
                    if (!provider.directoryExists(analysisToolsServerDir)) {
                        //Now we may assume, that the file was not uncompressed!
                        //Check if the zip file exists. If so, uncompress it.
                        if (provider.fileExists(remoteZipFile)) {
                            // Unzip the file again. foundExisting stays true
                            GString str = RoddyIOHelperMethods.getCompressor().getDecompressionString(remoteZipFile, analysisToolsServerDir, analysisToolsServerDir)
                            getInstance().execute(str, true)
                            provider.setDefaultAccessRightsRecursively(new File(analysisToolsServerDir.getAbsolutePath()), context)
                        } else {
                            // Uh Oh, the file is not existing, the directory is not existing! Copy again and unzip
                            foundExisting = false
                        }
                    }
                }

                if (foundExisting) {
                    //remoteFile.delete(); //Don't need that anymore
                    analysisToolsServerDir = new File(dstCommonExecutionDirectory, "/dir_" + foundExisting)
                    logger.postSometimesInfo("Skipping copy of file ${remoteFile.getName()}, a file with the same md5 was found.")
                } else {

                    analysisToolsServerDir = new File(dstCommonExecutionDirectory, "/dir_" + remoteFile.getName())
                    provider.checkDirectory(dstCommonExecutionDirectory, context, true)
                    provider.checkDirectory(analysisToolsServerDir, context, true)
                    provider.copyFile(localFile, new File(dstCommonExecutionDirectory, remoteFile.getName()), context)
                    provider.checkFile(context.getFileForAnalysisToolsArchiveOverview(), true, context)
                    provider.appendLineToFile(true, context.getFileForAnalysisToolsArchiveOverview(), "${remoteFile.getName()}:${archiveMD5}", true)

                    GString str = RoddyIOHelperMethods.getCompressor().getDecompressionString(new File(dstCommonExecutionDirectory, remoteFile.getName()), analysisToolsServerDir, analysisToolsServerDir)
                    getInstance().execute(str, true)
                    provider.setDefaultAccessRightsRecursively(new File(analysisToolsServerDir.getAbsolutePath()), context)
                    if (!provider.directoryExists(analysisToolsServerDir))
                        context.addErrorEntry(ExecutionContextError.EXECUTION_PATH_NOTFOUND.expand("The central archive ${analysisToolsServerDir.absolutePath} was not created!"))

                }
                provider.checkDirectory(dstAnalysisToolsDirectory, context, true)
                def linkCommand = "ln -s ${analysisToolsServerDir.getAbsolutePath()}/${subFolderOnRemote} ${dstAnalysisToolsDirectory.absolutePath}/${subFolder.getName()}"
                getInstance().execute(linkCommand, true)
        }
    }

    /**
     * Writes things like a command call file
     *
     * @param context
     */
    void writeAdditionalFilesAfterExecution(ExecutionContext context) {
        if (!context.getExecutionContextLevel().allowedToSubmitJobs) return

        context.setDetailedExecutionContextLevel(ExecutionContextSubLevel.RUN_FINALIZE_CREATE_JOBFILES)

        final FileSystemAccessProvider provider = FileSystemAccessProvider.getInstance()
        final String separator = Constants.ENV_LINESEPARATOR

        List<Command> commandCalls = context.getCommandCalls() ?: new LinkedList<Command>()
        StringBuilder realCalls = new StringBuilder()
        List<BEJobID> jobIDs = new LinkedList<>()
        int cnt = 0
        Map<String, String> jobIDReplacement = new HashMap<String, String>()
        StringBuilder repeatCalls = new StringBuilder()

        for (Command c : commandCalls) {
            BEJobID eID = c.getJobID()
            if (eID != null) {
                jobIDs.add(eID)
                if (eID.getShortID() != null) {
                    jobIDReplacement.put(eID.getShortID(), String.format('$j_' + "%08d", cnt++))
                }
            }
        }
        for (Command c : commandCalls) {
            BEJobID eID = c.getJobID()
            String cmdStr = c.toString()
            realCalls.append(eID).append(", ").append(cmdStr).append(separator)

            String repeatCallLine = String.format("%s=`%s`" + separator, eID, cmdStr)
            for (String k : jobIDReplacement.keySet()) {
                String v = jobIDReplacement.get(k)

                repeatCallLine = repeatCallLine.replace(k, v)
            }
            repeatCalls.append(repeatCallLine.substring(1))
        }

        context.setDetailedExecutionContextLevel(ExecutionContextSubLevel.RUN_FINALIZE_CREATE_BINARYFILES)

        RuntimeService rService = context.getRuntimeService()
        final File realCallFile = rService.getRealCallsFile(context)
        final File repeatCallFile = rService.getRepeatableJobCallsFile(context)
        provider.writeTextFile(realCallFile, realCalls.toString(), context)
        provider.writeTextFile(repeatCallFile, repeatCalls.toString(), context)
        rService.writeJobInfoFile(context)
    }

    boolean doesKnowTheUsername() { return false }

    String getUsername() {}

    boolean canCopyFiles() { return false }

    boolean canReadFiles() { return false }

    boolean canWriteFiles() { return false }

    boolean canDeleteFiles() { false }

    boolean canListFiles() { return false }

    boolean canModifyAccessRights() { return false }

    boolean canQueryFileAttributes() { return false }

    boolean writeTextFile(File file, String text) {}

    boolean writeBinaryFile(File file, Serializable serializable) {}

    boolean copyFile(File _in, File _out) {}

    boolean copyDirectory(File _in, File _out) {}

    boolean moveFile(File _from, File _to) {}

    boolean modifyAccessRights(File file, String rightsStr, String groupID) {}

    boolean createFileWithRights(boolean atomic, File file, String accessRights, String groupID, boolean blocking) {}

    boolean removeDirectory(File directory) {}

    boolean removeFile(File file) {}

    boolean appendLinesToFile(boolean atomic, File file, List<String> lines, boolean blocking) {}

    boolean appendLineToFile(boolean atomic, File file, String line, boolean blocking) {}

    Object loadBinaryFile(File file) {}

    String[] loadTextFile(File file) {}

    List<File> listFiles(File file, List<String> filters) {}

    List<File> listFiles(List<File> file, List<String> filters) {}

    boolean fileExists(File f) { return false }

    boolean directoryExists(File f) { return false }

    boolean isFileReadable(File f) {}

    boolean isFileWriteable(File f) {}

    boolean isFileExecutable(File f) {}

    FileAttributes queryFileAttributes(File file) {}

    Map<File, Boolean> getReadabilityOfAllFiles(List<File> listOfFiles) {
        List<File> allPaths = []
        for (File file in listOfFiles) {
            if (file == null) {
                //TODO Print out an error message? Or just ignore this because the error is already noted somewhere else.
                continue
            }
            if (!allPaths.contains(file.parentFile))
                allPaths << file.parentFile
        }

        Map<File, Boolean> readability = [:]
        List<File> allFiles = listFiles(allPaths, null)
        for (File file in listOfFiles)
            readability[file] = allFiles.contains(file)

        return readability
    }

    boolean needsPassword() { return false }

    boolean testConnection() { return true }

    /**
     * For remote files
     * @param file
     * @return
     */
    File getTemporaryFileForFile(File file) { return null }

    abstract boolean isAvailable()
}
