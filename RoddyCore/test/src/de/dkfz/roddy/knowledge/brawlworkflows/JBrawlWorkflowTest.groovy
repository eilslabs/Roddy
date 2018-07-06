/*
 * Copyright (c) 2017 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.knowledge.brawlworkflows

import de.dkfz.roddy.config.AnalysisConfiguration
import de.dkfz.roddy.config.ContextConfiguration
import de.dkfz.roddy.config.ProjectConfiguration
import de.dkfz.roddy.config.ToolEntry
import de.dkfz.roddy.config.ToolFileGroupParameter
import de.dkfz.roddy.knowledge.files.BaseFile
import de.dkfz.roddy.knowledge.files.FileGroup
import de.dkfz.roddy.knowledge.files.FileObject
import de.dkfz.roddy.knowledge.files.GenericFileGroup
import de.dkfz.roddy.plugins.LibrariesFactory
import groovy.transform.TypeCheckingMode
import org.junit.Ignore;
import org.junit.Test;

import java.lang.reflect.Method

/**
 * Created by heinold on 18.11.15.
 */
@groovy.transform.CompileStatic
public class JBrawlWorkflowTest {


    public static final String LOAD_FASTQ_FILES = "loadFastqFiles"

    @groovy.transform.CompileStatic(TypeCheckingMode.SKIP)
    private String callPrepareAndFormatLine(String line) {
        Method prepareAndReformatLine = JBrawlWorkflow.getDeclaredMethod("prepareAndReformatLine", String);
        prepareAndReformatLine.setAccessible(true);
        return prepareAndReformatLine.invoke(null, line)
    }

    @Test
    public void testPrepareAndReformatLine() {

        def linesAndExpected = [
                "set  srs=call context.getRuntimeService ( );": "set srs = call context.getRuntimeService ( );",
                "if(a==b())"                                  : "if ( a == b ( ) )",
                'if !runIndelDeepAnnotation ; then'           : "if ! runIndelDeepAnnotation; then",
                'set deepAnnotatedVCFFile=call "indelDeepAnnotation" (bamTumorMerged, bamControlMerged, rawVCFFile, "PIPENAME=INDEL_DEEPANNOTATION")'
                                                              : 'set deepAnnotatedVCFFile = call "indelDeepAnnotation" ( bamTumorMerged, bamControlMerged, rawVCFFile, "PIPENAME=INDEL_DEEPANNOTATION" )'

        ]

        linesAndExpected.each {
            String line, String exp ->
                assert callPrepareAndFormatLine(line) == exp;
        }
    }

    @groovy.transform.CompileStatic(TypeCheckingMode.SKIP)
    private String callAssembleCall(String[] _l, int indexOfCallee, StringBuilder temp, ContextConfiguration configuration, LinkedHashMap<String, String> knownObjects) {
        Method assembleCall = JBrawlWorkflow.class.getDeclaredMethod("_assembleCall", String[], int, StringBuilder, ContextConfiguration, LinkedHashMap);
        assembleCall.setAccessible(true);

        return (String) assembleCall.invoke(null, _l, indexOfCallee, temp, configuration, knownObjects);
    }

    @Test
    @Ignore("Test to create")
    public void testAssembleCall() {
//        callAssembleCall(null, 0, null, null, null);
        assert false
    }

    @groovy.transform.CompileStatic(TypeCheckingMode.SKIP)
    private String callAssembleLoadFilesCall(String[] _l, int indexOfCallee, StringBuilder temp, ContextConfiguration configuration, LinkedHashMap<String, String> knownObjects) {
        Method assembleLoadFilesCall = JBrawlWorkflow.class.getDeclaredMethod("_assembleLoadFilesCall", String[], int, StringBuilder, ContextConfiguration, LinkedHashMap);
        assembleLoadFilesCall.setAccessible(true);
        String result = assembleLoadFilesCall.invoke(null, _l, indexOfCallee, temp, configuration, knownObjects);

        return result as String;
    }

    @Test
    public void testAssembleLoadFilesCall() {
        StringBuilder tempBuilder = new StringBuilder();

        def aCfg = new AnalysisConfiguration(null, null, null, null, null, null, null)
        def pCfg = new ProjectConfiguration(null, null, null, null)
        ContextConfiguration cc = new ContextConfiguration(aCfg, pCfg);

        Class<BaseFile> testFileClass = LibrariesFactory.getInstance().loadRealOrSyntheticClass("TestFile", BaseFile.class as Class<FileObject>);
        def loadFastqFiles = new ToolEntry(LOAD_FASTQ_FILES, "testtools", "/tmp/testtools/${LOAD_FASTQ_FILES}.sh")
        loadFastqFiles.getOutputParameters(cc).add(
                new ToolFileGroupParameter(
                        (new GenericFileGroup([] as List)).class as Class<FileGroup>,
                        testFileClass,
                        "FUZZY_GROUP",
                        ToolFileGroupParameter.PassOptions.parameters));
        cc.getTools().add(loadFastqFiles)

        String[] _l = callPrepareAndFormatLine("""set inputfiles = loadfilesWith "${LOAD_FASTQ_FILES}"()'""").split("[ ]")
        int indexOfCallee = 4;

        def expected = " = de.dkfz.roddy.knowledge.files.GenericFileGroup<de.dkfz.roddy.synthetic.files.TestFile> inputfiles =\n" +
                       "       new de.dkfz.roddy.knowledge.files.GenericFileGroup<de.dkfz.roddy.synthetic.files.TestFile>(BEExecutionService.getInstance().executeTool(context, ${LOAD_FASTQ_FILES}\n" +
                       "           .replaceAll('\"', ''))\n" +
                       "           .collect { it -> new TestFile(it) });"

        def foundClass = callAssembleLoadFilesCall(_l, indexOfCallee, tempBuilder, cc, null);

        assert foundClass == "de.dkfz.roddy.knowledge.files.GenericFileGroup<de.dkfz.roddy.synthetic.files.TestFile>"
        assert expected == tempBuilder.toString();
    }

}