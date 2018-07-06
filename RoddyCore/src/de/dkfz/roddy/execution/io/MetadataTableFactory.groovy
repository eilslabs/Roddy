/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.execution.io

import de.dkfz.roddy.Roddy;
import de.dkfz.roddy.StringConstants;
import de.dkfz.roddy.client.RoddyStartupOptions
import de.dkfz.roddy.config.ConfigurationValue
import de.dkfz.roddy.core.Analysis
import de.dkfz.roddy.tools.LoggerWrapper
import de.dkfz.roddy.tools.RoddyConversionHelperMethods
import groovy.transform.CompileStatic
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import sun.reflect.generics.reflectiveObjects.NotImplementedException

/**
 * A factory to construct Roddys metadata table instance.
 * The metadata table instance is per start, so it will be provided and accessible via the Roddy class.
 * <p>
 * Let's see, how this will work out with the GUI.
 * <p>
 * Created by heinold on 14.04.16.
 */
@Deprecated   // See Issue #255.
@CompileStatic
final class MetadataTableFactory {

    private static final LoggerWrapper logger = LoggerWrapper.getLogger(LocalExecutionService.class.name)

    private static BaseMetadataTable _cachedTable;

    private MetadataTableFactory() {
    }

    /**
     * This method constructs the Metadata table valid for the current Roddy execution!
     * It will lookup implementation.
     */
    static BaseMetadataTable getTable(Analysis analysis) {
        if (!Roddy.isMetadataCLOptionSet()) {
            logger.rare("de.dkfz.roddy.execution.io.MetadataTableFactory.getTable: Building metadata table from filesystem input is not implemented.")
            return null
        }

        // Create a metadata table from a file
        if (!_cachedTable) {
            String[] split = Roddy.getCommandLineCall().getOptionValue(RoddyStartupOptions.usemetadatatable).split(StringConstants.SPLIT_COMMA);
            String file = split[0];
            String format = split.length == 2 && !RoddyConversionHelperMethods.isNullOrEmpty(split[1]) ? split[1] : null;

            def missingColValues = []
            def mandatoryColumns = []
            def cvalues = analysis.getConfiguration().getConfigurationValues()
            Map<String, String> columnIDMap = cvalues.get("metadataTableColumnIDs").getValue()
                    .split(StringConstants.COMMA)
                    .collectEntries {
                String colVar ->
                    ConfigurationValue colVal = cvalues.get(colVar);
                    if (!colVal) {
                        missingColValues << colVar;
                    }

                    if (colVal.hasTag("mandatory")) mandatoryColumns << colVal.id;
                    return [(colVar.toString()): colVal?.toString()]
            }

            _cachedTable = readTable(new File(file), format, columnIDMap, mandatoryColumns);
        }
        return _cachedTable;

/**         Leave it for later?
 // Search for Metadata implementations in any plugin
 // If too many were found, select via analysis xml file.
 // If none possible, select metadata table?
 **/
    }

    public static BaseMetadataTable readTable(Reader instream, String format, Map<String, String> internalToCustomIDMap, List<String> mandatoryColumns) {
        CSVFormat tableFormat = convertFormat(format)
        tableFormat = tableFormat.withCommentMarker('#' as char)
                .withIgnoreEmptyLines()
                .withHeader();
        CSVParser parser = tableFormat.parse(instream)
        def map = parser.headerMap as Map<String, Integer>
        def collect = parser.records.collect { it.toMap() }
        def inputTable = new BaseMetadataTable(map, internalToCustomIDMap, mandatoryColumns, collect)
        return inputTable
    }

    public static BaseMetadataTable readTable(File file, String format, Map<String, String> internalToCustomIDMap, List<String> mandatoryColumns) {
        Reader instream
        try {
            instream = new FileReader(file)
            return readTable(instream, format, internalToCustomIDMap, mandatoryColumns)
        } finally {
            instream?.close()
        }
    }

    public static CSVFormat convertFormat(String format) {
        if (format == null || format == "") format = "tsv";
        CSVFormat tableFormat
        switch (format.toLowerCase()) {
            case "tsv":
                tableFormat = CSVFormat.TDF
                break
            case "excel":
                tableFormat = CSVFormat.EXCEL
                break
            case "csv":
                tableFormat = CSVFormat.RFC4180
                break
            default:
                throw new IllegalArgumentException("Value '${format}' is not a valid format for file based metadata tables. Use 'tsv', 'csv' or 'excel' (case-insensitive)!")
        }
        tableFormat
    }
}
