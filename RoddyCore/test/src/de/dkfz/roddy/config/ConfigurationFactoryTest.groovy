/*
 * Copyright (c) 2017 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.config

import de.dkfz.roddy.RunMode
import de.dkfz.roddy.config.loader.ConfigurationFactory
import de.dkfz.roddy.config.loader.ConfigurationLoaderException
import de.dkfz.roddy.config.loader.ProcessingToolReader
import de.dkfz.roddy.core.ContextResource
import de.dkfz.roddy.execution.io.ExecutionService
import de.dkfz.roddy.execution.io.LocalExecutionService
import de.dkfz.roddy.execution.io.fs.FileSystemAccessProvider
import de.dkfz.roddy.knowledge.files.GenericFileGroup
import de.dkfz.roddy.plugins.LibrariesFactory
import de.dkfz.roddy.plugins.LibrariesFactoryTest
import de.dkfz.roddy.tools.BufferValue
import de.dkfz.roddy.tools.TimeUnit
import de.dkfz.roddy.tools.Tuple3
import groovy.transform.TypeCheckingMode
import groovy.util.slurpersupport.NodeChild
import org.junit.*
import org.junit.rules.ExpectedException

import java.lang.reflect.Method

import static de.dkfz.roddy.config.ResourceSetSize.*

/**
 * Tests for ConfigurationFactory
 */
@groovy.transform.CompileStatic
class ConfigurationFactoryTest {

    @ClassRule
    final public static ContextResource contextResource = new ContextResource()

    @Rule
    public ExpectedException thrown = ExpectedException.none()

    private static File testFolder1

    private static File testFolder2

    private static File testFolder3

    private static File testFolder4

    @BeforeClass
    static void setupClass() {
        ExecutionService.initializeService(LocalExecutionService, RunMode.CLI)

        FileSystemAccessProvider.initializeProvider(true)

        LibrariesFactory.initializeFactory(true)
        LibrariesFactory.getInstance().loadLibraries(LibrariesFactory.buildupPluginQueue(LibrariesFactoryTest.callLoadMapOfAvailablePlugins(), "DefaultPlugin").values() as List)

        testFolder1 = contextResource.tempFolder.newFolder("normalFolder1")
        testFolder2 = contextResource.tempFolder.newFolder("normalFolder2")
        testFolder3 = contextResource.tempFolder.newFolder("buggyCfgFolder1")
        testFolder4 = contextResource.tempFolder.newFolder("buggyCfgFolder2")

        LinkedHashMap<String, File> configurationFiles = [
                "project_A"  : testFolder1,
                "project_B"  : testFolder1,
                "project_C"  : testFolder1,
                "project_D"  : testFolder2,
                "project_E"  : testFolder2,
                "project_F"  : testFolder2,
                "something_A": testFolder1,
                "something_B": testFolder2,

                "project_G"  : testFolder3,
                "project_H"  : testFolder3,
                "project_I"  : testFolder3,
                "project_J"  : testFolder4,
                "project_K"  : testFolder4,
                "project_L"  : testFolder4,
                "something_C": testFolder3,
                "something_D": testFolder4,
        ]

        configurationFiles.each {
            String k, File f ->
                if (k.startsWith("project")) {
                    new File(f, "${k}.xml") << "<configuration configurationType='project' name='project_${k}'></configuration>"
                } else if (k.startsWith("something")) {
                    new File(f, "${k}.xml") << "<configuration name='standard_${k}'></configuration>"
                }
        }
        testFolder3.setReadable(false)
        testFolder4.setReadable(true, true)
    }

    @Test
    void testLoadInvalidConfigurationDirectories() {
        testFolder3.setReadable(false)
        testFolder4.setReadable(true)

        try {
            ConfigurationFactory.initialize([testFolder3, testFolder4])
        } catch (ConfigurationLoaderException e) {
            assert e.message == "Cannot access (read and execute) configuration directory '${testFolder3}'"
        }
    }

