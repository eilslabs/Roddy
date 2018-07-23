/*
 * Copyright (c) 2017 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.config.loader

import de.dkfz.roddy.Constants
import de.dkfz.roddy.Roddy
import de.dkfz.roddy.StringConstants
import de.dkfz.roddy.client.RoddyStartupOptions
import de.dkfz.roddy.config.*
import de.dkfz.roddy.config.Configuration.ConfigurationType
import de.dkfz.roddy.config.converters.BashConverter
import de.dkfz.roddy.config.converters.YAMLConverter
import de.dkfz.roddy.core.Project
import de.dkfz.roddy.core.ProjectLoaderException
import de.dkfz.roddy.core.Workflow
import de.dkfz.roddy.knowledge.brawlworkflows.JBrawlWorkflow
import de.dkfz.roddy.knowledge.files.BaseFile
import de.dkfz.roddy.knowledge.files.FileObject
import de.dkfz.roddy.knowledge.files.FileStage
import de.dkfz.roddy.knowledge.nativeworkflows.NativeWorkflow
import de.dkfz.roddy.plugins.LibrariesFactory
import de.dkfz.roddy.plugins.PluginInfo
import de.dkfz.roddy.plugins.SyntheticPluginInfo

import de.dkfz.roddy.tools.LoggerWrapper
import de.dkfz.roddy.tools.RoddyConversionHelperMethods
import de.dkfz.roddy.tools.RoddyIOHelperMethods
import de.dkfz.roddy.tools.Tuple3
import groovy.transform.TypeCheckingMode
import groovy.util.slurpersupport.NodeChild
import groovy.util.slurpersupport.NodeChildren
import groovy.xml.StreamingMarkupBuilder
import groovy.xml.XmlUtil
import org.apache.commons.io.filefilter.WildcardFileFilter
import org.xml.sax.SAXParseException

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.text.ParseException
import java.util.logging.Level

import static de.dkfz.roddy.StringConstants.*

/**
 * Factory for loading, importing, exporting and writing configuration files.
 * @author michael
 */
@groovy.transform.CompileStatic
class ConfigurationFactory {

    public static final String XMLTAG_EXECUTIONSERVICE_SSHUSER = "executionServiceSSHUser"

    public static final String XMLTAG_EXECUTIONSERVICE_SHOW_SSHCALLS = "executionServiceShowSSHCalls"

    public static final String XMLTAG_ATTRIBUTE_INHERITANALYSES = "inheritAnalyses"

    public static final String ERROR_PRINTOUT_XML_LINEPREFIX = "          "

    public static final LoggerWrapper logger = LoggerWrapper.getLogger(ConfigurationFactory.class.getSimpleName())


    private static ConfigurationFactory singleton

    private List<File> configurationDirectories = []

    private Map<String, PreloadedConfiguration> availableConfigurations = [:]

    private Map<ConfigurationType, List<PreloadedConfiguration>> availableConfigurationsByType = [:]

    private Map<ConfigurationType, Map<String, PreloadedConfiguration>> availableConfigurationsByTypeAndID = [:]


    static void initialize(List<File> configurationDirectories = null) {
        singleton = new ConfigurationFactory(configurationDirectories)
    }

    static ConfigurationFactory getInstance() {
        if (!singleton)
            initialize()
        return singleton
    }

    private ConfigurationFactory(List<File> configurationDirectories = null) {
        if (configurationDirectories == null)
            configurationDirectories = Roddy.getConfigurationDirectories()

        this.configurationDirectories.addAll(configurationDirectories)

        loadAvailableProjectConfigurationFiles()
    }

    private void loadAvailableProjectConfigurationFiles() {
        List<File> allFiles = []
        configurationDirectories.parallelStream().each {
            File baseDir ->
                logger.log(Level.CONFIG, "Searching for configuration files in: " + baseDir.toString())
                if (!baseDir.canRead() || !baseDir.canExecute()) {
                    logger.log(Level.SEVERE, "Cannot read from configuration directory ${baseDir.absolutePath}, does the folder exist und do you have access (read/execute) rights to it?")
                    throw new ConfigurationLoaderException("Cannot access (read and execute) configuration directory '${baseDir}'")
                }
                File[] files = baseDir.listFiles((FileFilter) new WildcardFileFilter(["*.xml", "*.sh", "*.yml"]))
                if (files == null) {
                    logger.info("No configuration files found in path ${baseDir.getAbsolutePath()}")
                }
                for (File f in files) {
                    synchronized (allFiles) {
                        if (!allFiles.contains(f))
                            allFiles << f
                    }
                }
        }

        Map<String, List<String>> pathsForCfgs = [:]
        List<String> duplicateConfigurationIDs = []
        for (File file in allFiles) {
            try {
                def icc = loadInformationalConfigurationContent(file)

                pathsForCfgs.get(icc.name, []) << icc.file.absolutePath
                if (availableConfigurations.containsKey(icc.name)) {
                    duplicateConfigurationIDs << icc.name
                    continue
                }

                availableConfigurations[icc.id] = icc
                availableConfigurationsByType.get(icc.type, []) << icc
                availableConfigurationsByTypeAndID.get(icc.type, [:])[icc.id] = icc
                for (PreloadedConfiguration iccSub in icc.getAllSubContent()) {
                    availableConfigurations[iccSub.id] = iccSub
                }

            } catch (ParseException ex) {
                logger.rare("File '${file}' is not a valid Roddy configuration file. Skipped!")
            } catch (UnknownConfigurationFileTypeException ex) {
                logger.severe("The file ${file.absolutePath} does not appear to be a valid Bash configuration file:\n\t ${ex.message}")
            } catch (Exception ex) {
                logger.severe("File ${file.absolutePath} cannot be loaded! Error in config file! ${ex.toString()}")
                logger.severe(RoddyIOHelperMethods.getStackTraceAsString(ex))
                throw new ConfigurationLoadError(null, null, "Could not load '${file}'", ex)
            }
        }
        if (duplicateConfigurationIDs) {

            StringBuilder messageForDuplicates = new StringBuilder("Configuration files using the same id were found:\n")
            for (String id in duplicateConfigurationIDs.sort().unique()) {
                messageForDuplicates << "\t" << id << ([" found in:"] + pathsForCfgs[id]).join("\n\t\t")
            }
            messageForDuplicates << "\n" << (["This is not allowed! Check your configuration directories for files containing using same ids:"] + configurationDirectories.collect { it.absolutePath }).join("\n\t")

            throw new ConfigurationLoaderException(messageForDuplicates.toString())
        }
    }

