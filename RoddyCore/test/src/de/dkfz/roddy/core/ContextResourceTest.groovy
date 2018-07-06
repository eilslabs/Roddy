/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.core

import de.dkfz.roddy.config.Configuration
import de.dkfz.roddy.knowledge.files.BaseFile
import groovy.transform.CompileStatic
import org.junit.Rule
import org.junit.Test

@CompileStatic
class ContextResourceTest {
    
    @Rule
    public ContextResource contextResource = new ContextResource()

    @Test
    void testGetTestBaseDirectory() {
        String testDir = contextResource.getTestBaseDirectory(ContextResourceTest.name).absolutePath
        assert testDir =~ /^\/tmp\/junit\d+\/${contextResource.DIR_PREFIX}\//
        assert testDir.endsWith(ContextResourceTest.name)
    }

    @Test
    void testGetDirectory() {
        File file = contextResource.getDirectory(ContextResourceTest.class.name, "verification")
        assert file.exists() && file.name.endsWith("verification")
    }

    @Test
    void testGetLoggingDirectory() {
        assert contextResource.getTestLoggingDirectory(ContextResource.name).name == "logdir"
    }

    @Test
    void testGetExecDirectory() {
        assert contextResource.getTestExecutionDirectory(ContextResource.name).name == "exec_dir"
    }

    @Test
    void testGetInputDirectory() {
        assert contextResource.getTestInputDirectory(ContextResource.name).name == "input"
    }

    @Test
    void testGetOutputDirectory() {
        assert contextResource.getTestOutputDirectory(ContextResource.name).name == "output"
    }

    @Test
    void testCreateSimpleRuntimeService() {
        def rs = contextResource.createSimpleRuntimeService(ContextResourceTest.class.name)
        // The following test will fail, because a new temporary file is created when accessing getTestLoggingDirectory.
        // We'll have to do that differently.
//        assert rs.getLoggingDirectory(null) == contextResource.getTestLoggingDirectory(ContextResourceTest.class.name)
        String logDir = rs.getLoggingDirectory(null).getAbsolutePath()
        assert logDir =~ /^\/tmp\/junit\d+\/RoddyTests\// && logDir.endsWith(ContextResourceTest.class.name + "/logdir")
    }

    @Test
    void testCreateSimpleContextWithClass() {
        contextResource.createSimpleContext(ContextResourceTest);
    }

    @Test
    void testCreateSimpleContextWithClassName() {
        contextResource.createSimpleContext(ContextResourceTest.name)
    }

    @Test
    void testCreateSimpleContextWithClassNameAndCustomConfig() {
        Configuration configuration = new Configuration(null);
        contextResource.createSimpleContext(ContextResourceTest.name, configuration);
    }

    @Test
    void testCreateSimpleContextWithClassNameCustomConfigAndRuntimeService() {
        Configuration configuration = new Configuration(null);
        def rs = new RuntimeService() {
            @Override
            Map<String, Object> getDefaultJobParameters(ExecutionContext context, String TOOLID) {
                return null
            }

            @Override
            String createJobName(ExecutionContext executionContext, BaseFile file, String TOOLID, boolean reduceLevel) {
                return null
            }

            @Override
            boolean isFileValid(BaseFile baseFile) {
                return false
            }

        }
        contextResource.createSimpleContext(ContextResourceTest.name, configuration, rs);
    }

}
