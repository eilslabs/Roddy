/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.plugins;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by heinold on 27.06.16.
 */
@groovy.transform.CompileStatic
public class PluginInfoMapTest {

    private PluginInfoMap assemblePluginInfoMap() {
        return new PluginInfoMap([
                "BasePlugin": [
                        "1.0.1"  : new PluginInfo("BasePlugin", null, null, null, "1.0.1", "2.3", "1.8", null),
                        "1.0.2"  : new PluginInfo("BasePlugin", null, null, null, "1.0.2", "2.3", "1.8", null),
                        "develop": new PluginInfo("BasePlugin", null, null, null, "develop", "2.3", "1.8", null),
                ] as Map<String, List<PluginInfo>>,
                "TestPlugin": [
                        "1.0.1"  : new PluginInfo("TestPlugin", null, null, null, "1.0.1", "2.3", "1.8", null),
                        "1.0.2"  : new PluginInfo("TestPlugin", null, null, null, "1.0.2", "2.3", "1.8", null),
                        "develop": new PluginInfo("TestPlugin", null, null, null, "develop", "2.3", "1.8", null),
                ] as Map<String, List<PluginInfo>>
        ] );
    }

    @Test(expected = RuntimeException)
    public void testGetPluginInfoWithDamagedPluginString() {
        PluginInfoMap pim = assemblePluginInfoMap();
        pim.getPluginInfoWithPluginString("BasePlugin:1.0.1:11")
    }

    @Test
    public void testGetPluginInfoByPluginString() {
        PluginInfoMap pim = assemblePluginInfoMap();
        assert null != pim.getPluginInfoWithPluginString("BasePlugin:1.0.1")
        assert null != pim.getPluginInfoWithPluginString("BasePlugin:develop")
        assert null != pim.getPluginInfoWithPluginString("BasePlugin") && pim.getPluginInfoWithPluginString("BasePlugin").getProdVersion() == "develop"
    }

    @Test(expected = PluginLoaderException)
    public void testGetPluginInfoWithMissingPluginEntry() {
        PluginInfoMap pim = assemblePluginInfoMap();
        pim.getPluginInfo("NewPlugin", "1.0.0")
    }

    @Test(expected = PluginLoaderException)
    public void testGetPluginInfoWithMissingVersionEntry() {
        PluginInfoMap pim = assemblePluginInfoMap();
        pim.getPluginInfo("BasePlugin", "1.0.0")
    }

    @Test
    public void testGetPluginInfo() {
        PluginInfoMap pim = assemblePluginInfoMap();
        assert null != pim.getPluginInfo("BasePlugin", "1.0.1")
        assert null != pim.getPluginInfo("BasePlugin", "1.0.2")
        assert null != pim.getPluginInfo("BasePlugin", "develop")
        assert null != pim.getPluginInfo("BasePlugin", null)
        assert pim.getPluginInfo("BasePlugin", null).getProdVersion() == "develop"
    }

    @Test
    public void testCheckExistence() {
        PluginInfoMap pim = assemblePluginInfoMap();
        assert !pim.checkExistence("NewPlugin", "1.0.0") //Non-existing plugin
        assert !pim.checkExistence("BasePlugin", "1.0.0") //Non-existing version
        assert pim.checkExistence("BasePlugin", "1.0.1") //Existing plugin and version
        assert pim.checkExistence("BasePlugin", null) //Existing plugin and unset version (null)
    }

    @Test
    public void testSize() {
        assert assemblePluginInfoMap().size() == 2;
    }

    @Test
    public void testGetAt(){
        // Test, if the access is available.
        assert assemblePluginInfoMap()["BasePlugin"].size() > 0;
        // In some cases, groovyc told me, that it does not know about the type and made an inherent cast to Object.
        // Without the type, nothing like size() or getAt() will work.
        // This is a groovy specific error and this is the test for it. It is strange somehow :/
        assert assemblePluginInfoMap()["BasePlugin"].size() + 1 > 0;
    }

    @Test
    public void testAsBoolean() {
        assert assemblePluginInfoMap();
        assert !(new PluginInfoMap(null));
    }
}