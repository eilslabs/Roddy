/*
 * Copyright (c) 2017 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.core

import de.dkfz.roddy.RunMode
import de.dkfz.roddy.execution.io.ExecutionService
import de.dkfz.roddy.execution.io.LocalExecutionService
import de.dkfz.roddy.execution.io.fs.FileSystemAccessProvider
import groovy.transform.CompileStatic
import org.junit.BeforeClass
import org.junit.Rule;
import org.junit.Test

/**
 * Created by heinold on 01.02.17.
 */
@CompileStatic
public class ExecutionContextTest {

    @Rule
    final public ContextResource contextResource = new ContextResource()

    private ExecutionContext createEmptyContext() {
        contextResource.createSimpleContext(this.getClass())
    }

    @BeforeClass
    public static void setup() {
        FileSystemAccessProvider.initializeProvider(true)
        ExecutionService.initializeService(LocalExecutionService.class, RunMode.CLI)
    }

    @Test
    public void valueIsEmpty() throws Exception {
        def context = createEmptyContext()
        assert context.valueIsEmpty(null, "var")
        assert context.getErrors().size() == 1
        assert !context.valueIsEmpty(false, "var")
    }

    @Test
    public void fileIsAccessible() throws Exception {
        File f = File.createTempFile("roddy", "fileIsAccessibleTest")
        f << "abc"
        def context = createEmptyContext()
        assert context.fileIsAccessible(f, "var")
        assert !context.fileIsAccessible(new File(f.getAbsolutePath() + "_abc"), "var")
        assert context.getErrors().size() == 1

    }

    @Test
    public void directoryIsAccessible() throws Exception {
        File f = File.createTempFile("roddy", "fileIsAccessibleTest")
        f << "abc"
        def context = createEmptyContext()
        assert context.directoryIsAccessible(f.parentFile, "var")
        assert !context.directoryIsAccessible(new File(f.parent + "_invalidDirectory"), "var")
        assert context.getErrors().size() == 1
    }

}