    @Test
    void testLoadValidConfigurationDirectories() {
        // Load context from valid directories and see, if the step fails.
        ConfigurationFactory.initialize([testFolder1, testFolder2])

        assert ConfigurationFactory.getInstance().getAvailableProjectConfigurations().size() == 6
        assert ConfigurationFactory.getInstance().getAvailableConfigurationsOfType(Configuration.ConfigurationType.OTHER).size() == 2
    }

    private NodeChild asNodeChild(String text) {
        return (NodeChild) new XmlSlurper().parseText(text)
    }

    @Test
    void testAsNodeChild() {
        assert asNodeChild("<atag></atag>") instanceof NodeChild
    }

    private NodeChild getValidToolResourceSetNodeChild() {
        NodeChild xml = (NodeChild) new XmlSlurper().parseText(
                """
                    <resourcesetsample>
                        <rset size="s" memory="3" cores="2" walltime="00:04" queue="ultrafast" nodeflag="testweise"/>
                        <rset size="m" memory="3g" cores="1" nodes="1" walltime="12"/>
                        <rset size="l" memory="300M" cores="2" nodes="1" walltime="120m"/>
                        <rset size="xl" memory="2T" cores="2" nodes="1" walltime="180h"/>
                    </resourcesetsample>
                """
        )
        return xml
    }

    private NodeChild getToolEntryWithInlineScript() {
        NodeChild xml = (NodeChild) new XmlSlurper().parseText(
                """
                    <tool name='samtoolsIndex' value='samtoolsIndexBamfile.sh' basepath='qcPipeline'>
                        <resourcesets>
                            <rset size="l" memory="1" cores="1" nodes="1" walltime="5"/>
                        </resourcesets>
                        <input type="file" typeof="de.dkfz.roddy.knowledge.files.GenericFileGroup" scriptparameter='FILENAME'/>
                        <output type="file" typeof="de.dkfz.roddy.knowledge.files.GenericFileGroup" scriptparameter='OUTFILENAME'/>
                        <script value='testscript.sh'>
                        <![CDATA[
                          echo 'test'
                          ]]>
                        </script>
                    </tool>
                """
        )
        return xml
    }

    @groovy.transform.CompileStatic(TypeCheckingMode.SKIP)
    private Collection<NodeChild> getNodeChildsOfValidResourceEntries() {
        def resourceSetNodeChild = getValidToolResourceSetNodeChild()
        return resourceSetNodeChild.rset as Collection<NodeChild>
    }

    @Test
    void testReadInlineScript() {
        def toolEntry = new ProcessingToolReader(getToolEntryWithInlineScript(), null).readProcessingTool()
        assert toolEntry.hasInlineScript()
        assert toolEntry.getInlineScript().equals("echo 'test'")
        assert toolEntry.getInlineScriptName().equals("testscript.sh")
    }

    @Test
    @Ignore("The factory does not have that methode anymore. ProcessingToolReader has one, but it returns a NullPointerException in this test.")
    void testParseToolResourceSet() {
        Method parseToolResourceSet = ConfigurationFactory.getDeclaredMethod("parseToolResourceSet", NodeChild, Configuration)
        parseToolResourceSet.setAccessible(true)

        Map<ResourceSetSize, ResourceSet> rsets = [:]

        getNodeChildsOfValidResourceEntries().each { rset ->
            ResourceSet result = (ResourceSet) parseToolResourceSet.invoke(null, rset, null)
            assert result
            assert result.size; //Check if the size has a valid value.

            rsets[result.size] = result
        }

        // Check the example datasets and see, if all necessary values were set and converted properly.

        assert rsets[s].mem instanceof BufferValue && rsets[s].mem.toString() == "3072M"; // Check 3 gigabyte
        assert rsets[s].cores instanceof Integer && rsets[s].cores == 2
        assert rsets[s].walltime instanceof TimeUnit && rsets[s].walltime.toString() == "00:00:00:04"
        assert rsets[s].queue instanceof String && rsets[s].queue == "ultrafast"
//        assert rsets[s].queue instanceof TimeUnit && rsets[s].queue == "testweise"

        assert rsets[m].mem instanceof BufferValue && rsets[m].mem.toString() == "3072M"; // Check 3 gigabyte
        assert rsets[m].cores instanceof Integer && rsets[m].cores == 1
        assert rsets[m].nodes instanceof Integer && rsets[m].nodes == 1
        assert rsets[m].walltime instanceof TimeUnit && rsets[m].walltime.toString() == "00:12:00:00"

        assert rsets[l].mem instanceof BufferValue && rsets[l].mem.toString() == "300M"; // 300 megabyte
        assert rsets[l].cores instanceof Integer && rsets[l].cores == 2
        assert rsets[l].nodes instanceof Integer && rsets[l].nodes == 1
        assert rsets[l].walltime instanceof TimeUnit && rsets[l].walltime.toString() == "00:02:00:00"; // 120m == 2h

        assert rsets[xl].mem instanceof BufferValue && rsets[xl].mem.toString() == "2097152M"; // 300 megabyte
        assert rsets[xl].cores instanceof Integer && rsets[xl].cores == 2
        assert rsets[xl].nodes instanceof Integer && rsets[xl].nodes == 1
        assert rsets[xl].walltime instanceof TimeUnit && rsets[xl].walltime.toString() == "07:12:00:00"; // 180h
    }

