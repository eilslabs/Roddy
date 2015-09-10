package de.dkfz.roddy.config

import de.dkfz.roddy.*
import de.dkfz.roddy.client.RoddyStartupOptions
import de.dkfz.roddy.config.converters.XMLConverter
import de.dkfz.roddy.config.validation.XSDValidator
import de.dkfz.roddy.knowledge.brawlworkflows.BrawlWorkflow
import de.dkfz.roddy.knowledge.nativeworkflows.NativeWorkflow
import de.dkfz.roddy.tools.*
import de.dkfz.roddy.Roddy
import de.dkfz.roddy.config.Configuration.ConfigurationType
import de.dkfz.roddy.config.ToolEntry.ToolFileGroupParameter.PassOptions
import de.dkfz.roddy.config.ToolEntry.ToolStringParameter.ParameterSetbyOptions
import de.dkfz.roddy.core.*
import de.dkfz.roddy.execution.io.fs.FileSystemInfoProvider
import de.dkfz.roddy.knowledge.files.*
import de.dkfz.roddy.plugins.*
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import groovy.util.slurpersupport.*
import groovy.xml.MarkupBuilder
import org.apache.commons.io.filefilter.WildcardFileFilter

import java.lang.annotation.Native
import java.lang.reflect.*
import java.util.logging.*

import static de.dkfz.roddy.Constants.ENV_LINESEPARATOR as NEWLINE
import static de.dkfz.roddy.StringConstants.*

/**
 * Factory for loading, importing, exporting and writing configuration files.
 * @author michael
 */
@groovy.transform.CompileStatic
public class ConfigurationFactory {

    public static final String XMLTAG_EXECUTIONSERVICE_SSHUSER = "executionServiceSSHUser";
    public static final String XMLTAG_EXECUTIONSERVICE_SHOW_SSHCALLS = "executionServiceShowSSHCalls";
    public static final String XMLTAG_ATTRIBUTE_INHERITANALYSES = "inheritAnalyses"
    public static final String XMLTAG_PREVENT_JOB_EXECUTION = "preventJobExecution";
    public static final String XMLTAG_USE_CENTRAL_ANALYSIS_ARCHIVE = "useCentralAnalysisArchive";
    public static final String XMLTAG_OUTPUT_FILE_GROUP = "outputFileGroup"
    public static final String XMLTAG_OUTPUT_UMASK = "outputUMask"

    public static final LoggerWrapper logger = LoggerWrapper.getLogger(ConfigurationFactory.class.name);

    private static ConfigurationFactory singleton = new ConfigurationFactory();


    private Map<String, InformationalConfigurationContent> availableConfigurations = [:];

    private Map<ConfigurationType, List<InformationalConfigurationContent>> availableConfigurationsByType = [:];

    private Map<ConfigurationType, Map<String, InformationalConfigurationContent>> availableConfigurationsByTypeAndID = [:]


    public static ConfigurationFactory getInstance() {
        return singleton;
    }

    private ConfigurationFactory() {
//        long test = System.nanoTime();
        loadAvailableProjectConfigurationFiles()
//        println((System.nanoTime() - test));
    }

    private void loadAvailableProjectConfigurationFiles() {
        List<File> pipelineDirectories = Roddy.getConfigurationDirectories();

        List<File> allFiles = []
        pipelineDirectories.parallelStream().each {
            File baseDir ->
                logger.log(Level.CONFIG, "Searching for configuration files in: " + baseDir.toString());
                File[] files = baseDir.listFiles((FileFilter) new WildcardFileFilter("*.xml"));
                if (files == null) {
                    logger.info("No configuration files found in path ${baseDir.getAbsolutePath()}");
                }
                for (File f in files) {
                    synchronized (allFiles) {
                        if (!allFiles.contains(f))
                            allFiles << f;
                    }
                }
        }

        allFiles.parallelStream().each {
            File it ->
                try {
                    def icc = loadInformationalConfigurationContent(it);
                    if (availableConfigurations.containsKey(icc.name)) {
                        throw new RuntimeException("Configuration with name ${icc.name} already exists! Names must be unique.")
                    }

                    availableConfigurations[icc.id] = icc;
                    availableConfigurationsByType.get(icc.type, []) << icc;
                    availableConfigurationsByTypeAndID.get(icc.type, [:])[icc.id] = icc;
                    for (InformationalConfigurationContent iccSub in icc.getAllSubContent()) {
                        availableConfigurations[iccSub.id] = iccSub;
                    }

                } catch (Exception ex) {
                    logger.severe("File ${it.absolutePath} cannot be loaded! Error in config file! ${ex.toString()}");
                    logger.severe(RoddyIOHelperMethods.getStackTraceAsString(ex));
                }
        }
    }

