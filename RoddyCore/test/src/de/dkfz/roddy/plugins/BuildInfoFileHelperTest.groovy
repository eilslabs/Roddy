/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.plugins

import groovy.transform.CompileStatic
import org.junit.Test

/**
 * Test class for the BuildInfoFile Helper class
 * Created by heinold on 01.03.16.
 */
@CompileStatic
class BuildInfoFileHelperTest {

    static BuildInfoFileHelper setupValidHelperObject() {
        List<String> entries = [
                LibrariesFactory.BUILDINFO_RUNTIME_APIVERSION + "=2.3",
                LibrariesFactory.BUILDINFO_RUNTIME_JDKVERSION + "=1.7",
                LibrariesFactory.BUILDINFO_DEPENDENCY + "=PluginBase:1.0.24",
                LibrariesFactory.BUILDINFO_DEPENDENCY + "=Invalid:1.x.24",
                LibrariesFactory.BUILDINFO_COMPATIBILITY + "=1.0.10"
        ]
        return new BuildInfoFileHelper("MasterMax", "1.0.11", entries);
    }

    @Test
    void constructObjectWithNullInput() {
        BuildInfoFileHelper buildInfoFileHelper = new BuildInfoFileHelper("Maximaxi", "1.0.11", null as List);
    }

    @Test
    void constructObject() {
        def helper = setupValidHelperObject()
        assert helper.getEntries().size() == 4
        assert helper.getEntries().containsKey(LibrariesFactory.BUILDINFO_RUNTIME_APIVERSION)
        assert helper.getEntries().containsKey(LibrariesFactory.BUILDINFO_RUNTIME_JDKVERSION)
        assert helper.getEntries().containsKey(LibrariesFactory.BUILDINFO_DEPENDENCY)
        assert helper.getEntries().containsKey(LibrariesFactory.BUILDINFO_COMPATIBILITY)
    }

    @Test
    void testGetDependencies() {
        assert setupValidHelperObject().getDependencies() == ["PluginBase": "1.0.24"]
    }

    @Test
    void testIsCompatibleTo() {
        assert !setupValidHelperObject().isCompatibleTo(new PluginInfo("MasterMax2", null, null, null,
                "1.0.9-0", "2.3", "1.7", null))

        //Actually the following case will not be upcoming, because the PluginInfo objects are created with values from the BuildInfoHelper object
        //assert setupValidHelperObject().isCompatibleTo(new PluginInfo("MasterMax", null, null, null, "1.0.10-0", "2.3", "1.7", "2.4.5", null));
        assert setupValidHelperObject().isCompatibleTo(new PluginInfo("MasterMax3", null, null, null,
                "1.0.10-0", "2.3", "1.7", null))
    }

    @Test
    void testGetJDKVersion() {
        assert setupValidHelperObject().getJDKVersion() == "1.7";
    }

    @Test
    void testGetRoddyAPIVersion() {
        assert setupValidHelperObject().getRoddyAPIVersion() == "2.3";
    }

}