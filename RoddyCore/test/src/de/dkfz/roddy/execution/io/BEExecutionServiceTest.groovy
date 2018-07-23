/*
 * Copyright (c) 2017 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.execution.io

import de.dkfz.roddy.config.ResourceSetSize
import de.dkfz.roddy.RunMode
import de.dkfz.roddy.config.Configuration
import de.dkfz.roddy.config.loader.ConfigurationFactory
import de.dkfz.roddy.config.ConfigurationValue
import de.dkfz.roddy.config.PreloadedConfiguration
import de.dkfz.roddy.config.ToolEntry
import de.dkfz.roddy.config.ToolFileGroupParameter
import de.dkfz.roddy.core.ExecutionContext
import de.dkfz.roddy.core.ContextResource
import de.dkfz.roddy.core.RuntimeService
import de.dkfz.roddy.execution.io.fs.FileSystemAccessProvider
import de.dkfz.roddy.knowledge.files.BaseFile
import de.dkfz.roddy.knowledge.files.FileGroup
import de.dkfz.roddy.knowledge.files.GenericFileGroup
import de.dkfz.roddy.plugins.LibrariesFactory
import de.dkfz.roddy.plugins.LibrariesFactoryTest
import groovy.transform.CompileStatic
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Ignore;
import org.junit.Test

/**
 * Created by heinold on 25.11.15.
 */
@CompileStatic
@Ignore("setupContext fails")
public class BEExecutionServiceTest {

    @ClassRule
    final static ContextResource contextResource = new ContextResource()

    public static ExecutionContext mockedContext;

    @BeforeClass
    public static void setupContext() {
        //Setup plugins and default configuration folders
        LibrariesFactory.initializeFactory(true)
        LibrariesFactory.getInstance().loadLibraries(LibrariesFactory.buildupPluginQueue(LibrariesFactoryTest.callLoadMapOfAvailablePlugins(), "DefaultPlugin").values() as List);
        ConfigurationFactory.initialize(LibrariesFactory.getInstance().getLoadedPlugins().collect { it -> it.getConfigurationDirectory() })

        final Configuration mockupConfig = new Configuration(new PreloadedConfiguration(null, Configuration.ConfigurationType.OTHER, "test", "", "", null, "", ResourceSetSize.l, null, null, null, null), ConfigurationFactory.getInstance().getConfiguration("default")) {
            @Override
            File getSourceToolPath(String tool) {
                if (tool == "wrapinScript")
                    return super.getSourceToolPath(tool);
                return new File("/tmp/RoddyTests/RoddyTestScript_ExecutionServiceTest.sh")
            }
        };

        mockupConfig.getConfigurationValues().add(new ConfigurationValue(RuntimeService.RODDY_CENTRAL_EXECUTION_DIRECTORY, "/tmp/roddyCentralDirectory"));

        mockedContext = contextResource.createSimpleContext(BEExecutionServiceTest, mockupConfig);
    }

    @Test
    public void testExecuteTool() throws Exception {

        // Create a test script which outputs several file paths
        def testFolder = "/tmp/RoddyTests"
        def testScriptPrefix = testFolder + "/RoddyTestScript_ExecutionServiceTest"
        def testScriptsFolder = new File(mockedContext.executionDirectory, "/analysisTools/RoddyTests")
        testScriptsFolder.mkdirs()
        (new File(testFolder)).mkdir()
        File testScript = new File(testScriptsFolder, "RoddyTestScript_ExecutionServiceTest.sh") // TODO This is Linux specific...!
        testScript.delete()
        testScript << """
            touch ${testScriptPrefix}_a ${testScriptPrefix}_b ${testScriptPrefix}_c ${testScriptPrefix}_d
            ls ${testScriptPrefix}_*
        """

        // Create the definition for it
        ExecutionContext context = mockedContext
        Configuration config = context.getConfiguration();

        def scriptsFolder = new File(mockedContext.executionDirectory, "analysisTools/roddyTools/")
        scriptsFolder.mkdirs()
        def sourceToolpath = config.getSourceToolPath("wrapinScript");
        def targetToolpath = new File(scriptsFolder, sourceToolpath.name)
        targetToolpath.delete();
        targetToolpath << sourceToolpath.text;
        targetToolpath.setExecutable(true, true);

        ToolEntry toolEntry = new ToolEntry("RoddyTests", "RoddyTests", "RoddyTestScript_ExecutionServiceTest.sh");
        toolEntry.getOutputParameters(config).add(new ToolFileGroupParameter(GenericFileGroup as Class<FileGroup>, (Class<BaseFile>)null, "TEST", "default"))
        config.getTools().add(toolEntry);

        // Initialize with fallback provider! Don't worry about errors at this point.
        FileSystemAccessProvider.initializeProvider(true);
        ExecutionService.initializeService(LocalExecutionService.class, RunMode.CLI);

        // Call it
        def executeTool = ExecutionService.getInstance().executeTool(context, "RoddyTests");

        // Check the output contents
        assert executeTool.size() == 4;
        assert executeTool == ["${testScriptPrefix}_a", "${testScriptPrefix}_b", "${testScriptPrefix}_c", "${testScriptPrefix}_d"]
    }
}