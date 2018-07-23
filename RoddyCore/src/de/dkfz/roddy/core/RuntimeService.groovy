/*
 * Copyright (c) 2018 German Cancer Research Center (DKFZ).
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.core

import de.dkfz.roddy.config.Configuration
import de.dkfz.roddy.config.ConfigurationError
import de.dkfz.roddy.config.ConfigurationValue
import de.dkfz.roddy.execution.jobs.Job
import de.dkfz.roddy.Constants
import de.dkfz.roddy.Roddy
import de.dkfz.roddy.StringConstants
import de.dkfz.roddy.config.ConfigurationConstants
import de.dkfz.roddy.config.ToolEntry
import de.dkfz.roddy.execution.io.BaseMetadataTable
import de.dkfz.roddy.execution.io.MetadataTableFactory
import de.dkfz.roddy.execution.io.fs.FileSystemAccessProvider
import de.dkfz.roddy.knowledge.files.BaseFile
import de.dkfz.roddy.tools.LoggerWrapper
import de.dkfz.roddy.tools.RoddyIOHelperMethods
import groovy.transform.CompileStatic
import org.apache.commons.io.filefilter.WildcardFileFilter

/**
 * A RuntimeService provides path calculations for file access.
 * Project specific runtime service instances should also provide methods for those projects like the data set collection, information retrieval and caching...
 * Runtime services should not be created directly. Instead use getInstance to get a provider.
 *
 * @author michael
 */
@CompileStatic
class RuntimeService {
    private static LoggerWrapper logger = LoggerWrapper.getLogger(RuntimeService.class.getSimpleName())
    public static final String FILENAME_RUNTIME_INFO = "versionsInfo.txt"
    public static final String FILENAME_RUNTIME_CONFIGURATION = "runtimeConfig.sh"
    public static final String FILENAME_RUNTIME_CONFIGURATION_XML = "runtimeConfig.xml"
    public static final String FILENAME_REALJOBCALLS = "realJobCalls.txt"
    public static final String FILENAME_REPEATABLEJOBCALLS = "repeatableJobCalls.sh"
    public static final String FILENAME_EXECUTEDJOBS_INFO = "executedJobs.txt"
    public static final String FILENAME_ANALYSES_MD5_OVERVIEW = "zippedAnalysesMD5.txt"
    public static final String DIRECTORY_RODDY_COMMON_EXECUTION = ".roddyExecutionStore"
    public static final String DIRNAME_RESOURCES = "resources"
    public static final String DIRNAME_ANALYSIS_TOOLS = "analysisTools"
    public static final String DIRNAME_BRAWLWORKFLOWS = "brawlworkflows"
    public static final String DIRNAME_CONFIG_FILES = "configurationFiles"
    public static final String RODDY_CENTRAL_EXECUTION_DIRECTORY = "RODDY_CENTRAL_EXECUTION_DIRECTORY"

    RuntimeService() {
        logger.warning("Reading in jobs is not fully enabled! See RuntimeService readInExecutionContext(). The method does not reconstruct parent files and dependencies.")
    }

    /**
     * Loads a list of input data set for the specified analysis.
     * In this method the data set is defined by its main directory (i.e. a pid like ICGC_PCA004)
     *
     * @param analysis
     * @return
     */
    List<DataSet> loadListOfInputDataSets(Analysis analysis) {
        //TODO: Better logging if the directory could not be read or no files were found.
        def directory = analysis.getInputBaseDirectory()
        return loadDataSetsFromDirectory(directory, analysis)
    }

    List<DataSet> loadListOfOutputDataSets(Analysis analysis) {
        def directory = analysis.getOutputBaseDirectory()
        return loadDataSetsFromDirectory(directory, analysis)
    }

    List<DataSet> loadCombinedListOfPossibleDataSets(Analysis analysis) {

        if (Roddy.isMetadataCLOptionSet()) {

            BaseMetadataTable table = MetadataTableFactory.getTable(analysis)
            List<String> _datasets = table.listDatasets()
            String pOut = analysis.getOutputBaseDirectory().getAbsolutePath() + File.separator
            return _datasets.collect { new DataSet(analysis, it, new File(pOut + it), table) }

        } else {

            List<DataSet> lid = loadListOfInputDataSets(analysis)
            List<DataSet> lod = loadListOfOutputDataSets(analysis)

            //Now combine lid and lod.
            Collection<DataSet> additional = lod.findAll {
                DataSet ds -> !lid.find { DataSet inLid -> inLid.getId() == ds.getId() }
            }
            lid += additional.each { DataSet ds -> ds.setAsAvailableInOutputOnly() }
            lid.removeAll { DataSet ds -> ds.getId().startsWith(".roddy") } //Filter out roddy specific files or folders.
            lid.sort { DataSet a, DataSet b -> a.getId().compareTo(b.getId()) }
            logger.postAlwaysInfo("Found ${lid.size()} datasets in the in- and output directories.")
            return lid
        }
    }