    // @Michael: Should the two following derived from pattern map to the same pattern ID if the same class attribute value is used?
    private static final String STR_VALID_DERIVEDFROM_PATTERN = "<filename class='TestFileWithParent' derivedFrom='TestParentFile' pattern='/tmp/onderivedFile'/>"
    private static final String STR_VALID_DERIVEDFROM_PATTERN_WITH_INLINESCRIPT = "<filename class='TestFileWithParent' derivedFrom='TestParentFile' pattern='/tmp/onderivedFile'/>"
    private static final String STR_VALID_DERIVEDFROM_PATTERN_WITH_ARR = "<filename class='TestFileWithParentArr' derivedFrom='TestParentFile[2]' pattern='/tmp/onderivedFile'/>"
    private static final String STR_VALID_ONMETHOD_PATTERN_FQN = "<filename class='TestFileOnMethod' onMethod='de.dkfz.roddy.knowledge.files.BaseFile.getFilename' pattern='/tmp/onMethod'/>"
    private static final String STR_VALID_ONMETHOD_PATTERN_WITH_CLASSNAME = "<filename class='TestFileOnMethod' onMethod='BaseFile.getFilename' pattern='/tmp/onMethodwithClassName'/>"
    private static final String STR_VALID_ONMETHOD_PATTERN_WITH_METHODNAME = "<filename class='TestFileOnMethod' onMethod='getFilename' pattern='/tmp/onMethod'/>"
    private static final String STR_VALID_ONTOOL_PATTERN = "<filename class='TestFileOnTool' onTool='testScript' pattern='/tmp/onTool'/>"
    private static final String STR_VALID_FILESTAGE_PATTERN = "<filename class='FileWithFileStage' fileStage=\"GENERIC\" pattern='/tmp/filestage'/>"
    private static final String STR_VALID_ONSCRIPTPARAMETER_WITH_TOOL_AND_PARAMNAME = "<filename class='TestOnScriptParameter' onScriptParameter='testScript:BAM_INDEX_FILE' pattern='/tmp/onScript' />"
    private static final String STR_VALID_ONSCRIPTPARAMETER_ONLY_PARAMNAME = "<filename class='TestOnScriptParameter' onScriptParameter='BAM_INDEX_FILE2' pattern='/tmp/onScript' />"
    private static final String STR_VALID_ONSCRIPTPARAMETER_ONLY_COLON_AND_PARAMNAME = "<filename class='TestOnScriptParameter' onScriptParameter=':BAM_INDEX_FILE3' pattern='/tmp/onScript' />"
    private static final String STR_VALID_ONSCRIPTPARAMETER_WITH_ANY_AND_PARAMNAME = "<filename class='TestOnScriptParameter' onScriptParameter='[ANY]:BAM_INDEX_FILE4' pattern='/tmp/onScript' />"
    private static final String STR_VALID_ONSCRIPTPARAMETER_FAILED = "<filename class='TestOnScriptParameter' onScriptParameter='[AffY]:BAM_INDEX_FILE5' pattern='/tmp/onScript' />" // Error!!
    private static final String STR_VALID_ONSCRIPTPARAMETER_WITHOUT_CLASS = "<filename onScriptParameter='testScript:BAM_INDEX_FILE6' pattern='/tmp/onScript' />"

