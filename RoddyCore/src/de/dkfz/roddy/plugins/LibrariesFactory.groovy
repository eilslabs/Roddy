/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.plugins

import de.dkfz.roddy.Roddy
import de.dkfz.roddy.StringConstants
import de.dkfz.roddy.config.loader.ConfigurationFactory
import de.dkfz.roddy.core.Initializable
import de.dkfz.roddy.knowledge.files.BaseFile
import de.dkfz.roddy.knowledge.files.FileObject
import de.dkfz.roddy.knowledge.nativeworkflows.NativeWorkflowConverter
import de.dkfz.roddy.tools.LoggerWrapper
import de.dkfz.roddy.tools.RuntimeTools
import de.dkfz.roddy.tools.Tuple2
import de.dkfz.roddy.tools.Tuple5
import de.dkfz.roddy.tools.versions.Version
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import de.dkfz.roddy.tools.versions.CompatibilityChecker

import java.util.regex.Pattern

/**
 * Factory to load and integrate plugins.
 */
@groovy.transform.CompileStatic
class LibrariesFactory extends Initializable {
    private static LoggerWrapper logger = LoggerWrapper.getLogger(LibrariesFactory.class.getSimpleName());

    private static LibrariesFactory librariesFactory;

    public static URLClassLoader urlClassLoader
    public static GroovyClassLoader centralGroovyClassLoader;


    public static final String PLUGIN_VERSION_DEVELOP = "develop";
    public static final String PLUGIN_DEFAULT = "DefaultPlugin";
    public static final String PLUGIN_BASEPLUGIN = "PluginBase";
    public static final String BUILDINFO_DEPENDENCY = "dependson";
    public static final String BUILDINFO_COMPATIBILITY = "compatibleto";
    public static final String BUILDINFO_TEXTFILE = "buildinfo.txt";
    public static final String BUILDVERSION_TEXTFILE = "buildversion.txt";
    public static final String BUILDINFO_STATUS = "status"
    public static final String BUILDINFO_STATUS_BETA = "beta"
    public static final String BUILDINFO_RUNTIME_JDKVERSION = "JDKVersion"
    public static final String BUILDINFO_RUNTIME_GROOVYVERSION = "GroovyVersion"
    public static final String BUILDINFO_RUNTIME_APIVERSION = "RoddyAPIVersion"
    public static final String PRIMARY_ERRORS = "PRIMARY_ERRORS"  // Primary errors are "important"
    public static final String SECONDARY_ERRORS = "SECONDARY_ERRORS" // Secondary errors could be "important" but are not checked in all cases.

    private List<String> loadedLibrariesInfo = [];

    private List<PluginInfo> loadedPlugins = [];

    private Map<PluginInfo, File> loadedJarsByPlugin = [:]

    private PluginInfoMap mapOfPlugins = [:];

    private static final Map<String, List<String>> mapOfErrorsForPluginEntries = [:]

    private static final Map<File, List<String>> mapOfErrorsForPluginFolders = [:]

    private boolean librariesAreLoaded = false;

    private SyntheticPluginInfo synthetic

    /**
     * Helper class to load real and synthetic classes.
     */
    final ClassLoaderHelper classLoaderHelper = new ClassLoaderHelper()

    /**
     * This resets the singleton and is not thread safe!
     * Actually only creates a new singleton clearing out old values.
     * @return
     */
    public static LibrariesFactory initializeFactory(boolean enforceinit = false) {
        if (!librariesFactory || enforceinit)
            librariesFactory = new LibrariesFactory();
        return librariesFactory;
    }

    private LibrariesFactory() {
        synthetic = new SyntheticPluginInfo("Synthetic", null, null, null, LibrariesFactory.PLUGIN_VERSION_DEVELOP, [:])
    }

    static List<String> getErrorsForPlugin(String plugin) {
        MapEntry hit = mapOfErrorsForPluginEntries.find { k, v -> k == plugin } as MapEntry
        if (hit == null as MapEntry) {
            return []
        } else {
            return hit.value as List<String>
        }
    }

    public SyntheticPluginInfo getSynthetic() {
        return synthetic;
    }

