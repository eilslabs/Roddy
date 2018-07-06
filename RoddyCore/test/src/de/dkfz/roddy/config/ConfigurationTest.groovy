/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.config

import groovy.transform.CompileStatic

/**
 * Created by heinold on 22.07.16.
 */
@CompileStatic
class ConfigurationTest extends GroovyTestCase {

    public PreloadedConfiguration mockContent(String name, PreloadedConfiguration parent) {
        return new PreloadedConfiguration(parent, Configuration.ConfigurationType.OTHER, name, "", "", null, "", ResourceSetSize.s, [], [], null, "");
    }

    void testGetResourcesSize() {
        def iccA = mockContent("A", null)
        def iccB = mockContent("B", iccA)
        def iccC = mockContent("C", iccB)

        Configuration cfgA = new Configuration(iccA)
        Configuration cfgB = new Configuration(iccB)
        Configuration cfgC = new Configuration(iccC)

        cfgC.getConfigurationValues().add(new ConfigurationValue(cfgC, ConfigurationConstants.CFG_USED_RESOURCES_SIZE, "xl"));

        assert cfgB.getResourcesSize() == ResourceSetSize.s;

        assert cfgC.getResourcesSize() == ResourceSetSize.xl;
    }
}