    public void loadAvailableAnalysisConfigurationFiles() {
        List<File> allFiles = []
        Map<File, PluginInfo> pluginsByFile = [:]
        for (PluginInfo pi in LibrariesFactory.getInstance().getLoadedPlugins()) {
            File configPath = RoddyIOHelperMethods.assembleLocalPath(pi.directory, "resources", "configurationFiles");
            File[] configFiles = configPath.listFiles((FileFilter) new WildcardFileFilter("*.xml"));
            for (File f in configFiles) {
                allFiles.add(f);
                pluginsByFile[f] = pi;
            }
        }

        for (File it in allFiles) {
            try {
                def icc = loadInformationalConfigurationContent(it);
                File readmeFile = RoddyIOHelperMethods.assembleLocalPath(pluginsByFile[it].directory, "README." + icc.id + ".txt");
                if (readmeFile.exists())
                    icc.setReadmeFile(readmeFile);

                if (availableConfigurations.containsKey(icc.name)) {
                    throw new RuntimeException("Configuration with name ${icc.name} already exists! Names must be unique.")
                }

                availableConfigurations[icc.id] = icc;
                availableConfigurationsByType.get(icc.type, []) << icc;
                availableConfigurationsByTypeAndID.get(icc.type, [:])[icc.id] = icc;
                for (InformationalConfigurationContent iccSub in icc.getAllSubContent()) {
                    availableConfigurations[iccSub.id] = iccSub;
                }

            } catch (Exception ex) {
                logger.severe("File ${it.absolutePath} cannot be loaded! Error in config file! ${ex.toString()}");
                logger.severe(RoddyIOHelperMethods.getStackTraceAsString(ex));
            }
        }
    }

    public void refresh() {
        singleton = new ConfigurationFactory();
    }

    public Map<String, InformationalConfigurationContent> getAllAvailableConfigurations() {
        return availableConfigurations;
    }

    /**
     * Returns a list of all availabe analysis configurations.
     * Returns an empty list if no configurations is known.
     * @return
     */
    public List<InformationalConfigurationContent> getAvailableAnalysisConfigurations() {
        return getAvailableConfigurationsOfType(ConfigurationType.ANALYSIS);
    }

    /**
     * Returns a list of all availabe project configurations.
     * Returns an empty list if no configurations is known.
     * @return
     */
    public List<InformationalConfigurationContent> getAvailableProjectConfigurations() {
        return getAvailableConfigurationsOfType(ConfigurationType.PROJECT);
    }

    /**
     * Returns a list of all availabe configurations of the given type.
     * Returns an empty list if no configurations is known.
     * @return
     */
    public List<InformationalConfigurationContent> getAvailableConfigurationsOfType(ConfigurationType type) {
        return availableConfigurationsByType.get(type, []);
    }

    /**
     * Loads basic info about a configuration file.
     *
     * Basic info contains i.e. the name, description, subconfigs and the type of a configuration.
     * @see InformationalConfigurationContent
     *
     * @param file The config file.
     * @return An object containing basic information about a configuration.
     */
    public InformationalConfigurationContent loadInformationalConfigurationContent(File file) {
        String text = file.text;
        NodeChild xml = (NodeChild) new XmlSlurper().parseText(text);
        return _loadInformationalConfigurationContent(file, text, xml, null);
    }

    /**
     * Loads a basic / informational part of each available configurationNode file.
     * Recursive helper method.
     * @param configurationNode
     * @return
     */
    @groovy.transform.CompileStatic(TypeCheckingMode.SKIP)
    private InformationalConfigurationContent _loadInformationalConfigurationContent(File file, String text, NodeChild configurationNode, InformationalConfigurationContent parent) {
        NodeChild.metaClass.extract = { String id, String defaultValue -> return extractAttributeText((NodeChild) delegate, id, defaultValue) }
        Map<String, Configuration> subConfigurations = [:];
        List<InformationalConfigurationContent> subConf = new LinkedList<InformationalConfigurationContent>();
        InformationalConfigurationContent icc = null;

        Configuration.ConfigurationType type = extractAttributeText(configurationNode, "configurationType", parent != null ? parent.type.name().toUpperCase() : Configuration.ConfigurationType.OTHER.name()).toUpperCase();
        String cls = extractAttributeText(configurationNode, "class", Project.class.name);
        String name = extractAttributeText(configurationNode, "name");
        String description = extractAttributeText(configurationNode, "description");
        String imports = extractAttributeText(configurationNode, "imports");

        if (type == ConfigurationType.PROJECT) {
            List<String> analyses = [];

            NodeChildren san = configurationNode.availableAnalyses;
            if (!Boolean.parseBoolean(extractAttributeText(configurationNode, XMLTAG_ATTRIBUTE_INHERITANALYSES, FALSE))) {
                analyses = _loadICCAnalyses(san);
            } else {
                analyses = parent.getListOfAnalyses();
            }
            ToolEntry.ResourceSetSize setSize = ToolEntry.ResourceSetSize.valueOf(extractAttributeText(configurationNode, "usedresourcessize", "l"));
            icc = new InformationalConfigurationContent(parent, type, name, description, cls, configurationNode, imports, setSize, analyses, subConf, file, text);
        } else {
            icc = new InformationalConfigurationContent(parent, type, name, description, cls, configurationNode, imports, subConf, file, text);
        }

        for (subConfiguration in configurationNode.subconfigurations.configuration) {
            subConf << _loadInformationalConfigurationContent(file, text, subConfiguration, icc);
        }

        return icc;
    }