    void loadAvailableAnalysisConfigurationFiles() {
        List<File> allFiles = []
        Map<File, PluginInfo> pluginsByFile = [:]
        for (PluginInfo pi in LibrariesFactory.getInstance().getLoadedPlugins()) {
            List<File> configFiles = pi.getConfigurationFiles()
            for (File f in configFiles) {
                allFiles.add(f)
                pluginsByFile[f] = pi
            }
        }

        for (File file in allFiles) {
            try {
                def icc = loadInformationalConfigurationContent(file)

                File readmeFile = RoddyIOHelperMethods.assembleLocalPath(pluginsByFile[file].directory, "README." + icc.id + ".txt")
                if (readmeFile.exists())
                    icc.setReadmeFile(readmeFile)

                if (!availableConfigurations.containsKey(icc.name)) {

                    availableConfigurations[icc.id] = icc
                    availableConfigurationsByType.get(icc.type, []) << icc
                    availableConfigurationsByTypeAndID.get(icc.type, [:])[icc.id] = icc
                    for (PreloadedConfiguration iccSub in icc.getAllSubContent()) {
                        availableConfigurations[iccSub.id] = iccSub
                    }

                } else {
                    if (availableConfigurations[icc.name].file != icc.file)
                        throw new ProjectLoaderException("Configuration with name ${icc.name} already exists! Names must be unique.")
                }

            } catch (ParseException ex) {
                logger.rare("File ${file} is not a valid Roddy configuration file. Skipped!")
            } catch (org.xml.sax.SAXParseException ex) {
                throw new ProjectLoaderException("The validation of a configuration file ${file.absolutePath} failed.")
            } catch (Exception ex) {
                logger.severe("An unknown exception occured during the attempt to load a configuration file:\n\t${file.absolutePath} cannot be loaded.\n\t${ex.toString()}")
                logger.sometimes(RoddyIOHelperMethods.getStackTraceAsString(ex))
                throw ex
            }
        }
    }

    Map<String, PreloadedConfiguration> getAllAvailableConfigurations() {
        return availableConfigurations
    }

    /**
     * Returns a list of all availabe analysis configurations.
     * Returns an empty list if no configurations is known.
     * @return
     */
    List<PreloadedConfiguration> getAvailableAnalysisConfigurations() {
        return getAvailableConfigurationsOfType(ConfigurationType.ANALYSIS)
    }

    /**
     * Returns a list of all availabe project configurations.
     * Returns an empty list if no configurations is known.
     * @return
     */
    List<PreloadedConfiguration> getAvailableProjectConfigurations() {
        return getAvailableConfigurationsOfType(ConfigurationType.PROJECT)
    }

    /**
     * Returns a list of all availabe configurations of the given type.
     * Returns an empty list if no configurations is known.
     * @return
     */
    List<PreloadedConfiguration> getAvailableConfigurationsOfType(ConfigurationType type) {
        return availableConfigurationsByType.get(type, [])
    }

    static String loadAndPreprocessTextFromFile(File file) {
        if (file.name.endsWith(".xml")) // Default behaviour
            return file.text

        if (file.name.endsWith(".sh")) // Easy Bash importer
            return loadAndPreprocessBashFile(file)

        if (file.name.endsWith(".groovy") || file.name.endsWith(".brawl"))
            return loadAndPreprocessBrawlFile(file)

        throw new UnknownConfigurationFileTypeException("Unknown file type ${file.name} for a configuration file.")
    }

    static String loadAndPreprocessYAMLFile(File s) {
        return new YAMLConverter().convertToXML(s)
    }

    static String loadAndPreprocessBashFile(File s) {
        return new BashConverter().convertToXML(s)
    }

