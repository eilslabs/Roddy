/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.execution.io

import de.dkfz.roddy.Roddy
import de.dkfz.roddy.client.cliclient.CommandLineCall;
import de.dkfz.roddy.config.AnalysisConfiguration
import de.dkfz.roddy.config.Configuration;
import de.dkfz.roddy.config.ConfigurationValue;
import de.dkfz.roddy.config.ProjectConfiguration;
import de.dkfz.roddy.config.RecursiveOverridableMapContainerForConfigurationValues;
import de.dkfz.roddy.core.Analysis;
import de.dkfz.roddy.core.Project
import groovy.transform.CompileStatic
import org.apache.commons.csv.CSVFormat
import org.junit.Ignore
import org.junit.Rule;
import org.junit.Test
import org.junit.rules.ExpectedException

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * Integration test class for
 * Created by heinold on 19.04.16.
 */
@CompileStatic
public class MetadataTableFactoryTest {
    @Test
    @Ignore("Analysis does not have a PreloadedConfiguration. NullPointerException.")
    public void getTable() throws Exception {

        // Create mockup project and analysis.
        Analysis analysis = new Analysis("Test",
                new Project(new ProjectConfiguration(null, "", null, null), null, null, null),
                null,
                new AnalysisConfiguration(null, null, null, null, null, null, "")
        )

        def configuration = analysis.getConfiguration()
        RecursiveOverridableMapContainerForConfigurationValues configurationValues = configuration.getConfigurationValues();

        // Add columns
        configurationValues.add(new ConfigurationValue(configuration, "datasetCol", "PID", "string", "", ["mandatory"] as List<String>));
        configurationValues.add(new ConfigurationValue(configuration, "fileCol", "File", "string", "", ["mandatory"] as List<String>));
        configurationValues.add(new ConfigurationValue(configuration, "rumpleCol", "Rumple", "string", "", [] as List<String>));

        // Add list of used columns
        configurationValues.add(new ConfigurationValue(configuration, "metadataTableColumnIDs", "datasetCol,fileCol,rumpleCol", "string", "", [] as List<String>));

        // Setup Roddy to allow --usemetadatatable
        def field = Roddy.getDeclaredField("commandLineCall")
        field.setAccessible(true);
        field.set(null, new CommandLineCall(["--usemetadatatable=" + BaseMetadataTableTest.getResourceFile(BaseMetadataTableTest.correctTable)] as List<String>));

        // Try and get and let's hope for the good.
        def table = MetadataTableFactory.getTable(analysis);
        assert table != null;
        assert table.size() == 8; // Just a basic check, if things were loaded, BaseMetadataTable.read is tested in a different way.
    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testConvertFormat() throws Exception {
        assert MetadataTableFactory.convertFormat(null) == CSVFormat.TDF
        assert MetadataTableFactory.convertFormat("") == CSVFormat.TDF
        assert MetadataTableFactory.convertFormat("tsv") == CSVFormat.TDF
        assert MetadataTableFactory.convertFormat("excel") == CSVFormat.EXCEL
        assert MetadataTableFactory.convertFormat("csv") == CSVFormat.RFC4180

        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("Value 'WONTWORK' is not a valid format for file based metadata tables. Use 'tsv', 'csv' or 'excel' (case-insensitive)!")
        MetadataTableFactory.convertFormat("WONTWORK")
    }
}