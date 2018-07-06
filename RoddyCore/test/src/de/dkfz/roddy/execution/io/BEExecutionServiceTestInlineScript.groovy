/*
 * Copyright (c) 2017 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.execution.io

import de.dkfz.roddy.RunMode
import de.dkfz.roddy.config.*
import de.dkfz.roddy.config.loader.ConfigurationFactory
import de.dkfz.roddy.core.ExecutionContext
import de.dkfz.roddy.core.ContextResource
import de.dkfz.roddy.knowledge.files.GenericFileGroup
import de.dkfz.roddy.plugins.LibrariesFactory
import de.dkfz.roddy.plugins.LibrariesFactoryTest
import de.dkfz.roddy.plugins.PluginInfo
import de.dkfz.roddy.tools.RoddyIOHelperMethods
import org.junit.BeforeClass
import org.junit.Ignore
import org.junit.Test

/**
 * Created by kaercher on 05.07.16.
 */
class BEExecutionServiceTestInlineScript {

    public static ExecutionContext mockedContext;
    public static Map<File, PluginInfo> listOfFolders = [:]

    @BeforeClass
    public static void setupContext() {
        //Setup plugins and default configuration folders
        LibrariesFactory.initializeFactory(true)
        LibrariesFactory.getInstance().loadLibraries(LibrariesFactory.buildupPluginQueue(LibrariesFactoryTest.callLoadMapOfAvailablePlugins(), "DefaultPlugin").values() as List);
        ConfigurationFactory.initialize(LibrariesFactory.getInstance().getLoadedPlugins().collect { it -> it.getConfigurationDirectory() })

        final Configuration mockupConfig = new Configuration(new PreloadedConfiguration(null, Configuration.ConfigurationType.OTHER, "test", "", "", null, "", ResourceSetSize.l, null, null, null, null), ConfigurationFactory.getInstance().getConfiguration("default"))
        mockedContext = ContextResource.createSimpleContext(BEExecutionServiceTest, mockupConfig);

        ExecutionService.initializeService(LocalExecutionService.class, RunMode.CLI);

        Map<File, PluginInfo> sourcePaths = [:];
        for (PluginInfo pluginInfo : LibrariesFactory.getInstance().getLoadedPlugins()) {
            pluginInfo.getToolsDirectories().values().each { sourcePaths[it] = pluginInfo }
        }
        listOfFolders = sourcePaths.findAll { File it, PluginInfo pInfo -> !it.getName().contains(".svn"); }
    }

    @Test
    @Ignore("Fix!")
    public void testWriteInlineScriptsAndCompressToolFolders() {
        Configuration cfg = mockedContext.getConfiguration();

        ToolEntry toolEntry = new ToolEntry("roddyTests", "roddyTests", "");
        toolEntry.getOutputParameters(cfg).add(new ToolFileGroupParameter(GenericFileGroup, null, "TEST"))
        def inlineScriptName = "testInlineScript.sh"
        toolEntry.setInlineScriptName(inlineScriptName)
        toolEntry.setInlineScript("echo test")
        cfg.getTools().add(toolEntry);

        Map<String, List<Map<String,String>>> mapOfInlineScripts = [:]

        for (ToolEntry tool in cfg.getTools().allValuesAsList) {
            if (tool.hasInlineScript()) {
                mapOfInlineScripts.get(tool.basePathId, []) << ["inlineScript":tool.getInlineScript(),"inlineScriptName":tool.getInlineScriptName()]
            }
        }

        ExecutionService.getInstance().writeInlineScriptsAndCorrectListOfFolders(listOfFolders,mapOfInlineScripts)

        assert  listOfFolders.size() == 2
        boolean hasCompressedFolder = false
        listOfFolders.each {
            File subFolder, PluginInfo pInfo ->
                File localCompressedFolder = ExecutionService.getInstance().mapOfPreviouslyCompressedArchivesByFolder[subFolder].localArchive;
                assert localCompressedFolder != null
                if (localCompressedFolder.name.equals("cTools_DefaultPlugin:current_roddyTests.zip")){
                    hasCompressedFolder = true
                    boolean hasInlineScript = false
                    File tempFolder = File.createTempDir();
                    tempFolder.deleteOnExit()
                    GString str = RoddyIOHelperMethods.getCompressor().getDecompressionString(localCompressedFolder, null, tempFolder);
                    ExecutionService.getInstance().execute(str, true);
                    def files = tempFolder.listFiles()[0].listFiles()
                    files.each {File file ->
                        if (file.name.equals(inlineScriptName)){
                            hasInlineScript = true
                        }
                    }
                    assert hasInlineScript
                }
        }
        assert  hasCompressedFolder
    }


    @Test
    @Ignore("Fix!")
    public void testWriteInlineScriptsAndCompressToolFoldersWithoutInlineScript() {
        Configuration cfg = mockedContext.getConfiguration();

        ToolEntry toolEntry = new ToolEntry("roddyTests", "roddyTests", "");
        toolEntry.getOutputParameters(cfg).add(new ToolFileGroupParameter(GenericFileGroup, null, "TEST"))
        def inlineScriptName = "testInlineScript.sh"
        cfg.getTools().add(toolEntry);

        Map<String, List<Map<String,String>>> mapOfInlineScripts = [:]

        for (ToolEntry tool in cfg.getTools().allValuesAsList) {
            if (tool.hasInlineScript()) {
                mapOfInlineScripts.get(tool.basePathId, []) << ["inlineScript":tool.getInlineScript(),"inlineScriptName":tool.getInlineScriptName()]
                assert false
            }
        }

        ExecutionService.getInstance().writeInlineScriptsAndCorrectListOfFolders(listOfFolders,mapOfInlineScripts)

        assert  listOfFolders.size() == 2
        boolean hasCompressedFolder = false
        listOfFolders.each {
            File subFolder, PluginInfo pInfo ->
                File localCompressedFolder = ExecutionService.getInstance().mapOfPreviouslyCompressedArchivesByFolder[subFolder].localArchive;
                assert localCompressedFolder != null
                if (localCompressedFolder.name.equals("cTools_DefaultPlugin:current_roddyTests.zip")){
                    hasCompressedFolder = true
                    boolean hasInlineScript = false
                    File tempFolder = File.createTempDir();
                    tempFolder.deleteOnExit()
                    GString str = RoddyIOHelperMethods.getCompressor().getDecompressionString(localCompressedFolder, null, tempFolder);
                    ExecutionService.getInstance().execute(str, true);
                    def files = tempFolder.listFiles()[0].listFiles()
                    files.each {File file ->
                        if (file.name.equals(inlineScriptName)){
                            assert false
                        }
                    }
                }
        }
        assert  hasCompressedFolder
    }

}