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
import de.dkfz.roddy.core.RuntimeService;
import de.dkfz.roddy.plugins.LibrariesFactory;
import de.dkfz.roddy.plugins.PluginInfo;
import de.dkfz.roddy.tools.RoddyIOHelperMethods;
import org.apache.commons.io.filefilter.WildcardFileFilter;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.util.*;

import static de.dkfz.roddy.StringConstants.SPLIT_COMMA;

/**
 * A configuration stores maps of different types:
 * - configuration values / value bundles
 * - basepaths
 * - tool entries
 * - enumerations
 * - filename patterns
 * <p/>
 * A configuration can import from other configurations (only first configurationType of file)
 * A configuration can inherit from other configurations
 *
 * @author michael
 */
public class Configuration implements ContainerParent<Configuration> {


    /**
     * Several levels of configurations.
     * Do not change the order of this! It is queried and compared several times.
     */
    public enum ConfigurationType {
        /**
         * Unknown / Unset
         */
        UNSET,
        /**
         * Other configurations (i.e. definition of filenames, tools)
         */
        OTHER,
        /**
         * For the definition of workflows.
         */
        ANALYSIS,
        /**
         * For the definition of projects
         */
        PROJECT
    }

    private static final de.dkfz.roddy.tools.LoggerWrapper logger = de.dkfz.roddy.tools.LoggerWrapper.getLogger(Configuration.class.getSimpleName());
    /**
     * The prototype with basic information about this configuration
     */
    protected final PreloadedConfiguration preloadedConfiguration;

    /**
     * A list of parent configuration objects. Order matters! Configurations are stored with
     * increasing priority, so parents[0] has the lowest and parents[n -1] has the highest priority
     */
    private final List<Configuration> parents = new LinkedList<>();

    private final Map<String, Configuration> subConfigurations = new LinkedHashMap<>();

    private List<ConfigurationValidationError> listOfValidationErrors = new LinkedList<>();
    private List<ConfigurationLoadError> listOfLoadErrors = new LinkedList<>();

    private final RecursiveOverridableMapContainerForConfigurationValues configurationValues =
            new RecursiveOverridableMapContainerForConfigurationValues(this, "configurationValues");

    /**
     * Bundles store values with the same name for the same configuration.
     * This can sometimes be necessary. So you do not need a sub configuration for each different set of
     * values.
     */
    private final RecursiveOverridableMapContainer<String, ConfigurationValueBundle, Configuration> configurationValueBundles =
            new RecursiveOverridableMapContainer<>(this, "configurationValueBundles");

    private final RecursiveOverridableMapContainer<String, ToolEntry, Configuration> tools =
            new RecursiveOverridableMapContainer<>(this, "tools");

    private final RecursiveOverridableMapContainer<String, Enumeration, Configuration> enumerations =
            new RecursiveOverridableMapContainer<>(this, "enumerations");

    private RecursiveOverridableMapContainer<String, FilenamePattern, Configuration> filenamePatterns =
            new RecursiveOverridableMapContainer<>(this, "filenamePatterns");

    public Configuration() {
        preloadedConfiguration = null;
    }

    /**
     * Creates a new configuration that can be filled by filling the containers.
     */
    public Configuration(PreloadedConfiguration icc) {
        this.preloadedConfiguration = icc;
    }

    /**
     * For main configurations
     * Read reversely
     * Remember to set the parent config afterwards.
     * With this configuration no dependency tree is created!
     */
    public Configuration(PreloadedConfiguration preloadedConfiguration, Configuration parentConfig) {
        this.preloadedConfiguration = preloadedConfiguration;
        this.addParent(parentConfig);
    }

    /**
     * @param preloadedConfiguration
     * @param parentConfigurations   A list of parent configuration objects.
     *                               Order matters! Configurations are stored with
     *                               increasing priority, so pcs[0] has the lowest
     *                               and pcs[n -1] has the highest priority
     */
    public Configuration(PreloadedConfiguration preloadedConfiguration, List<Configuration> parentConfigurations) {
        this.preloadedConfiguration = preloadedConfiguration;
        for (Configuration parentConfiguration : parentConfigurations) {
            addParent(parentConfiguration);
        }
    }

    /**
     * For main configurations
     */
    public Configuration(PreloadedConfiguration preloadedConfiguration, Map<String, Configuration> subConfigurations) {
        this.preloadedConfiguration = preloadedConfiguration;
        if (subConfigurations != null) {
            this.subConfigurations.putAll(subConfigurations);
        }
    }

    public PreloadedConfiguration getPreloadedConfiguration() {
        return preloadedConfiguration;
    }

    public List<String> getImportConfigurations() {
        if (preloadedConfiguration.imports.trim().length() == 0) return new LinkedList<String>();
        return Arrays.asList(preloadedConfiguration.imports.trim().split(SPLIT_COMMA));
    }

    public ConfigurationType getConfigurationLevel() {
        return preloadedConfiguration.type;
    }

    public void removeFilenamePatternsRecursively() {
        this.filenamePatterns = new RecursiveOverridableMapContainer<>(this, "filenamePatterns");
        for (Configuration parent : parents) {
            parent.removeFilenamePatternsRecursively();
        }
    }

