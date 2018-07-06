/*
 * Copyright (c) 2017 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.plugins

import de.dkfz.roddy.Roddy
import de.dkfz.roddy.RunMode
import de.dkfz.roddy.StringConstants
import de.dkfz.roddy.core.RuntimeService
import de.dkfz.roddy.knowledge.files.BaseFile
import de.dkfz.roddy.knowledge.files.GenericFileGroup
import de.dkfz.roddy.execution.io.LocalExecutionHelper
import de.dkfz.roddy.execution.io.ExecutionService
import de.dkfz.roddy.execution.io.LocalExecutionService
import de.dkfz.roddy.execution.io.fs.FileSystemAccessProvider
import de.dkfz.roddy.tools.RoddyIOHelperMethods
import de.dkfz.roddy.tools.RuntimeTools
import groovy.transform.TypeCheckingMode
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Ignore
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The tests for the jar file loading are not very nice.
 * The core classes Roddy and LibrariesFactory are singletons and they need to be reset for each test.
 * Also it is necessary to synchronize some of the tests. Not nice, but working.
 */
@groovy.transform.CompileStatic
@Ignore("NullPointerException in setupTestDataForPluginQueueTests. Fix!")
public class LibrariesFactoryTest {

    /**
     * Test data map for plugin chain loading mechanims
     * The map contains various plugins, versions and dependencies to other plugins.
     *  x.y.z / current (version)
     *  -n (revision)
     *  ,b (beta)
     *  ,c (compatible)
     *  ,bc (beta and compatible)
     *  ,z (zip file (without e.g. buildinfo))
     */
    private static final LinkedHashMap<String, LinkedHashMap<String, List<String>>> mapWithTestPlugins = [
            A: ["1.0.1"  : [],
                "1.0.24" : ["PluginBase:current", "DefaultPlugin:current"],
                "current": ["PluginBase:current", "DefaultPlugin:current"]
            ],
            B: ["0.9.0"    : ["A:1.0.1"],
                "1.0.1"    : ["A:1.0.24"],
                "1.0.1-r"  : [],   // Is not valid and will be filtered
                "1.0.2"    : ["A:1.0.24"],
                "1.0.2-1"  : ["A:1.0.24"],
                "1.0.2-2,b": ["A:1.0.24"],
                "1.0.3,c"  : ["A:current"]
            ],
            C: ["0.9.0"    : ["B:0.9.0"],
                "1.0.0,z"  : [], // Also test, if zipped plugins can be recognized. There should come a warning and they will get filtered.
                "1.0.0"    : [], // Unzipped version of the prior zip file
                "1.0.1"    : ["B:1.0.1"],
                "1.0.2,c"  : ["B:1.0.2-1"],
                "1.0.3"    : ["B:1.0.3"],
                "current,c": ["B:1.0.3"]
            ],
            D: ["0.9.0"  : ["C:0.9.0"],
                "1.0.1"  : ["C:1.0.1"],
                "1.0.2"  : ["C:1.0.2", "B:1.0.1"],
                "1.0.2-1": ["C:1.0.2"],
                "1.0.3"  : ["C:1.0.3", "B:1.0.2"],
                "1.0.4,z": [],
                "current": ["C:current"]
            ],
    ]

    /**
     * Keeps all available plugins which were created with the help of the mapWithTestPlugins map
     */
    private static PluginInfoMap mapOfAvailablePlugins

    public static TemporaryFolder pluginsBaseDirWithCorrectEntries = new TemporaryFolder();

    public static TemporaryFolder pluginsBaseDirWithInvalidEntries = new TemporaryFolder();

    public static TemporaryFolder pluginsBaseDirForResourceTests = new TemporaryFolder();

    public static Map<String, PluginInfo> pluginInfoObjectsForResourceTests = [:]

    public static File pluginsDirWithInvalidEntries

    @AfterClass
    public static void tearDownClass() {
        pluginsBaseDirWithCorrectEntries.delete();
    }

