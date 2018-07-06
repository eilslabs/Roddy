/*
 * Copyright (c) 2017 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.plugins

import de.dkfz.roddy.core.VersionWithDevelop
import de.dkfz.roddy.tools.versions.VersionInterval
import groovy.transform.CompileStatic

/**
 * A default Java based Roddy plugin
 * Created by heinold on 04.05.17.
 */
@CompileStatic
class JarFulPluginInfo extends PluginInfo {

    final File jarFile
    final BuildInfoFile buildInfoFile

    JarFulPluginInfo(String name, File directory, BuildInfoFile buildInfoFile, File jarFile, VersionWithDevelop version, Map<String, String> dependencies) {
        super(name, directory, version, buildInfoFile.roddyAPIVersion, dependencies)
        this.buildInfoFile = buildInfoFile
        this.jarFile = jarFile
    }

    JarFulPluginInfo(String name, File directory, File buildInfoFile, File jarFile, VersionWithDevelop version, Map<String, String> dependencies) {
        this(name, directory, new BuildInfoFile(buildInfoFile), jarFile, version, dependencies)
    }

    String getJdkVersion() {
        return buildInfoFile.getJDKVersion()
    }

    /** Get the list of VersionIntervals representing the 'compatibleto' fields in the buildinfo.txt.
     *
     * @return
     */
    @Override
    List<VersionInterval> getCompatibleVersionIntervals() {
        buildInfoFile.compatibleVersions.collect {
            new VersionInterval(it, version)
        } as List<VersionInterval>
    }

}
