/*
 * Copyright (c) 2017 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.knowledge.nativeworkflows

import de.dkfz.roddy.Roddy
import de.dkfz.roddy.client.RoddyStartupOptions
import de.dkfz.roddy.config.converters.BashConverter
import de.dkfz.roddy.config.loader.ConfigurationLoaderException
import de.dkfz.roddy.core.VersionWithDevelop
import de.dkfz.roddy.plugins.NativePluginInfo
import de.dkfz.roddy.plugins.PluginLoaderException
import de.dkfz.roddy.tools.LoggerWrapper
import de.dkfz.roddy.tools.RoddyConversionHelperMethods
import de.dkfz.roddy.tools.RoddyIOHelperMethods
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import groovy.util.slurpersupport.NodeChild

/**
 * Convert native plugins to Roddy conform plugins
 *
 * Created by heinold on 04.05.17.
 */
@CompileStatic
class NativeWorkflowConverter {

    public static final LoggerWrapper logger = LoggerWrapper.getLogger(NativeWorkflowConverter.class)

    public static final String NATIVE_WORKFLOW_CONFIGFILE = "analysisConfiguration.sh"

    public static final String NATIVE_WORKFLOW_SCRIPT_PREFIX = "runWorkflow_"

    public static final String NATIVE_WORKFLOW_SCRIPT_SUFFIX = ".sh"

    public static final String NATIVE_WORFLOW_TOOLS_CHECKSUM_FILE = "toolsChecksum.txt"

    NativePluginInfo nativePluginInfo

    File pluginDir

    NativeWorkflowConverter(NativePluginInfo nativePluginInfo) {
        this.nativePluginInfo = nativePluginInfo
        File folderForConvertedPlugins = Roddy.getFolderForConvertedNativePlugins()
        pluginDir = new File(folderForConvertedPlugins, nativePluginInfo.directory.name)
    }

    static boolean isNativePlugin(File directory) {
        try {
            // Just create a dummy plugin info, try catch and return false on PluginLoaderException. Both methods trow this if something is bad..
            def piTest = new NativePluginInfo("TEST", directory, VersionWithDevelop.fromString("develop"), null)
            piTest.getSourceWorkflow()
            piTest.getSourceConfigurationFile()
            return true
        } catch (PluginLoaderException ex) {
            return false
        }
    }

    /**
     *  It differs and cannot be recreated => throw exception
     *  It differs and can be recreated => delete existing target directory
     *  It does not differ => return
     */
    void convert() {
        // Check if there already is a directory

        if (!ensureWorkflowStatusOrFail()) {

            // Finally convert
            def convertedConfigurationDirectory = nativePluginInfo.getConfigurationDirectory()
            def toolsBaseDirectory = nativePluginInfo.getToolsDirectory()
            def convertedToolsDirectory = nativePluginInfo.getConvertedToolsDirectory()

            convertedConfigurationDirectory.mkdirs()
            toolsBaseDirectory.mkdirs()

            // Copy all scripts from original directory to the tools directory
            RoddyIOHelperMethods.copyDirectory(nativePluginInfo.getNativeSourceDirectory(), convertedToolsDirectory)

            nativePluginInfo.getConvertedConfigurationFile() << importConfiguration()

            // Let's see what we can read in detailed.
            // Additional configuration values?
            // Extract tool entries?
            // Auto extract parameters and file i/o?
            // Based on checkpoints. Then rerun is possible.

//        nativePluginInfo.getConvertedConfigurationFile() << new XMLConverter().convert(null, importedConfigurationText)
        }
    }

    private String importConfiguration() {
        String importedConfigurationText = new BashConverter().convertToXML(nativePluginInfo.getSourceConfigurationFile())
        // Extend the configuration by the tool entries.
        importedConfigurationText = extendAndCleanConfiguration(importedConfigurationText)
        importedConfigurationText
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    String extendAndCleanConfiguration(String importedConfigurationText) {
        try {
            NodeChild xml = (NodeChild) new XmlSlurper().parseText(importedConfigurationText)

            xml.attributes()["runtimeServiceClass"] = de.dkfz.roddy.core.RuntimeService.class.name
            xml.attributes()["nativeWorkflowTool"] = nativePluginInfo.getConvertedWorkflowScript().name
            xml.attributes()["targetJobManager"] = nativePluginInfo.getSourceClusterSystem().className
            xml.attributes()["listOfUsedTools"] = nativePluginInfo.getToolIDs().join(",")
            xml.attributes()["imports"] = "roddyNativeTools"
            xml.attributes()["usedToolFolder"] = [nativePluginInfo.getConvertedToolsDirectory().name, "roddyNativeTools"].join(",")

            // Get rid of the analysis import section
            xml.children().findAll { NodeChild it -> it.name() == "availableAnalyses" }.replaceNode {}

            List<String> toolIDs = nativePluginInfo.getToolIDs()
            List<String> toolFileNames = nativePluginInfo.getToolFileNames()
            xml.appendNode {
                processingTools {
                    for (int i = 0; i < toolIDs.size(); i++) {
                        String id = toolIDs[i]
                        String file = toolFileNames[i]
                        tool(name: "$id", value: "$file", basepath: nativePluginInfo.getConvertedToolsDirectory().name) {}
                    }

                }
            }

            // Finally add all found tools as tool entries.
            logger.postAlwaysInfo(RoddyConversionHelperMethods.toFormattedXML(xml))

            return new groovy.xml.StreamingMarkupBuilder().bindNode(xml) as String
        } catch (Exception ex) {
            println(importedConfigurationText)
            throw ex
        } finally {
        }
    }

    /**
     * Make sure, that the plugin:
     *   is the same and can be reused OR
     *   if it is not, check for --forcenativepluginconversion and delete the converted dir OR
     *   fail and throw an exception
     *
     * @return true , if you can reuse the existing converted workflow otherwise false. Note that the method will throw an exception on error!
     */
    boolean ensureWorkflowStatusOrFail() {
        // Check if it is still the same (md5 sums and so on) and if the --forcenativepluginconversion
        boolean forceoverride = Roddy.getCommandLineCall().isOptionSet(RoddyStartupOptions.forcenativepluginconversion)
        if (pluginDir.exists()) {
            String md5NewImport = RoddyIOHelperMethods.getMD5OfText(importConfiguration())
            String md5ExistingXML = RoddyIOHelperMethods.getMD5OfFile(nativePluginInfo.getConvertedConfigurationFile())

            def sourceMD5 = RoddyIOHelperMethods.getSingleMD5OfFilesInDirectoryExcludingDirectoryNames(nativePluginInfo.getNativeSourceDirectory())
            def existingMD5 = RoddyIOHelperMethods.getSingleMD5OfFilesInDirectoryExcludingDirectoryNames(nativePluginInfo.getConvertedToolsDirectory())
            def differs = (sourceMD5 != existingMD5) || (md5NewImport != md5ExistingXML)
            if (differs && !forceoverride)
                throw new PluginLoaderException("Cannot convert the native plugin ${nativePluginInfo.getName()}, it was already converted and is located in ${pluginDir}. The converted plugin differs from the original plugin. Please remove this directory before going on. Or call Roddy with --forcenativepluginconversion.")
            else if (differs) { // Delete and convert
                pluginDir.deleteDir()
                return false
            } else {
                // Reuse
                return true
            }
        }
        return false
    }


}
