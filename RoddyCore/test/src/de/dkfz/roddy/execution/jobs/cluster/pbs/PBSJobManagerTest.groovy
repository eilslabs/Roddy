/*
 * Copyright (c) 2017 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.execution.jobs.cluster.pbs

import de.dkfz.roddy.config.ResourceSet
import de.dkfz.roddy.config.ResourceSetSize
import de.dkfz.roddy.execution.io.NoNoExecutionService
import de.dkfz.roddy.execution.jobs.BatchEuphoriaJobManager
import de.dkfz.roddy.execution.jobs.JobManagerOptions
import de.dkfz.roddy.execution.jobs.ProcessingParameters
import de.dkfz.roddy.tools.BufferUnit
import de.dkfz.roddy.tools.BufferValue
import de.dkfz.roddy.tools.TimeUnit
import groovy.transform.CompileStatic
import org.junit.Test

import static org.junit.Assert.assertEquals

/**
 */
@CompileStatic
class PBSJobManagerTest {

    @Test
    void testConvertToolEntryToPBSCommandParameters() {
        ResourceSet rset1 = new ResourceSet(ResourceSetSize.l, new BufferValue(1, BufferUnit.G), 2, 1, new TimeUnit("h"), null, null, null)
        ResourceSet rset2 = new ResourceSet(ResourceSetSize.l, null, null, 1, new TimeUnit("h"), null, null, null)
        ResourceSet rset3 = new ResourceSet(ResourceSetSize.l, new BufferValue(1, BufferUnit.G), 2, null, null as TimeUnit, null, null, null)

        BatchEuphoriaJobManager cFactory = new PBSJobManager(new NoNoExecutionService(), JobManagerOptions.create().build())
        ProcessingParameters test = (ProcessingParameters) cFactory.convertResourceSet(null, rset1)
        assertEquals("-l mem=1024M -l walltime=00:01:00:00 -l nodes=1:ppn=2", test.getProcessingCommandString().trim())

        test = (ProcessingParameters) cFactory.convertResourceSet(null, rset2)
        assertEquals("-l walltime=00:01:00:00 -l nodes=1:ppn=1", test.getProcessingCommandString())

        test = (ProcessingParameters) cFactory.convertResourceSet(null, rset3)
        assertEquals("-l mem=1024M -l nodes=1:ppn=2", test.getProcessingCommandString())
    }
}
