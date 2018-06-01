/*
 * Copyright (c) 2018 German Cancer Research Center (DKFZ).
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */
package de.dkfz.roddy.knowledge.metadata

import de.dkfz.roddy.execution.io.BaseMetadataTable
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser

class MetadataTableFile<T extends BaseMetadataTable> implements MetadataStore<T> {

    File tableFile
    String tableFormat
    List<String> mandatoryColumns
    Map<String, String> columnIDmap

    static MetadataTableFile<T> create(File tableFile, String tableFormat, Map<String, String> columnIDmap,List<String> mandatoryColumns) {
        return new MetadataTableFile<T>(tableFile, tableFormat, columnIDmap, mandatoryColumns)
    }

    private MetadataTableFile(File tableFile, String tableFormat, Map<String, String> columnIDmap, List<String> mandatoryColumns) {
        this.tableFile = tableFile
        this.tableFormat = tableFormat
        this.columnIDmap = columnIDmap
        this.mandatoryColumns = mandatoryColumns
    }

    @Override
    T load() {
        return readTable(tableFile, tableFormat, columnIDmap, mandatoryColumns)
    }

    private static T readTable(Reader instream, String format, Map<String, String> internalToCustomIDMap, List<String> mandatoryColumns) {
        CSVFormat tableFormat = convertFormat(format)
        tableFormat = tableFormat.withCommentMarker('#' as char)
                .withIgnoreEmptyLines()
                .withHeader()
        CSVParser parser = tableFormat.parse(instream)
        def map = parser.headerMap as Map<String, Integer>
        def collect = parser.records.collect { it.toMap() }
        return T.create(map, internalToCustomIDMap, mandatoryColumns, collect)
    }

    private static T readTable(File file, String format, Map<String, String> internalToCustomIDMap, List<String> mandatoryColumns) {
        Reader instream
        try {
            instream = new FileReader(file)
            return readTable(instream, format, internalToCustomIDMap, mandatoryColumns)
        } finally {
            instream?.close()
        }
    }

    private static CSVFormat convertFormat(String format) {
        if (format == null || format == "") format = "tsv"
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
