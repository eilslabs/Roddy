/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.client.cliclient

import de.dkfz.roddy.config.ConfigurationValue
import groovy.transform.CompileStatic

/**
 * Created by heinold on 17.07.16.
 */
@CompileStatic
class CommandLineCallTest extends GroovyTestCase {
    void testGetSetConfigurationValues() {
        CommandLineCall clc = new CommandLineCall(["run", "prj@ana", "--cvalues=a:( a bash array ),b:1.0,c:2:double,d:'quoted string',e:1.0f,f:1"])
        ConfigurationValue cvalue = clc.getSetConfigurationValues()[0]
        assert cvalue.type == "bashArray"
        assert cvalue.id == "a";
        assert cvalue.value == "( a bash array )";

        cvalue = clc.getSetConfigurationValues()[1];
        assert cvalue.type == "double"
        assert cvalue.id == "b";
        assert cvalue.value == "1.0";

        cvalue = clc.getSetConfigurationValues()[2];
        assert cvalue.type == "double"
        assert cvalue.id == "c";
        assert cvalue.value == "2";

        cvalue = clc.getSetConfigurationValues()[3];
        assert cvalue.type == "string"
        assert cvalue.id == "d";
        assert cvalue.value == "'quoted string'";

        cvalue = clc.getSetConfigurationValues()[4];
        assert cvalue.type == "float"
        assert cvalue.id == "e";
        assert cvalue.value == "1.0f";

        cvalue = clc.getSetConfigurationValues()[5];
        assert cvalue.type == "integer"
        assert cvalue.id == "f";
        assert cvalue.value == "1";
    }
}