    private static NodeChild parseXML(String xml) {
        return (NodeChild) new XmlSlurper().parseText(xml)
    }

    private NodeChild getValidFilenamePatternsNodeChild() {
        return parseXML(
                """
                    <xml>
                        <filenames package='de.dkfz.roddy.knowledge.files' filestagesbase='de.dkfz.roddy.knowledge.files.FileStage'>
                            ${STR_VALID_DERIVEDFROM_PATTERN}
                            ${STR_VALID_DERIVEDFROM_PATTERN_WITH_ARR}
                            ${STR_VALID_ONMETHOD_PATTERN_FQN}
                            ${STR_VALID_ONMETHOD_PATTERN_WITH_CLASSNAME}
                            ${STR_VALID_ONMETHOD_PATTERN_WITH_METHODNAME}
                            ${STR_VALID_ONTOOL_PATTERN}
                            ${STR_VALID_FILESTAGE_PATTERN}
                            ${STR_VALID_ONSCRIPTPARAMETER_WITH_TOOL_AND_PARAMNAME}
                            ${STR_VALID_ONSCRIPTPARAMETER_ONLY_PARAMNAME}
                            ${STR_VALID_ONSCRIPTPARAMETER_ONLY_COLON_AND_PARAMNAME}
                            ${STR_VALID_ONSCRIPTPARAMETER_WITH_ANY_AND_PARAMNAME}
                            ${STR_VALID_ONSCRIPTPARAMETER_FAILED}
                            ${STR_VALID_ONSCRIPTPARAMETER_WITHOUT_CLASS}
                        </filenames>
                    </xml>
                """
        )
    }

    private NodeChild getInvalidFilenamePatternsNodeChild() {
        return parseXML(
                """
                    <xml>
                        <filenames package='de.dkfz.roddy.knowledge.files' filestagesbase='de.dkfz.roddy.knowledge.files.FileStage'>
                            ${STR_VALID_ONMETHOD_PATTERN_FQN}
                            ${STR_VALID_ONMETHOD_PATTERN_WITH_CLASSNAME}
                        </filenames>
                    </xml>
                """
        )
    }

    private NodeChild getParsedFilenamePattern(String filenamePattern) { return parseXML("<filenames filestagesbase='de.dkfz.roddy.knowledge.files.FileStage'>${filenamePattern}</filenames>"); }

    @Test
    void testLoadPatternClassWithNullAndSyntheticClass() {
        Tuple3<Class, Boolean, Integer> loadPatternClassResult = ConfigurationFactory.loadPatternClass((String) null, "ASyntheticTestClass", (Class) null)
        assert loadPatternClassResult != null
        assert ((Class) loadPatternClassResult.x).getName().endsWith("ASyntheticTestClass")
    }

    private Map<String, FilenamePattern> readFilenamePatterns(NodeChild nodeChild) {
        Method m = ConfigurationFactory.class.getDeclaredMethod("readFilenamePatterns", NodeChild)
        m.setAccessible(true)
        return m.invoke(null, nodeChild) as Map<String, FilenamePattern>
    }

    @Test
    void testReadInvalidFilenamePatternDefinition() {
        Map<String, FilenamePattern> filenamePatterns = readFilenamePatterns(getInvalidFilenamePatternsNodeChild())
        assert filenamePatterns.size() == 1
        assert filenamePatterns.values()[0].pattern == "/tmp/onMethodwithClassName"

    }

    @Test
    void testReadValidFilenamePatternDefinition() {
        Map<String, FilenamePattern> filenamePatterns = readFilenamePatterns(getValidFilenamePatternsNodeChild())
        assert filenamePatterns.size() == 9
    }

