/*
 * Copyright (c) 2018 German Cancer Research Center (DKFZ).
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */
package de.dkfz.roddy.knowledge.metadata

import de.dkfz.roddy.Roddy
import de.dkfz.roddy.StringConstants
import de.dkfz.roddy.client.RoddyStartupOptions
import de.dkfz.roddy.config.ConfigurationValue
import de.dkfz.roddy.core.Analysis
import de.dkfz.roddy.execution.io.BaseMetadataTable
import de.dkfz.roddy.execution.io.LocalExecutionService
import de.dkfz.roddy.tools.LoggerWrapper
import de.dkfz.roddy.tools.RoddyConversionHelperMethods

class MetadataStoreFactory<T extends BaseMetadataTable> {

    private static final LoggerWrapper logger = LoggerWrapper.getLogger(LocalExecutionService.class.name)

    private MetadataStoreFactory() {}

    /**
     * This method constructs the Metadata table valid for the current Roddy execution!
     * It will lookup implementation.
     */
    private static T getMetadataTableFile(Analysis analysis) {
        String[] split = Roddy.getCommandLineCall().getOptionValue(RoddyStartupOptions.usemetadatatable).split(StringConstants.SPLIT_COMMA)
        String file = split[0]
        String format = split.length == 2 && !RoddyConversionHelperMethods.isNullOrEmpty(split[1]) ? split[1] : null

        def missingColValues = []
        def mandatoryColumns = []
        def cvalues = analysis.getConfiguration().getConfigurationValues()
        Map<String, String> columnIDMap = cvalues.get("metadataTableColumnIDs").getValue()
                .split(StringConstants.COMMA)
                .collectEntries {
            String colVar ->
                ConfigurationValue colVal = cvalues.get(colVar)
                if (!colVal) {
                    missingColValues << colVar
                }

                if (colVal.hasTag("mandatory")) mandatoryColumns << colVal.id
                return [(colVar.toString()): colVal?.toString()]
        }

        MetadataTableFile<T> tableFile = MetadataTableFile.<T>create(new File(file), format, columnIDMap, mandatoryColumns)
        return tableFile.load()
    }


    static T getTable(Analysis analysis) {
        if (Roddy.isMetadataCLOptionSet()) {
            return this.<T>getMetadataTableFile(analysis)
        } else {
            logger.rare("de.dkfz.roddy.execution.metadata.MetadataStoreFactory.getTable: Building metadata table from filesystem input is not implemented.")
            /**
             * Get the description of the filename pattern matching. E.g. a closure, regular expressions, or similar.
             */
            return null as T
        }
    }

}
