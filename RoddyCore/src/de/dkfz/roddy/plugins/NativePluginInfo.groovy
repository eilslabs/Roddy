/*
 * Copyright (c) 2017 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.plugins

import de.dkfz.roddy.AvailableClusterSystems
import de.dkfz.roddy.Constants
import de.dkfz.roddy.Roddy
import de.dkfz.roddy.core.VersionWithDevelop
import de.dkfz.roddy.knowledge.nativeworkflows.NativeWorkflowConverter
import de.dkfz.roddy.tools.RuntimeTools
import groovy.transform.CompileStatic
import org.apache.commons.io.filefilter.WildcardFileFilter

/**
 * Represents native plugins with e.g. shell scripts which are auto-converted from native plugins
 * Created by heinold on 04.05.17.
 */
@CompileStatic
class NativePluginInfo extends PluginInfo {

    final File nativeSourceDirectory

    NativePluginInfo(String name, File nativeSourceDirectory, VersionWithDevelop version, Map<String, String> dependencies) {
        super(name, null, version, Roddy.usedRoddyVersion, dependencies)
        this.nativeSourceDirectory = nativeSourceDirectory
        // Can't be passed into super() because nativeSourceDirectory is not set.
        this.directory = getConvertedPluginDirectory()

        // Needs to be called a second time.
        fillListOfToolDirectories()
    }

    @Override
    boolean isCompatibleToRuntimeSystem() {
        return true
    }


    @Override
    protected void fillListOfToolDirectories() {
        listOfToolDirectories[getConvertedToolsDirectory().name] = getConvertedToolsDirectory()
        listOfToolDirectories["inlineScripts"] = getConvertedInlineScriptsDirectory()
    }

    @Override
    List<File> getConfigurationFiles() {
        if (getConfigurationDirectory().exists()) {
            return super.getConfigurationFiles()
            // Use the converted xmls by default.
        } else {
            return [getSourceConfigurationFile()]
        }
    }

    File getMD5File() {
        return new File(getConvertedPluginDirectory(), NativeWorkflowConverter.NATIVE_WORFLOW_TOOLS_CHECKSUM_FILE)
    }

    AvailableClusterSystems getSourceClusterSystem() {
        def extract = getSourceWorkflow().name[NativeWorkflowConverter.NATIVE_WORKFLOW_SCRIPT_PREFIX.length()..-(NativeWorkflowConverter.NATIVE_WORKFLOW_SCRIPT_SUFFIX.length() + 1)]
        return extract as AvailableClusterSystems
    }

    File getSourceWorkflow() {
        File[] listOfFiles = nativeSourceDirectory.listFiles((FilenameFilter)new WildcardFileFilter(NativeWorkflowConverter.NATIVE_WORKFLOW_SCRIPT_PREFIX + "*" + NativeWorkflowConverter.NATIVE_WORKFLOW_SCRIPT_SUFFIX))

        if (!listOfFiles)
            throw new PluginLoaderException("There is no workflow run script in directory ${nativeSourceDirectory}")
        if (listOfFiles.length > 1)
            throw new PluginLoaderException("There must only be one workflow run script in directory ${nativeSourceDirectory}")
        return listOfFiles[0]
    }

    File getSourceConfigurationFile() {
        File configurationFile = new File(getNativeSourceDirectory(), NativeWorkflowConverter.NATIVE_WORKFLOW_CONFIGFILE)
        if (!configurationFile.exists())
            throw new PluginLoaderException("There is no configuration file in directory ${nativeSourceDirectory}")
        return configurationFile
    }

    File getConvertedPluginDirectory() {
        return new File(Roddy.getFolderForConvertedNativePlugins(), nativeSourceDirectory.getName())
    }

    File getConvertedToolsDirectory() {
        return new File(getToolsDirectory(), "${name}Tools");
    }

    File getConvertedInlineScriptsDirectory() {
        return new File(getToolsDirectory(), "inlineScripts")
    }

    File getConvertedWorkflowScript() {
        return new File(getConvertedToolsDirectory(), getSourceWorkflow().name)
    }

    File getConvertedConfigurationFile() {
        return new File(getConfigurationDirectory(), "analysis${getName()}.xml")
    }

    List<String> getToolFileNames() {
        return getConvertedToolsDirectory().listFiles().collect { File file -> file.name }
    }

    List<String> getToolIDs() {
        return getToolFileNames().collect { String filename -> filename.replaceAll("[.]", "_") }
    }

}