    @Test
    void testReadFilenamePatternsForDerivedFromPatternType() {
        NodeChild xml = getParsedFilenamePattern(STR_VALID_DERIVEDFROM_PATTERN)
        testReadFilenamePatternsForDerivedFromPatternType_base(xml, "TestFileWithParent")
    }

    @Test
    void testReadFilenamePatternsForDerivedFromPatternType_WithArr() {
        NodeChild xml = getParsedFilenamePattern(STR_VALID_DERIVEDFROM_PATTERN_WITH_ARR)
        DerivedFromFilenamePattern fpattern = testReadFilenamePatternsForDerivedFromPatternType_base(xml, "TestFileWithParentArr")
        assert fpattern.acceptsFileArrays == true
        assert fpattern.enforcedArraySize == 2
    }

    @groovy.transform.CompileStatic(TypeCheckingMode.SKIP)
    DerivedFromFilenamePattern testReadFilenamePatternsForDerivedFromPatternType_base(NodeChild xml, String testfileEnding) {
        DerivedFromFilenamePattern fpattern = ConfigurationFactory.readDerivedFromFilenamePattern("de.dkfz.roddy.knowledge.files.FileStage", xml.filename.getAt(0)) as DerivedFromFilenamePattern
        assert fpattern.filenamePatternDependency == FilenamePatternDependency.derivedFrom
        assert fpattern.derivedFromCls.name.endsWith("TestParentFile")
        assert fpattern.cls.name.endsWith(testfileEnding)
        return fpattern
    }

    @Test
    void testReadFilenamePatternsForOnMethodPatternType() {
        NodeChild xml = getParsedFilenamePattern(STR_VALID_ONMETHOD_PATTERN_FQN)
        testReadFilenamePatternsForOnMethodPatternType_base(xml)
    }

    @Test
    void testReadFilenamePatternsForOnMethodPatternType_WithBaseFile() {
        NodeChild xml = getParsedFilenamePattern(STR_VALID_ONMETHOD_PATTERN_WITH_CLASSNAME)
        testReadFilenamePatternsForOnMethodPatternType_base(xml)
    }

    @Test
    void testReadFilenamePatternsForOnMethodPatternType_WithFileName() {
        NodeChild xml = getParsedFilenamePattern(STR_VALID_ONMETHOD_PATTERN_WITH_METHODNAME)
        testReadFilenamePatternsForOnMethodPatternType_base(xml)
    }

    @groovy.transform.CompileStatic(TypeCheckingMode.SKIP)
    void testReadFilenamePatternsForOnMethodPatternType_base(NodeChild xml) {
        OnMethodFilenamePattern fpattern = ConfigurationFactory.readOnMethodFilenamePattern("de.dkfz.roddy.knowledge.files", xml.filename.getAt(0)) as OnMethodFilenamePattern
        assert fpattern.filenamePatternDependency == FilenamePatternDependency.onMethod
        assert fpattern.cls.name.endsWith("TestFileOnMethod")
    }

    @Test
    @groovy.transform.CompileStatic(TypeCheckingMode.SKIP)
    void testReadFilenamePatternsForOnToolPatternType() {
        NodeChild xml = getParsedFilenamePattern(STR_VALID_ONTOOL_PATTERN)
        OnToolFilenamePattern fpattern = ConfigurationFactory.readOnToolFilenamePattern("de.dkfz.roddy.knowledge.files.FileStage", xml.filename.getAt(0)) as OnToolFilenamePattern
        assert fpattern.filenamePatternDependency == FilenamePatternDependency.onTool
        assert fpattern.cls.name.endsWith("TestFileOnTool")
    }

    @Test
    @groovy.transform.CompileStatic(TypeCheckingMode.SKIP)
    void testReadFilenamePatternsForFileStageBasedPatternType() {
        NodeChild xml = getParsedFilenamePattern(STR_VALID_FILESTAGE_PATTERN)
        FileStageFilenamePattern fpattern = ConfigurationFactory.readFileStageFilenamePattern(null, "de.dkfz.roddy.knowledge.files.FileStage", xml.filename.getAt(0))
        assert fpattern.filenamePatternDependency == FilenamePatternDependency.FileStage
        assert fpattern.cls.name.endsWith("FileWithFileStage")
    }


