/*
 * Copyright (c) 2017 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.execution.jobs

import de.dkfz.roddy.config.TestFileStageSettings
import de.dkfz.roddy.core.ContextResource;
import de.dkfz.roddy.knowledge.files.BaseFile
import groovy.transform.CompileStatic
import org.junit.Rule;
import org.junit.Test;

/**
 * Created by heinold on 10.01.17.
 */
@CompileStatic
public class BEJobTest {

    @Rule
    final public ContextResource contextResource = new ContextResource()

    @CompileStatic
    public static class TestFile extends BaseFile {

        public TestFile(BaseFile.ConstructionHelperForSourceFiles helper) {
            super(helper);
        }

        public File getPath() {
            return new File('/a/test/${jobParameter,name=\"abc\"}/${jobParameter,name=\"def\"}/text.txt');
        }

    }

    @Test
    public void replaceParametersInFilePath() throws Exception {
        BaseFile bf = new TestFile(new BaseFile.ConstructionHelperForSourceFiles(new File("/invalid"),
                contextResource.createSimpleContext(BEJobTest.name), new TestFileStageSettings<>(), null))
        def parm = ["abc": "avalue",
                    "def": "anothervalue"
        ] as Map<String, Object>
        assert Job.replaceParametersInFilePath(bf, parm) == new File('/a/test/avalue/anothervalue/text.txt')
    }

}