    static String loadAndPreprocessBrawlFile(File s) {
        String name = s.name.replace(".groovy", "").replace(".brawl", "")
        return """
            <configuration  configurationType='analysis' name='${name}' 
                            class='de.dkfz.roddy.core.Analysis' 
                            workflowClass='de.dkfz.roddy.knowledge.brawlworkflows.BrawlCallingWorkflow' 
                            usedToolFolders='roddyTools,inlineScripts' 
                            runtimeServiceClass='de.dkfz.roddy.core.RuntimeService'>
                <configurationvalues>
                    <cvalue name='activeBrawlWorkflow' value='${name}' type="string"/>
                </configurationvalues>
            </configuration>
        """
    }

    /**
     * Loads basic info about a configuration file.
     *
     * Basic info contains i.e. the name, description, subconfigs and the type of a configuration.
     * @see PreloadedConfiguration
     *
     * @param file The config file.
     * @return An object containing basic information about a configuration OR null, if the no preloaded config could be loaded.
     */
    PreloadedConfiguration loadInformationalConfigurationContent(File file) {
        String text = loadAndPreprocessTextFromFile(file)
        if (!text) {
            throw new ParseException("Could not identify file '${file.absolutePath}' as a Roddy configuration file." as String, 0)
        }

        NodeChild xml
        try {
            xml = (NodeChild) new XmlSlurper().parseText(text)
        } catch (SAXParseException ex) {
            throw new ConfigurationLoaderException("Project configuration file ${file} could not be loaded, see message(s) above.");
        }
        return _preloadConfiguration(file, text, xml, null)
    }

    /**
     * Loads a basic / informational part of each available configurationNode file.
     * Recursive helper method.
     * @param configurationNode
     * @return
     */
    @groovy.transform.CompileStatic(TypeCheckingMode.SKIP)
    private PreloadedConfiguration _preloadConfiguration(File file, String text, NodeChild configurationNode, PreloadedConfiguration parent) {
        NodeChild.metaClass.extract = { String id, String defaultValue -> return extractAttributeText((NodeChild) delegate, id, defaultValue) }
        List<PreloadedConfiguration> subConf = new LinkedList<PreloadedConfiguration>()
        PreloadedConfiguration icc

        Configuration.ConfigurationType type = extractAttributeText(configurationNode, "configurationType", parent != null ? parent.type.name().toUpperCase() : Configuration.ConfigurationType.OTHER.name()).toUpperCase()
        String cls = extractAttributeText(configurationNode, "class", Project.class.name)
        String name = extractAttributeText(configurationNode, "name")
        String description = extractAttributeText(configurationNode, "description")
        String imports = extractAttributeText(configurationNode, "imports")

        if (type == ConfigurationType.PROJECT) {
            List<String> analyses = []

            NodeChildren san = configurationNode.availableAnalyses
            if (!Boolean.parseBoolean(extractAttributeText(configurationNode, XMLTAG_ATTRIBUTE_INHERITANALYSES, FALSE))) {
                analyses = _loadPreloadedConfigurationAnalyses(san)
            } else {
                analyses = parent.getListOfAnalyses()
            }
            ResourceSetSize setSize = ResourceSetSize.valueOf(extractAttributeText(configurationNode, "usedresourcessize", "l"))
            icc = new PreloadedConfiguration(parent, type, name, description, cls, configurationNode, imports, setSize, analyses, subConf, file, text)
        } else {
            icc = new PreloadedConfiguration(parent, type, name, description, cls, configurationNode, imports, subConf, file, text)
        }

        for (subConfiguration in configurationNode.subconfigurations.configuration) {
            subConf << _preloadConfiguration(file, text, subConfiguration, icc)
        }

        return icc
    }

    @groovy.transform.CompileStatic(TypeCheckingMode.SKIP)
    private List<String> _loadPreloadedConfigurationAnalyses(NodeChildren analyses) {
        List<String> listOfanalyses = []
        for (analysis in analyses.analysis) {
            String id = analysis.@id.text()
            String configuration = analysis.@configuration.text()
            String useplugin = extractAttributeText(analysis, "useplugin", "")
            String killswitches = extractAttributeText(analysis, "killswitches", "")
            String idStr = "${id}::${configuration}::useplugin=${useplugin}::killswitches=${killswitches}".toString()

            listOfanalyses << idStr
        }
        return listOfanalyses
    }

    Configuration getConfiguration(String usedConfiguration) {
        PreloadedConfiguration icc = availableConfigurations[usedConfiguration]

        if (icc == null) {
            throw new ProjectLoaderException("The configuration identified by \"${usedConfiguration}\" cannot be found, is the identifier correct? Is the configuration available? Possible are:\n"
                    + convertMapToFormattedTable(availableConfigurations, 1, " : ", { PreloadedConfiguration v -> v.file }).join("\n")
            )
        }

        return loadConfiguration(icc)
    }