    /**
     * TODO Leave this static? Or make it a libraries factory based thing?
     * @return
     */
    static GroovyClassLoader getGroovyClassLoader() {
        if (centralGroovyClassLoader == null) {
            centralGroovyClassLoader = new GroovyClassLoader(ClassLoader.getSystemClassLoader())
            urlClassLoader = centralGroovyClassLoader;
        }
        return centralGroovyClassLoader;
    }

    /**
     * Maybe deprecated or a permanent shortcut?
     * @param name
     * @return
     */
    @Deprecated
    Class searchForClass(String name) {
        classLoaderHelper.searchForClass(name)
    }

    /**
     * Maybe deprecated or a permanent shortcut?
     * @param name
     * @return
     */
    @Deprecated
    Class loadRealOrSyntheticClass(String classOfFileObject, String baseClassOfFileObject) {
        return classLoaderHelper.loadRealOrSyntheticClass(classOfFileObject, baseClassOfFileObject)
    }

    /**
     * Maybe deprecated or a permanent shortcut?
     * @param name
     * @return
     */
    @Deprecated
    Class loadRealOrSyntheticClass(String classOfFileObject, Class<FileObject> constructorClass) {
        return classLoaderHelper.loadRealOrSyntheticClass(classOfFileObject, constructorClass)
    }

    /**
     * Maybe deprecated or a permanent shortcut?
     * @param name
     * @return
     */
    @Deprecated
    Class forceLoadSyntheticClassOrFail(String classOfFileObject, Class<BaseFile> constructor = BaseFile.class) {
        Class<BaseFile> _cls = classLoaderHelper.searchForClass(classOfFileObject);
        if (_cls && _cls.package.name.startsWith(SyntheticPluginInfo.SYNTHETIC_PACKAGE)) {
            return _cls
        }
        throw new RuntimeException("The requested class ${classOfFileObject} already exists and is not synthetic. However, the workflow requests a synthetic class.")
    }

    @groovy.transform.CompileStatic(TypeCheckingMode.SKIP)
    static Class generateSyntheticFileClassWithParentClass(String syntheticClassName, String constructorClassName, GroovyClassLoader classLoader = null) {
        ClassLoaderHelper.generateSyntheticFileClassWithParentClass(syntheticClassName, constructorClassName, classLoader)
    }

    Class loadClass(String className) throws ClassNotFoundException {
        return getGroovyClassLoader().loadClass(className);
    }

    /**
     * Resolve all used / necessary plugins and also look for miscrepancies.
     * @param usedPlugins
     */
    boolean resolveAndLoadPlugins(String[] usedPlugins) {
        if (!usedPlugins.join("").trim()) {
            logger.info("Call of resolveAndLoadPlugins was aborted, usedPlugins is empty.")
            return false
        }
        def mapOfAvailablePlugins = loadMapOfAvailablePluginsForInstance()
        if (!mapOfAvailablePlugins) {
            logger.severe("Could not load plugins from storage. Are the plugin directories properly set?\n" + Roddy.getPluginDirectories().join("\n\t"))
            return false
        }
        def queue = buildupPluginQueue(mapOfAvailablePlugins, usedPlugins)
        if (queue == null) {
            logger.severe("Could not build the plugin queue for: \n\t" + usedPlugins.join("\n\t"))

            logger.severe("Please see all available plugin folders and their sub directories:\n" +
                    mapOfErrorsForPluginFolders
                            .findAll { File key, List<String> values -> values.size() > 0 && key.isDirectory() }
                            .collect { File folder, List<String> errorsForFolder ->
                        "Folder ${folder}" + errorsForFolder ? " with ${errorsForFolder.size()}" : ""
                    }.collect {}.join("\n\t")
            )

            return false
        }

        Map<String, List<String>> errors = mapOfErrorsForPluginEntries.findAll { String k, List v -> v }
        if (errors) {
            StringBuilder builder = new StringBuilder("There were several plugin directories which were rejected:\n")
            builder << [
                    errors.collect { String k, List<String> v -> (["\t" + k] + v).join("\n\t\t") }.join("\n"),
                    "To prevent wrong plugin selection, Roddy needs you to keep your plugin directories clean. A plugin directory is dirty if:",
                    '\t- A contained directory does not follow the plugin name convention: "PluginName_\$major.\$minor.\$patch[-\$revision]"',
                    "\t- A plugin directory does not contain all necessary files and directories or other errors"
            ].join("\n")
            logger.severe(builder.toString())
            return false
        }
        // Prepare plugins in queue
        queue.each { String id, PluginInfo pi ->
            if (pi instanceof NativePluginInfo) {
                new NativeWorkflowConverter(pi as NativePluginInfo).convert()
            }
        }

        boolean finalChecksPassed = !checkOnToolDirDuplicates(queue.values() as List<PluginInfo>);
        if (!finalChecksPassed) {
            logger.severe("Final checks for plugin loading failed. There were duplicate tool directories.")
            return false
        };

        librariesAreLoaded = loadLibraries(queue.values() as List);
        return librariesAreLoaded
    }

