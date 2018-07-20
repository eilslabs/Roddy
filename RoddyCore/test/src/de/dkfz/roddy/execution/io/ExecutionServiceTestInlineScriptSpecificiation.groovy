/*
 * Copyright (c) 2018 German Cancer Research Center (DKFZ).
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */
package de.dkfz.roddy.execution.io

import de.dkfz.roddy.Roddy
import de.dkfz.roddy.RunMode
import de.dkfz.roddy.config.loader.ConfigurationFactory
import de.dkfz.roddy.core.ExecutionContext
import de.dkfz.roddy.core.ContextResource
import de.dkfz.roddy.core.RuntimeService
import de.dkfz.roddy.execution.io.fs.FileSystemAccessProvider
import de.dkfz.roddy.plugins.JarFulPluginInfo
import de.dkfz.roddy.plugins.LibrariesFactory
import de.dkfz.roddy.plugins.LibrariesFactoryTest
import de.dkfz.roddy.plugins.PluginInfo
import de.dkfz.roddy.tools.RoddyIOHelperMethods
import de.dkfz.roddy.tools.RuntimeTools
import de.dkfz.roddy.tools.RuntimeToolsTest
import groovy.transform.CompileStatic
import org.junit.ClassRule
import spock.lang.Shared
import spock.lang.Specification

import static de.dkfz.roddy.core.RuntimeService.*
import static de.dkfz.roddy.plugins.LibrariesFactory.*

/**
 * Tests in this class rely on the DefaultPlugin and some tools in it!
 * When the tests fail, check for changes in the DefaultPlugin.
 */
class ExecutionServiceTestInlineScriptSpecificiation extends Specification {

    @ClassRule
    static ContextResource contextResource = new ContextResource()

    @Shared
    public ExecutionContext mockedContext

    @Shared
    public ExecutionService executionService

    @Shared
    public Map<File, PluginInfo> listOfFolders = [:]

    @Shared
    public Map<File, PluginInfo> resultFoldersWithInlineScripts = [:]

    @Shared
    PluginInfo pluginInfoForTestPlugin

    @Shared
    File testPluginBaseFolder

    @Shared
    File testPluginFolder

    @Shared
    File testPluginToolFolder

    @CompileStatic
    def setupSpec() {
        contextResource.before()

        setupContextAndServices()

        fillFolderLists()

        createTestPlugin()
    }

    @CompileStatic
    void setupContextAndServices() {
        mockedContext = contextResource.createSimpleContext(ExecutionServiceTest)

        LibrariesFactory.initializeFactory(true)
        LibrariesFactory.instance.loadLibraries(LibrariesFactory.buildupPluginQueue(LibrariesFactoryTest.callLoadMapOfAvailablePlugins(), "DefaultPlugin").values() as List)
        ConfigurationFactory.initialize(LibrariesFactory.getInstance().getLoadedPlugins().collect { it -> it.getConfigurationDirectory() })
        FileSystemAccessProvider.initializeProvider(true)

        ExecutionService.initializeService(LocalExecutionService.class, RunMode.CLI)
        executionService = ExecutionService.instance
    }

    @CompileStatic
    void fillFolderLists() {
        for (PluginInfo pluginInfo in LibrariesFactory.getInstance().getLoadedPlugins()) {
            for (toolDirectory in pluginInfo.getToolsDirectories().values()) {
                listOfFolders[toolDirectory] = pluginInfo
            }
        }

        listOfFolders = listOfFolders.sort()
        resultFoldersWithInlineScripts = [:]
        // Store the first entry in the result list (roddyTools)
        resultFoldersWithInlineScripts[listOfFolders.keySet().first()] = listOfFolders.values().first()
        // Store the second entry. Assemble it. Unfortunately the path for inline scripts is a temporary folder with an unknown number in it.
        resultFoldersWithInlineScripts[new File("/tmp/groovy-generated-SOMETHINGTOTEST-tmpdir/roddyTools")] = listOfFolders.values().last()
    }