    @groovy.transform.CompileStatic(TypeCheckingMode.SKIP)
    private List<String> _loadICCAnalyses(NodeChildren analyses) {
        List<String> listOfanalyses = []
        for (analysis in analyses.analysis) {
            String id = analysis.@id.text();
            String configuration = analysis.@configuration.@id.text();
            String useplugin = extractAttributeText(analysis, "useplugin", "");
            String killswitches = extractAttributeText(analysis, "killswitches", "")
            String idStr = "${id}::${configuration}::useplugin=${useplugin}::killswitches=${killswitches}".toString();

            listOfanalyses << idStr;
        }
        return listOfanalyses;
    }

    public Configuration getConfiguration(String usedConfiguration) {
        InformationalConfigurationContent icc = availableConfigurations[usedConfiguration];

        if (icc == null) {
            throw new RuntimeException("Configuration path ${usedConfiguration} is not valid or the configuration is not existing.")
        }

        loadConfiguration(icc);
    }

    public Configuration loadConfiguration(InformationalConfigurationContent icc) {
        Configuration config = _loadConfiguration(icc);

        for (String ic in config.getImportConfigurations()) {
            try {
                Configuration cfg = getConfiguration(ic);
                config.addParent(cfg);
            } catch (Exception ex) {
                if (LibrariesFactory.getInstance().areLibrariesLoaded())
                    logger.severe("Configuration ${ic} cannot be read!" + ex.toString());
            }
        }
        return config;
    }

    /**
     * Reverse - recursively load a configuration. Start with the deepest configuration object and move to the front.
     * The reverse walk ist possible as the information about dependencies is stored in the InformationalConfigurationContent objects which are created on startup.
     */
    private Configuration _loadConfiguration(InformationalConfigurationContent icc) {
        Configuration parentConfig = icc.parent != null ? loadConfiguration(icc.parent) : null;
        NodeChild configurationNode = icc.configurationNode;
        Configuration config = null;

        try {
            //If the configurationNode is a project or a variant then it is allowed to import analysis configurations.
            config = createConfigurationObject(icc, configurationNode, parentConfig)

            try {
                readConfigurationValues(configurationNode, config)
            } catch (Exception ex) {
                config.addLoadError(new ConfigurationLoadError(config, "cValues", "Could not read configuration values for configuration ${icc.id}", ex));
            }

            try {
                readValueBundles(configurationNode, config)
            } catch (Exception ex) {
                config.addLoadError(new ConfigurationLoadError(config, "cValues", "Could not read configuration value bundles for configuration ${icc.id}", ex));
            }

            try {
                readFilenamePatterns(configurationNode, config)
            } catch (Exception ex) {
                config.addLoadError(new ConfigurationLoadError(config, "cValues", "Could not read filename patterns for configuration ${icc.id}", ex));
            }

            try {
                readProcessingTools(configurationNode, config)
            } catch (Exception ex) {
                config.addLoadError(new ConfigurationLoadError(config, "cValues", "Could not read processing tools for configuration ${icc.id}", ex));
            }

            try {
                readEnums(config, configurationNode)
            } catch (Exception ex) {
                config.addLoadError(new ConfigurationLoadError(config, "cValues", "Could not read enumerations for configuration ${icc.id}", ex));
            }

            return config;
        } catch (Exception ex) {
            logger.severe("Configuration ${icc.id} cannot be loaded!" + ex.toString());
            return null;
        }
    }