    public boolean areLibrariesLoaded() {
        return librariesAreLoaded
    }

    public List<PluginInfo> getLoadedPlugins() {
        return loadedPlugins;
    }

    Map<PluginInfo, File> getLoadedJarsByPlugin() {
        return loadedJarsByPlugin
    }

    public PluginInfoMap loadMapOfAvailablePluginsForInstance() {
        if (!mapOfPlugins) {
            def directories = Roddy.getPluginDirectories()
            List<PluginDirectoryInfo> mapOfIdentifiedPlugins = loadMapOfAvailablePlugins(directories)
            mapOfPlugins = loadPluginsFromDirectories(mapOfIdentifiedPlugins)
        }

        return mapOfPlugins
    }

    /**
     * This method returns a list of all plugins found in plugin directories.
     * This method distinguishes between Roddy environments for development and packed Roddy versions.
     * If the user is a developer, the development directories are set and preferred over bundled libraries.
     * * Why is this necessary?d to the
     *   plugin directory on compilation (and possibly out of version control. So Roddy tries to take the "original" version
     *   which resides in the plugins project space.
     * * Where can plugins be found?
     * - If you develop scripts and plugins, you possibly use a version control system. However the scripts are copie
     * - In the dist/libraries folder (non developer)
     * - In any other configured folder. You as the developer has to set external projects up in the configuration. (developer)
     *
     * @return
     */
    static List<PluginDirectoryInfo> loadMapOfAvailablePlugins(List<File> pluginDirectories) {

        //Search all plugin folders and also try to join those if possible.
        List<PluginDirectoryInfo> collectedPluginDirectories = [];

        for (File pBaseDirectory : pluginDirectories) {
            logger.postSometimesInfo("Parsing plugins folder: ${pBaseDirectory}");
            if (!pBaseDirectory.exists()) {
                logger.severe("The plugins directory $pBaseDirectory does not exist.")
                mapOfErrorsForPluginFolders.get(pBaseDirectory, []) << "The plugins directory $pBaseDirectory does not exist.".toString()
                continue;
            }
            if (!pBaseDirectory.canRead()) {
                logger.severe("The plugins directory $pBaseDirectory is not readable.")
                mapOfErrorsForPluginFolders.get(pBaseDirectory, []) << "The plugins directory $pBaseDirectory is not readable.".toString()
            }

            File[] directoryList = pBaseDirectory.listFiles().sort() as File[];
            for (File pEntry in directoryList) {

                Map<String, List<String>> errors = [
                        PRIMARY_ERRORS  : [],
                        SECONDARY_ERRORS: []
                ] as LinkedHashMap
                def workflowType = determinePluginType(pEntry, errors)
                mapOfErrorsForPluginEntries[pEntry.path] = (errors[PRIMARY_ERRORS] + errors[SECONDARY_ERRORS])

                if (workflowType == PluginType.INVALID)
                    continue

                collectedPluginDirectories << new PluginDirectoryInfo(pEntry, workflowType);
            }
        }

        return collectedPluginDirectories
    }

    /**
     * This and the following method should not be in here! We should use the FileSystemAccessProvider for it. 
     * However, the FSAP always tries to use the ExecService, if possible. All in all, with the current setup for FSAP / ES
     * interaction, it will not work. As we already decided to change that at some point, I'll put the method in here
     * and mark them as deprecated.
     * @param file
     * @return
     */
    @Deprecated
    static boolean checkFile(File file) {
        return file.exists() && file.isFile() && file.canRead()
    }

