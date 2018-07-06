/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.execution.io

import groovy.transform.CompileStatic

/**
 * The basic input table class for data input in table format instead of files.
 * To get the full power of the class, create a custom class in your workflow
 * extends this one and add all the stuff you need.
 */
@CompileStatic
class BaseMetadataTable {

    // A map translating "external" table column ids to "internal" ones.
    // The mapping is via standard values from some xml files.
    protected Map<String, String> internal2CustomIDMap = [:]
    protected Map<String, String> custom2InternalIDMap = [:]

    // The values of mandatoryColumns need to be internal column names ("datasetCol", "fileCol", etc.).
    protected List<String> mandatoryColumns = []

    // A map which links column id and column position.
    // The table uses internal column ids
    protected Map<String, Integer> headerMap = [:]
    protected List<Map<String, String>> records = []

    public static final String INPUT_TABLE_DATASET = "datasetCol"
    public static final String INPUT_TABLE_FILE = "fileCol"

    /**
     *  Copy constructor for subclasses
     */
    BaseMetadataTable(BaseMetadataTable origin) {
        this.internal2CustomIDMap += origin.internal2CustomIDMap
        this.custom2InternalIDMap += origin.custom2InternalIDMap
        this.mandatoryColumns += origin.mandatoryColumns
        this.headerMap += origin.headerMap
        this.records += origin.records
    }

    /**
     * Copy construct copies with subsetByColumn. This won't work with the other constructors.
     */
    protected BaseMetadataTable(BaseMetadataTable origin, List<Map<String, String>> records) {
        this.internal2CustomIDMap += origin.internal2CustomIDMap
        this.custom2InternalIDMap += origin.custom2InternalIDMap
        this.mandatoryColumns += origin.mandatoryColumns
        this.headerMap += origin.headerMap
        this.records += records
    }

    BaseMetadataTable(Map<String, Integer> headerMap, Map<String, String> internal2CustomIDMap, List<String> mandatoryColumns, List<Map<String, String>> records) {
        this.internal2CustomIDMap = internal2CustomIDMap
        this.internal2CustomIDMap.each {
            String key, String val -> custom2InternalIDMap[val] = key
        }
        this.mandatoryColumns = mandatoryColumns
        def collect = records.collect {
            Map<String, String> record ->
                Map<String, String> clone = [:] as Map<String, String>
                for (String header in internal2CustomIDMap.keySet())
                    clone[header] = (String) null
                for (String key in record.keySet()) {
                    String val = record[key]

                    def internalKey = custom2InternalIDMap[key]
                    if (internalKey == null)
                        throw new RuntimeException("The metadata table key '${key}' could not be mapped to an internal key!")

                    clone[internalKey] = val
                }
                return clone
        }

        this.headerMap = headerMap
        this.records = collect as List<Map<String, String>>
    }

    List<String> getMandatoryColumnNames() {
        return new LinkedList<String>(mandatoryColumns)
    }

    List<String> getOptionalColumnNames() {
        return internal2CustomIDMap.keySet() - mandatoryColumns as List<String>
    }

    protected void assertValidRecord(Map<String, String> record) {
        if (!record.keySet().equals(internal2CustomIDMap.keySet())) {
            throw new RuntimeException("Record has columns inconsistent with header: ${record}")
        }
        if (record.size() != headerMap.size()) {
            throw new RuntimeException("Record has the wrong size: ${record}")
        }
        mandatoryColumnNames.each {
            if (!record.containsKey(it) && record.get(it) != "") {
                throw new RuntimeException("Field '${it}' is not set for record: ${record}")
            }
        }
    }

    protected void assertHeader() {
        mandatoryColumnNames.each {
            if (!headerMap.containsKey(internal2CustomIDMap[it])) {
                throw new RuntimeException("Field '${it}' is missing")
            }
        }
    }

    void assertValidTable() {
        assertHeader()
        records.each { assertValidRecord(it) }
    }

    Map<String, Integer> getHeaderMap() {
        return headerMap as Map<String, Integer>
    }

    /**
     * @return The header names in the order defined by the headerMap
     */
    List<String> getHeader() {
        return headerMap.entrySet().collect { it.key } as List<String>
    }

    Map<String, String> getColumnIDMappingMap() {
//        return internal2CustomIDMap.collectEntries { String key, String val -> ["${key}".toString(): val] } as Map<String, String>
        Map<String, String> collect = internal2CustomIDMap.collectEntries {
            String key, String val ->
                def clone = [:]
                clone[key] = val
                clone as Map<String, String>
        } as Map<String, String>
        return collect
    }


    List<Map<String, String>> getTable() {
        return records.collect { it.clone() } as List<Map<String, String>>
    }

    BaseMetadataTable unsafeSubsetByColumn(String columnName, String value) {

        // Look into internal mapping table for headernames to varnames
        return new BaseMetadataTable(
                this,
                records.findAll { Map<String, String> row ->
                    row.get(columnName) == value
                })
    }

    /** Get a subset of rows by unique values in a specified column (internal column namespace).
     *  If mandatory column is selected, it is checked, whether the higher priority column, which
     *  are before the selected columns in the mandatory columns, are unique. This ensures that
     *  for instance not the same file is assigned to two different datasets.
     * @param columnName internal column name (e.g. "datasetCol")
     * @param value
     * @param check
     * @return
     */
    BaseMetadataTable subsetByColumn(String columnName, String value) {
        return unsafeSubsetByColumn(columnName, value).assertUniqueness(columnName)
    }


    /** Given a column names, throw if that column or some higher-priority mandatory column have non-unique values. */
    BaseMetadataTable assertUniqueness(String columnName = null) {
        for(String colToCheck : mandatoryColumnNames) {
            if (listColumn(colToCheck).unique().size() != 1) {
                throw new RuntimeException("For metadata table column(s) '${columnName}' higher-priority column values for '${colToCheck}' are not unique: ${listColumn(colToCheck).unique().sort()}")
            }
            if (colToCheck.equals(columnName)) {
                break
            }
        }
        return this
    }

    BaseMetadataTable subsetByDataset(String datasetId) {
        return subsetByColumn(INPUT_TABLE_DATASET, datasetId)
    }

    BaseMetadataTable unsafeSubsetBy(Map<String, String> columnValueMap) {
        BaseMetadataTable result = columnValueMap.inject(this) { BaseMetadataTable metaDataTable, String columnName, String value ->
            metaDataTable.unsafeSubsetByColumn(columnName, value)
        } as BaseMetadataTable
        return result
    }

    BaseMetadataTable subsetBy(Map<String, String> columnValueMap) {
        return unsafeSubsetBy(columnValueMap).assertUniqueness(columnValueMap.keySet().sort().join(","))
    }

    Integer size() {
        return records.size()
    }

    List<String> listColumn(String columnName) {
        return records.collect { Map<String, String> record ->
            record.get(columnName)
        }
    }

    List<String> listDatasets() {
        return listColumn(INPUT_TABLE_DATASET).unique()
    }

    List<File> listFiles() {
        return listColumn(INPUT_TABLE_FILE).unique().collect { new File(it) }
    }

    List<Map<String, String>> getRecords() {
        return records
    }

}