    /**
     * Create a configuration object which depends on the information taken out of the configuration xml file.
     *
     * @param icc The informational object for a configuration (file)
     * @param configurationNode The xml node read in by an xml slurper
     * @param parentConfig A (possibly) available parent configuration.
     * @return A new configuration object
     */
    @groovy.transform.CompileStatic(TypeCheckingMode.SKIP)
    private Configuration createConfigurationObject(InformationalConfigurationContent icc, NodeChild configurationNode, Configuration parentConfig) {
        Configuration config;
        if (icc.type >= ConfigurationType.PROJECT) {
            Map<String, AnalysisConfiguration> availableAnalyses = [:];
            String runtimeServiceClass = extractAttributeText(configurationNode, "runtimeServiceClass", null);
            config = new ProjectConfiguration(icc, runtimeServiceClass, availableAnalyses, parentConfig);
            boolean inheritAnalyses = Boolean.parseBoolean(extractAttributeText(configurationNode, XMLTAG_ATTRIBUTE_INHERITANALYSES, "false"));
            if (!inheritAnalyses) {
                availableAnalyses.putAll(_loadAnalyses(configurationNode.availableAnalyses));
            } else {
                if (parentConfig instanceof ProjectConfiguration) {
                    ProjectConfiguration pcParent = (ProjectConfiguration) parentConfig;
                    availableAnalyses.putAll(pcParent.getAnalyses());
                }
            }
        } else if (icc.type == ConfigurationType.ANALYSIS) {
            String brawlWorkflow = extractAttributeText(configurationNode, "brawlWorkflow", null);
            String brawlBaseWorkflow = extractAttributeText(configurationNode, "brawlBaseWorkflow", Workflow.class.name);

            String workflowTool = extractAttributeText(configurationNode, "nativeWorkflowTool", null);
            String commandFactoryClass = extractAttributeText(configurationNode, "targetCommandFactory", null);

            String workflowClass = extractAttributeText(configurationNode, "workflowClass");

            if(workflowTool && commandFactoryClass) {
                workflowClass = NativeWorkflow.class.name
            } else if(brawlWorkflow) {
                workflowClass = BrawlWorkflow.class.name
            }
            String cleanupScript = extractAttributeText(configurationNode, "cleanupScript", "cleanupScript");
            String[] _listOfUsedTools = extractAttributeText(configurationNode, "listOfUsedTools").split(SPLIT_COMMA);
            String[] _usedToolFolders = extractAttributeText(configurationNode, "usedToolFolders").split(SPLIT_COMMA);
            List<String> listOfUsedTools = _listOfUsedTools.size() > 0 && _listOfUsedTools[0] ? Arrays.asList(_listOfUsedTools) : null;
            List<String> usedToolFolders = _usedToolFolders.size() > 0 && _usedToolFolders[0] ? Arrays.asList(_usedToolFolders) : null;
            Map<String, TestDataOption> testdataOptions = new HashMap<>();

            for (NodeChild testdataoption in configurationNode.testdataoptions.testdataoption) {
                String tdId = extractAttributeText((NodeChild) testdataoption, "id", "small");
                String tdSize = extractAttributeText((NodeChild) testdataoption, "size", "10000");
                String tdRatio = extractAttributeText((NodeChild) testdataoption, "ratio", "absolute");
                String tdPath = extractAttributeText((NodeChild) testdataoption, "testDataPath", '${testDataDirectory}');
                String tdOutPath = extractAttributeText((NodeChild) testdataoption, "outputPath", '${testDataOutputBaseDirectory}');

                int size = Integer.parseInt(tdSize);
                TestDataOption.Ratio ratio = tdRatio.toLowerCase();
                ConfigurationValue path = new ConfigurationValue(config, "path", tdPath, "path");
                ConfigurationValue outputPath = new ConfigurationValue(config, "outputPath", tdOutPath, "path");
                TestDataOption tdo = new TestDataOption(tdId, size, ratio, path, outputPath);
                testdataOptions[tdId] = tdo;
            }
            config = new AnalysisConfiguration(icc, workflowClass, testdataOptions, parentConfig, listOfUsedTools, usedToolFolders, cleanupScript);

            if(workflowTool && commandFactoryClass) {
                ((AnalysisConfiguration) config).setNativeToolID(workflowTool);
                ((AnalysisConfiguration) config).setTargetCommandFactory(commandFactoryClass);
            }
            if(brawlWorkflow) {
                ((de.dkfz.roddy.config.AnalysisConfiguration) config).setBrawlWorkflow(brawlWorkflow);
                ((de.dkfz.roddy.config.AnalysisConfiguration) config).setBrawlBaseWorkflow(brawlBaseWorkflow);
            }

        } else {
            config = new Configuration(icc);
        }
        return config;
    }

    @groovy.transform.CompileStatic(TypeCheckingMode.SKIP)
    private void readValueBundles(NodeChild configurationNode, Configuration config) {
        Map<String, ConfigurationValueBundle> cvBundles = config.getConfigurationValueBundles().getMap();

        for (NodeChild cbundle in configurationNode.configurationvalues.configurationValueBundle) {
            Map<String, ConfigurationValue> bundleValues = new LinkedHashMap<String, ConfigurationValue>();
            for (cvalue in cbundle.cvalue) {
                ConfigurationValue _cvalue = readConfigurationValue(cvalue, config);
                bundleValues[_cvalue.id] = _cvalue;
            }
            cvBundles[cbundle.@name.text()] = bundleValues;
        }
    }