    /**
     * Will format a map to a two column table. The width of the first column will be calculated using the length of the
     * longest element.
     * @param map
     * @param cntOfTabs
     * @param tabSep
     * @param closureForValue
     * @return
     */
    @Deprecated
    static List<String> convertMapToFormattedTable(Map<String, ?> map, int cntOfTabs, String tabSep, Closure closureForValue) {
        final int keyWidth = map.keySet().collect { it.size() }.max()
        map.collect {
            def k, def v ->
                ("\t" * cntOfTabs) + k.toString().padRight(keyWidth) + tabSep + closureForValue(v)
        }
    }

    private static final List<File> _cfgFileLoaderMessageCache = []

    Configuration loadConfiguration(PreloadedConfiguration icc) {
        synchronized (_cfgFileLoaderMessageCache) {
            if (!_cfgFileLoaderMessageCache.contains(icc.file)) {
                logger.always("Load configuration file ${icc.file}")
                _cfgFileLoaderMessageCache << icc.file
            }
        }
        Configuration config = _loadConfiguration(icc)

        for (String ic in config.getImportConfigurations()) {
            try {
                Configuration cfg = getConfiguration(ic)
                config.addParent(cfg)
            } catch (Exception ex) {
                if (LibrariesFactory.getInstance().areLibrariesLoaded())
                    logger.severe("Configuration ${ic} cannot be loaded!")
                throw ex
            }
        }
        return config
    }

    /**
     * Reverse - recursively load a configuration. Start with the deepest configuration object and move to the top.
     * The reverse walk ist possible as the information about dependencies is stored in the PreloadedConfiguration objects which are created on startup.
     */
    private Configuration _loadConfiguration(PreloadedConfiguration icc) {
        Configuration parentConfig = icc.parent != null ? loadConfiguration(icc.parent) : null
        NodeChild configurationNode = icc.configurationNode
        Configuration config = null

        //If the configurationNode is a project or a variant then it is allowed to import analysis configurations.
        config = createConfigurationObject(icc, configurationNode, parentConfig)

        boolean configurationWasLoadedProperly = true;

        // Errors are always caught and a message is appended. We want to see everything, if possible.

        configurationWasLoadedProperly &= withErrorEntryOnUnknownException(config, "cvalues",
                "Could not read configuration values for configuration ${icc.id}", { readConfigurationValues(configurationNode, config) })
        configurationWasLoadedProperly &= withErrorEntryOnUnknownException(config, "cvbundles",
                "Could not read configuration value bundles for configuration ${icc.id}", { readValueBundles(configurationNode, config) })
        configurationWasLoadedProperly &= withErrorEntryOnUnknownException(config, "fnpatterns",
                "Could not read filename patterns for configuration ${icc.id}", {
            config.filenamePatterns.map.putAll(readFilenamePatterns(configurationNode))
        })
        configurationWasLoadedProperly &= withErrorEntryOnUnknownException(config, "enums",
                "Could not read enumerations for configuration ${icc.id}", { readEnums(config, configurationNode) })
        configurationWasLoadedProperly &= withErrorEntryOnUnknownException(config, "ptools",
                "Could not read processing tools for configuration ${icc.id}", { readProcessingTools(configurationNode, config) })

        if (!configurationWasLoadedProperly) {
            logger.severe("There were errors in the configuration file ${icc.file}.")
        }

        return config
        /**
         * TODO Maybe transform the ConfigurationFactory to object form thus allowing to store errors there.
         * Also evaluate the configurationWasLoadedProperly variable.
         */
    }

    private boolean withErrorEntryOnUnknownException(Configuration config, String id, String msg, Closure blk) {
        try {
            blk.call()
        } catch (ConfigurationError ex) {
            addFormattedErrorToConfig(msg + ' : ' + ex.message, id, null, config)
            return false
        }
        return true
    }

