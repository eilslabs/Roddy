/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.config.converters

import de.dkfz.roddy.Constants
import de.dkfz.roddy.Roddy
import de.dkfz.roddy.RunMode
import de.dkfz.roddy.config.Configuration
import de.dkfz.roddy.config.ConfigurationConstants
import de.dkfz.roddy.config.ConfigurationValue
import de.dkfz.roddy.core.ExecutionContext
import de.dkfz.roddy.core.ContextResource
import de.dkfz.roddy.execution.io.ExecutionService
import de.dkfz.roddy.execution.io.NoNoExecutionService
import de.dkfz.roddy.execution.io.fs.FileSystemAccessProvider
import org.junit.BeforeClass
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Created by heinold on 30.06.16.
 */
@groovy.transform.CompileStatic
class BashConverterTest {

    @Rule
    public final ContextResource contextResource = new ContextResource()

    public static final String CVAL_TEST_OUTPUT_DIRECTORY = "testOutputDirectory"
    public static final String CVAL_OUTPUT_BASE_DIRECTORY = "outputBaseDirectory"
    public static final String CVAL_OUTPUT_ANALYSIS_BASE_DIRECTORY = "outputAnalysisBaseDirectory"
    public static final String CVAL_TEST_BASHARRAY = "testBashArray"
    public static final String CVAL_TEST_BASHARRAY_QUOTES = "testBashArrayQuotes"
    public static final String CVAL_TEST_INTEGER = "testInteger"
    public static final String CVAL_TEST_FLOAT = "testFloat"
    public static final String CVAL_TEST_DOUBLE = "testDouble"
    public static final String CVAL_TEST_SPACE_QUOTES = "testSpaces"
    public static final String CVAL_TEST_TAB_QUOTES = "testTabs"
    public static final String CVAL_TEST_NEWLINE_QUOTES = "testNewlines"
    public static final String CVAL_TEST_SPACE_NQUOTE_ALREADY_QUOTED = "testQuotedSpaces"
    public static final String CVAL_TEST_EQUALITY_SIGN = "testEqualitySign"

    public static final String sampleBashCode = [
            "#name aConfig",
            "#imports anotherConfig",
            "#description aConfig",
            "#usedresourcessize m",
            "#analysis A,aAnalysis,TestPlugin:current",
            "#analysis B,bAnalysis,TestPlugin:current",
            "#analysis C,aAnalysis,TestPlugin:current",

            "outputBaseDirectory=/data/michael/temp/roddyLocalTest/testproject/rpp",
            "UNZIPTOOL=gunzip",
            'ZIPTOOL_OPTIONS="-c"',
            'sampleDirectory=/data/michael/temp/roddyLocalTest/testproject/vbp/A100/${sample}/${SEQUENCER_PROTOCOL}'
    ].join("\n")

    public static final String sampleXMLCode = [
            '<configuration name=\'aConfig\'',
            "imports='anotherConfig'",
            "description='aConfig'",
            "usedresourcessize='m' >",
            "  <availableAnalyses>",
            "    <analysis id='A' configuration='aAnalysis' useplugin='TestPlugin:current' />",
            "    <analysis id='B' configuration='bAnalysis' useplugin='TestPlugin:current' />",
            "    <analysis id='C' configuration='aAnalysis' useplugin='TestPlugin:current' />",
            "  </availableAnalyses>",
            "  <configurationvalues>",
            "    <cvalue name='outputBaseDirectory' value='/data/michael/temp/roddyLocalTest/testproject/rpp' type='string' />",
            "    <cvalue name='UNZIPTOOL' value='gunzip' type='string' />",
            "    <cvalue name='ZIPTOOL_OPTIONS' value='\"-c\"' type='string' />",
            '    <cvalue name=\'sampleDirectory\' value=\'/data/michael/temp/roddyLocalTest/testproject/vbp/A100/${sample}/${SEQUENCER_PROTOCOL}\' type=\'string\' />',
            "  </configurationvalues>",
            "</configuration>"
    ].join("\n")

    @BeforeClass
    static final void setup() {
        ExecutionService.initializeService(NoNoExecutionService, RunMode.UI)
        FileSystemAccessProvider.initializeProvider(true)
    }