    @groovy.transform.CompileStatic(TypeCheckingMode.SKIP)
    private void readFilenamePatterns(NodeChild configurationNode, Configuration config) {
        Map<String, FilenamePattern> filenamePatterns = config.getFilenamePatterns().getMap();

        for (NodeChild filenames in configurationNode.filenames) {
            String pkg = extractAttributeText(filenames, "package", "de.dkfz.roddy.synthetic.files");
            boolean packageIsSet = pkg && pkg != "de.dkfz.roddy.synthetic.files";
            String filestagesbase = extractAttributeText(filenames, "filestagesbase", null);

            for (NodeChild filename in filenames.filename) {
                try {
                    String classSimpleName = filename.@class.text();
                    String cls;
                    Class foundClass = !packageIsSet ? LibrariesFactory.instance.searchForClass(classSimpleName) : null;
                    if(foundClass)
                        cls = foundClass.name;
                    else
                        cls = (pkg != null ? pkg + "." : "") + filename.@class.text();
                    String pattern = filename.@pattern.text();
                    String selectionTag = extractAttributeText(filename, "selectiontag", FilenamePattern.DEFAULT_SELECTION_TAG);
                    Class _classID
                    try {
                        _classID = foundClass ?: LibrariesFactory.getInstance().searchForClass(cls);
                    } catch (Exception ex) {
                        _classID = null;
                    }

                    boolean isDerivedFromFile = filename.attributes().get("derivedFrom") != null
                    if (!_classID) {
                        //Create a synthetic class...
                        String constructorClassName = BaseFile.class.name
                        if (isDerivedFromFile) {
                            String dfc = filename.@derivedFrom.text();
                            //(pkg != null ? pkg + "." : "") + filename.@derivedFrom.text();
                            try {
                                Class foundDerivedFromClass = LibrariesFactory.instance.searchForClass(dfc.split(StringConstants.SPLIT_SBRACKET_LEFT)[0]);
                                if(foundDerivedFromClass)
                                    dfc = foundDerivedFromClass.name;
                            } catch (Exception ex) {
                            }
                            if(!dfc.contains(".")) dfc = pkg + "." + dfc;
                            if (dfc.contains("[")) {
                                int openingIndex = dfc.indexOf("[");
                                int closingIndex = dfc.indexOf("]");
                                if (closingIndex - 1 > openingIndex) {
                                    enforcedArraySize = Integer.parseInt(dfc[openingIndex + 1..-2]);
                                }
                                dfc = dfc[0..openingIndex - 1];
                                isArray = true;
                            }
                            constructorClassName = dfc;
                        }
                        String syntheticFileClass =
                                "package $pkg" + NEWLINE +
                                "import " + BaseFile.name + NEWLINE +
                                "public class ${filename.@class.text()} extends BaseFile {" + NEWLINE +
                                "    public ${filename.@class.text()}($constructorClassName baseFile) {" + NEWLINE +
                                "        super(baseFile);" + NEWLINE +
                                "    }" + NEWLINE +
                                "}";
                        GroovyClassLoader groovyClassLoader = LibrariesFactory.getGroovyClassLoader();
                        _classID = (Class<BaseFile>)groovyClassLoader.parseClass(syntheticFileClass);
                        LibrariesFactory.getInstance().getSynthetic().addClass(_classID);
                    }

                    Class<BaseFile> _cls = (Class<BaseFile>) _classID;
//                    Class<BaseFile> _cls = (Class<BaseFile>) LibrariesFactory.getInstance().loadClass(cls);
                    FilenamePattern fp = null;

                    if (isDerivedFromFile) {
                        String fnDerivedFrom = filename.@derivedFrom.text();
                        String dfc = filename.@derivedFrom.text();
                        //(pkg != null ? pkg + "." : "") + filename.@derivedFrom.text();
                        try {
                            Class foundDerivedFromClass = LibrariesFactory.instance.searchForClass(dfc.split(StringConstants.SPLIT_SBRACKET_LEFT)[0]);
                            if(foundDerivedFromClass)
                                dfc = foundDerivedFromClass.name;
                        } catch (Exception ex) {
                        }
                        if(!dfc.contains("."))
                            dfc = (!fnDerivedFrom.contains(".") && pkg != null ? pkg + "." : "") + fnDerivedFrom;
                        boolean isArray = false;
                        int enforcedArraySize = -1;
                        //Support for arrays of the same file class
                        if (dfc.contains("[")) {
                            int openingIndex = dfc.indexOf("[");
                            int closingIndex = dfc.indexOf("]");
                            if (closingIndex - 1 > openingIndex) {
                                enforcedArraySize = Integer.parseInt(dfc[openingIndex + 1..-2]);
                            }
                            dfc = dfc[0..openingIndex - 1];
                            isArray = true;
                        }

                        Class<BaseFile> _dfc = (Class<BaseFile>) LibrariesFactory.getInstance().loadClass(dfc);
                        fp = new FilenamePattern(_cls, _dfc, pattern, selectionTag, isArray, enforcedArraySize);
                    } else if (filename.attributes().get("fileStage") != null) {
                        String fileStage = filename.@fileStage.text();
                        FileStage fs = null;

                        if (fileStage.contains(".")) { //Load without a base package / class
                            int index = fileStage.lastIndexOf(".");

                            filestagesbase = fileStage[0..index - 1];
                            fileStage = fileStage[index + 1..-1];
                        }

                        if (!filestagesbase) {
                            throw new RuntimeException("Filestage was not specified correctly. Need a base package/class or full qualified name.")
                        }

                        Class baseClass = LibrariesFactory.getInstance().tryLoadClass(filestagesbase);
                        if (baseClass) {
                            Field f = baseClass.getDeclaredField(fileStage);
                            boolean isStatic = Modifier.isStatic(f.getModifiers());
                            if (!isStatic)
                                throw new RuntimeException("A filestage must be either a new object or a static field of a class.");
                            fs = (FileStage) f.get(null);
                        } else {
                            fs = LibrariesFactory.getInstance().loadClass(filestagesbase + "." + fileStage);
                        }

                        fp = new FilenamePattern(_cls, fs, pattern, selectionTag);
                    } else if (filename.attributes().get("onScript") != null) {
                        String scriptName = filename.@onScript.text();
                        Class<FileObject> calledClass = _cls;
                        fp = new FilenamePattern(_cls, scriptName, pattern, selectionTag);
                    } else if (filename.attributes().get("onMethod") != null) {
                        String methodName = filename.@onMethod.text();
                        Class<FileObject> calledClass = _cls;
                        if (methodName.contains(".")) { //Different class as source class!
                            String[] stuff = methodName.split(SPLIT_STOP);
                            String className = stuff[0];
                            String classPkg = null;
                            if (stuff.length > 2) { //We have a package specified.
                                classPkg = stuff[0..-3].join(".");
                                className = stuff[-2];
                            }
                            if (classPkg == null) classPkg = pkg;
                            String calledClassName = (classPkg != null ? classPkg + "." : "") + className;
                            calledClass = (Class<FileObject>) LibrariesFactory.getInstance().loadClass(calledClassName);
                            methodName = stuff[-1];
                        }
                        Method _method = null;
                        for (Method m : calledClass.getMethods()) {
                            if (m.name == methodName) {
                                _method = m;
                            }
                        }
                        fp = new FilenamePattern(_cls, calledClass, _method, pattern, selectionTag);
                    }
                    if (fp == null) {
                        throw new RuntimeException("filename pattern is not valid: ")
                    }
                    filenamePatterns.put(fp.getID(), fp);
//                        try {
//                            config.addFilenamePattern(fp);
//                        } catch (Exception ex) {
//                            throw new RuntimeException("problem with adding fp to cfg");
//                        }
//                    ClassLoader.getSystemClassLoader().get
                } catch (Exception ex) {
                    logger.severe("filename pattern definition is not valid: " + (new groovy.xml.StreamingMarkupBuilder().bindNode(filename) as String));
                }
            }
        }
    }

