/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.client.fxuiclient.fxdatawrappers;

import RoddyRMIInterfaceImplementation;
import de.dkfz.roddy.core.*;
import FileSystemAccessProvider;
import JobState;
import groovy.transform.CompileStatic;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.util.List;
import de.dkfz.roddy.client.rmiclient.RoddyRMIInterfaceImplementation
import de.dkfz.roddy.execution.jobs.JobState;
import groovy.transform.CompileStatic

/**
 * Encapsulates DataSets (like i.e. PIDs)
 */
@CompileStatic
public class FXDataSetWrapper implements Comparable<FXDataSetWrapper> {
    final String project;
    final String analysis;
    final String longAnalysisId;
    final String id;
    final String folder;
    JobState jobState = JobState.UNKNOWN;
    boolean executable = false;

    private ExtendedDataSetInfoObjectCollection extendedDataSetInfoObjectCollection = null;

    public FXDataSetWrapper(String project, String analysis, String longAnalysisId, String id, String folder) {
        this.project = project;
        this.analysis = analysis;
        this.longAnalysisId = longAnalysisId;
        this.id = id;
        this.folder = folder;
    }

    public String getProjectAnalysisIdentifier() {
        return project + "@" + analysis;
    }

    public String getLastExecutingUser() {
        return extendedDataSetInfoObjectCollection?.list ? extendedDataSetInfoObjectCollection.list[0].getExecutingUser() : "UNKNOWN";
    }

    public String getLastTimestamp() {
        return extendedDataSetInfoObjectCollection?.list ? extendedDataSetInfoObjectCollection.list[0].executionDateHumanReadable : "";
    }


    //    public AnalysisProcessingInformation getDummyProcessingInfo() {
//        return dataSet.getDummyAnalysisProcessingInformation(analysis);
//    }
//
//    public AnalysisProcessingInformation getRunningOrPlannedProcessingInfo() {
//        return dataSet.getActiveAnalysisProcessingInformation(analysis);
//    }
//
//    public List<AnalysisProcessingInformation> getProcessingInfo() {
//        return dataSet.getProcessingInformation(analysis);
//    }

//    public AnalysisProcessingInformation getValidProcessingInfo() {
//        AnalysisProcessingInformation api = getDummyProcessingInfo();
//        if (api == null)
//            api = getRunningOrPlannedProcessingInfo();
//        if (api == null && getProcessingInfo().size() > 0)
//            api = getProcessingInfo().get(0);
//        return api;
//    }

//    public boolean isExecutable() {
//        if(isExecutable == null) {
//            DataSet ds = dataSet;
//            Analysis analysis = getAnalysis();
//            Workflow workflow = analysis.getWorkflow();
//            isExecutable = workflow.checkExecutability(new ExecutionContext(FileSystemAccessProvider.getInstance().callWhoAmI(), analysis, ds, ExecutionContextLevel.QUERY_STATUS, ds.getOutputFolder(analysis), ds.getInputBaseDirectory(analysis), null, -1, true));
//        }
//        return isExecutable;
//    }

    @Override
    public int compareTo(FXDataSetWrapper o) {
        return id.compareTo(o.id);
    }
}
