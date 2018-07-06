/*
 * Copyright (c) 2018 German Cancer Research Center (DKFZ).
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.core

import groovy.transform.CompileStatic

/**
 * A super cohort dataset which contains several cohort datasets.
 *
 * Created by heinold on 06.12.16.
 */
@CompileStatic
class SuperCohortDataSet extends DataSet {
    final List<CohortDataSet> allCohorts = []

    SuperCohortDataSet(Analysis analysis, String id, List<CohortDataSet> allCohortDatasets, File outputDirectory) {
        super(analysis, id, outputDirectory)
        this.allCohorts += allCohortDatasets
    }
}