    @groovy.transform.CompileStatic(TypeCheckingMode.SKIP)
    private void readProcessingTools(NodeChild configurationNode, Configuration config) {
        Map<String, ToolEntry> toolEntries = config.getTools().getMap();

        for (NodeChild tool in configurationNode.processingTools.tool) {
            String toolID = tool.@name.text()
            String path = tool.@value.text()
            String basePathId = tool.@basepath.text()
            boolean overrideresourcesets = extractAttributeText(tool, "overrideresourcesets", "false").toBoolean();
            ToolEntry currentEntry = new ToolEntry(toolID, basePathId, path);
            if (overrideresourcesets)
                currentEntry.setOverridesResourceSets();
            int noOfChildren = tool.children().size();
            if (noOfChildren > 0) {
                List<ToolEntry.ToolParameter> inputParameters = new LinkedList<>();
                List<ToolEntry.ToolParameter> outputParameters = new LinkedList<>();
                List<ToolEntry.ResourceSet> resourceSets = new LinkedList<>();
                for (NodeChild child in tool.children()) {
                    String cName = child.name();

                    if (cName == "resourcesets") {
                        for (NodeChild rset in child.rset) {
                            try {
                                ToolEntry.ResourceSetSize rsetSize = rset.@size.text();
                                //Is it short defined or long defined?
                                String valueList = extractAttributeText(rset, "values", "");
                                if (!valueList) { //Must be fully specified.
                                    Float rsetUsedMemory = extractAttributeText(rset, "memory", null)?.toFloat();
                                    Integer rsetUsedCores = extractAttributeText(rset, "cores", null)?.toInteger();
                                    Integer rsetUsedNodes = extractAttributeText(rset, "nodes", null)?.toInteger();
                                    Integer rsetUsedWalltime = extractAttributeText(rset, "walltime", null)?.toInteger();
                                    String rsetUsedQueue = extractAttributeText(rset, "queue", null);
                                    String rsetUsedNodeFlag = extractAttributeText(rset, "nodeflag", null);
                                    resourceSets << new ToolEntry.ResourceSet(rsetSize, rsetUsedMemory, rsetUsedCores, rsetUsedNodes, rsetUsedWalltime, null, rsetUsedQueue, rsetUsedNodeFlag);
                                } else {
                                    String[] split = valueList.split(":");
                                    Integer[] splitInt = split.collect { String s -> return s.toInteger(); }
                                    resourceSets << new ToolEntry.ResourceSet(rsetSize, splitInt[0], splitInt[1], splitInt[2], splitInt[3], null, null, null);
                                }
                            } catch (Exception ex) {
                                config.addLoadError(new ConfigurationError("Resource set could not be read", config, "", ex));
                            }
                        }
                    } else if (cName == "input") {
                        inputParameters << parseToolParameter(toolID, child);
                    } else if (cName == "output") {
                        outputParameters << parseToolParameter(toolID, child);
                    }
                }
                currentEntry.setGenericOptions(inputParameters, outputParameters, resourceSets);
            }
            toolEntries[toolID] = currentEntry;
        }
    }