    @BeforeClass
    @groovy.transform.CompileStatic(TypeCheckingMode.SKIP)
    public static void setupTestDataForPluginQueueTests() {
        ExecutionService.initializeService(LocalExecutionService, RunMode.CLI);
        FileSystemAccessProvider.initializeProvider(true);
        pluginsBaseDirWithCorrectEntries.create();

        // Create the folder and file structure so that the loadPluginsFromDirectories method will work.
        for (String plugin : mapWithTestPlugins.keySet()) {
            Map<String, List<String>> pluginInfoMap = mapWithTestPlugins[plugin];
            List<String> listOfVersions = pluginInfoMap.keySet() as List<String>;
            for (int i = 0; i < listOfVersions.size(); i++) {
                String version = listOfVersions[i];
                List<String> vString = version.split(StringConstants.SPLIT_COMMA) as List<String>
                List<String> importList = pluginInfoMap[version];

                String folderName = plugin
                if (vString[0] != "current")
                    folderName += "_" + vString[0]

                if (vString[1]?.contains("z")) {
                    // Touch a zip file and continue
                    folderName += ".zip"
                    pluginsBaseDirWithCorrectEntries.newFile(folderName);
                    continue
                }

                File pFolder = pluginsBaseDirWithCorrectEntries.newFolder(folderName);
                File buildinfo = new File(pFolder, "buildinfo.txt");

                // Check, if there are additions to the versioning string.
                if (vString[1]?.contains("b"))
                    buildinfo << "status=beta\n"

                if (vString[1]?.contains("c") && i > 0)
                    buildinfo << "compatibleto=${listOfVersions[i - 1].split(StringConstants.SPLIT_COMMA)[0]}\n"

                importList.each {
                    buildinfo << "dependson=${it}\n"
                }
                buildinfo << ""

                new File(pFolder, "buildversion.txt") << "";
                new File(pFolder, "resources/analysisTools").mkdirs()
                new File(pFolder, "resources/configurationFiles").mkdirs()
            }
        }

        //Add additional "native" plugins (DefaultPlugin, PluginBase) and the temporary plugin folder
        List<File> pluginDirectories = [
                pluginsBaseDirWithCorrectEntries.root

        ] + Roddy.getPluginDirectories()

        mapOfAvailablePlugins = callLoadMapOfAvailablePlugins(pluginDirectories)

        // Check, if all plugins were recognized and if the version count matches.
        assert mapOfAvailablePlugins.size() >= mapWithTestPlugins.size(); // Take the additional plugins into account
        assert mapOfAvailablePlugins["PluginBase"]["current-0"] == null && mapOfAvailablePlugins["PluginBase"]["current"] != null
        assert mapWithTestPlugins["A"].size() == mapOfAvailablePlugins["A"].size();
        assert mapWithTestPlugins["B"].size() == mapOfAvailablePlugins["B"].size() + 1; // Lacks one filtered entry.
        assert mapWithTestPlugins["C"].size() == mapOfAvailablePlugins["C"].size() + 1; // Lacks one filtered zip entry.
        assert mapWithTestPlugins["D"].size() == mapOfAvailablePlugins["D"].size() + 1; // Lacks one filtered zip entry.

        assert mapOfAvailablePlugins["B"]["1.0.2-1"]?.previousInChain == mapOfAvailablePlugins["B"]["1.0.2-0"] && mapOfAvailablePlugins["B"]["1.0.2-1"]?.previousInChainConnectionType == PluginInfo.PluginInfoConnection.REVISION
        assert mapOfAvailablePlugins["B"]["1.0.3-0"]?.previousInChain == mapOfAvailablePlugins["B"]["1.0.2-2"] && mapOfAvailablePlugins["B"]["1.0.3-0"]?.previousInChainConnectionType == PluginInfo.PluginInfoConnection.EXTENSION
    }

    @BeforeClass
    public static void setupTestDataForPluginLoaderTest() {
        // Create a temp directory with several valid and several invalid directories.
        // Invalid are e.g. hidden directories, directories with malformated names, directories with missing content..
        // TODO Might be necessary to extend this to cover Auto-Detect of Python/Java/Groovy Plugins and contents. (Jar files).
        pluginsBaseDirWithInvalidEntries.create();

        File testSource = new File(LibrariesFactory.groovyClassLoader.getResource("LibrariesFactoryTestData.zip").file)
        pluginsDirWithInvalidEntries = pluginsBaseDirWithInvalidEntries.root
        String copyTestSourceommand = "unzip -d ${pluginsDirWithInvalidEntries} ${testSource}"
        LocalExecutionHelper.executeSingleCommand(copyTestSourceommand);
        pluginsDirWithInvalidEntries = new File(pluginsDirWithInvalidEntries, "LibrariesFactoryTestData")
        new File(pluginsDirWithInvalidEntries, "InValidContentCantRead").setReadable(false);
    }

