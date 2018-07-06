/*
 * Copyright (c) 2017 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.config.converters

import org.junit.Ignore
import org.junit.Test

/**
 * Created by heinold on 30.01.17.
 */
public class YAMLConverterTest {
    @Test
    @Ignore("Fix this test")
    public void convertToXML() throws Exception {
        String yaml = """
            configuration:
            availableAnalyses: {analysis: [{_id: onScriptTest, _configuration: testAnalysis, _useplugin: TestPluginWithJarFile}, {_id: WGS, _configuration: bisulfiteCoreAnalysis, _useplugin: AlignmentAndQCWorkflows}]}
            configurationvalues: {cvalue: [{_name: inputBaseDirectory, _value: $USERHOME/temp/roddyLocalTest/testproject/vbp, _type: path}, {_name: outputBaseDirectory, _value: $USERHOME/temp/roddyLocalTest/testproject/rpp, _type: path}, {_name: INDEX_PREFIX, _value: '${indexPrefix_bwa06_methylCtools_mm10_GRC}', _type: path}, {_name: CLIP_INDEX, _value: $DIR_EXECUTION/analysisTools/qcPipeline/trimmomatic/adapters/tagmentationPlassGroup.fa, _type: path}]}
            _configurationType: project
            _name: coWorkflowsTestProject
            _description: 'A test project for the purity estimation analysis.'
            _imports: coBaseProject
            _usedresourcessize: l
        """

        String xml = """
        <configuration configurationType='project' name='coWorkflowsTestProject'
               description='A test project for the purity estimation analysis.' imports="coBaseProject"
               usedresourcessize="l">
            <availableAnalyses>
                <analysis id='onScriptTest' configuration='testAnalysis' useplugin="TestPluginWithJarFile"/>
                <analysis id='WGS' configuration='bisulfiteCoreAnalysis' useplugin="AlignmentAndQCWorkflows"/>
            </availableAnalyses>
            <configurationvalues>
                <cvalue name='inputBaseDirectory' value='$USERHOME/temp/roddyLocalTest/testproject/vbp' type='path'/>
                <cvalue name='outputBaseDirectory' value='$USERHOME/temp/roddyLocalTest/testproject/rpp' type='path'/>
                <cvalue name="INDEX_PREFIX" value="${indexPrefix_bwa06_methylCtools_mm10_GRC}" type="path"/>
                <cvalue name='CLIP_INDEX' value='$DIR_EXECUTION/analysisTools/qcPipeline/trimmomatic/adapters/tagmentationPlassGroup.fa' type="path"/>
            </configurationvalues>
        </configuration>
        """
    }

}