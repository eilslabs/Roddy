/*
 * Copyright (c) 2018 German Cancer Research Center (DKFZ).
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.core

import de.dkfz.roddy.core.Analysis
import de.dkfz.roddy.core.DataSet
import groovy.transform.CompileStatic

/**
 * Created by heinold on 19.12.16.
 */
@CompileStatic
class CohortDataSet extends DataSet {
    final List<DataSet> allCohortDatasets

    CohortDataSet(Analysis analysis, String id, List<DataSet> allCohortDatasets, File outputDirectory) {
        super(analysis, id, outputDirectory)
        this.allCohortDatasets = allCohortDatasets
    }
}