    @Test
    void testReadFilenamePatternForOnScriptParameterPatternType_Failed() {
        NodeChild xml = getParsedFilenamePattern(STR_VALID_ONSCRIPTPARAMETER_FAILED)
        try {
            testReadFilenamePatternForOnScriptParameterPatternType_base(xml)
        } catch (RuntimeException exp) {
            assert exp.message.equals("Illegal Argument '[..]': [AffY]")
        }
    }


    @Test
    void testReadFilenamePatternForOnScriptParameterPatternType_WithoutClass() {
        NodeChild xml = getParsedFilenamePattern(STR_VALID_ONSCRIPTPARAMETER_WITHOUT_CLASS)
        try {
            testReadFilenamePatternForOnScriptParameterPatternType_base(xml)
        } catch (RuntimeException exp) {
            assert exp.message.startsWith("Missing 'class' attribute for onScriptParameter in:")
        }
    }

    @Test
    void testReadFilenamePatternForOnScriptParameterPatternType_OnlyColonAndParamName() {
        NodeChild xml = getParsedFilenamePattern(STR_VALID_ONSCRIPTPARAMETER_ONLY_COLON_AND_PARAMNAME)
        testReadFilenamePatternForOnScriptParameterPatternType_base(xml)
    }

    @Test
    void testReadFilenamePatternForOnScriptParameterPatternType_OnlyParamName() {
        NodeChild xml = getParsedFilenamePattern(STR_VALID_ONSCRIPTPARAMETER_ONLY_PARAMNAME)
        testReadFilenamePatternForOnScriptParameterPatternType_base(xml)
    }


    @Test
    void testReadFilenamePatternForOnScriptParameterPatternType_WithToolAndParamName() {
        NodeChild xml = getParsedFilenamePattern(STR_VALID_ONSCRIPTPARAMETER_WITH_TOOL_AND_PARAMNAME)
        testReadFilenamePatternForOnScriptParameterPatternType_base(xml)
    }

    @Test
    void testReadFilenamePatternForOnScriptParameterPatternType_WithAnyAndParamName() {
        NodeChild xml = getParsedFilenamePattern(STR_VALID_ONSCRIPTPARAMETER_WITH_ANY_AND_PARAMNAME)
        testReadFilenamePatternForOnScriptParameterPatternType_base(xml)
    }

    @groovy.transform.CompileStatic(TypeCheckingMode.SKIP)
    private void testReadFilenamePatternForOnScriptParameterPatternType_base(NodeChild xml) {
        OnScriptParameterFilenamePattern fpattern = ConfigurationFactory.readOnScriptParameterFilenamePattern("de.dkfz.roddy.knowledge.files.FileStage", xml.filename.getAt(0)) as OnScriptParameterFilenamePattern
        assert fpattern.filenamePatternDependency == FilenamePatternDependency.onScriptParameter
        assert fpattern.pattern == "/tmp/onScript"
        assert fpattern.cls.name.endsWith("TestOnScriptParameter")
    }

    @Test
    @Deprecated
    @Ignore("Should tparm.scriptParameterName not be != null? There seems to be nothing parsed out in de.dkfz.roddy.config.loader.ProcessingToolReader.parseChildFilesForFileGroup")
    void testParseFileGroupWithSubChildren() {
        NodeChild nc = asNodeChild("""
                <output type="filegroup" scriptparameter="APARM">
                    <output type="file" typeof="AFile" scriptparameter="FA"/>
                    <output type="file" typeof="BFile" scriptparameter="FB"/>
                    <output type="file" typeof="CFile" scriptparameter="FC"/>
                </output>
            """)
        ToolFileGroupParameter tparm = new ProcessingToolReader(null, null).parseFileGroup(nc, "testTool")
        assert tparm.files.size() == 3
        assert tparm.files.collect { ToolFileParameter tfp -> tfp.fileClass.simpleName } == ["AFile", "BFile", "CFile"]
        assert tparm.scriptParameterName == "APARM"
        assert tparm.passOptions == ToolFileGroupParameter.PassOptions.parameters
        assert tparm.indexOptions == ToolFileGroupParameter.IndexOptions.numeric
    }

