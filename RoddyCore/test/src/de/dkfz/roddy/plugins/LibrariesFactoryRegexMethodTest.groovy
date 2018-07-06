/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.plugins

import de.dkfz.roddy.Roddy
import de.dkfz.roddy.StringConstants
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import org.junit.rules.TemporaryFolder

import java.lang.reflect.Method

/**
 * The tests for the jar file loading are not very nice.
 * The core classes Roddy and LibrariesFactory are singletons and they need to be reset for each test.
 * Also it is necessary to synchronize some of the tests. Not nice, but working.
 */
@groovy.transform.CompileStatic
public class LibrariesFactoryRegexMethodTest {

    @Test
    public void testInvalidWorkflowIdentificationStringTester() {
        Map<String, Boolean> listOfIdentifiers = [
                "COWorkflows:1.0.1-0:develop": false,
                "COWorkflows:1.0"            : false,
                "COWorkflows:"               : false,
        ]
        listOfIdentifiers.each {
            String id, Boolean result ->
                assert LibrariesFactory.isPluginIdentifierValid(id) == result;
        }

    }

    @Test
    public void testValidWorkflowIdentificationStringTester() {
        Map<String, Boolean> listOfIdentifiers = [
                "COWorkflows:1.0.1-0": true,
                "COWorkflows:1.0.1-3": true,
                "COWorkflows:develop": true,
                "COWorkflows"        : true,
        ]
        listOfIdentifiers.each {
            String id, Boolean result ->
                assert LibrariesFactory.isPluginIdentifierValid(id) == result;
        }

    }

    @Test
    public void testInvalidWorkflowDirectoryNameTester() {
        Map<String, Boolean> listOfIdentifiers = [
                "COWorkflows_1.0.1-0:develop": false,
                "COWorkflows:1.0.1-r"        : false,
                "COWorkflows:1.0.1-3"        : false,
                "COWorkflows_develop"        : false,
        ]
        listOfIdentifiers.each {
            String id, Boolean result ->
                assert LibrariesFactory.isPluginDirectoryNameValid(id) == result;
        }

    }

    @Test
    public void testValidWorkflowDirectoryNameTester() {
        Map<String, Boolean> listOfIdentifiers = [
                "COWorkflows_1.0.1-3": true,
                "COWorkflows"        : true,
        ]
        listOfIdentifiers.each {
            String id, Boolean result ->
                assert LibrariesFactory.isPluginDirectoryNameValid(id) == result;
        }

    }
}
