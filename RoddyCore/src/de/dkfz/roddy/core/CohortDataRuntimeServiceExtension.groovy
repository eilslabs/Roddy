/*
 * Copyright (c) 2018 German Cancer Research Center (DKFZ).
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.core

import de.dkfz.roddy.Constants
import de.dkfz.roddy.StringConstants
import de.dkfz.roddy.tools.LoggerWrapper
import de.dkfz.roddy.tools.Tuple2
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode

/**
 * Helper class for the built int runtime service to work with (super)cohorts.
 */
@CompileStatic
class CohortDataRuntimeServiceExtension {

    private static LoggerWrapper logger = LoggerWrapper.getLogger(CohortDataRuntimeServiceExtension.class.getSimpleName())

    /**
     * The runtime service object to which this object belongs.
     */
    private RuntimeService runtimeService

    CohortDataRuntimeServiceExtension(RuntimeService runtimeService) {
        this.runtimeService = runtimeService
    }

    boolean validateCohortDataSetLoadingString(String s) {
        String PID = '[\\w*?_-]+'
        String C_IDENTIFIER = "[:][\\w_-]+"
        String SC_IDENTIFIER = "(${C_IDENTIFIER}[:]){0,1}"

        // First part PID, followed by 0 to n ;PID
        String COHORT = "c(${C_IDENTIFIER}){0,1}[:]${PID}(;${PID}){0,}"

        // First part COHORT, followed by 0 to n |COHORT
        def regex = "s${SC_IDENTIFIER}\\[${COHORT}(\\|$COHORT){0,}\\]"

        return s ==~ regex
    }

    public static final String SPLIT_PIPE = "[|]"

    LinkedHashMap<String, List<String>> transformCohortsString(String requestedCohorts) {
        LinkedHashMap<String, List<String>> cohorts = [:]
        for (String split in requestedCohorts.split(SPLIT_PIPE)) {
            Tuple2<String, List<String>> cohort = matchCohort(split)
            cohorts[cohort.x] = cohort.y
        }
        return cohorts
    }


    @CompileStatic(TypeCheckingMode.SKIP)
    Tuple2<String, LinkedHashMap<String, List<String>>> matchSuperCohort(String s) {
        def matchWithIdentifier = s =~ /s:(.+):\[(.+)\]/
        def matchWithoutIdentifier = s =~ /s\[(.+)\]/

        String superCohortID
        LinkedHashMap<String, List<String>> cohorts

        if (matchWithIdentifier) {
            cohorts = transformCohortsString(matchWithIdentifier[0][2])
            superCohortID = matchWithIdentifier[0][1]
        } else if (matchWithoutIdentifier) {
            cohorts = transformCohortsString(matchWithoutIdentifier[0][1])
            superCohortID = cohorts.keySet().join("_")
        } else {
//        throw "Not found"
        }
        return new Tuple2<String, LinkedHashMap<String, List<String>>>(superCohortID, cohorts)
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    Tuple2<String, List<String>> matchCohort(String s) {
        def withName = s =~ /c:(.+):(.+)/
        def withoutName = s =~ /c:(.+)/
        LinkedHashMap<String, List<String>> result = [:]
        if (withName) {
            return new Tuple2(withName[0][1], withName[0][2].split(StringConstants.SPLIT_SEMICOLON) as List<String>)
        } else if (withoutName) {
            def list = withoutName[0][1].split(StringConstants.SPLIT_SEMICOLON) as List<String>
            return new Tuple2(list.join("_"), list)
        } else {
        }
        return result
    }

    /**
     * In difference to the normal behaviour, the method / workflow loads with supercohorts and cohorts thus using a different
     * dataset id format on the command line:
     * run p@a s[c:DS0;DS1;...;DSn|c:DS2;...]
     *
     * comma separation stays
     * a cohort must be marked with c: in front of it. That is just to make the call clear and has no actual use!
     * entries in a cohort are separated by semicolon!
     * Remark, that order matters! The first entry of a cohor is the major cohort dataset which will e.g. be used for file
     * output, if applicable.
     *
     * TODO Identify cases, where the matching mechanism should fail! Like e.g. when a dataset is missing but requested.
     * - Using wildcards? At least one dataset should be matched for each wildcard entry.
     * - Using no wildcards? There must be one dataset match.
     *
     * @param analysis
     * @param pidFilters
     * @param suppressInfo
     * @return
     */
    List<DataSet> loadCohortDatasetsWithFilter(Analysis analysis, List<String> pidFilters) {
        List<DataSet> listOfDataSets = runtimeService.getListOfPossibleDataSets(analysis)

        if (!checkCohortDataSetIdentifiers(pidFilters)) return []

        // Checks are all done, now get the datasets..
        List<SuperCohortDataSet> datasets = pidFilters.collect { String superCohortDescription ->
            Tuple2<String, LinkedHashMap<String, List<String>>> superCohort = matchSuperCohort(superCohortDescription)

            String superCohortID = superCohort.x
            File superCohortOutputDirectory = new File(runtimeService.getOutputBaseDirectory(analysis), superCohortID)

            def cohortDataSets = superCohort.y.collect { String cohortID, List<String> cohortDatasetIdentifier ->
                List<DataSet> dList = collectDataSetsForCohort(cohortDatasetIdentifier as String[], analysis, listOfDataSets)
                dList = dList.sort { a,b -> a.id.compareTo(b.id) }.unique()
                return new CohortDataSet(analysis, cohortID, dList, new File(superCohortOutputDirectory, cohortID))
            }
            return new SuperCohortDataSet(analysis, superCohortID, cohortDataSets, superCohortOutputDirectory)
        }
        return datasets as List<DataSet>
    }

    private boolean checkCohortDataSetIdentifiers(List<String> pidFilters) {
        // First some checks, if the cohort loading string was set properly.
        boolean foundFaulty = false
        for (filter in pidFilters) {
            boolean faulty = !validateCohortDataSetLoadingString(filter)
            if (faulty) {
                logger.severe("The ${Constants.PID} string ${filter} is malformed.")
                foundFaulty = true
            }
        }
        if (foundFaulty) {
            logger.severe("The dataset list you provided contains errors, Roddy will not start jobs.")
            return false
        }
        return true
    }

    private List<DataSet> collectDataSetsForCohort(String[] datasetFilters, Analysis analysis, List<DataSet> listOfDataSets) {
        boolean error = false
        List<DataSet> dList = []

        dList = datasetFilters.collect { String _filter ->
            if (!_filter)
                return
            List<DataSet> res = runtimeService.selectDatasetsFromPattern(analysis, [_filter], listOfDataSets, true)
            if (_filter.contains("*") || _filter.contains("?")) {
                if (!res) {
                    logger.severe("Could not find a match for cohort part: ${_filter}")
                    error = true
                }
            } else if (res.size() < 1) {
                logger.severe("No match for cohort part: ${_filter}")
                error = true
            } else if (res.size() != 1) {
                logger.severe("Only one match is allowed for cohort part: ${_filter}")
                error = true
            }
            return res
        }.flatten() as List<DataSet>
        if (error) return []
        return dList as ArrayList<DataSet>
    }

}