    private Configuration createTestConfiguration() {
        Configuration configuration = new Configuration(null);
        configuration.getConfigurationValues().addAll([
                new ConfigurationValue(configuration, CVAL_OUTPUT_ANALYSIS_BASE_DIRECTORY, '${outputBaseDirectory}/Dideldum', "path"),
                new ConfigurationValue(configuration, CVAL_TEST_OUTPUT_DIRECTORY, "testvalue", "path"),
                new ConfigurationValue(configuration, CVAL_TEST_BASHARRAY, "( a b c d )", "bashArray"),
                new ConfigurationValue(configuration, CVAL_TEST_BASHARRAY_QUOTES, "'( a b c d )'", "bashArray"),
                new ConfigurationValue(configuration, CVAL_TEST_INTEGER, "100", "integer"),
                new ConfigurationValue(configuration, CVAL_TEST_FLOAT, "1.0", "float"),
                new ConfigurationValue(configuration, CVAL_TEST_DOUBLE, "1.0", "double"),
                new ConfigurationValue(configuration, CVAL_TEST_SPACE_QUOTES, "text with spaces"),
                new ConfigurationValue(configuration, CVAL_TEST_TAB_QUOTES, "text\twith\ttabs"),
                new ConfigurationValue(configuration, CVAL_TEST_NEWLINE_QUOTES, "text\nwith\nnewlines"),
                new ConfigurationValue(configuration, CVAL_TEST_SPACE_NQUOTE_ALREADY_QUOTED, "\"text with spaces\""),
                new ConfigurationValue(configuration, CVAL_TEST_EQUALITY_SIGN, "--par1=val1 --par2=val2"),
        ] as List<ConfigurationValue>)
        return configuration
    }

    // Too much of an integration test.
    //    @Test
    //    public void convert() throws Exception {
    //
    //    }

//    @Test
//    public void createNewDocumentStringBuilder() throws Exception {
//
//    }
//
//    @Test
//    public void appendConfigurationValues() throws Exception {
//
//    }
//
//    @Test
//    public void appendConfigurationValueBundles() throws Exception {
//
//    }
//
//    @Test
//    public void appendToolEntries() throws Exception {
//
//    }
//
    @Test
    public void appendDebugVariables() throws Exception {
        Configuration configuration = createTestConfiguration()

        assert new BashConverter().
               appendDebugVariables(configuration).
               toString().
               trim() ==  "declare -x WRAPPED_SCRIPT_DEBUG_OPTIONS=\"-v -x -o pipefail \""

        configuration.configurationValues.put(ConfigurationConstants.DEBUG_OPTIONS_USE_EXTENDED_EXECUTE_OUTPUT, "true", "boolean")
        assert new BashConverter().
               appendDebugVariables(configuration).
               toString().
               trim() == ["declare -x WRAPPED_SCRIPT_DEBUG_OPTIONS=\"-v -x -o pipefail \"",
                          "",
                          "export PS4='+(\${BASH_SOURCE}:\${LINENO}): \${FUNCNAME[0]: +\$ { FUNCNAME[0] }():}'"].join("\n")

        configuration.configurationValues.put(ConfigurationConstants.DEBUG_OPTIONS_USE_EXECUTE_OUTPUT, "false", "boolean")
        configuration.configurationValues.put(ConfigurationConstants.DEBUG_OPTIONS_USE_EXTENDED_EXECUTE_OUTPUT, "false", "boolean")
        assert new BashConverter().
                       appendDebugVariables(configuration).
                       toString().
                       trim() == "declare -x WRAPPED_SCRIPT_DEBUG_OPTIONS=\"-v -o pipefail \""
    }
//
//    @Test
//    public void appendPathVariables() throws Exception {
//
//    }
//
//    @Test
//    public void getConfigurationValuesSortedByDependencies() throws Exception {
//
//    }

