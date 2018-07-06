/*
 * Copyright (c) 2018 German Cancer Research Center (DKFZ).
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */
package de.dkfz.roddy.plugins

import de.dkfz.roddy.StringConstants
import de.dkfz.roddy.core.VersionWithDevelop
import de.dkfz.roddy.tools.LoggerWrapper

import java.text.ParseException

import static de.dkfz.roddy.plugins.LibrariesFactory.*

@groovy.transform.CompileStatic
class BuildInfoFile {
    private static LoggerWrapper logger = LoggerWrapper.getLogger(LibrariesFactory.class.getSimpleName());

    public static final String DEFAULT_JDK_VERSION = "1.8" // For backward compatibility, the api versions are set for older plugins
    public static final String DEFAULT_RODDY_VERSION = "2.2"

    private File buildInfoFilePath
    private Map<String, List<String>> entries = [:]
    private boolean hasBuildInfoEntries = false

    public static String[] validEntries = [
            BUILDINFO_RUNTIME_APIVERSION, // Full reference, otherwise Groovy makes a String out of the entry and does not take the constants content
            BUILDINFO_RUNTIME_GROOVYVERSION,
            BUILDINFO_RUNTIME_JDKVERSION,
            BUILDINFO_DEPENDENCY,
            BUILDINFO_STATUS_BETA,
            BUILDINFO_COMPATIBILITY,
    ]

    private void parse(List<String> lines) {
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
            logger.postAlwaysInfo("There are invalid entries in buildinfo.txt: '$buildInfoFilePath'\n  " + invalid.join("\n "))
    }

    BuildInfoFile(File buildInfoFile) {
        buildInfoFilePath = buildInfoFile
        parse(buildInfoFile.readLines())
    }

    // TODO Reuse existing plugin ID parser.
    Map<String, String> getDependencies() {
        Map<String, String> dependencies = [:]
        for (String entry in entries.get(BUILDINFO_DEPENDENCY, [])) {
            if (!LibrariesFactory.isPluginIdentifierValid(entry)) continue
            List<String> split = entry?.split(StringConstants.SPLIT_COLON) as List
            String workflow = split[0]
            String version = split.size() > 1 ? split[1] : PLUGIN_VERSION_DEVELOP
            dependencies[workflow] = version
        }
        return dependencies
    }

    /** For the plugin directory represented by this BuildInfoFile, get the list of Version representing the 'compatibleto' fields
     *  in the buildinfo.txt.
     *
     * @return
     */
    List<VersionWithDevelop> getCompatibleVersions() {
        return entries.get(BUILDINFO_COMPATIBILITY, []).collect { entry ->
            VersionWithDevelop compatibleVersion
            try {
                compatibleVersion = VersionWithDevelop.fromString(entry)
            } catch (ParseException e) {
                logger.postSometimesInfo("Version string ${entry} is invalid: " + e.message)
                return []
            }
            compatibleVersion
        } as List<VersionWithDevelop>
    }


    String getJDKVersion() {
        return entries.get(BUILDINFO_RUNTIME_JDKVERSION, [DEFAULT_JDK_VERSION])[0].split(StringConstants.SPLIT_STOP)[0..1].join(".")
    }

    String getRoddyAPIVersion() {
        return entries.get(BUILDINFO_RUNTIME_APIVERSION, [DEFAULT_RODDY_VERSION])[0].split(StringConstants.SPLIT_STOP)[0..1].join(".")
    }

}