    @Deprecated
    static boolean checkDirectory(File file) {
        return file.exists() && file.isDirectory() && file.canRead() && file.canExecute()
    }

    static PluginType determinePluginType(File directory, Map<String, List<String>> mapOfErrors = [:]) {
        logger.postRareInfo("  Parsing plugin folder: ${directory}");

        List<String> errors = mapOfErrors.get(PRIMARY_ERRORS, [])
        List<String> errorsUnimportant = mapOfErrors.get(SECONDARY_ERRORS, [])

        if (!directory.isDirectory()) {
            // Just return silently here.
            return PluginType.INVALID
        }
        if (directory.isHidden())
            return PluginType.INVALID
        if (!directory.canRead())
            errors << "Directory cannot be read"

        if (errors) {
            logger.postRareInfo((["A directory was rejected as a plugin directory because:"] + errors).join("\n\t"))
            return PluginType.INVALID
        }

        String dirName = directory.getName();
        if (!isPluginDirectoryNameValid(dirName)) {
            logger.postRareInfo("A directory was rejected as a plugin directory because its name did not match the naming rules.")
            errorsUnimportant << "A directory was rejected as a plugin directory because its name did not match the naming rules."
            return PluginType.INVALID
        }

        // Check if it is a native workflow
        // Search for a runWorkflow_[scheduler].sh
        if (NativeWorkflowConverter.isNativePlugin(directory)) {
            return PluginType.NATIVE
        } else {

            // If not, check for regular workflows.
            if (!checkFile(new File(directory, BUILDINFO_TEXTFILE)))
                errors << "The buildinfo.txt file is missing"
            if (!checkFile(new File(directory, BUILDVERSION_TEXTFILE)))
                errors << "The buildversion.txt file is missing"
            if (!checkDirectory(new File(directory, "resources/analysisTools")))
                errors << "The analysisTools resource directory is missing"
            if (!checkDirectory(new File(directory, "resources/configurationFiles")))
                errors << "The configurationFiles resource directory is missing"
        }

        if (errors) {
            logger.postRareInfo((["A directory was rejected as a plugin directory because:"] + errors).join("\n\t"))
            return PluginType.INVALID
        }
        return PluginType.RODDY
    }

//    @groovy.transform.CompileStatic(TypeCheckingMode.SKIP)
    private static List<PluginDirectoryInfo> checkValidPluginNames(List<PluginDirectoryInfo> collectedPluginDirectories) {
        List<PluginDirectoryInfo> collectedTemporary = [];
        collectedPluginDirectories.each { PluginDirectoryInfo pdi ->
            def name = pdi.directory.name
            String rev = (name.split("[-]") as List)[1]
            if (name.endsWith(".zip")) {
                logger.info("Did not consider to check ${name} as it is compressed and cannot be evaluated.")
                return
            }
            if (rev) rev = rev.split("[.]")[0]; // Filter out .zip
            if (rev?.isNumber() || !rev) collectedTemporary << pdi
            else mapOfErrorsForPluginFolders.get(pdi.directory, []) << "Filtered out plugin ${name}, as the revision id is not numeric.".toString()
        }
        return collectedTemporary
    }

    /** Make sure that the plugin directories are properly sorted before we start. This is especially useful
     *  if we search for revisions and extensions.
     */
    @CompileStatic(TypeCheckingMode.SKIP)
    private static List<PluginDirectoryInfo> sortPluginDirectories(List<PluginDirectoryInfo> collectedPluginDirectories) {
        collectedPluginDirectories = collectedPluginDirectories.sort {
            PluginDirectoryInfo left, PluginDirectoryInfo right ->
                List<String> splitLeft = left.directory.name.split("[_:.-]") as List
                List<String> splitRight = right.directory.name.split("[_:.-]") as List
                Tuple5<String, Integer, Integer, Integer, Integer> tLeft = new Tuple5<>(
                        splitLeft[0],
                        (splitLeft[1] ?: Integer.MAX_VALUE) as Integer,
                        (splitLeft[2] ?: Integer.MAX_VALUE) as Integer,
                        (splitLeft[3] ?: Integer.MAX_VALUE) as Integer,
                        (splitLeft[4] ?: 0) as Integer
                );
                Tuple5<String, Integer, Integer, Integer, Integer> tRight = new Tuple5<>(
                        splitRight[0],
                        (splitRight[1] ?: Integer.MAX_VALUE) as Integer,
                        (splitRight[2] ?: Integer.MAX_VALUE) as Integer,
                        (splitRight[3] ?: Integer.MAX_VALUE) as Integer,
                        (splitRight[4] ?: 0) as Integer
                );
                if (tLeft.x != tRight.x) return tLeft.x.compareTo(tRight.x)
                if (tLeft.y != tRight.y) return tLeft.y.compareTo(tRight.y)
                if (tLeft.z != tRight.z) return tLeft.z.compareTo(tRight.z)
                if (tLeft.w != tRight.w) return tLeft.w.compareTo(tRight.w)
                if (tLeft.q != tRight.q) return tLeft.q.compareTo(tRight.q)
                return 0
        }
        return collectedPluginDirectories
    }

