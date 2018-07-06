/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.core;

import de.dkfz.roddy.execution.io.BaseMetadataTable;
import de.dkfz.roddy.execution.io.fs.FileSystemAccessProvider;

import java.io.File;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Represents a unique dataset like i.e. a PID or a cohort.
 * A unique dataset belongs to a project. Each DataSet can be associated with one ore more analysesByID. This reference is managed by the analysis.
 * For each dataset a number of runs can exist.
 */
@groovy.transform.CompileStatic
public class DataSet extends InfoObject implements Serializable {

    private String id;

    /**
     * This is the PIDs base folder
     */
    private File outputBaseFolder;

    private Project project;

    private transient final Deque<DataSetListener> listeners = new ConcurrentLinkedDeque<>();

    /**
     * List of all analysesByID to which this DataSet is related.
     */
    private Map<String, Analysis> analysesByID = new HashMap<>();

    /**
     * It is only allowed to have one dummy / query process at a time for each analysis
     */
    private Map<Analysis, AnalysisProcessingInformation> processingInformationDummies = new HashMap<>();
    /**
     * It is only allowed to have one concurrent running process at a time for each analysis
     */
    private Map<Analysis, AnalysisProcessingInformation> processingInformationPlannedOrRunning = new HashMap<>();
    /**
     * This is a list of finished or read out processing info containers. The api objects are stored sorted by their creation / runtime
     */
    private Map<Analysis, List<AnalysisProcessingInformation>> processingInformation = new HashMap<>();

    /**
     * Marks a dataset as being only in the output directory.
     */
    private boolean onlyAvailableInOutputDirectory;

    /**
     * The metadata table instance specific for this dataset.
     */
    private BaseMetadataTable metadataTable;

    public DataSet(Analysis analysis, String id, File outputBaseFolder, BaseMetadataTable tableForDataset) {
        this(analysis, id, outputBaseFolder);
        assert tableForDataset != null;
        this.metadataTable = tableForDataset.subsetByDataset(id);
        assert metadataTable.listDatasets().size() == 1 && metadataTable.listDatasets().get(0).equals(id);
    }

    public DataSet(Analysis analysis, String id, File outputBaseFolder) {

        this.id = id;
        this.outputBaseFolder = outputBaseFolder;
        this.project = analysis.getProject();
        this.analysesByID.put(analysis.getName(), analysis);
    }

    public String getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public BaseMetadataTable getMetadataTable() {
        return metadataTable;
    }

    public void setOutputBaseFolder(File folder) {
        this.outputBaseFolder = folder;
    }

    /**
     * Returns a folder like i.e. results_per_pid/ICGC_PCA0xxx
     *
     * @return
     */
    public File getOutputBaseFolder() {
        return outputBaseFolder;
    }

    public File getInputFolderForAnalysis(Analysis analysis) {
        return analysis.getRuntimeService().getInputAnalysisBaseDirectory(this, analysis);
    }

    public File getOutputFolderForAnalysis(Analysis analysis) {
        return analysis.getRuntimeService().getOutputAnalysisBaseDirectory(this, analysis);
    }

    /**
     * Adds (or overrides) an analysis to the list.
     *
     * @param analysis
     */
    public void updateAnalysis(Analysis analysis) {
        analysesByID.put(analysis.getName(), analysis);
    }

    public Analysis getAnalysis(String id) {
        return analysesByID.get(id);
    }

    public boolean hasAnalysis(String id) {
        return analysesByID.containsKey(id);
    }

    /**
     * Returns a list of known / loaded analyses for this dataset.
     */
    public List<String> getListOfAnalyses() {
        return new LinkedList<>(analysesByID.keySet());
    }

    private List<AnalysisProcessingInformation> getAnalysisContainer(Analysis analysis) {
        if (!processingInformation.containsKey(analysis)) {
            processingInformation.put(analysis, new LinkedList<AnalysisProcessingInformation>());
        }
        return processingInformation.get(analysis);
    }

    public void addProcessingInformation(AnalysisProcessingInformation pi) {
        Analysis analysis = pi.getAnalysis();
        List<AnalysisProcessingInformation> con = getAnalysisContainer(analysis);
        if (con.contains(pi)) //Don't sort in existing objects.
            return;
        con.add(pi);
        Collections.sort(con, new Comparator<AnalysisProcessingInformation>() {
            @Override
            public int compare(AnalysisProcessingInformation o1, AnalysisProcessingInformation o2) {
                return o2.getExecPath().compareTo(o1.getExecPath());
            }
        });
    }

    public boolean hasDummyAnalysisProcessingInformation(Analysis analysis) {
        synchronized (processingInformationDummies) {
            return this.processingInformationDummies.get(analysis) != null;
        }
    }

    public boolean hasPlannedOrRunningAnalysisProcessingInformation(Analysis analysis) {
        synchronized (processingInformationPlannedOrRunning) {
            return this.processingInformationPlannedOrRunning.get(analysis) != null;
        }
    }

    public void removeDummyAnalysisProcessingInformation(Analysis analysis) {
        synchronized (processingInformationDummies) {
            processingInformationDummies.remove(analysis);
        }
    }

    public void setDummyAnalysisProcessingInformation(AnalysisProcessingInformation dummy) {
        Analysis analysis = dummy.getAnalysis();
        synchronized (processingInformationDummies) {
            if (processingInformationDummies.containsKey(analysis)) {

            } else {
                processingInformationDummies.put(analysis, dummy);
            }
        }
    }

    public void setPlannedOrRunningAnalysisProcessingInformation(AnalysisProcessingInformation plannedOrRunning) {
        Analysis analysis = plannedOrRunning.getAnalysis();
        synchronized (processingInformationPlannedOrRunning) {
            if (processingInformationPlannedOrRunning.containsKey(analysis)) {

            } else {
                processingInformationPlannedOrRunning.put(analysis, plannedOrRunning);
            }
        }
    }

    public AnalysisProcessingInformation getDummyAnalysisProcessingInformation(Analysis analysis) {
        return processingInformationDummies.get(analysis);
    }

    public AnalysisProcessingInformation getActiveAnalysisProcessingInformation(Analysis analysis) {
        return processingInformationPlannedOrRunning.get(analysis);
    }

    public List<AnalysisProcessingInformation> getProcessingInformation(Analysis analysis) {
        return new LinkedList<>(getAnalysisContainer(analysis));
    }

    public AnalysisProcessingInformation getLatestValidProcessingInformation(Analysis analysis) {
        List<AnalysisProcessingInformation> information = getProcessingInformation(analysis);
        FileSystemAccessProvider fip = FileSystemAccessProvider.getInstance();
        for (AnalysisProcessingInformation api : information) {
            ExecutionContext detailedProcessingInfo = api.getDetailedProcessingInfo();
            if (api == null || detailedProcessingInfo == null || detailedProcessingInfo.getExecutedJobs().size() == 0 || !fip.checkDirectory(api.getExecPath(), detailedProcessingInfo, false))
                continue;
            return api;
        }
        return null;
    }

    @Override
    public String toString() {
        return id;
    }

    public void setAsAvailableInOutputOnly() {
        onlyAvailableInOutputDirectory = true;
    }

    public boolean isOnlyAvailableInOutputDirectory() {
        return onlyAvailableInOutputDirectory;
    }
}
