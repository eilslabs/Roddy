/*
 * Copyright (c) 2018 German Cancer Research Center (DKFZ).
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */
package de.dkfz.roddy.plugins

import de.dkfz.roddy.core.VersionWithDevelop

class PluginInfoService {

    static PluginInfo create(String name, PluginType type, File directory, VersionWithDevelop version) {
        PluginInfo newPluginInfo
        if (type == PluginType.NATIVE) {
            newPluginInfo = new NativePluginInfo(name, directory, version, [:])
        } else if (type == PluginType.RODDY) {
            File jarFile = directory.listFiles().find { File f -> f.name.endsWith ".jar" }
            if (jarFile) {
                newPluginInfo = new JarFulPluginInfo(name, directory, new File(directory, LibrariesFactory.BUILDINFO_TEXTFILE), jarFile, version, [:])
            } else {
                newPluginInfo = new JarLessPluginInfo(name, directory, version, [:])
            }
        }
        return newPluginInfo
    }

}