    /**
     * Loads all available plugins (including revisions and versions) from a set of directories.
     * Type checking mode is set to skip for this method, because Groovy does not likes Generics and Comparisons in a combination...
     * return tLeft.x.compareTo(tRight.x) * 10000 +
     * tLeft.y.compareTo(tRight.y) * 1000 +
     * tLeft.z.compareTo(tRight.z) * 100 +
     * tLeft.w.compareTo(tRight.w) * 10 +
     * tLeft.q.compareTo(tRight.q) * 1;
     *
     * Will not work!
     *
     * @param collectedPluginDirectories
     * @return
     */
    public static PluginInfoMap loadPluginsFromDirectories(List<PluginDirectoryInfo> collectedPluginDirectories) {
        collectedPluginDirectories = sortPluginDirectories(checkValidPluginNames(collectedPluginDirectories))

        Map<String, Map<String, PluginInfo>> _mapOfPlugins = [:];
        for (PluginDirectoryInfo _entry : collectedPluginDirectories) {
            logger.postRareInfo("Processing plugin entry: ${_entry.directory}")
            if (_entry.type == PluginType.INVALID)
                throw new PluginLoaderException("loadPluginsFromDirectories does not take INVALID plugins!")

            File directory = _entry.directory;

            String pluginName = _entry.pluginID;
            String[] pluginVersionInfo = _entry.version.split(StringConstants.SPLIT_MINUS) as String[];
            String pluginVersion = pluginVersionInfo[0];
            String pluginRevision = pluginVersionInfo.length > 1 ? pluginVersionInfo[1] : "0";
            String pluginFullVersion = pluginVersion + "-" + pluginRevision;
            if (pluginVersion == PLUGIN_VERSION_DEVELOP) pluginFullVersion = PLUGIN_VERSION_DEVELOP;

            if (!pluginRevision.isInteger()) {
                throw new PluginLoaderException("Could not parse revision number from plugin-directory '${directory.absolutePath}'")
            }
            int revisionNumber = pluginRevision.toInteger();

            def pluginMap = _mapOfPlugins.get(pluginName, new LinkedHashMap<String, PluginInfo>())

            BuildInfoFileHelper biHelper = loadBuildinfoHelperObject(pluginName, pluginFullVersion, directory, _entry)

            PluginInfo previousPlugin = pluginMap.values().size() > 0 ? pluginMap.values().last() : null;
            boolean isRevisionOfPlugin = previousPlugin?.getMajorAndMinor() == pluginVersion && previousPlugin?.getRevision() == revisionNumber - 1;
            boolean isCompatible = biHelper.isCompatibleTo(previousPlugin);

            //Create a helper object which parses the buildinfo text file
            PluginInfo newPluginInfo

            if (_entry.type == PluginType.NATIVE) {
                newPluginInfo = new NativePluginInfo(pluginName, directory, pluginFullVersion, biHelper.getDependencies())
            } else if (_entry.type == PluginType.RODDY) {
                File jarFile = directory.listFiles().find { File f -> f.name.endsWith ".jar"; }
                if (jarFile) {
                    newPluginInfo = new JarFulPluginInfo(pluginName, directory, jarFile, pluginFullVersion, biHelper.getRoddyAPIVersion(), biHelper.getJDKVersion(), biHelper.getDependencies())
                } else {
                    newPluginInfo = new JarLessPluginInfo(pluginName, directory, pluginFullVersion, biHelper.getDependencies())
                }
            }

            pluginMap[pluginFullVersion] = newPluginInfo;
            if (isRevisionOfPlugin || isCompatible) {
                newPluginInfo.previousInChain = previousPlugin;
                previousPlugin.nextInChain = newPluginInfo;
            }

            if (isRevisionOfPlugin)
                newPluginInfo.previousInChainConnectionType = PluginInfo.PluginInfoConnection.REVISION;
            else if (isCompatible)
                newPluginInfo.previousInChainConnectionType = PluginInfo.PluginInfoConnection.EXTENSION;

            if (newPluginInfo.errors)
                mapOfErrorsForPluginEntries.get(newPluginInfo.directory.path, []).addAll(newPluginInfo.getErrors())
        }
        return new PluginInfoMap(_mapOfPlugins)
    }