    /**
     * Create a configuration object which depends on the information taken out of the configuration xml file.
     *
     * @param icc The informational object for a configuration (file)
     * @param configurationNode The xml node read in by an xml slurper
     * @param parentConfig A (optionally) available parent configuration.
     * @return A new configuration object
     */
    @groovy.transform.CompileStatic(TypeCheckingMode.SKIP)
    private Configuration createConfigurationObject(PreloadedConfiguration icc, NodeChild configurationNode, Configuration parentConfig) {
        Configuration config
        if (icc.type >= ConfigurationType.PROJECT) {
            Map<String, AnalysisConfiguration> availableAnalyses = [:]
            String runtimeServiceClass = extractAttributeText(configurationNode, "runtimeServiceClass", null)
            config = new ProjectConfiguration(icc, runtimeServiceClass, availableAnalyses, parentConfig)
            boolean inheritAnalyses = Boolean.parseBoolean(extractAttributeText(configurationNode, XMLTAG_ATTRIBUTE_INHERITANALYSES, "false"))
            if (!inheritAnalyses) {
                availableAnalyses.putAll(_loadAnalyses(configurationNode.availableAnalyses))
            } else {
                if (parentConfig instanceof ProjectConfiguration) {
                    ProjectConfiguration pcParent = (ProjectConfiguration) parentConfig
                    availableAnalyses.putAll(pcParent.getAnalyses())
                }
            }
        } else if (icc.type == ConfigurationType.ANALYSIS) {
            String brawlWorkflow = extractAttributeText(configurationNode, "brawlWorkflow", null)
            String brawlBaseWorkflow = extractAttributeText(configurationNode, "brawlBaseWorkflow", Workflow.class.name)
            String runtimeServiceClass = extractAttributeText(configurationNode, "runtimeServiceClass", null)

            String workflowTool = extractAttributeText(configurationNode, "nativeWorkflowTool", null)
            String jobManagerClass = extractAttributeText(configurationNode, "targetJobManager", null)

            String workflowClass = extractAttributeText(configurationNode, "workflowClass")

            if (workflowTool && jobManagerClass) {
                workflowClass = NativeWorkflow.class.name
            } else if (brawlWorkflow) {
                workflowClass = JBrawlWorkflow.class.name
            }
            String cleanupScript = extractAttributeText(configurationNode, "cleanupScript", "")
            String[] _listOfUsedTools = extractAttributeText(configurationNode, "listOfUsedTools").split(SPLIT_COMMA)
            String[] _usedToolFolders = extractAttributeText(configurationNode, "usedToolFolders").split(SPLIT_COMMA)
            List<String> listOfUsedTools = _listOfUsedTools.size() > 0 && _listOfUsedTools[0] ? Arrays.asList(_listOfUsedTools) : null
            List<String> usedToolFolders = _usedToolFolders.size() > 0 && _usedToolFolders[0] ? Arrays.asList(_usedToolFolders) : null

            config = new AnalysisConfiguration(icc, workflowClass, runtimeServiceClass, parentConfig, listOfUsedTools, usedToolFolders, cleanupScript)

            if (workflowTool && jobManagerClass) {
                ((AnalysisConfiguration) config).setNativeToolID(workflowTool.replace(".", "_"))
                ((AnalysisConfiguration) config).setJobManagerFactory(jobManagerClass)
            }
            if (brawlWorkflow) {
                ((de.dkfz.roddy.config.AnalysisConfiguration) config).setBrawlWorkflow(brawlWorkflow)
                ((de.dkfz.roddy.config.AnalysisConfiguration) config).setBrawlBaseWorkflow(brawlBaseWorkflow)
            }

        } else {
            config = new Configuration(icc)
        }
        return config
    }

    @groovy.transform.CompileStatic(TypeCheckingMode.SKIP)
    private void readValueBundles(NodeChild configurationNode, Configuration config) {
        Map<String, ConfigurationValueBundle> cvBundles = config.getConfigurationValueBundles().getMap()

        for (NodeChild cbundle in configurationNode.configurationvalues.configurationValueBundle) {
            Map<String, ConfigurationValue> bundleValues = new LinkedHashMap<String, ConfigurationValue>()
            for (cvalue in cbundle.cvalue) {
                ConfigurationValue _cvalue = readConfigurationValue(cvalue, config)
                bundleValues[_cvalue.id] = _cvalue
            }
            def cBundleID = cbundle.@name.text()
            cvBundles[cBundleID] = new ConfigurationValueBundle(cBundleID, bundleValues)
        }
    }

    @groovy.transform.CompileStatic(TypeCheckingMode.SKIP)
    private static Map<String, FilenamePattern> readFilenamePatterns(NodeChild configurationNode) {
        Map<String, FilenamePattern> filenamePatterns = [:]

        for (NodeChild filenames in configurationNode.filenames) {
            String pkg = extractAttributeText(filenames, "package", SyntheticPluginInfo.SYNTHETIC_PACKAGE)
            String filestagesbase = extractAttributeText(filenames, "filestagesbase", null)

            for (NodeChild filename in filenames.filename) {
                try {
                    FilenamePattern fp = null
                    if (filename.attributes().get("derivedFrom") != null) {
                        fp = readDerivedFromFilenamePattern(pkg, filename)
                    } else if (filename.attributes().get("fileStage") != null) {
                        fp = readFileStageFilenamePattern(pkg, filestagesbase, filename)
                    } else if (filename.attributes().get("onTool") != null) {
                        fp = readOnToolFilenamePattern(pkg, filename)
                    } else if (filename.attributes().get("onMethod") != null) {
                        fp = readOnMethodFilenamePattern(pkg, filename)
                    } else if (filename.attributes().get("onScriptParameter") != null) {
                        fp = readOnScriptParameterFilenamePattern(pkg, filename)
                    }
                    if (fp == null) {
                        throw new RuntimeException("filename pattern is not valid: ")
                    }
                    if (filenamePatterns.containsKey(fp.getID())) {
                        logger.severe("Duplicate filename pattern: " + (new groovy.xml.StreamingMarkupBuilder().bindNode(filename) as String))
                    }
                    filenamePatterns.put(fp.getID(), fp)
                } catch (Exception ex) {
                    logger.severe("${ex.message}: " + (new groovy.xml.StreamingMarkupBuilder().bindNode(filename) as String))
                }
            }
        }
        return filenamePatterns
    }