    /**
     * The method looks for datasets / directories in a path.
     * TODO This is project specific and should be handled as such in the future. Some projects i.e. rely on cohorts and not single datasets.
     *
     * @param directory
     * @param analysis
     * @return
     */
    private static LinkedList<DataSet> loadDataSetsFromDirectory(File directory, Analysis analysis) {
        logger.postRareInfo("Looking for datasets in ${directory.absolutePath}.")

        FileSystemAccessProvider fip = FileSystemAccessProvider.getInstance()

        List<DataSet> results = new LinkedList<DataSet>()
        List<File> files = fip.listDirectoriesInDirectory(directory)

        logger.postSometimesInfo("Found ${files.size()} datasets in ${directory.absolutePath}.")

        File pOut = analysis.getOutputBaseDirectory()
        String pOutStr = pOut.getAbsolutePath()
        for (File f : files) {
            String id = f.getName()
            //TODO: Correct writeConfigurationFile of the output directory! like basepath/dataSet/analysis/
            DataSet dataSet = new DataSet(analysis, id, new File(pOutStr + File.separator + id))
            results.add(dataSet)
        }
        return results
    }

    /**
     * A small cache which prevents Roddy from querying the file system for datasets several times.
     */
    private Map<Analysis, List<DataSet>> _listOfPossibleDataSetsByAnalysis = [:]

    /**
     * Fetches information to the projects data sets and various analysis related additional information for those data sets.
     * Retrieves the data sets for this analysis from it's project and appends the data for this analysis (if not already set).
     * <p>
     * getListOfPossibleDataSets without a parameter
     *
     * @return
     */
    List<DataSet>  getListOfPossibleDataSets(Analysis analysis, boolean avoidRecursion = false) {

        if (_listOfPossibleDataSetsByAnalysis[analysis] == null)
            _listOfPossibleDataSetsByAnalysis[analysis] = loadCombinedListOfPossibleDataSets(analysis)

        if (!avoidRecursion) {
            List<AnalysisProcessingInformation> previousExecs = readoutExecCacheFile(analysis)

            for (DataSet ds : _listOfPossibleDataSetsByAnalysis[analysis]) {
                for (AnalysisProcessingInformation api : previousExecs) {
                    if (api.getDataSet() == ds) {
                        ds.addProcessingInformation(api)
                    }
                }
                analysis.getProject().updateDataSet(ds, analysis)
            }
        }

        return _listOfPossibleDataSetsByAnalysis[analysis]
    }

    List<DataSet> loadDatasetsWithFilter(Analysis analysis, List<String> pidFilters, boolean suppressInfo = false) {
        if (analysis.configuration?.configurationValues?.getBoolean("loadCohortDatasets", false)) {
            return new CohortDataRuntimeServiceExtension(this).loadCohortDatasetsWithFilter(analysis, pidFilters)
        } else {
            loadStandardDatasetsWithFilter(analysis, pidFilters, suppressInfo)
        }
    }

    /**
     * There are non-cohort (=standard) datasets and cohort datasets
     * */
    List<DataSet> loadStandardDatasetsWithFilter(Analysis analysis, List<String> pidFilters, boolean suppressInfo = false) {
        if (pidFilters == null || pidFilters.size() == 0 || pidFilters.size() == 1 && pidFilters.get(0).equals("[ALL]")) {
            pidFilters = Arrays.asList("*")
        }
        List<DataSet> listOfDataSets = getListOfPossibleDataSets(analysis)
        return selectDatasetsFromPattern(analysis, pidFilters, listOfDataSets, suppressInfo)
    }