    @BeforeClass
    static void setupTestDataForResourceTests() {
        pluginsBaseDirForResourceTests.create()
        pluginInfoObjectsForResourceTests;

        ["A", "B"].each { String pID ->
            File pFolder = RoddyIOHelperMethods.assembleLocalPath(pluginsBaseDirForResourceTests.root, pID)
            ["toolDirA", "toolDirB", "toolDirC"].each { RoddyIOHelperMethods.assembleLocalPath(pFolder, RuntimeService.DIRNAME_RESOURCES, RuntimeService.DIRNAME_ANALYSIS_TOOLS, it).mkdirs() }
            pluginInfoObjectsForResourceTests[pID] = new PluginInfo(pID, null, pFolder, null, null, null, null, null)
        }

        ["C", "D"].each { String pID ->
            File pFolder = RoddyIOHelperMethods.assembleLocalPath(pluginsBaseDirForResourceTests.root, pID)
            ["toolDirD", "toolDirE", "toolDirF"].each { RoddyIOHelperMethods.assembleLocalPath(pFolder, RuntimeService.DIRNAME_RESOURCES, RuntimeService.DIRNAME_ANALYSIS_TOOLS, it).mkdirs() }
            pluginInfoObjectsForResourceTests[pID] = new PluginInfo(pID, null, pFolder, null, null, null, null, null)
        }
    }

    public static PluginInfoMap callLoadMapOfAvailablePlugins(List<File> additionalPluginDirectories = [], boolean single = false) {
        List<File> pluginDirectories = additionalPluginDirectories

        if (!single)
            pluginDirectories += [new File(Roddy.getApplicationDirectory(), "/dist/plugins/")]

        // Invoke the method and check the results.
        return LibrariesFactory.loadPluginsFromDirectories(LibrariesFactory.loadMapOfAvailablePlugins(pluginDirectories))
    }

    @Test
    public void testLoadMapOfAvailablePlugins() {
        def availablePlugins = callLoadMapOfAvailablePlugins([pluginsDirWithInvalidEntries], true);
        assert availablePlugins != null
        assert availablePlugins.size() == 2
        assert availablePlugins["ValidContent"].size() == 1
        assert availablePlugins["Valid"].size() == 1
    }

    @Test
    public void testPerformCompatibleAPIChecks() {
        assert LibrariesFactory.performAPIChecks([
                new PluginInfo("MasterMax", null, null, null, "1.0.10-0", "1.3", "1.3", null),
                new PluginInfo("MasterMax", null, null, null, "1.0.10-0", "1.3", "1.3", null)])
    }

    @Test
    public void testPerformIncompatibleAPIChecks() {
        assert false == LibrariesFactory.performAPIChecks([
                new PluginInfo("MasterMax", null, null, null, "1.0.10-0", "000", "1.3", null),
                new PluginInfo("MasterMax", null, null, null, "1.0.10-0", "1.3", "1.3", null)])
        assert false == LibrariesFactory.performAPIChecks([
                new PluginInfo("MasterMax", null, null, null, "1.0.10-0", "1.3", "000", null),
                new PluginInfo("MasterMax", null, null, null, "1.0.10-0", "1.3", "1.3", null)])
        assert false == LibrariesFactory.performAPIChecks([
                new PluginInfo("MasterMax", null, null, null, "1.0.10-0", "1.3", "1.3", null),
                new PluginInfo("MasterMax", null, null, null, "1.0.10-0", "1.3", "1.3", null)])

    }

    @Test
    public void testBuildupValidPluginQueue() {
        // Now test several plugin chains for consistency and expected results.
        // Validate if all to-be-loaded versions are properly found.
        Map<String, PluginInfo> pluginQueueOK = LibrariesFactory.buildupPluginQueue(mapOfAvailablePlugins, ["D:1.0.2-1"] as String[]);
        assert pluginQueueOK != null;
        assert pluginQueueOK["D"].prodVersion == "1.0.2-1" &&
                pluginQueueOK["C"].prodVersion == "1.0.2-0" &&
                pluginQueueOK["B"].prodVersion == "1.0.3-0" &&
                pluginQueueOK["A"].prodVersion == "current" &&
                pluginQueueOK["PluginBase"].prodVersion == "current" &&
                pluginQueueOK["DefaultPlugin"].prodVersion == "current";

    }

    @Test
    public void testBuildupInvalidPluginQueue() {
        Map<String, PluginInfo> pluginQueueFaulty = LibrariesFactory.buildupPluginQueue(mapOfAvailablePlugins, ["D:1.0.2"] as String[]);
        assert pluginQueueFaulty == null;

    }

    @Test
    public void testBuildupPluginQueueContainingCompatibleEntries() {
        Map<String, PluginInfo> pluginQueueCompatible = LibrariesFactory.buildupPluginQueue(mapOfAvailablePlugins, ["D:1.0.3"] as String[]);
        assert pluginQueueCompatible != null;
        assert pluginQueueCompatible["D"].prodVersion == "1.0.3-0";
        assert pluginQueueCompatible["C"].prodVersion == "current";
        assert pluginQueueCompatible["B"].prodVersion == "1.0.3-0";
        assert pluginQueueCompatible["A"].prodVersion == "current";
        assert pluginQueueCompatible["PluginBase"].prodVersion == "current";
        assert pluginQueueCompatible["DefaultPlugin"].prodVersion == "current";

    }