    @groovy.transform.CompileStatic(TypeCheckingMode.SKIP)
    private void readEnums(Configuration config, NodeChild configurationNode) {

        Map<String, Enumeration> enumerations = config.getEnumerations().getMap();

        for (enumeration in configurationNode.enumerations.enum) {
            String eName = enumeration.@name.text()
            enumeration.attributes().get("description");
            String eDescription = extractAttributeText(enumeration, "description");
            String extendStr = extractAttributeText(enumeration, "extends");
            //TODO Enumeration extend
            List<EnumerationValue> values = [];
            for (value in enumeration.value) {
                String vID = value.@id.text()
                String valueTag = value.@valueTag.text()
                String vDescription = extractAttributeText(value, "description");
                values << new EnumerationValue(vID, vDescription, valueTag);
            }
            enumerations[eName] = new Enumeration(eName, values, eDescription);
        }


        try {
            for (Enumeration e in enumerations.values()) {
                config.getEnumerations().add(e);
            }
        } catch (NullPointerException ex) {
            logger.severe("Configuration ${icc.id} null pointer");
        }
    }

    @groovy.transform.CompileStatic(TypeCheckingMode.SKIP)
    private Map<String, AnalysisConfiguration> _loadAnalyses(NodeChildren nAnalyses, AnalysisConfiguration parentConfiguration = null) {
        Map<String, AnalysisConfiguration> availableAnalyses = [:]
        for (NodeChild analysis in nAnalyses.analysis) {
            String analysisID = extractAttributeText((NodeChild) analysis, "id");
            String analysisCfg = extractAttributeText((NodeChild) analysis, "configuration");
            AnalysisConfiguration ac = new AnalysisConfigurationProxy(parentConfiguration, analysisID, analysisCfg, analysis); //
            availableAnalyses[analysisID] = ac;

//        availableAnalyses[analysisID] = ac;
            _loadAnalyses(analysis.subanalyses, ac).each {
                String k, AnalysisConfiguration subConfig ->
                    availableAnalyses[analysisID + "-" + k] = subConfig;
            }
        }
        return availableAnalyses;
    }

//    @groovy.transform.CompileStatic(TypeCheckingMode.SKIP)
    public AnalysisConfiguration lazyLoadAnalysisConfiguration(AnalysisConfigurationProxy proxy) {
        String analysisID = proxy.getAnalysisID();
        String analysisCfg = proxy.getAnalysisCfg();
        AnalysisConfiguration parentConfiguration = proxy.getParentConfiguration();
        AnalysisConfiguration ac = (AnalysisConfiguration) getConfiguration(analysisCfg);
        proxy.setAnalysisConfiguration(ac);
        if (parentConfiguration) {
            ac.addParent(parentConfiguration)
        }

        // See if there are configurationvalues for the projects analysis entry which override the analysis configuration values.
        NodeChild analysis = proxy.getAnalysisNode()
        readConfigurationValues(analysis, ac);
        return ac
    }

    /**
     * Load a map of configuration values from the specified node. Store those values in the configuration.
     * @param configurationNode
     * @param config
     */
    @groovy.transform.CompileStatic(TypeCheckingMode.SKIP)
    public void readConfigurationValues(NodeChild configurationNode, Configuration config) {
        Map<String, ConfigurationValue> configurationValues = config.getConfigurationValues().getMap();
        for (NodeChild cvalueNode in configurationNode.configurationvalues.cvalue) {
            //TODO Code deduplication! Also in readCVBundle.
            ConfigurationValue cvalue = readConfigurationValue(cvalueNode, config)
            if (configurationValues.containsKey(cvalue.id)) {
                String cval0 = configurationValues[cvalue.id].value;//?.length() > 20 ? configurationValues[cvalue.id].value[0 .. 20] : configurationValues[cvalue.id].value;
                String cval1 = cvalue.value;//?.length() ? cvalue.value[0..20] : cvalue.value;
                config.addLoadError(new ConfigurationLoadError(config, "cValues", "Value ${cvalue.id} in config ${config.getID()} with value ${cval0} is not unique and will be overriden with value ${cval1}".toString(), null));
            }
            configurationValues[cvalue.id] = cvalue;
        }
    }

    /**
     * Read in a single configurationvalue from the node cvalueNode.
     * @param configurationNode
     * @param config
     */
    @groovy.transform.CompileStatic(TypeCheckingMode.SKIP)
    private ConfigurationValue readConfigurationValue(NodeChild cvalueNode, Configuration config) {
        String key = cvalueNode.@name.text();
        String value = cvalueNode.@value.text();
        String type = extractAttributeText(cvalueNode, "type");
        String description = extractAttributeText(cvalueNode, "description");
        return new ConfigurationValue(config, key, value, type, description);
    }


    private String extractAttributeText(NodeChild node, String id, String defaultText = "") {

        try {
            if (node.attributes().get(id) != null) {
                return node.attributes().get(id).toString();
            }
        } catch (Exception ex) {
            logger.severe("" + ex);
        }
        return defaultText;
    }


