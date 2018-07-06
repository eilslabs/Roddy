/*
 * Copyright (c) 2017 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.core

import de.dkfz.roddy.Roddy
import de.dkfz.roddy.RunMode
import de.dkfz.roddy.execution.io.ExecutionService
import de.dkfz.roddy.execution.io.LocalExecutionService
import de.dkfz.roddy.execution.io.fs.FileSystemAccessProvider
import groovy.transform.CompileStatic
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

import java.lang.reflect.Field

/**
 * Created by heinold on 04.04.17.
 */
@CompileStatic
class ExecutionContextReaderAndWriterTest {

    @Rule
    final public ContextResource contextResource = new ContextResource()

    @Test
    void readInExecutionContext() throws Exception {

    }

    @Test
    @Ignore("Analysis configuration needs to be non-null! Fix!")
    void getInitialContext() throws Exception {
        FileSystemAccessProvider.initializeProvider(true)
        ExecutionService.initializeService(LocalExecutionService, RunMode.CLI)
        Field f = Roddy.class.getDeclaredField("jobManager")
        f.setAccessible(true)
        f.set(null, contextResource.createMockupJobManager())

        ExecutionContext context = contextResource.createSimpleContext(ExecutionContextReaderAndWriterTest.class)
        Analysis analysis = context.getAnalysis()
        DataSet ds = new DataSet(analysis, "TEST", new File(analysis.getOutputAnalysisBaseDirectory(), "TEST")) {
            @Override
            File getInputFolderForAnalysis(Analysis a) {
                return new File("/tmp")
            }

            @Override
            File getOutputFolderForAnalysis(Analysis a) {
                return new File("/tmp")
            }
        }
        // Use old time syntax here. Date would otherwise struggle with the milliseconds.
        def execDir = new File(context.getExecutionDirectory(), "exec_170402_171935000_testuser_" + analysis.getName())
        AnalysisProcessingInformation api = new AnalysisProcessingInformation(analysis, ds, execDir)
        ExecutionContext ec = new ExecutionContextReaderAndWriter(context.getRuntimeService()).getInitialContext(api)

        assert ec.getTimestamp() == new Date(117, 03, 02, 17, 19, 35)
        assert ec.getExecutingUser() == "testuser"
        assert ec.getExecutionDirectory() == execDir
    }

    @Test
    void readInJobStateLogFile() throws Exception {

    }

    @Test
    void readJobInfoFile() throws Exception {

    }

    @Test
    void writeJobInfoFile() throws Exception {

    }

}