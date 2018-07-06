/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.plugins

import de.dkfz.roddy.StringConstants
import de.dkfz.roddy.tools.LoggerWrapper
import de.dkfz.roddy.tools.versions.CompatibilityChecker
import de.dkfz.roddy.tools.versions.Version
import de.dkfz.roddy.tools.versions.VersionLevel

import static de.dkfz.roddy.plugins.LibrariesFactory.*

/**
 * Created by heinold on 03.03.16.
 */
@groovy.transform.CompileStatic
class BuildInfoFileHelper {
    private static LoggerWrapper logger = LoggerWrapper.getLogger(LibrariesFactory.class.getSimpleName());

    public static final String DEFAULT_JDK_VERSION = "1.8" // For backward compatibility, the api versions are set for older plugins
    public static final String DEFAULT_RODDY_VERSION = "2.2"

    private Map<String, List<String>> entries = [:]
    private final String pluginName
    private final String pluginVersion
    private final boolean hasBuildInfoEntries

    public static String[] validEntries = [
            BUILDINFO_RUNTIME_APIVERSION, // Full reference, otherwise Groovy makes a String out of the entry and does not take the constants content
            BUILDINFO_RUNTIME_GROOVYVERSION,
            BUILDINFO_RUNTIME_JDKVERSION,
            BUILDINFO_DEPENDENCY,
            BUILDINFO_STATUS_BETA,
            BUILDINFO_COMPATIBILITY,
    ]

    /** This constructor is mainly for testing **/
    BuildInfoFileHelper(String pluginName, String pluginVersion, List<String> lines) {
        this.pluginName = pluginName
        this.pluginVersion = pluginVersion
        def invalid = []
        if (!lines) lines = []
        for (String _line in lines) {
            _line = _line.trim()
            if(!_line) continue
            if(_line.startsWith("#")) continue

            String[] line = _line.split(StringConstants.SPLIT_EQUALS)
            if (!validEntries.contains(line[0])) {
                invalid << line[0]
            } else {
                entries.get(line[0], []) << line[1]
            }
        }

        hasBuildInfoEntries = true

        if (invalid)
            logger.postAlwaysInfo("There are invalid entries in file buildinfo.txt for plugin ${pluginName}:${pluginVersion}:\n  " + invalid.join("\n "));
    }

    /** This constructor is the "real" constructor **/
    BuildInfoFileHelper(String pluginName, String pluginVersion, File buildinfoFile) {
        this(pluginName, pluginVersion, buildinfoFile.readLines())
    }

    BuildInfoFileHelper(String pluginname, String pluginVersion) {
        this.pluginName = pluginname
        this.pluginVersion = pluginVersion
        hasBuildInfoEntries = false
    }

    Map<String, List<String>> getEntries() {
        return entries
    }

    Map<String, String> getDependencies() {
        Map<String, String> dependencies = [:]
        for (String entry in entries.get(BUILDINFO_DEPENDENCY, [])) {
            if (!LibrariesFactory.isPluginIdentifierValid(entry)) continue
            List<String> split = entry?.split(StringConstants.SPLIT_COLON) as List
            String workflow = split[0]
            String version = split.size() > 1 ? split[1] : PLUGIN_VERSION_DEVELOP
            dependencies[workflow] = version
        }
        return dependencies;
    }

    /**
     * Not revision but compatible. Check, if the former plugin id (excluding the revision number) is set as compatible.
     * Ignore malformed entries!! Use a regex for that.
     *
     * @param previousPlugin
     * @return
     */
    boolean isCompatibleTo(PluginInfo previousPlugin) {

        if(previousPlugin == null) return false // The value is not set

        def _entries = entries.get(BUILDINFO_COMPATIBILITY, [])
        if(!_entries) return false // There is no entry in the buildinfo.txt file.

        String entry = _entries[0] as String;
        if (!(entry ==~ ~/.*-\d+$/)) //Ends with something like -123 or so... if not append it.
            entry += "-0"

        if (!isVersionStringValid(entry)) {
            logger.postSometimesInfo("Version string ${entry} is invalid.")
            return false
        }
        if (previousPlugin?.getProdVersion() != entry) {
            logger.postSometimesInfo("The compatibility entry ${entry} points to a not existing version for ${pluginName}, the last known entry is ${previousPlugin?.getProdVersion()}")
            return false
        }
        return true
    }

    String getJDKVersion() {
        return entries.get(BUILDINFO_RUNTIME_JDKVERSION, [DEFAULT_JDK_VERSION])[0].split(StringConstants.SPLIT_STOP)[0..1].join(".")
    }

    String getRoddyAPIVersion() {
        return entries.get(BUILDINFO_RUNTIME_APIVERSION, [DEFAULT_RODDY_VERSION])[0].split(StringConstants.SPLIT_STOP)[0..1].join(".")
    }

}