    private String extractAttributeText(NodeChildren nodeChildren, String id, String defaultText = "") {
        String name = nodeChildren.name();
        Object o = nodeChildren.parent().children().find { NodeChild child -> child.name() == name }
        if (o instanceof NodeChild)
            return extractAttributeText((NodeChild) o, id, defaultText);
        return defaultText;
    }

    /**
     * Load tool parameters like group, files and constraints...
     * @param child
     * @return
     */
    @groovy.transform.CompileStatic(TypeCheckingMode.SKIP)
    private ToolEntry.ToolParameter parseToolParameter(String toolID, NodeChild child) {
        String type = child.@type.text();
        if (type == "file") { //Load a file
            String cls = child.@typeof.text();
            Class _cls = LibrariesFactory.getInstance().searchForClass(cls);
            if (_cls == null) {
                logger.severe("Class ${cls} could not be found!");
            }
            String pName = child.@scriptparameter.text();
            String fnpSelTag = extractAttributeText(child, "fnpatternselectiontag", FilenamePattern.DEFAULT_SELECTION_TAG);
            boolean check = Boolean.parseBoolean(extractAttributeText(child, "check", "true"));
            String parentFileVariable = extractAttributeText(child, "variable", null); //This is only the case for child files.

            List<ToolEntry.ToolConstraint> constraints = new LinkedList<ToolEntry.ToolConstraint>();
            for (constraint in child.constraint) {
                String method = constraint.@method.text();
                String methodonfail = constraint.@methodonfail.text();
                constraints << new ToolEntry.ToolConstraint(_cls.getMethod(methodonfail), _cls.getMethod(method));
            }

            // A file can have several defined child files
            List<ToolEntry.ToolFileParameter> subParameters = new LinkedList<ToolEntry.ToolFileParameter>();
            for (NodeChild fileChild in child.children()) {
                subParameters << (ToolEntry.ToolFileParameter) parseToolParameter(toolID, fileChild);
            }
            ToolEntry.ToolFileParameter tp = new ToolEntry.ToolFileParameter(_cls, constraints, pName, check, fnpSelTag, subParameters, parentFileVariable);

            return tp;
        } else if (type == "tuple") {
            int tupleSize = child.children().size();
            if (!FileObjectTupleFactory.isValidSize(tupleSize)) {
                logger.severe("Tuple is of wrong size for tool ${toolID}.")
            }
            List<ToolEntry.ToolFileParameter> subParameters = new LinkedList<ToolEntry.ToolFileParameter>();
            for (NodeChild fileChild in child.children()) {
                subParameters << (ToolEntry.ToolFileParameter) parseToolParameter(toolID, fileChild);
            }
            return new ToolEntry.ToolTupleParameter(subParameters);
        } else if (type == "filegroup") {
            String cls = child.@typeof.text();
            PassOptions passas = Enum.valueOf(PassOptions.class, extractAttributeText(child, "passas", PassOptions.parameters.name()));
            String pName = child.@scriptparameter.text();
            Class _cls = LibrariesFactory.getInstance().loadClass(cls);

            if (_cls == null) {
                logger.severe("Class ${cls} could not be found!");
            }
            List<ToolEntry.ToolFileParameter> subParameters = new LinkedList<ToolEntry.ToolFileParameter>();
            int childCount = child.children().size();
            if (childCount == 0 && passas != PassOptions.array)
                logger.severe("No files in the file group. Configuration is not valid.")
            for (NodeChild fileChild in child.children()) {
                subParameters << (ToolEntry.ToolFileParameter) parseToolParameter(toolID, fileChild);
            }
            ToolEntry.ToolFileGroupParameter tpg = new ToolEntry.ToolFileGroupParameter(_cls, subParameters, pName, passas);
            return tpg;
        } else if (type == "string") {
            ParameterSetbyOptions setby = Enum.valueOf(ParameterSetbyOptions.class, extractAttributeText(child, "setby", ParameterSetbyOptions.callingCode.name()))
            String pName = child.@scriptparameter.text();
            ToolEntry.ToolStringParameter tsp = null;
            if (setby == ParameterSetbyOptions.callingCode) {
                tsp = new ToolEntry.ToolStringParameter(pName);
            } else {
                tsp = new ToolEntry.ToolStringParameter(pName, extractAttributeText(child, "cValueID"));
                //TODO Validate if cValueID == null!
            }
            return tsp;
        }
    }

// TODO Reenable cfg write
//    public void writeConfiguration(Configuration configuration, String path) {
//        String xmltext = new XMLConverter().convert(null, configuration)
//        File fw = new File(path)
//        BufferedWriter bw = fw.newWriter()
//        bw.write(xmltext)
//        bw.flush()
//        bw.close()
//    }

    ProjectConfiguration getProjectConfiguration(String s) {
        return getConfiguration(s) as ProjectConfiguration;
    }

    AnalysisConfiguration getAnalysisConfiguration(String s) {
        Map test = availableConfigurationsByTypeAndID[ConfigurationType.ANALYSIS];
        return test[s] as AnalysisConfiguration;
    }

}