    List<DataSet> selectDatasetsFromPattern(Analysis analysis, List<String> pidFilters, List<DataSet> listOfDataSets, boolean suppressInfo) {

        List<DataSet> selectedDatasets = new LinkedList<>()
        WildcardFileFilter wff = new WildcardFileFilter(pidFilters)
        for (DataSet ds : listOfDataSets) {
            File inputFolder = getInputAnalysisBaseDirectory(ds, analysis)
            if (!wff.accept(inputFolder))
                continue
            if (!suppressInfo) logger.info(String.format("Selected dataset %s for processing.", ds.getId()))
            selectedDatasets.add(ds)
        }

        if (selectedDatasets.size() == 0)
            logger.postAlwaysInfo("There were no available datasets for the provided pattern.")
        return selectedDatasets
    }

    ExecutionContext readInExecutionContext(AnalysisProcessingInformation api) {
        return new ExecutionContextReaderAndWriter(this).readInExecutionContext(api)
    }

    List<Job> readJobInfoFile(ExecutionContext context) {
        return new ExecutionContextReaderAndWriter(this).readJobInfoFile(context)
    }

    String writeJobInfoFile(ExecutionContext context) {
        return new ExecutionContextReaderAndWriter(this).writeJobInfoFile(context)
    }

    List<String> collectNamesOfRunsForPID(DataSet dataSet) {
        FileSystemAccessProvider fip = FileSystemAccessProvider.getInstance()
        List<File> execList = fip.listDirectoriesInDirectory(dataSet.getOutputBaseFolder(), Arrays.asList(ConfigurationConstants.RODDY_EXEC_DIR_PREFIX + "*"))
        List<String> names = new LinkedList<String>()
        for (File f : execList) {
            names.add(f.getName())
        }
        return names
    }

    File getDirectory(String dirName, ExecutionContext context) {
        return new File(getOutputAnalysisBaseDirectory(context), dirName)
    }


    File getBaseExecutionDirectory(ExecutionContext context) {
        String outPath = getOutputAnalysisBaseDirectory(context.getDataSet(), context.getAnalysis()).absolutePath
        String sep = FileSystemAccessProvider.getInstance().getPathSeparator()
        return new File("${outPath}${sep}roddyExecutionStore")
    }

    File getExecutionDirectory(ExecutionContext context) {
        if (context.hasExecutionDirectory())
            return context.getExecutionDirectory()

        String outPath = getOutputAnalysisBaseDirectory(context.getDataSet(), context.getAnalysis()).absolutePath
        String sep = FileSystemAccessProvider.getInstance().getPathSeparator()

        String dirPath = "${outPath}${sep}roddyExecutionStore${sep}${ConfigurationConstants.RODDY_EXEC_DIR_PREFIX}${context.getTimestampString()}_${context.getExecutingUser()}_${context.getAnalysis().getName()}"
        if (context.getExecutionContextLevel() == ExecutionContextLevel.CLEANUP)
            dirPath += "_cleanup"
        return new File(dirPath)
    }

    File getCommonExecutionDirectory(ExecutionContext context) {
        try {

            def values = context.getConfiguration().getConfigurationValues()
            if (values.hasValue(RODDY_CENTRAL_EXECUTION_DIRECTORY)) {
                File configuredPath = values.get(RODDY_CENTRAL_EXECUTION_DIRECTORY, null)?.toFile(context)
                if (configuredPath)
                    return configuredPath
            }
        } catch (Exception ex) {
            logger.severe("There was an error in toFile for cvalue ${RODDY_CENTRAL_EXECUTION_DIRECTORY} in RuntimeService:getCommonExecutionDirectory()")
        }
        return new File(getOutputBaseDirectory(context).getAbsolutePath() + FileSystemAccessProvider.getInstance().getPathSeparator() + DIRECTORY_RODDY_COMMON_EXECUTION)
    }

    File getAnalysedMD5OverviewFile(ExecutionContext context) {
        return new File(getCommonExecutionDirectory(context).getAbsolutePath(), FILENAME_ANALYSES_MD5_OVERVIEW)
    }

    File getLoggingDirectory(ExecutionContext context) {
        return getExecutionDirectory(context)
    }

    File getLockFilesDirectory(ExecutionContext context) {
        File f = new File(getTemporaryDirectory(context), "lockfiles")
        return f
    }

    File getTemporaryDirectory(ExecutionContext run) {
        return new File(getExecutionDirFilePrefixString(run) + "temp")
    }

