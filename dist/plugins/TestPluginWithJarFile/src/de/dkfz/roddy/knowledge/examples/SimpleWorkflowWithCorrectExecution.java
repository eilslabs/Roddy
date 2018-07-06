/*
 * Copyright (c) 2017 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.knowledge.examples;


import de.dkfz.roddy.core.ExecutionContext;
import de.dkfz.roddy.core.Workflow;
import de.dkfz.roddy.knowledge.files.BaseFile;
import de.dkfz.roddy.knowledge.files.Tuple4;

import java.util.List;

/**
 */
public class SimpleWorkflowWithCorrectExecution extends Workflow {
    @Override
    public boolean execute(ExecutionContext context) {
        SimpleRuntimeService srs = (SimpleRuntimeService) context.getRuntimeService();

        BaseFile directFile = getSourceFile(context, "/tmp/test.txt");

//        BaseFile fileFromTool = getSourceFileUsingTool(context, "fileLoaderTool");
//
//        List<BaseFile> fileListFromTool = getSourceFilesUsingTool(context, "fileListLoaderTool");

        SimpleTestTextFile initialTextFile = srs.createInitialTextFile(context);
        SimpleTestTextFile textFile1 = initialTextFile.test1(); //(TextFile) GenericMethod.callGenericTool("testScript", initialTextFile);
        SimpleTestTextFile textFile2 = textFile1.test2();//(TextFile) GenericMethod.callGenericTool("testScript", textFile1);
        call("testScriptMultiInMixedParameters",textFile1, "STRING_IN=a", textFile2, "STRING_IN2=b", textFile1);
//        Tuple4 mout1 = (Tuple4) call("testScriptWithMultiOut", textFile2);
//        Tuple4 mout2 = (Tuple4) call("testScriptWithMultiOut2", textFile2);
        return true;
    }
}