    @groovy.transform.CompileStatic(TypeCheckingMode.SKIP)
    static FilenamePattern readDerivedFromFilenamePattern(String pkg, NodeChild filename) {
        String classSimpleName = filename.@class.text()
        String fnDerivedFrom = filename.@derivedFrom.text()
        String pattern = filename.@pattern.text()
        String selectionTag = extractAttributeText(filename, "selectiontag", Constants.DEFAULT)

        Tuple3<Class, Boolean, Integer> parentClassResult = loadPatternClass(pkg, fnDerivedFrom)
        Tuple3<Class, Boolean, Integer> classResult = loadPatternClass(pkg, classSimpleName, parentClassResult.x)

        FilenamePattern fp = new DerivedFromFilenamePattern(classResult.x, parentClassResult.x, pattern, selectionTag, parentClassResult.y, parentClassResult.z)
        return fp
    }

    static Tuple3<Class, Boolean, Integer> loadPatternClass(String pkg, String className, Class constructorClass = null) {

        String cls
        boolean packageIsSet = pkg && pkg != SyntheticPluginInfo.SYNTHETIC_PACKAGE;
        Class foundClass = !packageIsSet ? LibrariesFactory.instance.searchForClass(className) : null;
        if (foundClass)
            cls = foundClass.name
        else
            cls = (pkg != null ? pkg + "." : "") + className

        // Test if parent class contains something like [0-9] at the end. However, this test does not extract the size of the array.
        //        boolean doesArrays = Pattern.compile('\\[\\d\\]$').matcher("test[2]").findAll();

        int enforcedArraySize = -1
        boolean isArray = false
        if (cls.contains("[")) {
            int openingIndex = cls.indexOf("[")
            int closingIndex = cls.indexOf("]")
            if (closingIndex - 1 > openingIndex) {
                enforcedArraySize = Integer.parseInt(cls[openingIndex + 1..-2])
            }
            cls = cls[0..openingIndex - 1]
            className = className.split("\\[")[0]
            isArray = true
        }

        Class _classID
        try {
            _classID = foundClass ?: LibrariesFactory.getInstance().searchForClass(cls)
        } catch (Exception ex) {
            _classID = null
        }

        if (!_classID) {
            //Create a synthetic class...
            String constructorClassName = constructorClass ? constructorClass.name : BaseFile.class.name
            _classID = LibrariesFactory.getInstance().loadRealOrSyntheticClass(className, constructorClassName)
        }
        return new Tuple3<Class, Boolean, Integer>(_classID, isArray, enforcedArraySize)
    }

    @groovy.transform.CompileStatic(TypeCheckingMode.SKIP)
    static FilenamePattern readOnMethodFilenamePattern(String pkg, NodeChild filename) {
        String methodName = filename.@onMethod.text()
        String pattern = filename.@pattern.text()
        String selectionTag = extractAttributeText(filename, "selectiontag", Constants.DEFAULT)
        Class<FileObject> _cls = loadPatternClass(pkg, filename.@class.text(), BaseFile).x
        Class<FileObject> calledClass = _cls
        if (methodName.contains(".")) { //Different class as source class!
            String[] stuff = methodName.split(SPLIT_STOP)
            String className = stuff[0 .. -2].join(".")
            methodName = stuff[-1]
            calledClass = LibrariesFactory.instance.classLoaderHelper.searchForClass(className)
        }
        Method _method = null
        for (Method m : calledClass.getMethods()) {
            if (m.name == methodName) {
                _method = m
            }
        }
        FilenamePattern fp = new OnMethodFilenamePattern(_cls, calledClass, _method, pattern, selectionTag)
        return fp
    }

    @groovy.transform.CompileStatic(TypeCheckingMode.SKIP)
    static FilenamePattern readOnScriptParameterFilenamePattern(String pkg, NodeChild filename) {
        String scriptParameter = filename.@onScriptParameter.text()
        String pattern = filename.@pattern.text()
        String toolName, parameterName
        String[] splitResult = scriptParameter.trim().split(":")
        if (splitResult.size() == 1) {
            //any tool and param
            toolName = null
            parameterName = splitResult.first()
        } else if (splitResult.size() == 2) {
            toolName = splitResult[0]
            parameterName = splitResult[1]

            if (toolName.equals("[ANY]") || toolName.equals("")) {
                //only param OR [ANY] tool and param
                toolName = null
            } else if (toolName.startsWith("[")) {
                throw new RuntimeException("Illegal Argument '[..]': ${toolName}")
            }

        } else {
            throw new RuntimeException("Too many colons: ${scriptParameter}")
        }
        if (filename.@class == "") {
            throw new RuntimeException("Missing 'class' attribute for onScriptParameter in: ${groovy.xml.XmlUtil.serialize(filename)}")
        }
        Class<FileObject> _cls = loadPatternClass(pkg, filename.@class.text(), BaseFile).x

        FilenamePattern fp = new OnScriptParameterFilenamePattern(_cls, toolName, parameterName, pattern)
        return fp
    }

    @groovy.transform.CompileStatic(TypeCheckingMode.SKIP)
    static FilenamePattern readOnToolFilenamePattern(String pkg, NodeChild filename) {
        Class<FileObject> _cls = loadPatternClass(pkg, filename.@class.text(), BaseFile).x
        String scriptName = filename.@onTool.text()
        String pattern = filename.@pattern.text()
        String selectionTag = extractAttributeText(filename, "selectiontag", Constants.DEFAULT)
        FilenamePattern fp = new OnToolFilenamePattern(_cls, scriptName, pattern, selectionTag)
        return fp
    }

