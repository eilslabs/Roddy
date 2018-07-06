/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

//package de.dkfz.roddy.client.cliclient
//
//import de.dkfz.roddy.Constants
//import de.dkfz.roddy.Roddy
//import de.dkfz.roddy.client.RoddyStartupModes
//
//import java.nio.file.Paths
//import java.security.Permission
//
///**
// * Created by michael on 15.10.14.
// * Cover all startup modes at least in such a way, that their code at least runs once without exceptions.
// */
//@groovy.transform.CompileStatic
//class RoddyCLIClientTest extends GroovyTestCase {
//    public static final TESTCONFIG = "testProject@test"
//    def USE_TEST_CONFIG = "--useconfig=applicationPropertiesForTests.ini"
//    def WAIT_FOR_JOBS = "--waitforjobs"
//
//    /**
//     * Taken from http://stackoverflow.com/questions/309396/java-how-to-test-methods-that-call-system-exit
//     * Why I choose this? Because there are no further dependencies! A good alternative seems JMockit which introduces
//     * some sort of static method hiding / mocking. This can mask i.e. System.exit.
//     */
//    protected static class ExitException extends SecurityException {
//        public final int status;
//
//        public ExitException(int status) {
//            super("There is no escape!");
//            this.status = status;
//        }
//    }
//
//    private static class NoExitSecurityManager extends SecurityManager {
//        @Override
//        public void checkPermission(Permission perm) {
//            // allow anything.
//        }
//
//        @Override
//        public void checkPermission(Permission perm, Object context) {
//            // allow anything.
//        }
//
//        @Override
//        public void checkExit(int status) {
//            super.checkExit(status);
////            throw new ExitException(status);
//        }
//    }
//
//    @Override
//    protected void setUp() throws Exception {
//        super.setUp();
//        System.setSecurityManager(new NoExitSecurityManager());
//    }
//
//    @Override
//    protected void tearDown() throws Exception {
//        System.setSecurityManager(null); // or save and restore original
//        super.tearDown();
//    }
//
//    void testShowConfigurationPaths() {
//        RoddyCLIClient.showConfigurationPaths();
//    }
//
//    void testShowConfiguration() {
//        Roddy.main([RoddyStartupModes.showconfig.name(), TESTCONFIG, USE_TEST_CONFIG] as String[]);
//    }
//
//    void testValidateConfiguration() {
//        CommandLineCall properCall = new CommandLineCall([RoddyStartupModes.validateconfig.name(), TESTCONFIG] as List<String>);
//        RoddyCLIClient.parseStartupMode(properCall);
//    }
//
//    void testListWorkflows() {
//        CommandLineCall properCall = new CommandLineCall([RoddyStartupModes.listworkflows.name()] as List<String>);
//        CommandLineCall properCallFiltered = new CommandLineCall([RoddyStartupModes.listworkflows.name(), "testProject"] as List<String>);
//        CommandLineCall properCallFilteredShortlist = new CommandLineCall([RoddyStartupModes.listworkflows.name(), "--shortlist"] as List<String>);
//        assert (properCall.startupMode == RoddyStartupModes.listworkflows);
//        RoddyCLIClient.parseStartupMode(properCall);
//        RoddyCLIClient.parseStartupMode(properCallFiltered);
//        RoddyCLIClient.parseStartupMode(properCallFilteredShortlist);
//    }
//
//    void testListDatasets() {
//        try {
//            Roddy.main([RoddyStartupModes.listdatasets.name(), TESTCONFIG, USE_TEST_CONFIG] as String[]);
//        } catch (ExitException ex) {
//            //This one is expected
//        }
//    }
//
//    void testTestrun() {
//
//    }
//
//    void testTestrerun() {
//
//    }
//
//    void testRun() {
//        Roddy.main([RoddyStartupModes.testrun.name(), TESTCONFIG, "A100", USE_TEST_CONFIG, WAIT_FOR_JOBS] as String[]);
//    }
//
//    void testRerun() {
//        Roddy.main([RoddyStartupModes.testrerun.name(), TESTCONFIG, "A100", USE_TEST_CONFIG, WAIT_FOR_JOBS] as String[]);
//    }
//
//    void testCheckWorkflowStatus() {
//        try {
//            Roddy.main([RoddyStartupModes.checkworkflowstatus.name(), TESTCONFIG, USE_TEST_CONFIG] as String[]);
//        } catch (RoddyCLIClientTest.ExitException ex) {
//            //This one is expected
//            assert ex != null;
//        }
//    }
//
//    void testPerformCommandLineSetup() {
//
//    }
//
//    void testAskForPassword() {
//
//    }
//}
