/*
 * Copyright (c) 2017 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.plugins

import de.dkfz.roddy.core.ContextResource
import de.dkfz.roddy.core.RuntimeService
import de.dkfz.roddy.tools.RoddyIOHelperMethods
import org.junit.BeforeClass

/**
 * Created by heinold on 21.04.17.
 */
class PluginInfoTest extends GroovyTestCase {

    static File testdir

    static File validPlugin

    static File badPlugin

    @BeforeClass
    static void setupTestDirs() {
        testdir = ContextResource.getDirectory(PluginInfoTest.name, "data")
        validPlugin = RoddyIOHelperMethods.assembleLocalPath(testdir, "Valid")
        badPlugin = RoddyIOHelperMethods.assembleLocalPath(testdir, "Invalid")
        ["1", "2"].each {
            RoddyIOHelperMethods.assembleLocalPath(testdir, "Valid", RuntimeService.DIRNAME_RESOURCES, RuntimeService.DIRNAME_ANALYSIS_TOOLS, it).mkdirs()
        }
        ["1", ".3"].each {
            RoddyIOHelperMethods.assembleLocalPath(testdir, "Invalid", RuntimeService.DIRNAME_RESOURCES, RuntimeService.DIRNAME_ANALYSIS_TOOLS, it).mkdirs()
        }
        RoddyIOHelperMethods.assembleLocalPath(testdir, "Invalid", RuntimeService.DIRNAME_RESOURCES, RuntimeService.DIRNAME_ANALYSIS_TOOLS, "file") << "TOUCHED"
    }

    void testConstructionWithValidDirectoryEntries() {
        new PluginInfo("Valid", null, validPlugin, null, null, null, null, null)
    }

    void testConstructionWithInvalidContents() {
        new PluginInfo("Invalid", null, badPlugin, null, null, null, null, null)
    }
}
