/*
 * Copyright (c) 2017 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.plugins

import de.dkfz.roddy.AvailableClusterSystems
import de.dkfz.roddy.Roddy
import de.dkfz.roddy.core.ContextResource
import de.dkfz.roddy.core.RuntimeService
import de.dkfz.roddy.knowledge.nativeworkflows.NativeWorkflowConverter
import groovy.transform.CompileStatic
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

/**
 * Created by heinold on 05.05.17.
 */
@CompileStatic
class NativePluginInfoTest {

    @ClassRule
    final public static ContextResource contextResource = new ContextResource()

    static File pluginDirIncomplete

    static File pluginDirComplete

    static File testBaseDirectory

    static File convertedPluginBaseDir

    public static final String separator = File.separator
    public static final String PLUGIN_INCOMPLETE = "PluginComplete"
    public static final String PLUGIN_COMPLETE = "PluginComplete"
    public static final String PLUGIN_COMPLETE_WITH_VERSION = PLUGIN_COMPLETE + "_1.0.0"

    @BeforeClass
    static void setup() {
        testBaseDirectory = contextResource.getDirectory(NativePluginInfoTest.class.name, "variousTests")
        pluginDirIncomplete = new File(testBaseDirectory, "${PLUGIN_INCOMPLETE}_1.0.0")
        pluginDirComplete = new File(testBaseDirectory, PLUGIN_COMPLETE_WITH_VERSION)

        pluginDirIncomplete.mkdirs()
        pluginDirComplete.mkdirs()

        new File(pluginDirComplete, NativeWorkflowConverter.NATIVE_WORKFLOW_CONFIGFILE) << "a=b\nc=d"
        new File(pluginDirComplete, NativeWorkflowConverter.NATIVE_WORKFLOW_SCRIPT_PREFIX + "pbs" + NativeWorkflowConverter.NATIVE_WORKFLOW_SCRIPT_SUFFIX) << "a=b\nc=d"

        convertedPluginBaseDir = Roddy.getFolderForConvertedNativePlugins()
    }

    private String getToolsDirForPluginComplete() {
        def pi = getPluginInfoComplete()
        return [
                Roddy.getFolderForConvertedNativePlugins(),
                PLUGIN_COMPLETE_WITH_VERSION,
                RuntimeService.DIRNAME_RESOURCES,
                RuntimeService.DIRNAME_ANALYSIS_TOOLS,
                pi.name + "Tools"
        ].join(separator)
    }

    NativePluginInfo getPluginInfoIncomplete() {
        return new NativePluginInfo("PluginIncomplete", pluginDirIncomplete, "1.0.0", null)
    }

    NativePluginInfo getPluginInfoComplete() {
        return new NativePluginInfo(PLUGIN_COMPLETE, pluginDirComplete, "1.0.0", null)
    }

    @Test
    void testFillListOfToolDirectories() {
        def pi = getPluginInfoComplete()
        assert pi.getToolsDirectories().size() == 2
        assert pi.getToolsDirectories().values()[0].absolutePath == getToolsDirForPluginComplete()
    }

    @Test
    void testGetMD5File() {
        assert getPluginInfoComplete().getMD5File().absolutePath == [Roddy.getFolderForConvertedNativePlugins(), PLUGIN_COMPLETE_WITH_VERSION, NativeWorkflowConverter.NATIVE_WORFLOW_TOOLS_CHECKSUM_FILE].join(separator)
    }

    @Test
    void testGetSourceClusterSystem() {
        assert getPluginInfoComplete().getSourceClusterSystem() == AvailableClusterSystems.pbs
    }

    @Test
    void testGetSourceWorkflow() {
        assert getPluginInfoComplete().getSourceWorkflow().absolutePath == [pluginDirComplete, "runWorkflow_pbs.sh"].join(separator)
    }

    @Test
    void testGetSourceConfigurationFile() {
        assert getPluginInfoComplete().getSourceConfigurationFile().absolutePath == [pluginDirComplete, "analysisConfiguration.sh"].join(separator)
    }

    @Test
    void testGetConvertedPluginDirectory() {
        assert getPluginInfoComplete().getConvertedPluginDirectory().absolutePath == Roddy.getFolderForConvertedNativePlugins().absolutePath + separator + PLUGIN_COMPLETE + "_1.0.0"
    }

    @Test
    void testGetConvertedToolsDirectory() {
        assert getPluginInfoComplete().getConvertedToolsDirectory().absolutePath == getToolsDirForPluginComplete()
    }

    @Test
    void testGetConvertedWorkflowScript() {
        assert getPluginInfoComplete().getConvertedWorkflowScript().absolutePath == getToolsDirForPluginComplete() + separator + "runWorkflow_pbs.sh"
    }

    @Test
    void testGetConvertedConfigurationFile() {
        assert getPluginInfoComplete().getConvertedConfigurationFile().absolutePath == [
                Roddy.getFolderForConvertedNativePlugins().absolutePath,
                PLUGIN_COMPLETE_WITH_VERSION,
                RuntimeService.DIRNAME_RESOURCES,
                RuntimeService.DIRNAME_CONFIG_FILES,
                "analysisPluginComplete.xml"
        ].join(separator)
    }
}