    @groovy.transform.CompileStatic(TypeCheckingMode.SKIP)
    static FilenamePattern readFileStageFilenamePattern(String pkg, String filestagesbase, NodeChild filename) {
        Class<FileObject> _cls = loadPatternClass(pkg, filename.@class.text(), BaseFile).x
        String fileStage = filename.@fileStage.text()
        String pattern = filename.@pattern.text()
        String selectionTag = extractAttributeText(filename, "selectiontag", Constants.DEFAULT)
        FileStage fs = null

        if (fileStage.contains(".")) { //Load without a base package / class
            int index = fileStage.lastIndexOf(".")

            filestagesbase = fileStage[0..index - 1]
            fileStage = fileStage[index + 1..-1]
        }

        if (!filestagesbase) {
            throw new RuntimeException("Filestage was not specified correctly. Need a base package/class or full qualified name.")
        }

        Class baseClass
        try {
            baseClass = LibrariesFactory.getInstance().loadClass(filestagesbase)
        } catch (ClassNotFoundException ex) {
            logger.severe("Could not load class ${filestagesbase}")
        }
        if (baseClass) {
            Field f = baseClass.getDeclaredField(fileStage)
            boolean isStatic = Modifier.isStatic(f.getModifiers())
            if (!isStatic)
                throw new RuntimeException("A filestage must be either a new object or a static field of a class.")
            fs = (FileStage) f.get(null)
        } else {
            fs = LibrariesFactory.getInstance().loadClass(filestagesbase + "." + fileStage)
        }

        FilenamePattern fp = new FileStageFilenamePattern(_cls, fs, pattern, selectionTag)
        return fp
    }

    @groovy.transform.CompileStatic(TypeCheckingMode.SKIP)
    boolean readProcessingTools(NodeChild configurationNode, Configuration config) {
        Map<String, ToolEntry> toolEntries = config.getTools().getMap()
        boolean hasErrors = false
        for (NodeChild tool in configurationNode.processingTools.tool) {
            String toolID = tool.@name.text()
            logger.postRareInfo("Processing tool ${toolID}")
            def toolReader = new ProcessingToolReader(tool, config)
            def toolEntry = toolReader.readProcessingTool()
            if (toolReader.hasErrors()) {
                String xml
                try {
                    xml = ERROR_PRINTOUT_XML_LINEPREFIX + XmlUtil.serialize(new StreamingMarkupBuilder().bind { it -> it.faulty tool }.toString()).readLines()[1..-2].join("\n" + ERROR_PRINTOUT_XML_LINEPREFIX)

                } catch (Exception ex) {
                    xml = "Cannot display xml code for tool node."
                }
                config.addLoadError(new ConfigurationLoadError(config, "ConfigurationFactory - " + (toolID ?: "Tool id was not properly set"), "Tool ${toolID} could not be read. Please check the tool syntax and following errors:\n" + xml, null))
                config.addLoadErrors(toolReader.loadErrors)
                hasErrors = true
            } else
                toolEntries[toolID] = toolEntry
        }
        return !hasErrors
    }

    @groovy.transform.CompileStatic(TypeCheckingMode.SKIP)
    private void readEnums(Configuration config, NodeChild configurationNode) {

        Map<String, Enumeration> enumerations = config.getEnumerations().getMap()

        for (enumeration in configurationNode.enumerations.enum) {
            String eName = enumeration.@name.text()
            enumeration.attributes().get("description")
            String eDescription = extractAttributeText(enumeration, "description")
            String extendStr = extractAttributeText(enumeration, "extends")
            //TODO Enumeration extend
            List<EnumerationValue> values = []
            for (value in enumeration.value) {
                String vID = value.@id.text()
                String valueTag = value.@valueTag.text()
                String vDescription = extractAttributeText(value, "description")
                values << new EnumerationValue(vID, vDescription, valueTag)
            }
            enumerations[eName] = new Enumeration(eName, values, eDescription)
        }


        try {
            for (Enumeration e in enumerations.values()) {
                config.getEnumerations().add(e)
            }
        } catch (NullPointerException ex) {
            logger.severe("Configuration ${icc.id} null pointer")
        }
    }

    @groovy.transform.CompileStatic(TypeCheckingMode.SKIP)
    private Map<String, AnalysisConfiguration> _loadAnalyses(NodeChildren nAnalyses, AnalysisConfiguration parentConfiguration = null) {
        Map<String, AnalysisConfiguration> availableAnalyses = [:]
        for (NodeChild analysis in nAnalyses.analysis) {
            String analysisID = extractAttributeText((NodeChild) analysis, "id")
            String analysisCfg = extractAttributeText((NodeChild) analysis, "configuration")
            String usePluginAttribute = extractAttributeText((NodeChild) analysis, "useplugin")
            String killSwitchesAttribute = extractAttributeText((NodeChild) analysis, "killswitches")
            AnalysisConfiguration ac = new AnalysisConfigurationProxy(parentConfiguration, analysisID, analysisCfg, usePluginAttribute, killSwitchesAttribute, analysis)
            availableAnalyses[analysisID] = ac

            _loadAnalyses(analysis.subanalyses, ac).each {
                String k, AnalysisConfiguration subConfig ->
                    availableAnalyses[analysisID + "-" + k] = subConfig
            }
        }
        return availableAnalyses
    }

