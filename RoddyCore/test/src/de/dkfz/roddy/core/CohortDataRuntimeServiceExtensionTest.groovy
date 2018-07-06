package de.dkfz.roddy.core

import de.dkfz.roddy.RunMode
import de.dkfz.roddy.StringConstants
import de.dkfz.roddy.config.AnalysisConfiguration
import de.dkfz.roddy.config.Configuration
import de.dkfz.roddy.config.ConfigurationConstants
import de.dkfz.roddy.config.ConfigurationValue
import de.dkfz.roddy.config.ProjectConfiguration
import de.dkfz.roddy.execution.io.ExecutionService
import de.dkfz.roddy.execution.io.LocalExecutionService
import de.dkfz.roddy.execution.io.fs.FileSystemAccessProvider
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.Test
import spock.lang.Specification

@CompileStatic
class CohortDataRuntimeServiceExtensionTest {

    @ClassRule
    final public static ContextResource contextResource = new ContextResource()

    @Test
    void loadCohortDatasetsWithFilter() {
        ExecutionService.initializeService(LocalExecutionService.class, RunMode.CLI)
        FileSystemAccessProvider.initializeProvider(true)
        RuntimeService rs = new RuntimeService() {
            @Override
            List<DataSet> getListOfPossibleDataSets(Analysis analysis) {
                return (1..10).collect { int i -> new DataSet(analysis, "DS_${i + 1000}", new File("/tmp/somesets/DS_${i + 1000}".toString()))
                }
            }
        }
        def context = contextResource.createSimpleContext(getClass(), new Configuration(null), rs);
        context.configurationValues.add(new ConfigurationValue(ConfigurationConstants.CFG_OUTPUT_BASE_DIRECTORY, "/tmp/sometests"));

        CohortDataRuntimeServiceExtension se = new CohortDataRuntimeServiceExtension(rs)

        // Try good cases
        assert se.loadCohortDatasetsWithFilter(context.analysis, ["s[c:DS_1001]"]).size() == 1
        assert se.loadCohortDatasetsWithFilter(context.analysis, ["s[c:DS_1001;DS_1003]"]).size() == 1
        assert se.loadCohortDatasetsWithFilter(context.analysis, ["s[c:DS_*]"]).size() == 1
        assert se.loadCohortDatasetsWithFilter(context.analysis, ["s[c:*1001]"]).size() == 1

        List<SuperCohortDataSet> result = se.loadCohortDatasetsWithFilter(context.analysis, "s[c:DS_1001;DS_1002|c:DS_1004|c:DS_1005],s[c:DS_1001;DS_1002|c:DS_1003]".split(StringConstants.SPLIT_COMMA) as List<String>) as List<SuperCohortDataSet>
        // Two super cohorts
        assert result.size() == 2

        // Check one result.
        result = se.loadCohortDatasetsWithFilter(context.analysis, ["s:aName:[c:anotherName:DS_1001;DS_1002;DS_1003]"]) as List<SuperCohortDataSet>
        def superCohort = result[0]
        assert superCohort.allCohorts.size() == 1 && superCohort.allCohorts[0] instanceof CohortDataSet
        assert superCohort.allCohorts[0].allCohortDatasets.size() == 3
        assert superCohort.allCohorts[0].allCohortDatasets[0].id == "DS_1001"
        assert superCohort.allCohorts[0].allCohortDatasets[1].id == "DS_1002"
        assert superCohort.allCohorts[0].allCohortDatasets[2].id == "DS_1003"
        assert superCohort.id == "aName"
        assert superCohort.allCohorts[0].id == "anotherName"
    }
}