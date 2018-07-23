/*
 * Copyright (c) 2017 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.config

import de.dkfz.roddy.Constants
import de.dkfz.roddy.config.loader.ConfigurationFactory
import de.dkfz.roddy.core.ExecutionContext
import de.dkfz.roddy.core.ContextResource
import de.dkfz.roddy.knowledge.files.BaseFile
import de.dkfz.roddy.knowledge.files.FileObject
import de.dkfz.roddy.knowledge.files.GenericFile
import de.dkfz.roddy.knowledge.methods.GenericMethod
import de.dkfz.roddy.plugins.LibrariesFactory
import de.dkfz.roddy.plugins.LibrariesFactoryTest
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.After
import org.junit.Test

import static de.dkfz.roddy.Constants.DEFAULT

/**
 * Created by heinold on 07.01.2016.
 */
@groovy.transform.CompileStatic
class FilenamePatternTest {

    @ClassRule
    final public static ContextResource contextResource = new ContextResource()

    public static ExecutionContext mockedContext

    private static Class testClass

    @BeforeClass
    static void setUpMocks() throws Exception {
        //Setup plugins and default configuration folders
        LibrariesFactory.initializeFactory(true)
        LibrariesFactory.getInstance().loadLibraries(LibrariesFactory.buildupPluginQueue(LibrariesFactoryTest.callLoadMapOfAvailablePlugins(), "DefaultPlugin").values() as List)
        ConfigurationFactory.initialize(LibrariesFactory.getInstance().getLoadedPlugins().collect { it -> it.getConfigurationDirectory() })

        final Configuration mockupConfig = new Configuration(new PreloadedConfiguration(null, Configuration.ConfigurationType.OTHER, DEFAULT, "", "", null, "", ResourceSetSize.l, null, null, null, null), ConfigurationFactory.getInstance().getConfiguration(DEFAULT)) {
            @Override
            File getSourceToolPath(String tool) {
                if (tool == "wrapinScript")
                    return super.getSourceToolPath(tool)
                return new File("/tmp/RoddyTests/RoddyTestScript_ExecutionServiceTest.sh")
            }
        }

        mockedContext = contextResource.createSimpleContext(FilenamePatternTest, mockupConfig)

        testClass = LibrariesFactory.getInstance().loadRealOrSyntheticClass("FilenamePatternTest_testFilenamePatternWithSelectionByToolID", BaseFile.class as Class<FileObject>)

        ToolEntry toolEntry = new ToolEntry("RoddyTests", "RoddyTests", "RoddyTestScript_ExecutionServiceTest.sh")
        toolEntry.getOutputParameters(mockupConfig).add(new ToolFileParameter(testClass, null, "TEST", new ToolFileParameterCheckCondition(true)))

        mockupConfig.getTools().add(toolEntry)
    }

    @After
    void tearDown() throws Exception {

    }

    @Test
    void testFilenamePatternWithSelectionByToolID() {
        FilenamePattern fp = new OnToolFilenamePattern(testClass, "RoddyTests", "/tmp/RoddyTests/testFileResult.sh", DEFAULT)
        BaseFile.ConstructionHelperForGenericCreation helper = new BaseFile.ConstructionHelperForGenericCreation(mockedContext, mockedContext.getConfiguration().getTools().getValue("RoddyTests"), "RoddyTests", null, null, new TestFileStageSettings(), null)
        String filename = fp.apply((BaseFile) testClass.newInstance(helper))
        assert filename == "/tmp/RoddyTests/testFileResult.sh"
    }

    @Test
    @Ignore("Empty test")
    void testFilenamePatternWithDerivedParentClass() {
        assert false
    }

    @Test
    @Ignore("Empty test")
    void testFilenamePatternWithSelectionByMethod() {
        assert false
    }

    @Test
    @Ignore("Empty test")
    void testFilenamePatternWithFileStage() {
        assert false
    }

    @Test
    @Ignore("Empty test")
    void testFilenamePatternsForFileGroupWithNumericIndices() {
        assert false
    }

