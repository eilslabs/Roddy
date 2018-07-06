/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.config;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * A project configuration extends the standard configuration by adding project specific fields and methods.
 * Each project and subproject has its own set of analyses as those are not  inherited from a parent project.
 */
public class ProjectConfiguration extends Configuration {

    private final Map<String, AnalysisConfiguration> availableAnalysis;
    private String runtimeServiceClass;


    /**
     * For main configurations
     */
    public ProjectConfiguration(PreloadedConfiguration preloadedConfiguration, String runtimeServiceClass, Map<String, AnalysisConfiguration> availableAnalysis, Configuration parentConfig) {
        super(preloadedConfiguration, parentConfig);
        if (availableAnalysis != null)
            this.availableAnalysis = availableAnalysis;
        else
            this.availableAnalysis = new LinkedHashMap<String, AnalysisConfiguration>();
        this.runtimeServiceClass = runtimeServiceClass;
    }

//    /**
//     * For main configurations
//     */
//    public ProjectConfiguration(PreloadedConfiguration preloadedConfiguration, String runtimeServiceClass, Map<String, AnalysisConfiguration> availableAnalysis, Map<String, ConfigurationValue> configurationValues, Map<String, Map<String, ConfigurationValue>> configurationValueBundles, Map<String, ConfigurationValue> basePaths, Map<String, ToolEntry> tools, Map<String, Configuration> subConfigurations, boolean dontOverrideParentSettings) {
//        super(preloadedConfiguration, configurationValues, configurationValueBundles, basePaths, tools, subConfigurations, dontOverrideParentSettings);
//        if (availableAnalysis != null)
//            this.availableAnalysis = availableAnalysis;
//        else
//            this.availableAnalysis = new LinkedHashMap<String, AnalysisConfiguration>();
//        this.runtimeServiceClass = runtimeServiceClass;
//    }

    public List<String> getListOfAnalysisIDs() {
        return new LinkedList<String>(availableAnalysis.keySet());
    }

    public AnalysisConfiguration getAnalysis(String analysisID) {
        return availableAnalysis.get(analysisID);
    }

    public Map<String, AnalysisConfiguration> getAnalyses() {
        return availableAnalysis;
    }

    public String getProjectClass() {
        if (super.getConfiguredClass() != null)
            return super.getConfiguredClass();
        String projectClass = null;
        for (Configuration c : getParents())
            if (c instanceof ProjectConfiguration) {
                projectClass = ((ProjectConfiguration) c).getProjectClass();
                if (projectClass != null)
                    return projectClass;
            }

        throw new RuntimeException("No project class is defined for configuration " + preloadedConfiguration.id);
    }

    /**
     * A project always has exactly one master configuration. All sub projects are considered as child configurations!
     *
     * @return
     */
    public boolean isMasterConfiguration() {
        return preloadedConfiguration.parent == null;
    }

    /**
     * Shows if this is a child project.
     *
     * @return
     */
    public boolean isChildConfiguration() {
        return !isMasterConfiguration();
    }

    /**
     * A runtime service can be derived from a parent configuration instance. Return the first runtime service class name found among the parents.
     *
     * @return
     */
    public String getRuntimeServiceClass() {
        if ((runtimeServiceClass == null || runtimeServiceClass.trim().length() == 0) && getParents().size() > 0) {
            String rsc;
            for (Configuration c : getParents()) {
                if ((c instanceof ProjectConfiguration))
                    rsc = ((ProjectConfiguration) c).getRuntimeServiceClass();
                else if(c instanceof  AnalysisConfiguration)
                    rsc = ((AnalysisConfiguration) c).getRuntimeServiceClass();
                else
                    continue;
                if (rsc != null) return rsc;
            }
        }
        return runtimeServiceClass;
    }

    public void setRuntimeServiceClass(String p) {
        this.runtimeServiceClass = p;
    }
}