    public RecursiveOverridableMapContainer<String, FilenamePattern, Configuration> getFilenamePatterns() {
        return filenamePatterns;
    }

    public RecursiveOverridableMapContainer<String, ToolEntry, Configuration> getTools() {
        return tools;
    }

    public RecursiveOverridableMapContainer<String, Enumeration, Configuration> getEnumerations() {
        return enumerations;
    }

    public RecursiveOverridableMapContainerForConfigurationValues getConfigurationValues() {
        return configurationValues;
    }

    public RecursiveOverridableMapContainer<String, ConfigurationValueBundle, Configuration> getConfigurationValueBundles() {
        return configurationValueBundles;
    }

    /**
     * Returns the name of this configuration
     *
     * @return
     */
    public String getName() {
        return preloadedConfiguration.name;
    }

    /**
     * Returns the id / path of this configuration like prostate.subproject
     *
     * @return
     */
    @Override
    public String getID() {
        return preloadedConfiguration.id;
    }

    public String getDescription() {
        return preloadedConfiguration.description;
    }

    public String getConfiguredClass() {
        return preloadedConfiguration.className;
    }

    public ResourceSetSize getResourcesSize() {
        if (configurationValues.hasValue(ConfigurationConstants.CFG_USED_RESOURCES_SIZE)) {
            try {
                return ResourceSetSize.valueOf(configurationValues.getValue(ConfigurationConstants.CFG_USED_RESOURCES_SIZE).toString());
            } catch (ConfigurationError e) {
                throw new RuntimeException("Unrecoverable error", e);
            }
        }
        return preloadedConfiguration.usedresourcessize;
    }

    /**
     * If the configuration is a project configuration then the name of the project will be returned.
     * Variants do not alter the project name and use the name of the first project found in their parents.
     * If the project name is not set this returns the configurations simple name (i.e. project, but not qcpipeline.project)
     * TODO: Implement and verify rules: A project can extend a project and a workflow. A variant can extend a project or a variant.
     *
     * @return The projects name or null.
     */
    public String getProjectName() {
        //Search the configuration from which to take the name.
        String projectName = null;
        if (this.getConfigurationLevel() == ConfigurationType.PROJECT) {
            projectName = configurationValues.get("projectName", getName()).toString();
        } else if (this.preloadedConfiguration.type.ordinal() < ConfigurationType.PROJECT.ordinal()) {
            //This is not a project configuration and not a variant.
        } else if (this.preloadedConfiguration.type.ordinal() > ConfigurationType.PROJECT.ordinal()) {
            //Return the parents getProjectName(). This is recursive and should lead to the project configuration.
            String tempName = null;
            for (Configuration parent : parents) {
                String tName = parent.getProjectName();
                if (tName == null) {
                    continue;
                }
                tempName = tName;
                break;
            }

            projectName = tempName;
        }

        // Return the value and if not set the name of the config.
        return projectName;
    }

    public List<Configuration> getParents() {
        return parents;
    }

    @Override
    public RecursiveOverridableMapContainer getContainer(String id) {
        if (configurationValues.is(id)) {
            return configurationValues;
        } else if (configurationValueBundles.is(id)) {
            return configurationValueBundles;
        } else if (tools.is(id)) {
            return tools;
        } else if (enumerations.is(id)) {
            return enumerations;
        } else if (filenamePatterns.is(id)) {
            return filenamePatterns;
        }
        return null;
    }

    /**
     * Clears the list of parent configuration objects and sets c as the single parent.
     *
     * @param c
     */
    public void setParent(Configuration c) {
        parents.clear();
        parents.add(c);
    }

    /**
     * Add a parent to the parents list. Note, that the added configuration has a higher priority
     * than the ones already in the list.
     *
     * @param p
     */
    public void addParent(Configuration p) {
        if (p == null) return;
        if (!parents.contains(p))
            parents.add(p);
    }

    public Map<String, Configuration> getSubConfigurations() {
        return subConfigurations;
    }

    public List<Configuration> getListOfSubConfigurations() {
        return new LinkedList<Configuration>(subConfigurations.values());
    }

    public File getBrawlWorkflowSourceFile(String brawlName) {
        // Brawl workflows can have the ending .brawl OR .groovy (better for e.g. Idea)
        File wf = getBrawlWorkflowFile(brawlName, Arrays.asList(".brawl", ".groovy"));
        return wf;
    }

    public File getJBrawlWorkflowSourceFile(String brawlName) {
        return getBrawlWorkflowFile(brawlName, Arrays.asList(".jbrawl"));
    }

