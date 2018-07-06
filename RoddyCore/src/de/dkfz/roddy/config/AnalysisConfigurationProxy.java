/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.config;

import de.dkfz.roddy.config.loader.ConfigurationFactory;
import de.dkfz.roddy.config.loader.ConfigurationLoadError;
import de.dkfz.roddy.config.validation.ConfigurationValidationError;
import de.dkfz.roddy.core.ExecutionContext;
import groovy.util.slurpersupport.NodeChild;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * The AC Proxy is used to enable lazy loading of analyis configuration objects.
 * It encapsulates a real AC object.
 * <p>
 * Created by michael on 20.03.15.
 */
public class AnalysisConfigurationProxy extends AnalysisConfiguration {

    private AnalysisConfiguration analysisConfiguration;
    private AnalysisConfiguration parentConfiguration;
    private final String analysisID;
    private final String analysisCfg;
    private final String usePluginAttribute;
    private final String killswitchesAttribute;
    private NodeChild analysisNode;

    public AnalysisConfigurationProxy(AnalysisConfiguration parentConfiguration, String analysisID, String analysisCfg, String usePluginAttribute, String killswitchesAttribute, NodeChild analysisNode) {
        super(null, null, null, null, null, null, null);
        this.usePluginAttribute = usePluginAttribute;
        this.killswitchesAttribute = killswitchesAttribute;
        this.analysisNode = analysisNode;

//        PreloadedConfiguration preloadedConfiguration, String
//    } workflowClass, Map<String, TestDataOption> testdataOptions, Configuration parentConfiguration, List<String> listOfUsedTools, List<String> usedToolFolders, String cleanupScript) {
//        super(preloadedConfiguration, workflowClass, testdataOptions, parentConfiguration, listOfUsedTools, usedToolFolders, cleanupScript);
        analysisConfiguration = null;
        this.parentConfiguration = parentConfiguration;
        this.analysisID = analysisID;
        this.analysisCfg = analysisCfg;
    }

    private synchronized AnalysisConfiguration checkAnalysisConfig() {
        if (analysisConfiguration == null) {
            analysisConfiguration = ConfigurationFactory.getInstance().lazyLoadAnalysisConfiguration(this);
        }
        return analysisConfiguration;
    }

    public AnalysisConfiguration getAnalysisConfiguration() {
        return analysisConfiguration;
    }

    public void setAnalysisConfiguration(AnalysisConfiguration analysisConfiguration) {
        this.analysisConfiguration = analysisConfiguration;
    }

    public AnalysisConfiguration getParentConfiguration() {
        return parentConfiguration;
    }

    public String getAnalysisID() {
        return analysisID;
    }

    public String getAnalysisCfg() {
        return analysisCfg;
    }

    public String getUsePluginAttribute() {
        return usePluginAttribute;
    }

    public String getKillswitchesAttribute() {
        return killswitchesAttribute;
    }

    public NodeChild getAnalysisNode() {
        return analysisNode;
    }

    @Override
    public String getWorkflowClass() {
        return checkAnalysisConfig().getWorkflowClass();
    }

    @Override
    public ResourceSetSize getResourcesSize() {
        return checkAnalysisConfig().getResourcesSize();
    }

    @Override
    public void setBrawlWorkflow(String brawlWorkflow) {
        checkAnalysisConfig().setBrawlWorkflow(brawlWorkflow);
    }

    @Override
    public String getBrawlWorkflow() {
        return checkAnalysisConfig().getBrawlWorkflow();
    }

    @Override
    public String getBrawlBaseWorkflow() {
        return checkAnalysisConfig().getBrawlBaseWorkflow();
    }

    @Override
    public void setBrawlBaseWorkflow(String brawlBaseWorkflow) {
        checkAnalysisConfig().setBrawlBaseWorkflow(brawlBaseWorkflow);
    }

    @Override
    public List<String> getListOfUsedTools() {
        return checkAnalysisConfig().getListOfUsedTools();
    }

    @Override
    public String getRuntimeServiceClass() {
        return checkAnalysisConfig().getRuntimeServiceClass();
    }

    @Override
    public List<String> getUsedToolFolders() {
        return checkAnalysisConfig().getUsedToolFolders();
    }

    @Override
    public boolean hasCleanupScript() {
        return checkAnalysisConfig().hasCleanupScript();
    }

    @Override
    public String getCleanupScript() {
        return checkAnalysisConfig().getCleanupScript();
    }

    @Override
    public boolean isNative() {
        return checkAnalysisConfig().isNative();
    }

    @Override
    public void setNativeToolID(String id) {
        checkAnalysisConfig().setNativeToolID(id);
    }