    @Test
    void testCloneFileGroupParameter() {
        NodeChild nc = asNodeChild("""
                <output type="filegroup" scriptparameter="APARM">
                    <output type="file" typeof="AFile" scriptparameter="FA"/>
                    <output type="file" typeof="BFile" scriptparameter="FB"/>
                    <output type="file" typeof="CFile" scriptparameter="FC"/>
                </output>
            """)
        ToolFileGroupParameter tparm = new ProcessingToolReader(null, null).parseFileGroup(nc, "testTool")
        def clone = tparm.clone()
        assert clone
        assert tparm.files.size() == clone.files.size()

    }

    @Test
    void testParseFileGroupWithMinimalDefinition() {
        NodeChild nc = asNodeChild("""<output type="filegroup" fileclass="TestFile" scriptparameter="APARM"/>""")
        ToolFileGroupParameter tparm = new ProcessingToolReader(null, null).parseFileGroup(nc, "testTool")
        assert tparm.isGeneric()
        assert tparm.getGenericClassString() == "de.dkfz.roddy.knowledge.files.GenericFileGroup<de.dkfz.roddy.synthetic.files.TestFile>"
        assert tparm.passOptions == ToolFileGroupParameter.PassOptions.parameters
        assert tparm.indexOptions == ToolFileGroupParameter.IndexOptions.numeric
    }

    @Test(expected = RuntimeException)
    @Ignore("TODO: Should this throw, or return null? Currently, the latter is happening!")
    void testParseFileGroupWithMissingOptions() {
        String xml = """<output type="filegroup" />"""
        NodeChild nc = (NodeChild) new XmlSlurper().parseText(xml)
        new ProcessingToolReader(null, null).parseFileGroup(nc, "testTool")
    }

    @Test
    void testParseFileGroupForInputFileGroupPassasParameters() {
        NodeChild nc = asNodeChild("<input type='filegroup' typeof='GenericFileGroup' fileclass='ASyntheticTestClass' passas='parameters' scriptparameter='APARM' />")
        ToolFileGroupParameter res = new ProcessingToolReader(null, null).parseFileGroup(nc, "EMPTY")
        assert res
        assert res.groupClass == GenericFileGroup.class
        assert res.genericFileClass.name.endsWith("ASyntheticTestClass")
        assert res.passOptions == ToolFileGroupParameter.PassOptions.parameters
    }

    @Test
    void testParseFileGroupForOutputFileGroupPassasParametersAndDefaultFileIndex() {
        NodeChild nc = asNodeChild("<output type='filegroup' typeof='de.dkfz.roddy.knowledge.files.GenericFileGroup' fileclass='ASyntheticClass' passas='parameters' scriptparameter='APARM' />")
        ToolFileGroupParameter res = new ProcessingToolReader(null, null).parseFileGroup(nc, "EMPTY")
        assert res
        assert res.groupClass == GenericFileGroup.class
        assert res.genericFileClass.name.endsWith("ASyntheticClass")
        assert res.passOptions == ToolFileGroupParameter.PassOptions.parameters
        assert res.indexOptions == ToolFileGroupParameter.IndexOptions.numeric
    }


    @Test
    void testParseFileGroupForOutputFileGroupPassasParametersWithStringIndexForFilenames() {
        NodeChild nc = asNodeChild("<output type='filegroup' typeof='de.dkfz.roddy.knowledge.files.GenericFileGroup' fileclass='ASyntheticClass' passas='parameters' indices='strings' scriptparameter='APARM'/>")
        ToolFileGroupParameter res = new ProcessingToolReader(null, null).parseFileGroup(nc, "EMPTY")
        assert res
        assert res.groupClass == GenericFileGroup.class
        assert res.genericFileClass.name.endsWith("ASyntheticClass")
        assert res.passOptions == ToolFileGroupParameter.PassOptions.parameters
        assert res.indexOptions == ToolFileGroupParameter.IndexOptions.strings
    }
}