    Date extractDateFromExecutionDirectory(File dir) {
        String[] splitted = dir.name.split(StringConstants.SPLIT_UNDERSCORE)
        if (splitted.size() < 3)
            throw new RuntimeException("Could not split presumable execution directory on '_' into at least three parts: ${dir.name}")
        return InfoObject.parseTimestampString(splitted[1] + StringConstants.SPLIT_UNDERSCORE + splitted[2])
    }

    private String getExecutionDirFilePrefixString(ExecutionContext run) {
        try {
            return String.format("%s%s", run.getExecutionDirectory().getAbsolutePath(), FileSystemAccessProvider.getInstance().getPathSeparator())
        } catch (Exception ex) {
            return String.format("%s%s", run.getExecutionDirectory().getAbsolutePath(), FileSystemAccessProvider.getInstance().getPathSeparator())
        }
    }

    File getConfigurationFile(ExecutionContext run) {
        return new File(getExecutionDirFilePrefixString(run) + FILENAME_RUNTIME_CONFIGURATION)
    }

    File getXMLConfigurationFile(ExecutionContext run) {
        return new File(getExecutionDirFilePrefixString(run) + FILENAME_RUNTIME_CONFIGURATION_XML)
    }

    File getRealCallsFile(ExecutionContext run) {
        return new File(getExecutionDirFilePrefixString(run) + FILENAME_REALJOBCALLS)
    }

    File getRepeatableJobCallsFile(ExecutionContext run) {
        return new File(getExecutionDirFilePrefixString(run) + FILENAME_REPEATABLEJOBCALLS)
    }

    File getJobInfoFile(ExecutionContext context) {
        return new File(getExecutionDirFilePrefixString(context) + FILENAME_EXECUTEDJOBS_INFO)
    }

    File getJobStateLogFile(ExecutionContext run) {
        return new File(run.getExecutionDirectory().getAbsolutePath() + FileSystemAccessProvider.getInstance().getPathSeparator() + ConfigurationConstants.RODDY_JOBSTATE_LOGFILE)
    }

    File getExecCacheFile(Analysis analysis) {
        return new File(analysis.getOutputBaseDirectory().getAbsolutePath() + FileSystemAccessProvider.getInstance().getPathSeparator() + ConfigurationConstants.RODDY_EXEC_CACHE_FILE)
    }

    File getRuntimeFile(ExecutionContext context) {
        return new File(getExecutionDirFilePrefixString(context) + FILENAME_RUNTIME_INFO)
    }

    /** Only the first matching ${dataSet} or ${pid} will be returned. ${dataSet} has precedence over ${pid}.
     *  No checks are done that there is a unique solution.
     * @param path
     * @param analysis
     * @return
     */
    String extractDataSetIDFromPath(File path, Analysis analysis) {
        String pattern = getOutputAnalysisBaseCV(analysis).toFile(analysis).getAbsolutePath()
        RoddyIOHelperMethods.getPatternVariableFromPath(pattern, Constants.DATASET, path.getAbsolutePath()).
                orElse(RoddyIOHelperMethods.getPatternVariableFromPath(pattern, Constants.PID, path.getAbsolutePath()).
                        orElse(Constants.UNKNOWN))
    }

    /**
     * Looks in the projects output directory if the cache file exists. If it does not exist it is created using the find command.
     * @param project
     */
    List<AnalysisProcessingInformation> readoutExecCacheFile(Analysis analysis) {
        File cacheFile = getExecCacheFile(analysis)
        String[] execCache = FileSystemAccessProvider.getInstance().loadTextFile(cacheFile)
        if (execCache == null)
            execCache = new String[0]
        List<AnalysisProcessingInformation> processInfo = []
        List<File> execDirectories = []
        Arrays.asList(execCache).parallelStream().each {
            String line ->
                if (line == "") return
                String[] info = line.split(",")
                if (info.length < 2) return
                File path = new File(info[0])
                execDirectories << path
                String dataSetID
                try {
                    dataSetID = analysis.getRuntimeService().extractDataSetIDFromPath(path, analysis)
                } catch (RuntimeException e) {
                    throw new RuntimeException(e.message + ". Please delete/backup '${cacheFile}' and restart Roddy.")
                }
                Analysis dataSetAnalysis = analysis.getProject().getAnalysis(info[1])
                DataSet ds = analysis.getDataSet(dataSetID)
                if (dataSetAnalysis == analysis) {
                    AnalysisProcessingInformation api = new AnalysisProcessingInformation(dataSetAnalysis, ds, path)
                    if (info.length > 3) {
                        String user = info[3]
                        api.setExecutingUser(user)
                    }
                    synchronized (processInfo) {
                        processInfo << api
                    }
                }
        }
        if (processInfo.size() == 0) {
            logger.postSometimesInfo("No process info objects could be matched for ${execCache.size()} lines in the cache file.")
            //TODO Possible input output directory mismatch or configuration error!
        }
        processInfo.sort { AnalysisProcessingInformation p1, AnalysisProcessingInformation p2 -> p1.getExecPath().absolutePath.compareTo(p2.getExecPath().getAbsolutePath()) }
        return processInfo
    }

