/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.knowledge.examples


import de.dkfz.roddy.core.ExecutionContext
import de.dkfz.roddy.core.SuperCohortDataSet
import de.dkfz.roddy.core.Workflow
import de.dkfz.roddy.knowledge.files.BaseFile
import de.dkfz.roddy.knowledge.files.Tuple4
import groovy.transform.CompileStatic

/**
 */
@CompileStatic
class SimpleCohortWorkflow extends Workflow {
    @Override
    boolean execute(ExecutionContext context) {
        def supercohort = context.dataSet as SuperCohortDataSet

        for (cohort in supercohort.allCohorts) {
            for (dataset in cohort.allCohortDatasets) {
                println("Cohort dataset ${dataset.id} supposed to be in ${dataset.getInputFolderForAnalysis(context.analysis)}")
            }
        }

        context.configurationValues.put("a", '${b}')
        context.configurationValues.put("b", '${a}')
        context.configurationValues["a"].toString()

        return true
    }
}
