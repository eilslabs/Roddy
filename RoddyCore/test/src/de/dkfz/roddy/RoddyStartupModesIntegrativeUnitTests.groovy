/*
 * Copyright (c) 2017 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy

import groovy.transform.CompileStatic
import org.junit.BeforeClass
import org.junit.Test

import java.util.concurrent.Semaphore

/**
 * The aim of this class is to setup a test environment, start Roddy with its various run modes and see
 * if they all are still running.
 *
 * The class is more a heavy integration tests but it is totally necessary!
 *
 * For the beginning, only the most used modes are tested.
 *
 * TODO is there a possibility to leave out this test class in regular test cases? It is quite intense as we will need to startup
 * several compiled Roddy instances. I will restrict it with a semaphore for the beginning.
 *
 * TODO This class was created in the wrong branch, so let's leave it for now until the diff is accepted.
 */
@CompileStatic
public class RoddyStartupModesIntegrativeUnitTests {

    public static final Semaphore maxRoddyTestInstances = new Semaphore(4);

    public def withSemaphore(Closure c) {
        maxRoddyTestInstances.acquire()
        try {
            c()
        } finally {
            maxRoddyTestInstances.release()
        }
    }

    @BeforeClass
    public static final void setupTestEnvironment() {
    }

    @Test
    public void testPrintappconfig() {
        withSemaphore {

        }
    }

    @Test
    public void testPrepareprojectconfig() {
        withSemaphore {

        }
    }

    @Test
    public void testPrintruntimeconfig() {
        withSemaphore {

        }
    }

    @Test
    public void testListDatasets() {
        withSemaphore {

        }
    }

    @Test
    public void testRun() {
        withSemaphore {

        }
    }

    @Test
    public void testRerun() {
        withSemaphore {

        }
    }

    @Test
    public void testTestRun() {
        withSemaphore {

        }
    }

    @Test
    public void testTestRerun() {
        withSemaphore {

        }
    }

    @Test
    public void testCheckworkflowstatus() {
        withSemaphore {

        }
    }

    @Test
    public void testShowconfig() {
        withSemaphore {

        }
    }
}
