/*
 * Copyright (c) 2017 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.plugins

import de.dkfz.roddy.Roddy
import de.dkfz.roddy.core.VersionWithDevelop
import groovy.transform.CompileStatic

/**
 * Represents standard Roddy plugins without a Jar file.
 * Created by heinold on 04.05.17.
 */
@CompileStatic
class JarLessPluginInfo extends PluginInfo {

    JarLessPluginInfo(String name, File directory, VersionWithDevelop version, Map<String, String> dependencies) {
        super(name, directory, version, Roddy.usedRoddyVersion, dependencies)
    }

    @Override
    boolean isCompatibleToRuntimeSystem() {
        return true
    }
}