    private File getBrawlWorkflowFile(String brawlName, List<String> suffix) {
        List<PluginInfo> pluginInfos = LibrariesFactory.getInstance().getLoadedPlugins();
        Map<String, File> availableBasePaths = new LinkedHashMap<>();
        List<File> allFiles = new LinkedList<>();
        List<String> filenames = new LinkedList<>();
        for (String s : suffix)
            filenames.add(brawlName + s);
        FileFilter filter = (FileFilter) new WildcardFileFilter(filenames);
        for (PluginInfo pluginInfo : pluginInfos) {
            File[] files = pluginInfo.getBrawlWorkflowDirectory().listFiles(filter);
            if (files != null && files.length > 0)
                allFiles.addAll(Arrays.asList(files));
        }
        if (allFiles.size() == 1) return allFiles.get(0);
        else if (allFiles.size() == 0)
            logger.severe("No Brawl workflow '" + brawlName + "' could be found");
        else if (allFiles.size() > 1)
            logger.severe("Too many Brawl workflows called " + brawlName);
        return null;
    }

    public File getSourceToolPath(String tool) throws ConfigurationError {
        List<PluginInfo> pluginInfos = LibrariesFactory.getInstance().getLoadedPlugins();
        Map<String, File> availableBasePaths = new LinkedHashMap<>();
        for (PluginInfo pluginInfo : pluginInfos) {
            availableBasePaths.putAll(pluginInfo.getToolsDirectories());
        }

        ToolEntry te = null;
        try {
            te = tools.getValue(tool);
        } catch (ConfigurationError e) {
            throw new ConfigurationError("Unknown tool ID", tool, e);
        }
        if (te.basePathId.length() > 0 && !availableBasePaths.containsKey(te.basePathId)) {
            throw new ConfigurationError("Base path for tool is not configured", tool);
        }
        File bPath = availableBasePaths.get(te.basePathId);

        Map<String, String> localPath = new LinkedHashMap<>();
        localPath.put(ConfigurationConstants.CVALUE_PLACEHOLDER_EXECUTION_DIRECTORY, ".");
        File toolPath = new File(bPath.getAbsolutePath(), te.path);
        return toolPath;
    }

    /** The actual path to the copy of the tool on the execution host (which can be local or remote). */
    public File getProcessingToolPath(ExecutionContext context, String tool) throws ConfigurationError {
        ToolEntry te = null;
        try {
            te = tools.getValue(tool);
        } catch (ConfigurationError e) {
            throw new ConfigurationError("Unknown tool ID", tool, e);
        }
        File toolPath = new File(new File(new File(context.getExecutionDirectory(), RuntimeService.DIRNAME_ANALYSIS_TOOLS), te.basePathId), te.path);
        return toolPath;
    }

    public String getProcessingToolMD5(String tool) throws ConfigurationError {
        if (tool == null || tool == "") {
            logger.warning("Tool id not correctly specified for md5 query.");
            throw new ConfigurationError("Tool ID not correctly specified for md5 query", tool);
        }
        File sourceToolPath = getSourceToolPath(tool);
        return RoddyIOHelperMethods.getMD5OfFile(sourceToolPath);
    }

    public String getSSHExecutionUser() {
        return configurationValues.get(ConfigurationFactory.XMLTAG_EXECUTIONSERVICE_SSHUSER).toString();
    }

    public boolean getShowSSHCalls() {
        return configurationValues.getBoolean(ConfigurationFactory.XMLTAG_EXECUTIONSERVICE_SHOW_SSHCALLS);
    }

    @Override
    public String toString() {
        return String.format("Configuration %s / %s of type %s", getName(), getID(), getClass().getName());
    }

    //
//    public FilenamePattern getFilenamePattern(BaseFile cls, BaseFile derivedFromCls) {
//        Class _cls = cls.getClass();
//        Class _derivedFromCls = derivedFromCls.getClass();
//        FilenamePattern pattern = getFilenamePattern(FilenamePattern.assembleID(_cls, _derivedFromCls));
//        return pattern;
//    }
//
//    public FilenamePattern getFilenamePattern(BaseFile newFile, FileStage stage) {
//        Class cls = newFile.getClass();
//        FilenamePattern pattern = getFilenamePattern(FilenamePattern.assembleID(cls, stage));
//        return pattern;
//    }
    public void addValidationError(ConfigurationValidationError error) {
        this.listOfValidationErrors.add(error);
    }

    public void addLoadError(ConfigurationLoadError error) {
        this.listOfLoadErrors.add(error);
    }

    public void addLoadErrors(Collection<ConfigurationLoadError> errors) {
        this.listOfLoadErrors.addAll(errors);
    }

    public List<ConfigurationLoadError> getListOfLoadErrors() {
        LinkedList<ConfigurationLoadError> errors = new LinkedList<>();
        for (Configuration c : parents) {
            errors.addAll(c.getListOfLoadErrors());
        }
        errors.addAll(listOfLoadErrors);
        return errors;
    }


    public boolean hasErrors() {
        boolean hasErrors = listOfLoadErrors.size() > 0;
        if (parents != null) {
            for (Configuration parent : parents) {
                hasErrors |= parent.hasErrors();
            }
        }
        return hasErrors;
    }

    public boolean isInvalid() {
        return this.listOfValidationErrors.size() > 0;
    }

//    /**
//     * Integrates another configuration without overriding existing values.
//     *
//     * @param cfg
//     */
//    public void integrate(Configuration cfg, boolean completeHierarchy) {
//        ConfigurationFactory.integrateConfig(cfg, this, completeHierarchy);
//    }

}