    /**
     * Create a temporary test plugin for compression tests
     */
    @CompileStatic
    void createTestPlugin() {
        testPluginBaseFolder = contextResource.getDirectory(ExecutionServiceTestInlineScriptSpecificiation.name, "testPluginFolder")
        testPluginFolder = new File(testPluginBaseFolder, "TestPlugin")
        testPluginToolFolder = RoddyIOHelperMethods.assembleLocalPath(testPluginFolder, DIRNAME_RESOURCES, DIRNAME_ANALYSIS_TOOLS)
        File cfgs = RoddyIOHelperMethods.assembleLocalPath(testPluginFolder, DIRNAME_RESOURCES, DIRNAME_CONFIG_FILES)
        File scripts = new File(testPluginToolFolder, "scripts")
        scripts.mkdirs()
        cfgs.mkdirs()
        File jarFile = new File(testPluginFolder, "TestPlugin.jar") << ""
        new File(testPluginFolder, BUILDINFO_TEXTFILE) << ""
        new File(testPluginFolder, BUILDVERSION_TEXTFILE) << ""

        // define some scripts with some content.
        ["a.sh", "b.sh", "c.sh"].each {
            new File(testPluginToolFolder, it) << "#!/bin/bash\nsleep 10\necho abc"
        }
        pluginInfoForTestPlugin = new JarFulPluginInfo("TestPlugin", testPluginFolder, jarFile, "develop", RuntimeTools.getRoddyRuntimeVersion(), RuntimeTools.getJavaRuntimeVersion(), [:])
    }

    @CompileStatic
    def cleanupSpec() {
        contextResource.after()
    }

    def "test persist inline scripts and tool folder reassembly without any inline scripts"(Map<File, PluginInfo> folders, Map<String, List<Map<String, String>>> mapOfInlineScriptsBySubfolder, Map<File, PluginInfo> expectedResult) {
        expect:
        executionService.persistInlineScriptsAndAssembleFinalListOfToolFolders(folders, mapOfInlineScriptsBySubfolder).sort() == expectedResult

        where:
        folders       | mapOfInlineScriptsBySubfolder | expectedResult
        listOfFolders | [:]                           | listOfFolders
    }

    /**
     * This test has a lot of check conditions but this way we really make sure, that
     * a) the created inline script exists and has the right content
     * b) the target temporary folder exists
     */
    def "test persist inline scripts and tool folder reassembly with inline script"(Map<File, PluginInfo> folders, Map<String, List<Map<String, String>>> mapOfInlineScriptsBySubfolder, Map<File, PluginInfo> expectedResult) {
        when:
        Map<File, PluginInfo> actualResult = executionService.persistInlineScriptsAndAssembleFinalListOfToolFolders(folders, mapOfInlineScriptsBySubfolder)
        actualResult = actualResult.sort()
        File arKey0 = actualResult.keySet().first()
        File arKey1 = actualResult.keySet().last()
        File expKey1 = expectedResult.keySet().last()
        File targetInlineScript = new File(arKey1, mapOfInlineScriptsBySubfolder.values().first().first()["inlineScriptName"])

        then:
        actualResult[arKey0] == expectedResult[arKey0]
        actualResult[arKey1] == expectedResult[expKey1]
        expKey1 != arKey1
        expKey1.absolutePath.contains("groovy-generated-")
        expKey1.name == mapOfInlineScriptsBySubfolder.keySet().first()
        expKey1.name == arKey1.name
        targetInlineScript.exists()
        targetInlineScript.text == mapOfInlineScriptsBySubfolder.values().first().first()["inlineScript"]

        where:
        folders       | mapOfInlineScriptsBySubfolder                                                         | expectedResult
        listOfFolders | ["roddyTools": [["inlineScript": "echo abc", "inlineScriptName": "inlineScript.sh"]]] | resultFoldersWithInlineScripts
    }

    def "test tool folder compression"() {
        def filename = "cTools_${pluginInfoForTestPlugin.getName()}:${pluginInfoForTestPlugin.getProdVersion()}_${pluginInfoForTestPlugin.getName()}.zip"
        setup:
        // Clean up test compression file in ~/.roddy first
        File fileToTest = new File(Roddy.getCompressedAnalysisToolsDirectory(), filename)
        File fileToTestMD5 = new File(Roddy.getCompressedAnalysisToolsDirectory(), filename + "_contentmd5")
        if (fileToTest.exists())
            fileToTest.delete()
        if (fileToTestMD5)
            fileToTestMD5.delete()

        Map<File, PluginInfo> foldersToCompress = [(testPluginFolder): pluginInfoForTestPlugin]

        when:
        executionService.compressToolFolders(foldersToCompress)

        then:
        fileToTest.exists()
        fileToTestMD5.exists()

        cleanup:
        if (fileToTest.exists())
            fileToTest.delete()
        if (fileToTestMD5)
            fileToTestMD5.delete()
    }

}