/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.execution.io

import de.dkfz.roddy.RunMode
import de.dkfz.roddy.plugins.LibrariesFactory
import groovy.transform.CompileStatic
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

/**
 * Created by heinold on 13.04.16.
 */
@CompileStatic
public class BaseMetadataTableTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none()

    public static final File resourceDir = new File("test/resources");
    public static final String correctTable = "InputTableTest_CorrectTable1.tsv"
    public static final String damagedTable = "InputTableTest_DamagedTable1.tsv"

    public static final String RUMPLE_COL = "rumpleCol"

    public static final Map<String, String> internal2CustomIDMap = [
            "datasetCol": "PID",
            "fileCol"   : "File",
            "rumpleCol" : "Rumple"
    ]

    public static final List<String> mandatoryColumnsTable = [BaseMetadataTable.INPUT_TABLE_DATASET,
                                                              BaseMetadataTable.INPUT_TABLE_FILE] as List<String>

    private BaseMetadataTable readTable(String table) {
        String testFileName = getResourceFile(table)

        BaseMetadataTable inputTable = MetadataTableFactory.readTable(new File(testFileName), "tsv", internal2CustomIDMap, mandatoryColumnsTable);
        return inputTable;
    }

    public static final String getResourceFile(String table) {
        String testFileName = new File("RoddyCore/test/resources", table)
        testFileName
    }

    @Test
    public void testClone() {
        BaseMetadataTable table = readTable(correctTable);
        new BaseMetadataTable(table);
    }

    @Test
    public void testCorrectHeaderToColumnMapping() {
        BaseMetadataTable table = readTable(correctTable);
        assert table.getHeaderMap() == ["PID": 0, "File": 1, "Rumple": 2]
        def map = table.getColumnIDMappingMap()
        assert map == internal2CustomIDMap;
    }

    @Test
    public void testReadTable_correctTable() throws Exception {
        BaseMetadataTable table = readTable(correctTable)
        assert table != null
        assert table.getHeaderMap().size() == internal2CustomIDMap.size()
        assert table.size() == 8
    }

    @Test(expected = Exception)
    public void testReadTable_damagedTable() {
        BaseMetadataTable table = readTable(damagedTable)
        assert table == null
    }

    @Test
    public void testAssertValidTable() throws Exception {
        BaseMetadataTable table = readTable(correctTable)
        table.assertValidTable();
    }

    @Test
    public void testGetHeader() throws Exception {
        BaseMetadataTable table = readTable(correctTable)

        def keys = table.getHeaderMap().keySet()
        assert keys.size() == internal2CustomIDMap.size()
        assert keys.containsAll(["PID", "File"])
    }

    @Test
    public void testGetOptionalColumnNames() {
        BaseMetadataTable
    }

    @Test
    public void testSubsetByColumn() throws Exception {
        BaseMetadataTable table = readTable(correctTable)
        def subtable = table.subsetByColumn(BaseMetadataTable.INPUT_TABLE_DATASET, "b")
        assert subtable.size() == 2
        assert table.subsetByColumn(BaseMetadataTable.INPUT_TABLE_DATASET, "a").size() == 4
        assert table.unsafeSubsetByColumn(RUMPLE_COL, "ambiguous").size() == 8

        thrown.expect(RuntimeException.class)
        thrown.expectMessage("For metadata table column(s) '${RUMPLE_COL}' higher-priority column values for 'datasetCol' are not unique: [")

        table.subsetByColumn(RUMPLE_COL, "ambiguous")
    }

    @Test void testSubsetBy() throws Exception {
        BaseMetadataTable table = readTable(correctTable)

        thrown.expect(RuntimeException.class)
        thrown.expectMessage("For metadata table column(s) '${BaseMetadataTable.INPUT_TABLE_DATASET},${RUMPLE_COL}' higher-priority column values for 'fileCol' are not unique: [")

        table.subsetBy((BaseMetadataTable.INPUT_TABLE_DATASET): "a", (RUMPLE_COL): "ambiguous")
    }

    @Test
    public void testListDatasets() {
        BaseMetadataTable table = readTable(correctTable);
        def result = table.listDatasets();
        assert result == ["a", "b", "c"]
    }

}