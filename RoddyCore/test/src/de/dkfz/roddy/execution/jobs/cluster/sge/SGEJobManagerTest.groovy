/*
 * Copyright (c) 2017 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.execution.jobs.cluster.sge

import de.dkfz.roddy.config.Configuration
import de.dkfz.roddy.config.PreloadedConfiguration
import de.dkfz.roddy.config.ResourceSet
import de.dkfz.roddy.config.ResourceSetSize
import de.dkfz.roddy.execution.jobs.BatchEuphoriaJobManager
import de.dkfz.roddy.execution.jobs.ProcessingParameters
import de.dkfz.roddy.tools.BufferUnit
import de.dkfz.roddy.tools.BufferValue
import de.dkfz.roddy.tools.TimeUnit
import groovy.transform.CompileStatic
import org.junit.Ignore
import org.junit.Test

/**
 */
@CompileStatic
@Ignore("Test fails. SGE support in development. Test should probably go to BE anyway.")
class SGEJobManagerTest {

    @Test
    void testConvertToolEntryToPBSCommandParameters() {
        ResourceSet rset1 = new ResourceSet(ResourceSetSize.l, new BufferValue(1, BufferUnit.G), 2, 1, new TimeUnit("h"), null, null, null)
        ResourceSet rset2 = new ResourceSet(ResourceSetSize.l, null, null, 1, new TimeUnit("h"), null, null, null)
        ResourceSet rset3 = new ResourceSet(ResourceSetSize.l, new BufferValue(1, BufferUnit.G), 2, null, null as TimeUnit, null, null, null)

        Configuration cfg = new Configuration(new PreloadedConfiguration(null, Configuration.ConfigurationType.OTHER, "test", "", "", null, "", ResourceSetSize.l, null, null, null, null))

        BatchEuphoriaJobManager cFactory = null; //new SGEJobManager(false);
        ProcessingParameters test = (ProcessingParameters) cFactory.convertResourceSet(null, rset1)
        assert test.getProcessingCommandString().trim().equals("-V -l s_data=1024M")

        test = (ProcessingParameters) cFactory.convertResourceSet(null, rset2)
        assert test.getProcessingCommandString().equals(" -V")

        test = (ProcessingParameters) cFactory.convertResourceSet(null, rset3)
        assert test.getProcessingCommandString().equals(" -V -l s_data=1024M")
    }


}
