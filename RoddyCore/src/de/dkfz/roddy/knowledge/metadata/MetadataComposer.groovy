/*
 * Copyright (c) 2018 German Cancer Research Center (DKFZ).
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */
package de.dkfz.roddy.knowledge.metadata

import de.dkfz.b080.co.common.COConfig
import de.dkfz.b080.co.files.Sample
import de.dkfz.roddy.Roddy
import de.dkfz.roddy.StringConstants
import de.dkfz.roddy.client.RoddyStartupOptions
import de.dkfz.roddy.config.ConfigurationValue
import de.dkfz.roddy.core.Analysis
import de.dkfz.roddy.core.DataSet
import de.dkfz.roddy.core.ExecutionContext
import de.dkfz.roddy.execution.io.BaseMetadataTable
import de.dkfz.roddy.execution.io.MetadataTableFactory
import de.dkfz.roddy.tools.RoddyConversionHelperMethods

import static de.dkfz.b080.co.files.COConstants.CVALUE_BAMFILE_LIST
import static de.dkfz.b080.co.files.COConstants.CVALUE_SAMPLE_LIST

/**
 * Given a configuration and command line parameters, decide where to take which metadata information from. This class centralizes code that used
 * to be distributed all over.
 */
class MetadataComposer {

    List<MetadataStore> metadataSources = []

    boolean isMetadataCLOptionSet = false


    MetadataComposer(Boolean isMetadataCLOptionSet) {
        this.isMetadataCLOptionSet = isMetadataCLOptionSet
    }

    String

    List<String> getLibraryNames() {
        if (isMetadataCLOptionSet) {
            return runtimeService.extractLibrariesFromMetadataTable(executionContext, name)
        } else {
            return runtimeService.extractLibrariesFromSampleDirectory(path)
        }
    }

    // from BasicCOProjectRuntimeService
    List<Sample> getSamplesForContext(ExecutionContext context) {
        COConfig cfg = new COConfig(context)
        List<Sample> samples
        String extractedFrom
        List<String> samplesPassedInConfig = cfg.getSampleList()

        if (Roddy.isMetadataCLOptionSet()) {
            samples = extractSamplesFromMetadataTable(context)
            extractedFrom = "input table '${getMetadataTable(context)}'"
        } else if (samplesPassedInConfig) {
            logger.postSometimesInfo("Samples were passed as configuration value: ${samplesPassedInConfig}")
            samples = samplesPassedInConfig.collect { String it -> new Sample(context, it) }
            extractedFrom = "${CVALUE_SAMPLE_LIST} configuration value"
        } else if (cfg.fastqFileListIsSet) {
            List<File> fastqFiles = cfg.getFastqList().collect { String f -> new File(f) }
            samples = extractSamplesFromFastqList(fastqFiles, context)
            extractedFrom = "fastq_list configuration value"
        } else if (cfg.getExtractSamplesFromOutputFiles()) {
            samples = extractSamplesFromOutputFiles(context)
            extractedFrom = "output files"
        } else if (cfg.extractSamplesFromBamList) {
            List<File> bamFiles = cfg.getBamList().collect { String f -> new File(f); }
            samples = extractSamplesFromFilenames(bamFiles, context)
            extractedFrom = "${CVALUE_BAMFILE_LIST} configuration value "
        } else {
            samples = extractSamplesFromSampleDirs(context)
            extractedFrom = "subdirectories of input directory '${context.inputDirectory}'"
        }

        // Remove unknown samples
        samples.removeAll { Sample sample ->
            sample.sampleType == Sample.SampleType.UNKNOWN
        }
        if (samples.size() == 0) {
            logger.warning("No valid samples could be extracted from ${extractedFrom} for dataset ${context.getDataSet().getId()}.")
        }
        return samples
    }

    // RuntimeService
    List<DataSet> loadCombinedListOfPossibleDataSets(Analysis analysis) {

        if (Roddy.isMetadataCLOptionSet()) {

            BaseMetadataTable table = MetadataTableFactory.getTable(analysis)
            List<String> _datasets = table.listDatasets()
            String pOut = analysis.getOutputBaseDirectory().getAbsolutePath() + File.separator
            return _datasets.collect { new DataSet(analysis, it, new File(pOut + it), table) }

        } else {

            List<DataSet> lid = loadListOfInputDataSets(analysis)
            List<DataSet> lod = loadListOfOutputDataSets(analysis)

            //Now combine lid and lod.
            Collection<DataSet> additional = lod.findAll {
                DataSet ds -> !lid.find { DataSet inLid -> inLid.getId() == ds.getId() }
            }
            lid += additional.each { DataSet ds -> ds.setAsAvailableInOutputOnly() }
            lid.removeAll { DataSet ds -> ds.getId().startsWith(".roddy") } //Filter out roddy specific files or folders.
            lid.sort { DataSet a, DataSet b -> a.getId().compareTo(b.getId()) }
            logger.postAlwaysInfo("Found ${lid.size()} datasets in the in- and output directories.")
            return lid
        }
    }

    // from MetadataTableFactory
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
    }
}