    static BuildInfoFileHelper loadBuildinfoHelperObject(String name, String fullVersion, File directory, PluginDirectoryInfo pluginDirectoryInfo) {

        BuildInfoFileHelper biHelper
        if (pluginDirectoryInfo.needsBuildInfoFile()) {
            biHelper = new BuildInfoFileHelper(name, fullVersion, directory.listFiles().find { File f -> f.name == BUILDINFO_TEXTFILE })
        } else {
            File f = directory.listFiles().find { File f -> f.name == BUILDINFO_TEXTFILE }
            if (f)
                biHelper = new BuildInfoFileHelper(name, fullVersion, f.readLines())
            else
                biHelper = new BuildInfoFileHelper(name, fullVersion)
        }
        return biHelper

    }

    public static Map<String, PluginInfo> buildupPluginQueue(PluginInfoMap mapOfPlugins, String[] usedPlugins) {
        List<String> usedPluginsCorrected = [];
        List<Tuple2<String, String>> pluginsToCheck = usedPlugins.collect { String requestedPlugin ->
            List<String> pSplit = requestedPlugin.split("[:-]") as List;
            String id = pSplit[0];
            String version = pSplit[1] ?: PLUGIN_VERSION_DEVELOP;
            String revision = pSplit[2] ?: "0"
            String fullVersion = version + (version != PLUGIN_VERSION_DEVELOP ? "-" + revision : "")

            usedPluginsCorrected << [id, fullVersion].join(":");
            return new Tuple2(id, fullVersion);
        } as List<Tuple2<String, String>>;
        usedPlugins = usedPluginsCorrected;

        Map<String, PluginInfo> pluginsToActivate = [:];
        while (pluginsToCheck.size() > 0) {

            final String id = pluginsToCheck[0].x;
            String version = pluginsToCheck[0].y;
            //There are now some  "as String" conversions which are just there for the Idea code view... They'll be shown as faulty otherwise.
            if (version != PLUGIN_VERSION_DEVELOP && !(version as String).contains("-")) version += "-0";

            if (!mapOfPlugins.checkExistence(id as String, version as String)) {
                if (id) { // Skip empty entries and reduce one message.
                    mapOfErrorsForPluginEntries.get(id as String, []) << ("The plugin ${id}:${version} could not be found, are the plugin paths properly set?").toString();
                }
            }
            pluginsToCheck.remove(0);

            // Set pInfo to a valid instance.
            PluginInfo pInfo = mapOfPlugins.getPluginInfo(id as String, version as String);

            // Now, if the plugin is not in usedPlugins (and therefore not fixed), we search the newest compatible
            // version of it which may either be a revision (x:x.y-[0..n] or a higher compatible version.
            // Search the last valid entry in the chain.
            if (!usedPlugins.contains("${id}:${version}")) {
                while (true) {
                    version = pInfo.prodVersion;
                    if (usedPlugins.contains("${id}:${version}")) //Break, if the list of used plugins contains the selected version of the plugin
                        break
                    if (pInfo.nextInChain == null) break // Break if this was the last entry in the chain
                    pInfo = pInfo.nextInChain
                }
            }

            if (pInfo == null)
                pInfo = mapOfPlugins.getPluginInfo(id as String, PLUGIN_VERSION_DEVELOP);
            if (pInfo == null)
                continue;
            if (pluginsToActivate[id as String] != null) {
                if (pluginsToActivate[id as String].prodVersion != version) {
                    def msg = "Plugin version collision: Plugin ${id} cannot both be loaded in version ${version} and ${pluginsToActivate[id as String].prodVersion}. Not starting up."
                    logger.severe(msg)
                    return null
                } else {
                    //Not checking again!
                }
            } else {
                Map<String, String> dependencies = pInfo.getDependencies()
                dependencies.each { String k, String v ->
                    if (v != PLUGIN_VERSION_DEVELOP && !v.contains("-")) v += "-0";
                    pluginsToCheck << new Tuple2(k, v);
                }
                pluginsToActivate[id as String] = pInfo;
            }
            //Load default plugins, if necessary.
            if (!pluginsToCheck) {
                if (!pluginsToActivate.containsKey(PLUGIN_DEFAULT)) {
                    pluginsToActivate[PLUGIN_DEFAULT] = mapOfPlugins.getPluginInfo(PLUGIN_DEFAULT, PLUGIN_VERSION_DEVELOP);
                }
                if (!pluginsToActivate.containsKey(PLUGIN_BASEPLUGIN)) {
                    pluginsToActivate[PLUGIN_BASEPLUGIN] = mapOfPlugins.getPluginInfo(PLUGIN_BASEPLUGIN, PLUGIN_VERSION_DEVELOP);
                }
            }
        }
        return pluginsToActivate;
    }

