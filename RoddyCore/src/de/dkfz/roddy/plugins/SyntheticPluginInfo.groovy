/*
 * Copyright (c) 2017 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.plugins

import de.dkfz.roddy.core.VersionWithDevelop
import de.dkfz.roddy.tools.RuntimeTools
import groovy.transform.CompileStatic

/**
 * The synthetic plugin info object is solely used for automatically created synthetic file classes.
 * It is not usable for other purposes.
 * Created by heinold on 09.05.17.
 */
@CompileStatic
class SyntheticPluginInfo extends PluginInfo {

    public static final String SYNTHETIC_PACKAGE = "de.dkfz.roddy.synthetic.files"


    SyntheticPluginInfo(String name, File zipFile, File directory, File developmentDirectory, VersionWithDevelop prodVersion, Map<String, String> dependencies)
        throws IOException {
        super(name, zipFile, directory, developmentDirectory, prodVersion, RuntimeTools.getRoddyRuntimeVersion(), dependencies)
    }

    final Map<String, Class> map = [:]

    void addClass(Class cls) {
        map[cls.name] = cls
        map[cls.simpleName] = cls
    }
}
