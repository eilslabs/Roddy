/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.core

import de.dkfz.roddy.Constants
import de.dkfz.roddy.Roddy
import de.dkfz.roddy.StringConstants
import de.dkfz.roddy.client.RoddyStartupOptions
import de.dkfz.roddy.client.cliclient.RoddyCLIClient
import de.dkfz.roddy.config.*
import de.dkfz.roddy.config.loader.ConfigurationFactory
import de.dkfz.roddy.config.loader.ConfigurationLoaderException
import de.dkfz.roddy.config.validation.XSDValidator
import de.dkfz.roddy.execution.io.ExecutionService
import de.dkfz.roddy.execution.io.MetadataTableFactory
import de.dkfz.roddy.execution.io.fs.FileSystemAccessProvider
import de.dkfz.roddy.plugins.LibrariesFactory
import de.dkfz.roddy.plugins.PluginInfo
import de.dkfz.roddy.plugins.PluginInfoMap
import de.dkfz.roddy.plugins.PluginLoaderException
import de.dkfz.roddy.tools.RoddyIOHelperMethods

import java.lang.reflect.InvocationTargetException
import java.nio.channels.FileLock

import static de.dkfz.roddy.Constants.RODDY_CONFIGURATION_MAGICSTRING

/**
 * The project factory converts a configuration to a project/analysis. It stores a reference to already loaded projects and reuses them if possible.
 * A project can have multiple analyses
 */
@groovy.transform.CompileStatic
class ProjectLoader {

    private static final de.dkfz.roddy.tools.LoggerWrapper logger = de.dkfz.roddy.tools.LoggerWrapper.getLogger(ProjectLoader.class.getSimpleName());

    /**
     * Cache necessary for RMI... TODO Better into libraries factory?
     */
    private static Map<String, Analysis> _analysisCache = [:]

    private enum ProjectConfigurationSource {
        Unset,
        Configuration,
        Automatic
    }

    private static final int PSRC_UNSET = 0

    private static final int PROJECT_SRC_CONFIGURATION = 1

    private static final int PSRC_PLUGIN = 2

    private ProjectConfiguration projectConfiguration

    List<AnalysisImportKillSwitch> killSwitches

    String projectID

    String analysisID

    Project project

    Analysis analysis

    ProjectConfigurationSource projectConfigurationSource

    ProjectLoader() {

    }

    ProjectLoader(ProjectConfiguration projectConfiguration) {
        this.projectConfiguration = projectConfiguration
    }