    @Override
    public String getNativeToolID() {
        return checkAnalysisConfig().getNativeToolID();
    }

    @Override
    public void setJobManagerFactory(String targetJobManager) {
        checkAnalysisConfig().setJobManagerFactory(targetJobManager);
    }

    @Override
    public String getTargetJobManagerClass() {
        return checkAnalysisConfig().getTargetJobManagerClass();
    }

    @Override
    public PreloadedConfiguration getPreloadedConfiguration() {
        return checkAnalysisConfig().getPreloadedConfiguration();
    }

    @Override
    public List<String> getImportConfigurations() {
        return checkAnalysisConfig().getImportConfigurations();
    }

    @Override
    public ConfigurationType getConfigurationLevel() {
        return checkAnalysisConfig().getConfigurationLevel();
    }

    @Override
    public void removeFilenamePatternsRecursively() {
        checkAnalysisConfig().removeFilenamePatternsRecursively();
    }

    @Override
    public RecursiveOverridableMapContainer<String, FilenamePattern, Configuration> getFilenamePatterns() {
        return checkAnalysisConfig().getFilenamePatterns();
    }

    @Override
    public RecursiveOverridableMapContainer<String, ToolEntry, Configuration> getTools() {
        return checkAnalysisConfig().getTools();
    }

    @Override
    public RecursiveOverridableMapContainer<String, Enumeration, Configuration> getEnumerations() {
        return checkAnalysisConfig().getEnumerations();
    }

    @Override
    public RecursiveOverridableMapContainerForConfigurationValues getConfigurationValues() {
        return checkAnalysisConfig().getConfigurationValues();
    }

    @Override
    public RecursiveOverridableMapContainer<String, ConfigurationValueBundle, Configuration> getConfigurationValueBundles() {
        return checkAnalysisConfig().getConfigurationValueBundles();
    }

    @Override
    public String getName() {
        return checkAnalysisConfig().getName();
    }

    @Override
    public String getID() {
        return checkAnalysisConfig().getID();
    }

    @Override
    public String getDescription() {
        return checkAnalysisConfig().getDescription();
    }

    @Override
    public String getConfiguredClass() {
        return checkAnalysisConfig().getConfiguredClass();
    }

    @Override
    public String getProjectName() {
        return checkAnalysisConfig().getProjectName();
    }

    @Override
    public List<Configuration> getParents() {
        return checkAnalysisConfig().getParents();
    }

    @Override
    public RecursiveOverridableMapContainer getContainer(String id) {
        return checkAnalysisConfig().getContainer(id);
    }

    @Override
    public void setParent(Configuration c) {
        super.setParent(c);
        if (c != null)
            checkAnalysisConfig().setParent(c);
    }

    @Override
    public void addParent(Configuration p) {
        super.addParent(p);
        if (p != null)
            checkAnalysisConfig().addParent(p);
    }

    @Override
    public Map<String, Configuration> getSubConfigurations() {
        return checkAnalysisConfig().getSubConfigurations();
    }

    @Override
    public List<Configuration> getListOfSubConfigurations() {
        return checkAnalysisConfig().getListOfSubConfigurations();
    }

    @Override
    public File getSourceToolPath(String tool) throws ConfigurationError {
        return checkAnalysisConfig().getSourceToolPath(tool);
    }

    @Override
    public File getProcessingToolPath(ExecutionContext context, String tool) throws ConfigurationError {
        return checkAnalysisConfig().getProcessingToolPath(context, tool);
    }

    @Override
    public String getProcessingToolMD5(String tool) throws ConfigurationError {
        return checkAnalysisConfig().getProcessingToolMD5(tool);
    }

    @Override
    public String getSSHExecutionUser() {
        return checkAnalysisConfig().getSSHExecutionUser();
    }

    @Override
    public boolean getShowSSHCalls() {
        return checkAnalysisConfig().getShowSSHCalls();
    }

    @Override
    public String toString() {
        return checkAnalysisConfig().toString();
    }

    @Override
    public void addValidationError(ConfigurationValidationError error) {
        super.addValidationError(error);
    }

    @Override
    public void addLoadError(ConfigurationLoadError error) {
        super.addLoadError(error);
    }

    @Override
    public boolean hasErrors() {
        return checkAnalysisConfig().hasErrors();
    }

    @Override
    public List<ConfigurationLoadError> getListOfLoadErrors() {
        return checkAnalysisConfig().getListOfLoadErrors();
    }

    @Override
    public boolean isInvalid() {
        return checkAnalysisConfig().isInvalid();
    }
}