    /**
     * Returns true, if there are any duplicate tool directories in the provided list of plugins
     * @param plugins
     * @return
     */
    static boolean checkOnToolDirDuplicates(List<PluginInfo> plugins) {
        Collection<String> original = plugins.collect { it.toolsDirectories.keySet() }.flatten() as Collection<String>  // Put all elements into one list
        def normalized = original.unique(false)  // Normalize the list, so that duplicates are removed.
        boolean result = normalized.size() != original.size() // Test, if the size changed. If so, original contained duplicates.

        // For verbose output

        logger.sometimes((["", "Found tool folders:"] + (plugins.collect { it.toolsDirectories.collect { String k, File v -> k.padRight(30) + " : " + v } }.flatten().sort() as List<String>)).join("\n\t"));

        return result
    }

    public static boolean addFile(File f) throws IOException {
        return addURL(f.toURI().toURL());
    }

    /**
     * The following method adds a jar file to the current classpath.
     * The code is initially taken from here:
     * http://stackoverflow.com/questions/60764/how-should-i-load-jars-dynamically-at-runtime
     * Beware that classes must only be added once due to several constrictions.
     * See the mentioned site for more information.
     *
     * @param u
     * @throws IOException
     */
    public static boolean addURL(URL u) throws IOException {
        try {
            getGroovyClassLoader().addURL(u);
            return true;
        } catch (Throwable t) {
            logger.severe("A plugin could not be loaded: " + u)
            return false;
        }
    }

    public static LibrariesFactory getInstance() {
        if (librariesFactory == null) {
            logger.postSometimesInfo("The libraries factory for plugin management was not initialized! Creating a new, empty object.")
            librariesFactory = new LibrariesFactory();
        }

        return librariesFactory;
    }

    public boolean loadLibraries(List<PluginInfo> pluginInfo) {
        if (!performAPIChecks(pluginInfo))
            return false;

        // TODO Cover with a unit or integration test (if not already done...)
        List<String> errors = [];
        //All is right? Let's go
        Map<String, String> loadedPluginsPrintout = [:]
        pluginInfo.parallelStream().each { PluginInfo pi ->
            if (!pi.directory) {
                synchronized (errors) {
                    errors << "Ignored ${pi.fullID}, directory not found.".toString();
                }
                return;
            }

            File jarFile
            if (pi instanceof JarFulPluginInfo) {
                jarFile = pi.directory.listFiles().find { File f -> f.name.endsWith(".jar") };
                if (jarFile && !addFile(jarFile)) {
                    synchronized (errors) {
                        errors << "Ignored ${pi.fullID}, Jar file was not available.".toString();
                    }
                    return;
                }
            } else if (pi instanceof NativePluginInfo) {

            }

            synchronized (loadedLibrariesInfo) {
                String stringLeft = "Loaded plugin ${pi.getName()}:${pi.getProdVersion()}".toString()
                String stringRight = "(${pi.getDirectory()})".toString()
                loadedPluginsPrintout[stringLeft] = stringRight
                loadedPlugins << pi;
                loadedLibrariesInfo << "${stringLeft} from (${stringRight})".toString()
                loadedJarsByPlugin[pi] = jarFile;
            }
        }
        logger.always("\n" + ConfigurationFactory.convertMapToFormattedTable(loadedPluginsPrintout, 0, " ", { String v -> v }).join("\n"))

        if (errors) {
            logger.severe("Some plugins were not loaded:\n\t" + errors.join("\n\t"))
        }
        return !errors
    }

