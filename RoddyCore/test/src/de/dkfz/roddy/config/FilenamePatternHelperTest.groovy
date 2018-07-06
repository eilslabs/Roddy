/*
 * Copyright (c) 2017 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.config

import de.dkfz.roddy.core.ExecutionContext
import de.dkfz.roddy.core.ContextResource
import groovy.transform.CompileStatic
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

/**
 * Created by heinold on 10.01.17.
 */
@CompileStatic
class FilenamePatternHelperTest {

    @ClassRule
    final public static ContextResource contextResource = new ContextResource()

    static ExecutionContext mockupContext

    @BeforeClass
    static void setup() {
        mockupContext = contextResource.createSimpleContext(FilenamePatternHelperTest)
    }

    @Test
    void testSetup() {
        assert mockupContext
    }

    @Test
    void extractEmptyCommand() {
        // Extract empty command means to detect something unknown inside ${ .. }
        def val = 'abc${value}def'
        def result = FilenamePatternHelper.extractCommand( "", val)
        assert result.rawName == "value"
    }

    @Test
    void extractRegularCommand() {
        // Extract empty command means to detect something unknown inside ${ .. }
        def val = 'abc${cvalue,name="value"}def'
        def result = FilenamePatternHelper.extractCommand( '\${cvalue', val)
        assert result.rawName == "cvalue"
        assert result.fullString == '${cvalue,name="value"}'
        assert result.attributes.size() == 1
        assert result.attributes["name"].value == "value"
    }

    @Test
    void extractCommands() throws Exception {
        String commandID = '\${jobParameter'
        String temp = '/a/real/string/${jobParameter,name="test",default="abc"}/${jobParameter,name="test2"}/a_filename.txt'
        def result = FilenamePatternHelper.extractCommands(mockupContext, commandID, temp)
        assert result
        assert result.size() == 2
        assert result[0].attributes["name"].value == "test"
        assert result[0].attributes["default"].value == "abc"
        assert result[1].attributes["name"].value == "test2"
    }

    @Test
    void testExtractCommandsWithUnclosedLiterals() {
        String commandID = '\${jobParameter'
        String temp = '/a/real/string/${jobParameter,name="test"}/${jobParameter,name="test2"/a_filename.txt'
        def result = FilenamePatternHelper.extractCommands(mockupContext, commandID, temp)
        assert result
        // TODO What should happen?
//        assert false
    }

}