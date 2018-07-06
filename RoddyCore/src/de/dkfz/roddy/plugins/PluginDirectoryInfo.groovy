/*
 * Copyright (c) 2017 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.plugins

import de.dkfz.roddy.StringConstants
import de.dkfz.roddy.core.VersionWithDevelop
import groovy.transform.CompileStatic

/**
 * Contains basic information about a plugin folder.
 * Created by heinold on 29.04.17.
 */
@CompileStatic
class PluginDirectoryInfo {

    final File directory

    final String pluginName

    final VersionWithDevelop version

    final PluginType type

    PluginDirectoryInfo(File file, PluginType pluginType) {
        String[] splitName = file.name.split(StringConstants.SPLIT_UNDERSCORE) // Split name and version
        directory = file
        pluginName = splitName[0]
        version = VersionWithDevelop.fromString(splitName.size() > 1 ? splitName[1] : LibrariesFactory.PLUGIN_VERSION_DEVELOP)
        type = pluginType
    }

    boolean needs

    boolean needsBuildInfoFile() {
        return type.needsBuildInfoFile
    }

}