    File getLogFileForJob(Job job) {
        //Returns the log files path of the job.
        Roddy.getJobManager().queryExtendedJobStateById([job.jobID]).get(job.jobID).logFile
    }

    boolean hasLogFileForJob(Job job) {
        return getLogFileForJob(job) != null
    }

    List<File> getAdditionalLogFilesForContext(ExecutionContext executionContext) {
        File loggingDirectory = executionContext.getLoggingDirectory()
        List<File> files = FileSystemAccessProvider.getInstance().listFilesInDirectory(loggingDirectory)
        List<File> notUsed = executionContext.getLogFilesForExecutedJobs()
        for (File f : notUsed)
            files.remove(f)
        return files
    }


    private static ConfigurationValue getInputBaseDirectoryCV(Configuration configuration) {
        ConfigurationValue inputBaseDirectory = configuration.getConfigurationValues().
                get(ConfigurationConstants.CFG_INPUT_BASE_DIRECTORY)
        if (inputBaseDirectory.toString() == "")
            throw new ConfigurationError("${ConfigurationConstants.CFG_INPUT_BASE_DIRECTORY} not set. Consider --useiodir command-line option.",
                    configuration, ConfigurationConstants.CFG_INPUT_BASE_DIRECTORY, null)
        return inputBaseDirectory
    }

    File getInputBaseDirectory(ExecutionContext context) {
        return getInputBaseDirectoryCV(context.configuration).toFile(context)
    }

    File getInputBaseDirectory(Analysis analysis) {
        return getInputBaseDirectoryCV(analysis.configuration).toFile(analysis)
    }

    File getInputBaseDirectory(DataSet dataset, Analysis analysis) {
        return getInputBaseDirectoryCV(analysis.configuration).toFile(analysis, dataset)
    }

    File getInputAnalysisBaseDirectory(DataSet dataSet, Analysis analysis) {
        // The default value was set to a value resulting in the same results as the previous version of this method, even if the
        // new inputAnalysisBaseDirectory variable is not defined.
        return analysis.getConfiguration().getConfigurationValues().
                get(ConfigurationConstants.CFG_INPUT_ANALYSIS_BASE_DIRECTORY,
                        "\${${ConfigurationConstants.CFG_INPUT_BASE_DIRECTORY}}/\${${Constants.DATASET}}").
                toFile(analysis, dataSet)
    }

    File getInputAnalysisBaseDirectory(ExecutionContext context) {
        return getInputAnalysisBaseDirectory(context.dataSet, context.analysis)
    }



    private static ConfigurationValue getOutputBaseDirectoryCV(Configuration configuration) {
        ConfigurationValue outputBaseDirectory = configuration.getConfigurationValues().
                get(ConfigurationConstants.CFG_OUTPUT_BASE_DIRECTORY)
        if (outputBaseDirectory.toString() == "")
            throw new ConfigurationError("${ConfigurationConstants.CFG_OUTPUT_BASE_DIRECTORY} not set. Consider --useiodir command-line option.",
                    configuration, ConfigurationConstants.CFG_OUTPUT_BASE_DIRECTORY, null)
        return outputBaseDirectory
    }

    File getOutputBaseDirectory(ExecutionContext context) {
        return getOutputBaseDirectoryCV(context.configuration).toFile(context)
    }

    File getOutputBaseDirectory(Analysis analysis) {
        return getOutputBaseDirectoryCV(analysis.configuration).toFile(analysis)
    }

    File getOutputBaseDirectory(DataSet dataSet, Analysis analysis) {
        return getOutputBaseDirectoryCV(analysis.configuration).toFile(analysis, dataSet)
    }

