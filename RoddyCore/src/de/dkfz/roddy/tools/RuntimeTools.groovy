/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.tools

import de.dkfz.roddy.Constants
import de.dkfz.roddy.StringConstants
import de.dkfz.roddy.plugins.LibrariesFactory
import de.dkfz.roddy.tools.versions.Version
import de.dkfz.roddy.tools.versions.VersionLevel

/**
 * A class which gives you access to several runtime specific values (e.g. from System.getProperty...)
 * Created by heinold on 29.02.16.
 */
@groovy.transform.CompileStatic
final class RuntimeTools {
    private RuntimeTools() {}

    private static final LoggerWrapper logger = LoggerWrapper.getLogger(RuntimeTools)

    static String getRoddyRuntimeVersion() {
        return Version.fromString(Constants.APP_CURRENT_VERSION_STRING).toString(VersionLevel.MINOR)
    }

    static String getJavaRuntimeVersion() {
        String javaRuntimeVersion = System.getProperty("java.version").split("[.]")[0..1].join(".")
        javaRuntimeVersion
    }

    /**
     * Extract the groovy library version from the current class path
     * @return
     */
    static String getGroovyRuntimeVersion() {
        File groovyLibrary = getGroovyLibrary()
        String version = groovyLibrary.name.split(StringConstants.SPLIT_MINUS)[2].split("[.]")[0..1].join(".")
        return version;
    }

    static File getBuildinfoFile() {
        return new File(getDevelopmentDistFolder(), LibrariesFactory.BUILDINFO_TEXTFILE);
    }

    static File getDevelopmentDistFolder() {
        return new File(System.getProperty("user.dir"), "dist/bin/develop")
    }

    static File getGroovyLibrary() {
        // Try to get Groovy from the environment. This is needed for groovyserv.
        // If this is not working get it from the classpath.
        logger.rare(([""] + System.getenv().collect { String k, String v -> "${k}=${v}" }.join("\n") + [""]).flatten().join("\n"))
        if (System.getenv().containsKey("RODDY_GROOVYLIB_PATH")) {
            def file = new File(System.getenv("RODDY_GROOVYLIB_PATH"))
            logger.info("Loading groovy library from GroovyServ environment " + file)
            return file
        } else {
            def file = new File(System.getProperty("java.class.path").split("[:]").find { new File(it).name.startsWith("groovy") })
            if (file == null)
                throw new RuntimeException("Could not find groovy library in class path: " + System.getProperty("java.class.path"))
            logger.info("Loading groovy library from local environment " + file)
            return file
        }
    }
}