    @Test
    @Ignore("Empty test")
    void testFilenamePatternsForFileGroupWithStringIndices() {
        assert false
    }

    @Test
    @Ignore("Empty test")
    void testComplexFilenamePattern() {
        assert false
    }

    @Test
    @Ignore("Fix. Use true temp dirs. NullPointerException.")
    public void testJobCreationWithFileUsingToolIDForNamePattern() {
        contextResource.createMockupJobManager()

        FilenamePattern fp = new OnToolFilenamePattern(testClass, "RoddyTests", "/tmp/RoddyTests/testFileResult.sh", DEFAULT)
        mockedContext.getConfiguration().getFilenamePatterns().add(fp)
        BaseFile sourceFile = BaseFile.constructSourceFile(GenericFile, new File("/tmp/RoddyTests/output/abcfile"), mockedContext)
        BaseFile result = (BaseFile) GenericMethod.callGenericTool("RoddyTests", sourceFile)
        assert result != null
        assert result.class == testClass
    }



    @Test
    @Ignore("Empty test")
    void testExtractCommand() {
        assert false
    }

    @Test
    @Ignore("Empty test")
    void testExtractCommands() {
        assert false
    }

    @Test
    void testFillFileGroupIndex() {
        String src = 'abc_${fgindex}_def'
        def parentFile = new GenericFile(new BaseFile.ConstructionHelperForManualCreation(mockedContext, null, null, null, null, null, null));
        def file = new GenericFile(new BaseFile.ConstructionHelperForManualCreation(parentFile, null, null, null, null, null, "1", null, null));
        file.hasIndexInFileGroup()
        assert createFilenamePattern().fillFileGroupIndex(src, file) == 'abc_1_def'
    }

    @Test
    @Ignore("Empty test")
    void testFillDirectories() {
        assert false
    }

    @Test
    void testFillConfigurationVariable() {
        String srcFull = 'something_${avalue}_${cvalue,name="anothervalue"}_${cvalue,name="unknown",default="bebe"}'
        ExecutionContext context = createMockupContext()
        FilenamePattern fpattern = createFilenamePattern()
        assert fpattern.fillConfigurationVariables(srcFull, context.configuration) == 'something_abc_abc_bebe'
    }

    @Test
    @Ignore("Infinite loop bug")
    void testFillWithUnsetVariables() {
        String srcFull = 'something_${avalue}_${cvalue,name="anothervalue"}_${cvalue,name="unknown"}'
        ExecutionContext context = createMockupContext()
        FilenamePattern fpattern = createFilenamePattern()
        assert fpattern.fillConfigurationVariables(srcFull, context.configuration) == 'something_abc_abc_${cvalue}'
    }

    @Test
    @Ignore("Infinite loop bug")
    void testWithDataSetID() {
        String srcFull = 'something_${avalue}_${cvalue,name="unknown"}_${pid}_${fileStageID[0]}'
        ExecutionContext context = createMockupContext()
        FilenamePattern fpattern = createFilenamePattern()
        assert fpattern.fillConfigurationVariables(srcFull, context.configuration) == 'something_abc_${cvalue}_${pid}_${fileStageID[0]}'
    }

    private FilenamePattern createFilenamePattern() {
        String srcFull
        def fpattern = new FilenamePattern(LibrariesFactory.getInstance().loadRealOrSyntheticClass("FPTTestClass", "BaseFile"), srcFull, DEFAULT) {

            @Override
            String getID() { return null }

            @Override
            FilenamePatternDependency getFilenamePatternDependency() { return null }

            @Override
            protected BaseFile getSourceFile(BaseFile[] baseFiles) { return null }

        }
        fpattern
    }

    private ExecutionContext createMockupContext() {
        Configuration cfg = new Configuration(null);
        cfg.configurationValues << new ConfigurationValue(cfg, "avalue", "abc")
        cfg.configurationValues << new ConfigurationValue(cfg, "anothervalue", "abc")
        def context = contextResource.createSimpleContext(getClass(), cfg)
        context
    }
}