    /**
     * Perform checks, if all API versions match the current runtime setup.
     * Only Roddy version is checked (Groovy is bundled with Roddy; JDK version is enforced by Roddy).
     */
    static boolean performAPIChecks(List<PluginInfo> pluginInfos) {
        List<PluginInfo> incompatiblePlugins = []
        for (pi in pluginInfos) {
            if (!pi.isCompatibleToRuntimeSystem())
                incompatiblePlugins << pi
        }
        if (incompatiblePlugins) {
            logger.severe("Could not load plugins, runtime API versions mismatch! Provided is Roddy ${RuntimeTools.getRoddyRuntimeVersion()}.\n"
                    + incompatiblePlugins.collect { PluginInfo pi -> "\tRoddy ${pi.roddyAPIVersion} required by ${pi.fullID}"}.join("\n")
            )
        }
        return !incompatiblePlugins
    }

    List<String> getLoadedLibrariesInfoList() {
        return loadedLibrariesInfo
    }

    static boolean isVersionStringValid(String s) {
        Pattern patternOfPluginIdentifier = ~/([0-9]*[.][0-9]*[.][0-9]*([-][0-9]){0,}|[:]develop)/
        return s ==~ patternOfPluginIdentifier
    }

    /**
     * A helper method to identify whether a workflow identification string is valid, e.g.:
     *       "COWorkflows:1.0.1-0:develop": false,
     *       "COWorkflows:1.0.1-0"        : true,
     *       "COWorkflows:1.0.1-3"        : true,
     *       "COWorkflows"                : true,
     *       "COWorkflows:develop"        : true
     * @param s
     * @return
     */
    static boolean isPluginIdentifierValid(String s) {
        //Pattern patternOfPluginIdentifier = ~/[a-zA-Z]*[:]{1,1}[0-9]*[.][0-9]*[.][0-9]*([-][0-9]){0,}|[a-zA-Z]*[:]develop|[a-zA-Z]*/
        Pattern patternOfPluginIdentifier = ~/([a-zA-Z]*)([:]{1,1}[0-9]*[.][0-9]*[.][0-9]*([-][0-9]){0,}|[:]develop|$)/
        return s ==~ patternOfPluginIdentifier;
    }

    /**
     * A helper method to identify whether a plugin directory name is valid, e.g.:
     *        "COWorkflows_1.0.1-0:develop": false,
     *        "COWorkflows:1.0.1-r"        : false,
     *        "COWorkflows:1.0.1-3"        : false,
     *        "COWorkflows_1.0.1-3"        : true,
     *        "COWorkflows"                : true,
     *        "COWorkflows_develop"        : false
     * @param s
     * @return
     */
    public static boolean isPluginDirectoryNameValid(String s) {
        //Pattern patternOfPluginIdentifier = ~/[a-zA-Z]*[_]{1,1}[0-9]*[.][0-9]*[.][0-9]*[-][0-9]{1,}|[a-zA-Z]*[_]{1,1}[0-9]*[.][0-9]*[.][0-9]*|[a-zA-Z]*/
        Pattern patternOfPluginIdentifier = ~/([a-zA-Z]*)([_]{1,1}[0-9]*[.][0-9]*[.][0-9]*[-][0-9]{1,}|[_]{1,1}[0-9]*[.][0-9]*[.][0-9]*|$)/
        return s ==~ patternOfPluginIdentifier;
    }

    @Override
    public boolean initialize() {
    }

    @Override
    public void destroy() {
    }
}
