/*
 * Copyright (c) 2018 German Cancer Research Center (DKFZ).
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */


package de.dkfz.roddy.core

import de.dkfz.roddy.Constants
import de.dkfz.roddy.RunMode
import de.dkfz.roddy.StringConstants
import de.dkfz.roddy.config.*
import de.dkfz.roddy.execution.io.ExecutionService
import de.dkfz.roddy.execution.io.LocalExecutionService
import de.dkfz.roddy.execution.io.fs.FileSystemAccessProvider
import groovy.transform.CompileStatic
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

/**
 * Created by heinold on 09.11.15.
 */
@CompileStatic
class RuntimeServiceTest {

    @Rule
    final public ContextResource contextResource = new ContextResource()

    final static File baseTestDirectory = new File("/tmp/roddyCentralDirectory")
    final static File outputBaseDirectory = new File(baseTestDirectory, "output/\${projectName}")
    static File inputBaseDirectory
    final static String outputAnalysisBaseDirectory = "\${outputBaseDirectory}/\${dataSet}"
    final static String inputAnalysisBaseDirectory = "\${inputBaseDirectory}/\${dataSet}"

    private static ExecutionContext mockedContext

    @BeforeClass
    static void initialize() {
        ExecutionService.initializeService(LocalExecutionService, RunMode.CLI)
        FileSystemAccessProvider.initializeProvider(true)
    }

    public <T extends Configuration> T setDirectories(T config) {
        inputBaseDirectory= new File(getClass().classLoader.getResource("testpids").path)
        config.configurationValues.add(new ConfigurationValue(ConfigurationConstants.CFG_INPUT_BASE_DIRECTORY, inputBaseDirectory.toString()))
        config.configurationValues.add(new ConfigurationValue(ConfigurationConstants.CFG_INPUT_ANALYSIS_BASE_DIRECTORY, inputAnalysisBaseDirectory))
        config.configurationValues.add(new ConfigurationValue(ConfigurationConstants.CFG_OUTPUT_BASE_DIRECTORY, outputBaseDirectory.toString()))
        config.configurationValues.add(new ConfigurationValue(ConfigurationConstants.CFG_OUTPUT_ANALYSIS_BASE_DIRECTORY, outputAnalysisBaseDirectory))
        config.configurationValues.add(new ConfigurationValue(RuntimeService.RODDY_CENTRAL_EXECUTION_DIRECTORY, baseTestDirectory.toString()))
        return config
    }

    @Before
    void setup() {
        final Configuration mockupConfig = new Configuration(new PreloadedConfiguration(null, Configuration.ConfigurationType.OTHER, "test", "", "", null, "", ResourceSetSize.l, null, null, null, null))
        mockedContext = contextResource.createSimpleContext(RuntimeServiceTest.class, setDirectories(mockupConfig), new RuntimeService())
    }

    @Test
    void testGetCommonExecutionDirectory() throws Exception {
        assert mockedContext.runtimeService.getCommonExecutionDirectory(mockedContext).getAbsolutePath() == baseTestDirectory.toString()
    }

    @Test
    void testGetAnalysedMD5OverviewFile() throws Exception {
        assert mockedContext.runtimeService.getAnalysedMD5OverviewFile(mockedContext).getAbsolutePath() == new File(baseTestDirectory,"zippedAnalysesMD5.txt").toString()
    }

    private String substituteVariables(String value) {
        return value.
                replace("\${${ConfigurationConstants.CFG_OUTPUT_BASE_DIRECTORY}}", outputBaseDirectory.toString()).
                replace("\${${ConfigurationConstants.CFG_INPUT_BASE_DIRECTORY}}", inputBaseDirectory.toString()).
                replace("\${${Constants.PROJECT_NAME}}", mockedContext.project.name).
                replace("\${${Constants.DATASET}}", mockedContext.dataSet.id)
    }

    @Test
    void testGetTestAnalysis() {
        def a = mockedContext.analysis
        assert a.getInputBaseDirectory().toString() ==
                substituteVariables(inputBaseDirectory.toString())
        assert a.getOutputBaseDirectory().toString() ==
                substituteVariables(outputBaseDirectory.toString())
    }

    @Test
    void testGetInputFolderForAnalysis() {
        def analysis = mockedContext.analysis
        assert analysis.runtimeService.getInputBaseDirectory(analysis).toString() ==
                substituteVariables(inputBaseDirectory.toString())
    }

    @Test
    void testGetOutputFolderForAnalysis() {
        def analysis = mockedContext.analysis
        assert analysis.runtimeService.getOutputBaseDirectory(analysis).toString() ==
                substituteVariables(outputBaseDirectory.toString())
    }

    @Test
    void testGetOutputFolderForProject() {
        assert mockedContext.runtimeService.getOutputBaseDirectory(mockedContext).toString() ==
                substituteVariables(outputBaseDirectory.toString())
    }

    @Test
    void testGetInputFolderForDataSetAndAnalysis() {
        assert mockedContext.runtimeService.getInputAnalysisBaseDirectory(mockedContext.dataSet, mockedContext.analysis).toString() ==
                substituteVariables(inputAnalysisBaseDirectory.toString())
    }

    @Test
    void testGetOutputFolderForDataSetAndAnalysis() {
        assert mockedContext.runtimeService.getOutputAnalysisBaseDirectory(mockedContext.dataSet, mockedContext.analysis).toString() ==
                substituteVariables(outputAnalysisBaseDirectory.toString())
    }

    @Test
    void getDefaultJobParameters() throws Exception {
        def context = mockedContext
        def result = new RuntimeService().getDefaultJobParameters(context, "aTool")
        assert result["pid"] == context.dataSet.id
        assert result["PID"] == context.dataSet.id
        assert result["ANALYSIS_DIR"]
    }

    @Test
    @Ignore("Analysis configuration needs to be non-null! Fix!")
    void loadInproperDatasetsWithFilterAndFail() throws Exception {

        Analysis a = mockedContext.analysis

        // Try erroneous cases first.
        assert !a.getRuntimeService().loadDatasetsWithFilter(a, ["ADDD;ADDD"]) // Missing prefix
        assert !a.getRuntimeService().loadDatasetsWithFilter(a, ["s[c:ADDF]"]) // Missing directory
        assert !a.getRuntimeService().loadDatasetsWithFilter(a, ["c:ADDF"]) // Missing directory
        assert !a.getRuntimeService().loadDatasetsWithFilter(a, ["s[c:AE??;AE*]"]) // Missing directory

    }
}