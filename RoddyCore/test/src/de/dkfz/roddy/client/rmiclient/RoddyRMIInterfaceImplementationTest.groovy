/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.client.rmiclient;

import de.dkfz.roddy.Roddy
import de.dkfz.roddy.RoddyTest
import de.dkfz.roddy.core.ContextResource
import de.dkfz.roddy.execution.io.LocalExecutionHelper
import de.dkfz.roddy.execution.io.ExecutionResult
import de.dkfz.roddy.execution.io.ExecutionService
import de.dkfz.roddy.execution.io.LocalExecutionService
import de.dkfz.roddy.execution.jobs.JobState;
import de.dkfz.roddy.plugins.LibrariesFactory
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Rule;
import org.junit.Test

/**
 * Test class with integration tests for the RMI interface.
 * <p>
 * Created by heinold on 12.09.16.
 */
@CompileStatic
public class RoddyRMIInterfaceImplementationTest {

    @ClassRule
    final public static ContextResource contextResource = new ContextResource()

    public static class FakeExecService extends LocalExecutionService {

        private List<String> executedCommands = [];

        public List<String> getExecutedCommands() {
            return executedCommands
        }

        FakeExecService() {
        }

        @Override
        protected ExecutionResult _execute(String string, boolean waitFor, boolean ignoreErrors, OutputStream outputStream) {
            ExecutionResult list = super._execute(string, waitFor, ignoreErrors, outputStream);
            executedCommands << string;
            return list;
        }
    }

    public static File testSource
    public static File testBase
    public static File testproject
    public static File testConfigDir
    public static File testIni
    public static File testConfig
    public static File testExecCache
    public static boolean runIntegrationTests = true;

    /**
     * TODO The following code should be transformed into a general integration test code for e.g. submisssion etc.
     */
    @BeforeClass
    static void createTestProjectAndConfig() {
        runIntegrationTests = RoddyTest.runIntegrationTests()

        if(!runIntegrationTests) return

        testSource = new File(LibrariesFactory.groovyClassLoader.getResource("exampleProject").file)
        testBase = contextResource.getDirectory(RoddyRMIInterfaceImplementationTest.name, "exampleProject");

        LocalExecutionHelper.executeSingleCommand("mkdir -p ${testBase.parent}; cp -r ${testSource}/* ${testBase}");

        testproject = new File(testBase, "project");

        testConfigDir = new File(testBase, "config");
        testConfig = new File(testConfigDir, "projectsExampleMinimal.xml");
        testIni = new File(testConfigDir, "exampleConfig.ini");
        testExecCache = new File(testproject, ".roddyExecCache.txt")

        def sourceIni = new File(LibrariesFactory.groovyClassLoader.getResource("exampleProject/config/${testIni.name}").file);
        def sourceXML = new File(LibrariesFactory.groovyClassLoader.getResource("exampleProject/config/${testConfig.name}").file)
        def sourceCache = new File(LibrariesFactory.groovyClassLoader.getResource("exampleProject/project/${testExecCache.name}").file)

        testIni.delete()
        testConfig.delete()
        testExecCache.delete()
        testIni << sourceIni.text.replace("#testConfigDir#", testConfigDir.getAbsolutePath()).replace("#execService", FakeExecService.class.name);
        testConfig << sourceXML.text.replace("#IODIR#", testproject.getAbsolutePath());
        testExecCache << sourceCache.text.replace("#IODIR#", testproject.getAbsolutePath());

        Thread.start {
            Roddy.main("rmi example@test 66000 --useconfig=${testIni} --verbositylevel=5".split(" "));
        }
        while (!RoddyRMIServer.isActive()) {
            Thread.sleep(125);
        }
    }

    @AfterClass
    public static void tearDownClass() {
        if(!runIntegrationTests) return
        Roddy.resetMainStarted();
    }

    @Test
    public void testLoadAnalysis() {
        if(!runIntegrationTests) return
        def analysis = new RoddyRMIInterfaceImplementation().loadAnalysis("test");
        assert analysis;
        assert analysis.name == "test";
    }

    @Test
    public void testLoadLongAnalysis() {
        if(!runIntegrationTests) return
        def analysis = new RoddyRMIInterfaceImplementation().loadAnalysis("test::abc::dd");
        assert analysis;
        assert analysis.name == "test";
    }

    @Test
    public void listdatasets() throws Exception {
        if(!runIntegrationTests) return
        RoddyRMIInterfaceImplementation iface = new RoddyRMIInterfaceImplementation();

        def listdatasets = iface.listdatasets("test");

        assert listdatasets != null;
        assert listdatasets.size() == 3;

        // Try a second time, this failed in the past.
        listdatasets = iface.listdatasets("test");

        assert listdatasets != null;
        assert listdatasets.size() == 3;
    }

    @Test
    public void queryExtendedDataSetInfo() throws Exception {
        if(!runIntegrationTests) return
        RoddyRMIInterfaceImplementation iface = new RoddyRMIInterfaceImplementation();

        RoddyRMIInterfaceImplementation.ExtendedDataSetInfoObjectCollection info = iface.queryExtendedDataSetInfo("queryExtendedDataSetInfo", "test");

        assert info.dataset.id == "queryExtendedDataSetInfo";
        assert info.list.size() == 1
        def list = info.list;

        assert list[0].executedJobs[0].jobState == JobState.COMPLETED_SUCCESSFUL
        assert list[0].executedJobs[0].jobId == "14011"
        assert list[0].executedJobs[0].toolId == "testScript"
        assert list[0].executedJobs[1].jobState == JobState.COMPLETED_SUCCESSFUL
        assert list[0].executedJobs[2].jobState == JobState.FAILED
    }

    @Test
    public void testrun() throws Exception {
        if(!runIntegrationTests) return
        RoddyRMIInterfaceImplementation iface = new RoddyRMIInterfaceImplementation();

        def list = iface.run(["run"], "test");
        FakeExecService es = ExecutionService.getInstance() as FakeExecService;
        assert es.getExecutedCommands().findAll { it.startsWith(" pid=run") }
        assert list.size() == 1;
        assert list[0].executedJobs.size() > 0;

    };

    @Test
    public void testrerun() throws Exception {
        if(!runIntegrationTests) return
        RoddyRMIInterfaceImplementation iface = new RoddyRMIInterfaceImplementation();

        iface.run(["rerun"], "test");
        FakeExecService es = ExecutionService.getInstance() as FakeExecService;
        assert es.getExecutedCommands().findAll { it.startsWith(" pid=rerun") }
    };

    @Test
    public void testQueryJobState() throws Exception {
        if(!runIntegrationTests) return
        RoddyRMIInterfaceImplementation iface = new RoddyRMIInterfaceImplementation();
        assert false
    }

    @Test
    public void testReadLocalFile() throws Exception {
        if(!runIntegrationTests) return
        RoddyRMIInterfaceImplementation iface = new RoddyRMIInterfaceImplementation();

        def contents = iface.readLocalFile(testIni.getAbsolutePath())
        assert contents.size() > 0;
        assert testIni.readLines() == contents;
    }

    @Test
    public void testReadRemoteFile() throws Exception {
        if(!runIntegrationTests) return
        RoddyRMIInterfaceImplementation iface = new RoddyRMIInterfaceImplementation();

        def contents = iface.readRemoteFile(testIni.getAbsolutePath())
        assert contents.size() > 0;
        assert testIni.readLines() == contents;
    }

}