    @Test
    void convertConfigurationValueToShellScriptLine() throws Exception {
        def configuration = createTestConfiguration()

        File tmpDir = contextResource.tempFolder.newFile()
        configuration.configurationValues.put(CVAL_OUTPUT_BASE_DIRECTORY, tmpDir.absolutePath, "path")
        Roddy.applicationConfiguration.getOrSetApplicationProperty(Constants.APP_PROPERTY_SCRATCH_BASE_DIRECTORY, tmpDir.absolutePath)

        Map<String, String> listWiAutoQuoting = [
                (CVAL_TEST_OUTPUT_DIRECTORY)           : "declare -x    testOutputDirectory=testvalue",
                (CVAL_TEST_BASHARRAY)                  : "declare -x    testBashArray=\"( a b c d )\"",
                (CVAL_TEST_BASHARRAY_QUOTES)           : "declare -x    testBashArrayQuotes='( a b c d )'",
                (CVAL_TEST_INTEGER)                    : "declare -x -i testInteger=100",
                (CVAL_TEST_FLOAT)                      : "declare -x    testFloat=1.0",
                (CVAL_TEST_DOUBLE)                     : "declare -x    testDouble=1.0",
                // These tests here fail, if you only start this test. Leave them at the end, so we can at least test the other tests.
                (CVAL_OUTPUT_BASE_DIRECTORY)           : "declare -x    ${CVAL_OUTPUT_BASE_DIRECTORY}=$tmpDir".toString(),
                (CVAL_OUTPUT_ANALYSIS_BASE_DIRECTORY)  : "declare -x    ${CVAL_OUTPUT_ANALYSIS_BASE_DIRECTORY}=$tmpDir/Dideldum".toString(),
                (CVAL_TEST_SPACE_QUOTES)               : "declare -x    ${CVAL_TEST_SPACE_QUOTES}=\"text with spaces\"".toString(),
                (CVAL_TEST_TAB_QUOTES)                 : "declare -x    ${CVAL_TEST_TAB_QUOTES}=\"text\twith\ttabs\"".toString(),
                (CVAL_TEST_NEWLINE_QUOTES)             : "declare -x    ${CVAL_TEST_NEWLINE_QUOTES}=\"text\nwith\nnewlines\"".toString(),
                (CVAL_TEST_SPACE_NQUOTE_ALREADY_QUOTED): "declare -x    ${CVAL_TEST_SPACE_NQUOTE_ALREADY_QUOTED}=\"text with spaces\"".toString(),
                (CVAL_TEST_EQUALITY_SIGN)              : "declare -x    ${CVAL_TEST_EQUALITY_SIGN}=\"--par1=val1 --par2=val2\"".toString(),
        ]

        Map<String, String> listWOAutoQuoting = [
                (CVAL_TEST_BASHARRAY)                  : "declare -x    testBashArray=( a b c d )",
        ]

        ExecutionContext context = contextResource.createSimpleContext(BashConverterTest, configuration)

        listWiAutoQuoting.each { String id, String expected ->
            def val = new BashConverter().
                    convertConfigurationValue(configuration.getConfigurationValues()[id], context,
                            true, true).toString()

            assert val.toString() == expected
        }

        // DISABLE auto quoting.
        listWOAutoQuoting.each { String id, String expected ->
            def val = new BashConverter().convertConfigurationValue(configuration.getConfigurationValues()[id], context, true, false).toString()
        }
    }

    @Test
    public void testGetHeaderValue() {
        assert new BashConverter().getHeaderValue(sampleBashCode.readLines(), "description", "") == "aConfig"
    }

    @Test
    public void testGetHeaderValues() {
        List<String> values = new BashConverter().getHeaderValues(sampleBashCode.readLines(), "analysis", [])
        assert values.size() == 3
    }

    @Test
    @Ignore("Fix this test")
    public void testConvertToXML() {
//        def converted = new BashConverter().convertToXML(sampleBashCode)
//        boolean valid = converted == sampleXMLCode
//        def xmlLines = sampleXMLCode.readLines()
//        def convertedLines = converted.readLines()
//        assert convertedLines.size() == xmlLines.size()
//        for (int i = 0; i < convertedLines.size(); i++) {
//            def validated = convertedLines[i] == xmlLines[i]
//            if (!validated) println(convertedLines[i] + "\n" + xmlLines[i])
//            valid &= validated
//        }
//        assert valid
    }
}