    AnalysisConfiguration lazyLoadAnalysisConfiguration(AnalysisConfigurationProxy proxy) {
        String analysisID = proxy.getAnalysisID()
        String analysisCfg = proxy.getAnalysisCfg()
        AnalysisConfiguration parentConfiguration = proxy.getParentConfiguration()
        AnalysisConfiguration ac = (AnalysisConfiguration) getConfiguration(analysisCfg)
        proxy.setAnalysisConfiguration(ac)
        if (parentConfiguration) {
            ac.addParent(parentConfiguration)
        }

        // See if there are configurationvalues for the projects analysis entry which override the analysis configuration values.
        NodeChild analysis = proxy.getAnalysisNode()
        readConfigurationValues(analysis, ac)
        return ac
    }

    /**
     * Load a map of configuration values from the specified node. Store those values in the configuration.
     * @param configurationNode
     * @param config
     */
    @groovy.transform.CompileStatic(TypeCheckingMode.SKIP)
    void readConfigurationValues(NodeChild configurationNode, Configuration config) {
        Map<String, ConfigurationValue> configurationValues = config.getConfigurationValues().getMap()
        for (NodeChild cvalueNode in configurationNode.configurationvalues.cvalue) {
            //TODO Code deduplication! Also in readCVBundle.
            ConfigurationValue cvalue = readConfigurationValue(cvalueNode, config)
            if (!Roddy.getCommandLineCall().isOptionSet(RoddyStartupOptions.ignorecvalueduplicates) && configurationValues.containsKey(cvalue.id)) {
                String cval0 = configurationValues[cvalue.id].value//?.length() > 20 ? configurationValues[cvalue.id].value[0 .. 20] : configurationValues[cvalue.id].value;
                String cval1 = cvalue.value//?.length() ? cvalue.value[0..20] : cvalue.value;
                addFormattedErrorToConfig("Value ${cvalue.id} in the configurationvalues block in ${config.getID()} is defined more than once and might contain the wrong value.".toString(), "cvalue", cvalueNode, config)
            }
            configurationValues[cvalue.id] = cvalue
        }
    }

    static void addFormattedErrorToConfig(String message, String id, NodeChild child, Configuration config) {
        config.addLoadError(new ConfigurationLoadError(config, id, message + ([""] + RoddyConversionHelperMethods.toFormattedXML(child)).join("\n" + ERROR_PRINTOUT_XML_LINEPREFIX), null))
    }

    /**
     * Read in a single configurationvalue from the node cvalueNode.
     * @param configurationNode
     * @param config
     */
    @groovy.transform.CompileStatic(TypeCheckingMode.SKIP)
    private ConfigurationValue readConfigurationValue(NodeChild cvalueNode, Configuration config) {
        String key = cvalueNode.@name.text()
        String value = cvalueNode.@value.text()
        String type = extractAttributeText(cvalueNode, "type", "string")
        List<String> tags = extractAttributeText(cvalueNode, "tags", null)?.split(StringConstants.COMMA)

        if (!cvalueNode.attributes().containsKey("name"))
            addFormattedErrorToConfig("The key attribute must be set for a cvalue entry.", "cvalues", cvalueNode, config)
        if (!cvalueNode.attributes().containsKey("value"))
            addFormattedErrorToConfig("The value attribute must be set for a cvalue entry.", "cvalues", cvalueNode, config)

        //OK, here comes some sort of valuable hack. In the past it was so, that sometimes people forgot to set
        //any directory to "path". In case of the output directories, this was a bad thing! So we know about
        //this specific type of variable and just set it to path at any time.
        if (key.endsWith("OutputDirectory"))
            type = "path"
        String description = extractAttributeText(cvalueNode, "description")
        return new ConfigurationValue(config, key, value, type, description, tags)
    }


    static String extractAttributeText(NodeChild node, String id, String defaultText = "") {
        try {
            if (node.attributes().get(id) != null) {
                return node.attributes().get(id).toString()
            }
        } catch (Exception ex) {
            logger.severe("" + ex)
            throw new ConfigurationLoadError(null, id, "Attribute '${id}' not defined in node ${node.name()}", ex)
        }
        return defaultText
    }


    static String extractAttributeText(NodeChildren nodeChildren, String id, String defaultText = "") {
        String name = nodeChildren.name()
        Object o = nodeChildren.parent().children().find { NodeChild child -> child.name() == name }
        if (o instanceof NodeChild)
            return extractAttributeText((NodeChild) o, id, defaultText)
        return defaultText
    }

    ProjectConfiguration getProjectConfiguration(String s) {
        return getConfiguration(s) as ProjectConfiguration
    }

    AnalysisConfiguration getAnalysisConfiguration(String s) {
        Map test = availableConfigurationsByTypeAndID[ConfigurationType.ANALYSIS]
        return test[s] as AnalysisConfiguration
    }

}