    /** Get the value of the outputAnalysisBaseDirectory variable given the ExecutionContext. If that variable is not defined fall back to
     *  outputBaseDirectory. In both cases context variables (e.g. "${dataset}") are substituted if possible.
     *
     * @param context
     * @return
     */
    private ConfigurationValue getOutputAnalysisBaseCV(Analysis analysis) {
        return analysis.getConfiguration().getConfigurationValues().
                get(ConfigurationConstants.CFG_OUTPUT_ANALYSIS_BASE_DIRECTORY,
                        getOutputBaseDirectory(analysis).toString())
    }

    File getOutputAnalysisBaseDirectory(DataSet dataSet, Analysis analysis) {
        return getOutputAnalysisBaseCV(analysis).toFile(analysis, dataSet)
    }

    File getOutputAnalysisBaseDirectory(ExecutionContext context) {
        return getOutputAnalysisBaseDirectory(context.dataSet, context.analysis)
    }




    File getAnalysisToolsDirectory(ExecutionContext executionContext) {
        File analysisToolsDirectory = new File(getExecutionDirectory(executionContext), DIRNAME_ANALYSIS_TOOLS)
        return analysisToolsDirectory
    }

    Map<String, Object> getDefaultJobParameters(ExecutionContext context, String TOOLID) {
        String dataset = context.getDataSet().toString()
        Map<String, Object> parameters = [
                (Constants.PID)          : (Object) dataset,
                (Constants.PID_CAP)      : dataset,
                (Constants.DATASET)      : dataset,
                (Constants.DATASET_CAP)  : dataset,
                (Constants.ANALYSIS_DIR) : context.getOutputDirectory().getParentFile().getParent()
        ]
        return parameters
    }

    String createJobName(ExecutionContext executionContext, BaseFile bf, String TOOLID, boolean reduceLevel) {
        return _createJobName(executionContext, bf, TOOLID, reduceLevel)
    }

    static String _createJobName(ExecutionContext executionContext, BaseFile bf, String TOOLID, boolean reduceLevel) {
        ExecutionContext rp = bf.getExecutionContext()
        String runtime = rp.getTimestampString()
        String pid = rp.getDataSet().getId()
        StringBuilder sb = new StringBuilder()
        sb.append("r").append(runtime).append("_").append(pid).append("_").append(TOOLID)
        return sb.toString()
    }

    /**
     * Checks if a folder is valid
     *
     * A folder is valid if:
     * <ul>
     *   <li>its parents are valid</li>
     *   <li>it was not created recently (within this context)</li>
     *   <li>it exists</li>
     *   <li>it can be validated (i.e. by its size or files, but not with a lengthy operation!)</li>
     * </ul>
     */
    boolean isFileValid(BaseFile baseFile) {

        //Parents valid?
        boolean parentsValid = true
        for (BaseFile bf in baseFile.parentFiles) {
            if (bf.isTemporaryFile()) continue //We do not check the existence of parent files which are temporary.
            if (bf.isSourceFile()) continue
            if (!bf.isFileValid()) {
                return false
            }
        }

        boolean result = true

        //Source files should be marked as such and checked in a different way. They are assumed to be valid.
        if (baseFile.isSourceFile())
            return true

        //Temporary files are also considered as valid.
        if (baseFile.isTemporaryFile())
            return true

        try {
            //Was freshly created?
            if (baseFile.creatingJobsResult != null && baseFile.creatingJobsResult.successful) {
                result = false
            }
        } catch (Exception ex) {
            result = false
        }

        try {
            //Does it exist and is it readable?
            if (result && !baseFile.isFileReadable()) {
                result = false
            }
        } catch (Exception ex) {
            result = false
        }

        try {
            //Can it be validated?
            //TODO basefiles are always validated!
            if (result && !baseFile.checkFileValidity()) {
                result = false
            }
        } catch (Exception ex) {
            result = false
        }

        // TODO? If the file is not valid then also temporary parent files should be invalidated! Or at least checked.
        if (!result) {
            // Something is missing here! Michael?
        }

        return result
    }

    @Deprecated
    void releaseCache() {}

    @Deprecated
    boolean initialize() {}

    @Deprecated
    void destroy() {
    }

    String calculateAutoCheckpointFilename(ToolEntry toolEntry, List<Object> parameters) {
        "AUTOCHECKPOINT_${toolEntry.id}_${(parameters.collect { it.toString().hashCode() }.join("") + "SAFEGUARDFOREMTPYLIST").hashCode()}"
    }

}