    @Test
    public void testBuildupInvalidPluginQueueWithFixatedEntries() {
        Map<String, PluginInfo> pluginQueueFixatedEntriesFaulty = LibrariesFactory.buildupPluginQueue(mapOfAvailablePlugins, ["D:1.0.3", "C:1.0.3", "B:1.0.2-2"] as String[]);
        assert pluginQueueFixatedEntriesFaulty == null;

    }

    @Test
    public void testBuildupValidPluginQueueWithFixatedEntries() {
        Map<String, PluginInfo> pluginQueueFixatedEntriesOK = LibrariesFactory.buildupPluginQueue(mapOfAvailablePlugins, ["D:1.0.2-1", "C:1.0.2", "B:1.0.2-1"] as String[]);
        assert pluginQueueFixatedEntriesOK != null;
        assert pluginQueueFixatedEntriesOK["D"].prodVersion == "1.0.2-1" &&
                pluginQueueFixatedEntriesOK["C"].prodVersion == "1.0.2-0" &&
                pluginQueueFixatedEntriesOK["B"].prodVersion == "1.0.2-1" &&
                pluginQueueFixatedEntriesOK["A"].prodVersion == "1.0.24-0" &&
                pluginQueueFixatedEntriesOK["PluginBase"].prodVersion == "current" &&
                pluginQueueFixatedEntriesOK["DefaultPlugin"].prodVersion == "current";

    }

    @Test
    public void testBuildupValidPluginQueueWithMissingDefaultLibraryEntries() {
        Map<String, PluginInfo> pluginQueueWODefaultLibs = LibrariesFactory.buildupPluginQueue(mapOfAvailablePlugins, ["D:0.9.0"] as String[]);
        assert pluginQueueWODefaultLibs != null;
        assert pluginQueueWODefaultLibs["PluginBase"].prodVersion == "current" &&
                pluginQueueWODefaultLibs["DefaultPlugin"].prodVersion == "current";
    }

    @Test
    void testCheckOnToolDirDuplicatesWithDuplicates() {
        assert LibrariesFactory.checkOnToolDirDuplicates([pluginInfoObjectsForResourceTests["A"], pluginInfoObjectsForResourceTests["B"]])
        assert LibrariesFactory.checkOnToolDirDuplicates([pluginInfoObjectsForResourceTests["C"], pluginInfoObjectsForResourceTests["D"]])
    }

    @Test
    void testCheckOnToolDirDuplicatesWithoutDuplicates() {
        assert !LibrariesFactory.checkOnToolDirDuplicates([pluginInfoObjectsForResourceTests["A"], pluginInfoObjectsForResourceTests["C"]])
        assert !LibrariesFactory.checkOnToolDirDuplicates([pluginInfoObjectsForResourceTests["B"], pluginInfoObjectsForResourceTests["D"]])
    }

    @Test
    void testGetRoddyPackages() {
        Package[] list = new ClassLoaderHelper().getRoddyPackages()
        assert list.findAll { Package p -> p.name.startsWith(Roddy.package.name) } == list
    }

    @Test
    public void testSearchForClass() {
        Class _cls = LibrariesFactory.getInstance().searchForClass("GenericFileGroup")
        assert _cls == GenericFileGroup
    }

    @Test
    public void testLoadRealOrSyntheticClassRealClass() {
        Class _cls = LibrariesFactory.getInstance().loadRealOrSyntheticClass("GenericFileGroup", "FileGroup");
        assert _cls == GenericFileGroup
    }

    @Test
    public void testLoadRealOrSyntheticClassSynthClass() {
        Class _cls = LibrariesFactory.getInstance().loadRealOrSyntheticClass("ASyntheticFileGroup", "FileGroup");
        assert _cls.name.endsWith("ASyntheticFileGroup")
    }

    @Test
    public void testGenerateSyntheticFileClassWithParentClass() {
        Class _cls = LibrariesFactory.generateSyntheticFileClassWithParentClass("TestClass", "BaseFile", new GroovyClassLoader());
        assert _cls != null && _cls.name.equals(SyntheticPluginInfo.SYNTHETIC_PACKAGE + ".TestClass");
        assert _cls.getDeclaredConstructors().size() == 1;
        assert _cls.getDeclaredConstructors()[0].parameterTypes[0] == BaseFile.ConstructionHelperForBaseFiles
    }
}
