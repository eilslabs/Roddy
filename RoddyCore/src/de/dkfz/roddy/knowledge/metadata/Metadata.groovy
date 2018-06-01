/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.knowledge.metadata

import de.dkfz.roddy.config.ConfigurationError
import groovy.transform.CompileStatic

/**
 * The basic input table class for data input in table format instead of files.
 * To get the full power of the class, create a custom class in your workflow
 * extends this one and add all the stuff you need.
 */
@CompileStatic
class Metadata<T extends Metadata<T>> {

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

    Metadata() {}

    static T create() {
        return (T) new Metadata<T>()
    }

    /**
     *  Kind of copy constructor for subclasses
     */
    static T create(Metadata<T> origin) {
        return create(origin, origin.records)
    }

    protected static T create(Metadata<T> origin, List<Map<String, String>> records) {
        return create(origin.headerMap, origin.internal2CustomIDMap, origin.mandatoryColumns, records)
    }

    protected static T create(Map<String, Integer> headerMap, Map<String, String> internal2CustomIDMap, List<String> mandatoryColumns, List<Map<String, String>> records) {
        return (T) new Metadata<T>(headerMap, internal2CustomIDMap, mandatoryColumns, records)
    }

    protected Metadata(Map<String, Integer> headerMap, Map<String, String> internal2CustomIDMap, List<String> mandatoryColumns, List<Map<String, String>> records) {
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
                        throw new ConfigurationError("The metadata table key '${key}' could not be mapped to an internal key!", key)

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

    T unsafeSubsetByColumn(String columnName, String value) {
        return create(
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
    T subsetByColumn(String columnName, String value) {
        return unsafeSubsetByColumn(columnName, value).assertUniqueness(columnName)
    }


    /** Given a column names, throw if that column or some higher-priority mandatory column have non-unique values. */
    T assertUniqueness(String columnName = null) {
        for(String colToCheck : mandatoryColumnNames) {
            if (listColumn(colToCheck).unique().size() != 1) {
                throw new RuntimeException("For metadata table column(s) '${columnName}' higher-priority column values for '${colToCheck}' are not unique: ${listColumn(colToCheck).unique().sort()}")
            }
            if (colToCheck.equals(columnName)) {
                break
            }
        }
        return create(this)
    }

    T subsetByDataset(String datasetId) {
        return subsetByColumn(INPUT_TABLE_DATASET, datasetId)
    }

    T unsafeSubsetBy(Map<String, String> columnValueMap) {
        return columnValueMap.inject((T) this) { Object metadata, String columnName, String value ->
            (metadata as T).unsafeSubsetByColumn(columnName, value)
        }
    }

    T subsetBy(Map<String, String> columnValueMap) {
        return unsafeSubsetBy(columnValueMap).assertUniqueness(columnValueMap.keySet().sort().join(","))
    }

    Integer size() {
        return records.size()
    }

    List<String> listColumn(String columnName) {
        return records.collect { Map<String, String> record ->
            if (!record.containsKey(columnName)) {
                throw new RuntimeException("Requested unknown column '$columnName'. Know only: ${record.keySet().join(", ")}")
            }
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