    Analysis loadAnalysisConfiguration(String analysisName, Project project, AnalysisConfiguration configuration) {
        if (configuration == null)
            return null;

        Analysis analysis = null;

        try {
            Class analysisClass = LibrariesFactory.getInstance().loadClass(configuration.getConfiguredClass());
            String _runtimeServiceClass = configuration.getRuntimeServiceClass();
            RuntimeService runtimeService

            if (_runtimeServiceClass) {
                Class runtimeServiceClass = LibrariesFactory.getInstance().loadClass(_runtimeServiceClass);
                runtimeService = (RuntimeService) runtimeServiceClass.getConstructor().newInstance();
            }
            def constructor = analysisClass.getConstructor(String.class, Project.class, RuntimeService.class, AnalysisConfiguration.class)
            analysis = (Analysis) constructor.newInstance(analysisName, project, runtimeService, configuration);
            logger.sometimes("Created an analysis object of class ${analysis.class.name}.")
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        return analysis;
    }

    /**
     * There is some code duplication in this method and I do not actually like it. It has to be refactored.
     * @return
     */
    String getPluginRoddyAPILevel(String configurationIdentifier) {
        if (configurationIdentifier == null) return null

        extractProjectIDAndAnalysisIDOrFail(configurationIdentifier)

        if (analysisID == null) return null //Return null (which needs to be checked then) because an auto id is not possible.

        String fullAnalysisID = getFullAnalysisID(projectID, analysisID)

        LibrariesFactory librariesFactory = LibrariesFactory.initializeFactory()

        String pluginString
        def res = dissectFullAnalysisID(fullAnalysisID)
        pluginString = res[0]

        PluginInfoMap mapOfPlugins = librariesFactory.loadMapOfAvailablePluginsForInstance()
        PluginInfo pinfo = mapOfPlugins.getPluginInfoWithPluginString(pluginString)
        return pinfo.getRoddyAPIVersion()
    }

    /**
     * Load an analysis with a set project/analysis identifier.
     *
     * @param configurationIdentifier Something like [project.subproject.subproject]@[analysisID] where analysisID will be used to find the correct analysis.
     * @return An analysis object containing linking a project and an analysis configuration.
     */
    Analysis loadAnalysisAndProject(String configurationIdentifier) {
        try {
            if (_analysisCache.containsKey(configurationIdentifier))
                return _analysisCache[configurationIdentifier]

            extractProjectIDAndAnalysisIDOrFail(configurationIdentifier)

            checkAndPreparePremisesOrFail()

            loadPluginsOrFail(projectID, analysisID)

            System.err.println() // Just for console output formatting
            loadProjectConfigurationOrFail(projectID, analysisID)

            attachAdditionallyPassedConfigurations()

            System.err.println() // Just for console output formatting
            createProjectFromConfigurationOrFail()

            loadAnalysisOrFail()

            // Also a failed analysis object should go to cache.
            // Put into cache
            _analysisCache[configurationIdentifier] = analysis

            performFinalChecksOrFail(analysis, configurationIdentifier, projectConfiguration, projectID)

            // Try to build up the metadata table from here on. Project and analysis are ready.
            MetadataTableFactory.getTable(analysis)
            return analysis
        } catch (ConfigurationLoaderException ex) {
            logger.severe(ex.message)
            return null
        } catch (ProjectLoaderException ex) {
            logger.severe(ex.message)
            return null
        } catch (PluginLoaderException ex) {
            logger.severe(ex.message)
            return null
        }
    }

    void extractProjectIDAndAnalysisIDOrFail(String configurationIdentifier) {
        assert configurationIdentifier != null
        String[] splitProjectAnalysis
        if (configurationIdentifier.findAll("[@]").size() == 1) {
            splitProjectAnalysis = configurationIdentifier.split(StringConstants.SPLIT_AT)
            projectConfigurationSource = ProjectConfigurationSource.Configuration
        } else if (configurationIdentifier.findAll("[:]").size() == 1) {
            splitProjectAnalysis = configurationIdentifier.split(StringConstants.SPLIT_COLON)
            projectConfigurationSource = ProjectConfigurationSource.Automatic
        } else {
            throw new ProjectLoaderException("The requested analysis identifier ${configurationIdentifier} is invalid. It must only contain one '@' or ':'")
        }
        projectID = splitProjectAnalysis[0]

        if (splitProjectAnalysis.length == 1) {
            throw new ProjectLoaderException("There was no analysis specified for configuration ${projectID}\n\t Please specify the configuration string as [configuration_id]@[analysis_id].");
        }

        analysisID = splitProjectAnalysis[1];

        if (analysisID == null)
            throw new ProjectLoaderException("Could not extract analysis for ${configurationIdentifier}.");
    }

    void checkAndPreparePremisesOrFail() {
        if (projectConfigurationSource == ProjectConfigurationSource.Unset)
            throw new ProjectLoaderException("There is no project identifier set. It is not possible to run without setting projectConfigurationSource in the ProjectLoader.")

        if (projectConfigurationSource == ProjectConfigurationSource.Configuration)
            return

        if (projectConfigurationSource == ProjectConfigurationSource.Automatic) {
            // Create a project configuration, if all premises are valid.

            // Check, if the i/o directory is set on the command line
            if (!Roddy.useCustomIODirectories())
                throw new ProjectLoaderException("It is not possible to use the project configuration free mode without the --${RoddyStartupOptions.useiodir.name()} option.")

            createAndWriteAutoConfigurationFile()

            return
        }

        throw new ProjectLoaderException("The project identifier is wrong it must either be a configuration identifier or a plugin name. Properly set projectConfigurationSource in the ProjectLoader.")
    }

    private void createAndWriteAutoConfigurationFile() {
        // Create a temporary project configuration in ~/.roddy/configurationFreeMode
        String configurationFileName = "projects" + projectID + "_" + analysisID + "_" + Integer.toHexString(Roddy.commandLineCall.getOptionValue(RoddyStartupOptions.useiodir).hashCode()) + ".sh"

        String newProjectID = "CFreeMode_" + Integer.toHexString(configurationFileName.hashCode())
        String pluginID = projectID.replaceFirst(StringConstants.SPLIT_UNDERSCORE, StringConstants.COLON)
        String analysisImport = analysisID + "," + (analysisID.endsWith("Analysis") ? analysisID : analysisID + "Analysis") + "," + pluginID
        List<String> lines = []
        lines << "# " + RODDY_CONFIGURATION_MAGICSTRING
        lines << "#name " + newProjectID
        lines << "#analysis " + analysisImport
        if (Roddy.isOptionSet(RoddyStartupOptions.baseconfig)) lines << "#imports " + Roddy.commandLineCall.getOptionValue(RoddyStartupOptions.baseconfig)

        lines << "inputBaseDirectory=" + Roddy.customBaseInputDirectory
        lines << "outputBaseDirectory=" + Roddy.customBaseOutputDirectory
        lines << "outputAnalysisBaseDirectory=\${outputBaseDirectory}/\${${Constants.PID}}".toString()

        projectID = "CFreeMode_" + Integer.toHexString(configurationFileName.hashCode())

        File configurationFile = new File(Roddy.getFolderForConfigurationFreeMode(), configurationFileName)

        logger.always("Will use automatically generated configuration file: ${configurationFileName}")

        // The idea behind channel locks is, that the lock is JVM wide and therefore shared across all JVM instances on a single machine.
        // Not more not less. It will just prevent the current user from mutual file access / file overrides and will not work for multiple users
        // in the same directory. However, the directory here exists in the user directory and will only be accessed by a single user.
        // The method is platform dependent.
        // See the many comments on stack overflow
        RandomAccessFile directoryLockFile = new RandomAccessFile(new File(Roddy.getFolderForConfigurationFreeMode(), ".configLock"), "rw")
        FileLock jvmWideDirectoryLockFile = directoryLockFile.channel.lock()

        try {
            String configurationText = lines.join("\n")
            if (configurationFile.exists()) {
                if (RoddyIOHelperMethods.getMD5OfText(configurationText) != RoddyIOHelperMethods.getMD5OfFile(configurationFile)) {
                    configurationFile.delete()
                    configurationFile << configurationText
                }
            } else {
                configurationFile << configurationText
            }
        } finally {
            // Unlock and close the file
            jvmWideDirectoryLockFile.release()
            directoryLockFile.close()
        }
    }

    void loadPluginsOrFail(String projectID, String analysisID) {
        String fullAnalysisID = getFullAnalysisID(projectID, analysisID)

        def res
        LibrariesFactory librariesFactory = LibrariesFactory.initializeFactory();
        boolean pluginsAreLoaded = false;

        // Get the plugin string and enabled kill switches
        res = dissectFullAnalysisID(fullAnalysisID)
        String pluginString = res[0];
        killSwitches = res[1] as List<AnalysisImportKillSwitch>;

        if (pluginString) {
            pluginsAreLoaded = librariesFactory.resolveAndLoadPlugins(pluginString);
        }

        // If no plugin is set, load all libraries with the settings from the ini file
        String[] iniPluginVersion = Roddy.getPluginVersionEntries();
        if (!pluginsAreLoaded && iniPluginVersion) {
            pluginsAreLoaded = librariesFactory.resolveAndLoadPlugins(iniPluginVersion)
        }

        if (!pluginsAreLoaded) {
            throw new ProjectLoaderException("Unrecoverable errors, could not load plugins for analysis ${analysisID}")
        }
    }

    void loadProjectConfigurationOrFail(String projectID, String analysisID) {
        ConfigurationFactory fac = ConfigurationFactory.getInstance();
        // Just make sure all files are loaded.
        fac.loadAvailableAnalysisConfigurationFiles();

        projectConfiguration = fac.getProjectConfiguration(projectID);

        if (projectConfiguration.hasErrors()) {
            RoddyCLIClient.checkConfigurationErrorsAndMaybePrintAndFail(projectConfiguration)
        }

        PreloadedConfiguration iccAnalysis = ((AnalysisConfigurationProxy) projectConfiguration.getAnalysis(analysisID)).preloadedConfiguration;
        if (!XSDValidator.validateTree(iccAnalysis) && Roddy.isStrictModeEnabled()) {
            throw new ProjectLoaderException("Validation of project configuration failed.")
        }
    }

    /**
     * If --additionalImports is passed on the command line, try to add all set configuration objects
     * as a parent to the loaded project configuration.
     */
    void attachAdditionallyPassedConfigurations() {
        if (Roddy.isOptionSet(RoddyStartupOptions.additionalImports)) {
            Roddy.commandLineCall.getOptionList(RoddyStartupOptions.additionalImports).each {
                String cfg ->
                    projectConfiguration.addParent(ConfigurationFactory.instance.getConfiguration(cfg))
            }
        }
    }

    /**
     * Creates a project out of a configuration object. The project is stored in the project map afterwards.
     * If the project is already known it is checked whether the available project knows about the new analysis.
     * If the analysis is already available then just return the known object.
     * May return null if the configuration is at least on the project level.
     *
     * @param configuration
     * @return
     */
    void createProjectFromConfigurationOrFail() {
        List<Project> subProjects = new LinkedList<Project>();
        List<Analysis> analyses = new LinkedList<Analysis>();

        String _projectClass
        String _runtimeServiceClass
        Class projectClass = null
        Class runtimeServiceClass = null

        try {
            _projectClass = projectConfiguration.getConfiguredClass();
            _runtimeServiceClass = projectConfiguration.getRuntimeServiceClass();
            logger.postSometimesInfo("Found project class: " + _projectClass);
            if (_runtimeServiceClass)
                logger.postSometimesInfo("Found runtime service class set in project configuration: " + _runtimeServiceClass);
            projectClass = LibrariesFactory.getInstance().loadClass(_projectClass);
            RuntimeService runtimeService
            if (_runtimeServiceClass) {
                runtimeServiceClass = LibrariesFactory.getInstance().loadClass(_runtimeServiceClass);
                runtimeService = (RuntimeService) runtimeServiceClass.getConstructor().newInstance();
            }
            project = (Project) projectClass.getConstructor(ProjectConfiguration.class, RuntimeService.class, List.class, List.class).newInstance(projectConfiguration, runtimeService, subProjects, analyses);
        } catch (ClassNotFoundException e) {
            logger.severe("Cannot find project or runtime class! " + e.toString());
        } catch (NoSuchMethodException e) {
            logger.severe("Cannot find constructor <init>(Configuration)! " + e.toString());
        } catch (Exception e) {
            logger.severe("Cannot call constructor or error during call! (" + projectClass + ", " + runtimeServiceClass + ") ex=" + e.toString());
        }

        if (!projectConfiguration) {
            throw new ProjectLoaderException("Could not load project ${projectID}!")
        }
    }

    void loadAnalysisOrFail() {
        AnalysisConfiguration analysisConfiguration = loadAnalysisConfigurationFromProjectConfigurationOrFail(projectConfiguration, analysisID)

        extractKillSwitchesFromAnalysisConfiguration(analysisConfiguration)

        if (analysisConfiguration != null)
            analysis = loadAnalysisConfiguration(analysisID, project, analysisConfiguration)
        project.getAnalyses().add(analysis)

        if (analysis == null)
            throw new ProjectLoaderException("Could not load analysis ${analysisID}")
    }

    AnalysisConfiguration loadAnalysisConfigurationFromProjectConfigurationOrFail(ProjectConfiguration projectConfiguration, String analysisID) {
        AnalysisConfiguration analysisConfiguration = projectConfiguration.getAnalysis(analysisID);
        if (!analysisConfiguration) {
            throw new ProjectLoaderException("The analysis configuration for ${analysisID} could not be retrieved.")
        }
        return analysisConfiguration
    }

    void extractKillSwitchesFromAnalysisConfiguration(AnalysisConfiguration analysisConfiguration) {
        if (killSwitches.contains(AnalysisImportKillSwitch.FilenameSection)) {
            logger.postSometimesInfo("Killswitch for filename patterns is ACTIVE! Please see, if filenames are properly set.")
            analysisConfiguration.removeFilenamePatternsRecursively();
        }
    }

    /**
     * Dissects an analysis id string. Returns:
     * [0] = plugin
     * [1] = killswitches
     * @param fullAnalysisID
     * @return
     */
    static List dissectFullAnalysisID(String fullAnalysisID) {
        String[] splitEntries = fullAnalysisID.split("[:][:]");

        // If the plugin is set, find "parent" plugins with the proper version.
        String pluginPart = splitEntries?.find { String part -> part.startsWith("useplugin") }
        String pluginStr = null;

        if (pluginPart && pluginPart.size() > "useplugin=".size()) {
            // Extract the plugin and its version.
            pluginStr = pluginPart.split("[=]")[1]
        }

        // Get kill switches from the fullAnalysisID.
        String[] killSwitchKeyVal = splitEntries?.find { String part -> part.startsWith("killswitches") }.split(StringConstants.EQUALS)
        List<AnalysisImportKillSwitch> killSwitches;
        if (killSwitchKeyVal.size() == 2) {
            killSwitches = killSwitchKeyVal[1]?.split(StringConstants.SPLIT_COMMA)?.collect { it as AnalysisImportKillSwitch } as List;
        } else {
            killSwitches = new LinkedList<AnalysisImportKillSwitch>()
        }
        [pluginStr, killSwitches]
    }

    String getFullAnalysisID(String projectID, String analysisID) {
        PreloadedConfiguration iccProject = loadAndValidateProjectICCOrFail(projectID);

        String fullAnalysisID = loadFullAnalysisIDOrFail(iccProject, analysisID);
        fullAnalysisID
    }

    PreloadedConfiguration loadAndValidateProjectICCOrFail(String projectID) {
        ConfigurationFactory fac = ConfigurationFactory.getInstance();
        PreloadedConfiguration iccProject = fac.getAllAvailableConfigurations()[projectID];

        if (!iccProject) {
            throw new ProjectLoaderException("The project configuration \"${projectID}\" could not be found (call Roddy with listworkflows)")
        } else {
            logger.postRareInfo("Loading information configuration context for ${projectID} from ${iccProject.file}")
        }

        //Validate the project icc
        XSDValidator.validateTree(iccProject);
        return iccProject;
    }

    String loadFullAnalysisIDOrFail(PreloadedConfiguration iccProject, String analysisID) {
        String fullAnalysisID = iccProject.getListOfAnalyses().find { String aID -> aID.split("[:][:]")[0] == analysisID; }

        if (!fullAnalysisID) {
            throw new ProjectLoaderException("The analysis \"${analysisID}\" could not be found (call Roddy with listworkflows)")
        }
        return fullAnalysisID
    }

    /**
     * Check if an analysis is available and if the runtime service is setup properly.
     */
    void performFinalChecksOrFail(Analysis analysis, String configurationIdentifier, ProjectConfiguration projectConfiguration, String projectID) {
        if (analysis == null) {
            StringBuilder sb = new StringBuilder();
            sb << "Could not load analysis ${configurationIdentifier}, try one of those: " << Constants.ENV_LINESEPARATOR;
            for (String aID : projectConfiguration.listOfAnalysisIDs) {
                sb << "  " << projectID << "@" << aID << Constants.ENV_LINESEPARATOR;
            }
            throw new ProjectLoaderException(sb.toString());
        }

        if (analysis.getRuntimeService() == null) {
            throw new ProjectLoaderException("There is no runtime service class set for the selected analysis. This has to be set in either the project configuration or the analysis configuration.");
        }

        List<String> errors = []

        // Earliest check for valid input and output directories. If they are not accessible or writeable.
        // The checks are done before the readability tests because we need to check for the raw configuration values
        // as the next check will already use translated value

        String valueInDir = analysis.configuration.configurationValues.get(ConfigurationConstants.CFG_INPUT_BASE_DIRECTORY, "").value
        String valueOutDir = analysis.configuration.configurationValues.get(ConfigurationConstants.CFG_OUTPUT_BASE_DIRECTORY, "").value
        
        if (!valueInDir && !valueOutDir) {
            errors << "Both the input and output base directories are not set. You must set at least inputBaseDirectory or outputBaseDirectory."

        } else {

            // Fill variable, if it is missing. Log a warning.
            if(!valueInDir) {
                logger.always("The input base directory is not set. Taking the path of the output base directory instead.")
                analysis.configuration.configurationValues.add(new ConfigurationValue(ConfigurationConstants.CFG_INPUT_BASE_DIRECTORY, valueOutDir, "path"))
            }

            if(!valueOutDir) {
                logger.always("The output base directory is not set. Taking the path of the input base directory instead.")
                analysis.configuration.configurationValues.add(new ConfigurationValue(ConfigurationConstants.CFG_OUTPUT_BASE_DIRECTORY, valueInDir, "path"))
            }

            // Now start with the input directory
            errors += checkDirForReadabilityAndExecutability(analysis.getInputBaseDirectory(), "input")
            errors += checkDirForReadabilityAndExecutability(analysis.getOutputBaseDirectory(), "output")

            // Out dir needs to be writable
            if (!FileSystemAccessProvider.instance.isWritable(analysis.getOutputBaseDirectory()))
                errors << (String) "The output was not writeable at path ${analysis.getOutputBaseDirectory()}."
        }

        if (!errors)
            return

        throw new ProjectLoaderException((["There were errors in directory access checks:"] + errors).join("\n\t"))
    }

    List<String> checkDirForReadabilityAndExecutability(File dirToCheck, String dirtype) {
        List<String> errors = []
        for (File _dir = dirToCheck; _dir; _dir = _dir.parentFile) {
            boolean readable = FileSystemAccessProvider.instance.isReadable(_dir)
            boolean executable = FileSystemAccessProvider.instance.isExecutable(_dir)
            if (!readable || !executable) {
                if (!readable && !executable)
                    errors << (String) "The ${dirtype} directory was neither readable nor executable at path ${_dir}."
                else if (!readable)
                    errors << (String) "The ${dirtype} directory was not readable at path ${_dir}."
                else if (!executable)
                    errors << (String) "The ${dirtype} directory was not executable at path ${_dir}."
                break
            }
        }
        return errors
    }
}
