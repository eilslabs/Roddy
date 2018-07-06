/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy

import de.dkfz.roddy.client.cliclient.CommandLineCall
import de.dkfz.roddy.client.RoddyStartupModes
import de.dkfz.roddy.config.ResourceSetSize
import de.dkfz.roddy.execution.jobs.cluster.pbs.PBSJobManager
import de.dkfz.roddy.tools.RoddyConversionHelperMethods
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Ignore
import org.junit.Test

import java.lang.reflect.Field
import java.lang.reflect.Modifier

/**
 */
@CompileStatic
class RoddyTest {

    public static final String RUN_INTEGRATIONTESTS = "RUN_INTEGRATIONTESTS"

    static boolean runIntegrationTests() {
        return RoddyConversionHelperMethods.toBoolean(System.getenv(RUN_INTEGRATIONTESTS), false)
    }

    private static File temporarySettingsDirectory = new File(System.getProperty("user.home"), ".RODDY_TEST_SETTINGS_DIRECTORY")

    // Helper method to set a final static field accessible and writable!
    // Taken from: http://stackoverflow.com/questions/2474017/using-reflection-to-change-static-final-file-separatorchar-for-unit-testing/2474242#2474242
    static void resetFinalStaticFieldValue(Class cls, String fieldName, Object newValue) throws Exception {
        Field field = cls.getDeclaredField(fieldName)
        field.setAccessible(true)

        // remove final modifier from field
        Field modifiersField = Field.class.getDeclaredField("modifiers")
        modifiersField.setAccessible(true)
        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL)

        field.set(null, newValue)
    }


    @BeforeClass
    static void setup() {
        // Reset the settings folder in the home directory
        resetFinalStaticFieldValue(Roddy, "SETTINGS_DIRECTORY_NAME", ".RODDY_TEST_SETTINGS_DIRECTORY")
    }

    @AfterClass
    static void tearDown() {
        temporarySettingsDirectory.deleteDir()
    }


    @Test
    void testGetUsedResourceSize() {
        // Nothing in.
        assert Roddy.getUsedResourcesSize() == null

        // Wrongly set => null
        Field f = Roddy.class.getDeclaredField("commandLineCall")
        f.setAccessible(true)
        f.set(null, new CommandLineCall(["testrun", "a@b", "--usedresourcessize=xlff"]))

        assert Roddy.getUsedResourcesSize() == null

        // Right set
        f.set(null, new CommandLineCall(["testrun", "a@b", "--usedresourcessize=l"]))
        assert Roddy.getUsedResourcesSize() == ResourceSetSize.l
    }

    @Ignore
    @Test
    void testInitializeJobManager() {
        Roddy.initializeJobManager(RoddyStartupModes.run)
        assert Roddy.getJobManager() instanceof PBSJobManager
    }

    @Test
    void testGetSettingsDirectory() {
        File f = Roddy.getSettingsDirectory()
        assert f == temporarySettingsDirectory
        assert temporarySettingsDirectory.exists()
    }

    @Test
    void testGetApplicationDirectory() {
        File directory = Roddy.getApplicationDirectory()
        def files = directory.listFiles()
        assert files.find { it.name == "roddy.sh" }
        assert files.find { it.name == "README.md" }
    }

    @Test
    void testGetLogDirectory() {
        File logdir = new File(temporarySettingsDirectory, "logs")
        File lDir = Roddy.getApplicationLogDirectory()
        assert lDir == logdir
        assert lDir.exists()
    }

    @Test
    @Ignore("Fix this!")
    void testGetPropertiesFilePathCascadeWithValidEntries() {

        // Absolute path
        File _temp = File.createTempFile("roddy", "ini")
        // Temp folder:
        File tempFolder = _temp.parentFile
        File iniInTemp = new File(tempFolder, "tempProperties.ini")
        File iniInSettingsDir = new File(Roddy.getSettingsDirectory(), "tempProperties.ini")
        File iniInApplicationDir = new File(Roddy.getApplicationDirectory(), "tempProperties.ini")

        // Reset configured path.
        resetFinalStaticFieldValue(Roddy, "customPropertiesFile", iniInTemp.getAbsolutePath())

        iniInTemp << ""
        iniInSettingsDir << ""
        iniInApplicationDir << ""

        _temp.delete()

        File inTempResult = Roddy.getPropertiesFilePath()
        iniInTemp.delete()

        // Path in .roddy
        File inSettingsResult = Roddy.getPropertiesFilePath()
        iniInSettingsDir.delete()

        // Path in application directory (Hard to test, don't want to touch anything here. Maybe only do, if there is a file present?)
        File inApplicationDirResult = Roddy.getPropertiesFilePath()
        iniInApplicationDir.delete()

        resetFinalStaticFieldValue(Roddy, "customPropertiesFile", null)
        File emptyFile = Roddy.getPropertiesFilePath()

        assert iniInTemp == inTempResult
        assert iniInSettingsDir == inSettingsResult
        assert iniInApplicationDir == inApplicationDirResult
        assert emptyFile == new File(Roddy.getSettingsDirectory(), Constants.APP_PROPERTIES_FILENAME)
    }

    @Test
    void integrationTestInitializeRoddy() {
        if(!runIntegrationTests()) return


        // Test is broken. It is an integration test and needs to run in the new settings folder...
        // Maybe also needs to move to a different class for Roddy integration tests.

        assert false
//        LoggerWrapper.setup();
//
////        Roddy.createInterruptSignalHandler();
//
//        CommandLineCall clc = new CommandLineCall(new LinkedList<>());
//
//        Roddy.performInitialSetup(new String[0], clc.startupMode);
//
//        Roddy.parseAdditionalStartupOptions(clc);
//
//        // TODO: Currently this needs an applicationProperties.ini in ~/.roddy/.
//        Roddy.loadPropertiesFile();
//
//        Roddy.initializeServices(true);
//
//        assert FileSystemAccessProvider.getInstance() != null;
//        assert Roddy.getJobManager() != null;
//        assert BEExecutionService.getInstance() != null;
    }


}
