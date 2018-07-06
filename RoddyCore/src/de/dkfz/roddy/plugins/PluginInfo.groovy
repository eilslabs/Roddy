/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.plugins

import de.dkfz.roddy.core.RuntimeService
import de.dkfz.roddy.core.VersionWithDevelop
import de.dkfz.roddy.tools.LoggerWrapper
import de.dkfz.roddy.tools.RuntimeTools
import de.dkfz.roddy.tools.versions.CompatibilityChecker
import de.dkfz.roddy.tools.versions.Version
import de.dkfz.roddy.tools.versions.VersionInterval
import de.dkfz.roddy.tools.versions.VersionLevel
import org.apache.commons.io.filefilter.WildcardFileFilter

import java.nio.file.Files
import java.nio.file.attribute.PosixFileAttributes

/**
 * An informational class for loaded plugins.
 */
class PluginInfo {

    private static LoggerWrapper logger = LoggerWrapper.getLogger(PluginInfo.class)

    enum PluginInfoConnection {
        /**
         * Revisions mark plugins which contain smaller bugfixes but not extensions. In overall, the revision
         * does not introduce extensions or functional changes.
         */
        REVISION,

        /**
         * Extension plugins are plugins, which add functionality or bugfixes to an existing plugin.
         * They are considered as compatible to their precursors and plugins using the precursors. However
         * plugins referencing these plugins are not allowed to use older versions of the plugin.
         */
        EXTENSION,

        /**
         * Plugin versions which are marked incompatible to their precursor will not be considered for
         * auto version select by Roddy.
         */
        INCOMPATIBLE
    }

    protected String name
    protected File directory
    protected File developmentDirectory
    final VersionWithDevelop version
    protected final String roddyAPIVersion
    protected Map<String, String> dependencies
    protected final File zipFile
    /**
     * Stores the next entry in the plugin chain or null if there is nor further plugin available.
     */
    private PluginInfo nextInChain = null
    /**
     * Stores the previous entry in the plugin chain or null if there is no precursor.
     */
    private PluginInfo previousInChain = null
    /**
     * Stores the connection type of this PI object relative to its precursor.
     */
    private PluginInfoConnection previousInChainConnectionType = PluginInfoConnection.INCOMPATIBLE
    private boolean isBetaPlugin = false
    protected final Map<String, File> listOfToolDirectories = new LinkedHashMap<>()
    private final List<String> errors = new LinkedList<>()
    PluginInfo(String name, File directory, VersionWithDevelop version, String roddyAPIVersion, Map<String, String> dependencies) {
        this(name, null, directory, null, version, roddyAPIVersion, dependencies)
    }

    @Deprecated
    PluginInfo(String name, File zipFile, File directory, File developmentDirectory, String version, String roddyAPIVersion,
                      Map<String, String> dependencies = [:]) {
        this(name, zipFile, directory, developmentDirectory, VersionWithDevelop.fromString(version), roddyAPIVersion, dependencies)
    }

    @Deprecated
    PluginInfo(String name, File zipFile, File directory, File developmentDirectory, VersionWithDevelop version, String roddyAPIVersion,
                      Map<String, String> dependencies = [:]) {
        this.name = name
        this.directory = directory
        this.developmentDirectory = developmentDirectory
        this.version = version
        this.roddyAPIVersion = roddyAPIVersion
        this.dependencies = dependencies
        this.zipFile = zipFile
        fillListOfToolDirectories()
    }

    protected void fillListOfToolDirectories() {
        File toolsBaseDir = null
        toolsBaseDir = getToolsDirectory()
        if (toolsBaseDir != null && toolsBaseDir.exists() && toolsBaseDir.isDirectory()) { //Search through the default folders, if possible.
            for (File file : toolsBaseDir.listFiles()) {
                PosixFileAttributes attr
                try {
                    attr = Files.readAttributes(file.toPath(), PosixFileAttributes.class)
                } catch (IOException ex) {
                    errors.add("An IOException occurred while accessing '" + file.getAbsolutePath() + "': " + ex.getMessage())
                    continue
                }

                if (!attr.isDirectory() || file.isHidden()) {
                    continue
                }

                String toolsDir = file.getName()
                listOfToolDirectories.put(toolsDir, file)
            }
        }
    }

    VersionWithDevelop getVersion() {
        return version
    }

    List<String> getErrors() {
        return errors
    }

    File getToolsDirectory() {
        return new File(new File(directory, RuntimeService.DIRNAME_RESOURCES), RuntimeService.DIRNAME_ANALYSIS_TOOLS)
    }

    File getBrawlWorkflowDirectory() {
        return new File(new File(directory, RuntimeService.DIRNAME_RESOURCES), RuntimeService.DIRNAME_BRAWLWORKFLOWS)
    }

    File getConfigurationDirectory() {
        return new File(new File(directory, RuntimeService.DIRNAME_RESOURCES), RuntimeService.DIRNAME_CONFIG_FILES)
    }

    List<File> getConfigurationFiles() {
        File configPath = getConfigurationDirectory()
        List<File> configurationFiles = new LinkedList<>()
        configurationFiles.addAll(Arrays.asList(configPath.listFiles((FileFilter) new WildcardFileFilter(["*.sh", "*.xml"]))))
        if (getBrawlWorkflowDirectory().exists() && getBrawlWorkflowDirectory().canRead()) {
            File[] files = getBrawlWorkflowDirectory().listFiles((FileFilter) new WildcardFileFilter(["*.brawl", "*.groovy"]))
            configurationFiles.addAll(Arrays.asList(files))
        }
        return configurationFiles
    }

    String getName() {
        return name
    }

    boolean isBetaPlugin() {
        return isBetaPlugin
    }

    void setIsBetaPlugin(boolean betaPlugin) {
        isBetaPlugin = betaPlugin
    }

    File getDirectory() {
        return directory
    }

    protected void setDirectory(File f) {
        directory = f
    }

    String getRoddyAPIVersion() {
        return roddyAPIVersion
    }

    Map<String, File> getToolsDirectories() {
        return listOfToolDirectories
    }

    Map<String, String> getDependencies() {
        return dependencies
    }

    void setNextInChain(PluginInfo nextInChain) {
        this.nextInChain = nextInChain
    }

    PluginInfo getNextInChain() {
        return nextInChain
    }

    void setPreviousInChain(PluginInfo previousInChain) {
        this.previousInChain = previousInChain
    }

    PluginInfo getPreviousInChain() {
        return previousInChain
    }

    void setPreviousInChainConnectionType(PluginInfoConnection previousInChainConnectionType) {
        this.previousInChainConnectionType = previousInChainConnectionType
    }

    PluginInfoConnection getPreviousInChainConnectionType() {
        return previousInChainConnectionType
    }

    String getFullID() {
        return name + ":" + version
    }

    /** Plugins are compatible to the current Roddy version, if they have the plugin requires the same major version and the same or lower minor level. */
    boolean isCompatibleToRuntimeSystem() {
        return CompatibilityChecker.isBackwardsCompatibleTo(
                Version.fromString(RuntimeTools.getRoddyRuntimeVersion()),
                Version.fromString(getRoddyAPIVersion()),
                VersionLevel.MINOR)
    }

    int getRevision() {
        return version.getAt(VersionLevel.REVISION)
    }

    @Override
    String toString() {
        return getFullID()
    }

    /**
     * Not revision but compatible. Check, if the former plugin id (excluding the revision number) is set as compatible.
     * Ignore malformed entries!! Use a regex for that.
     *
     * @param otherPlugin
     * @return
     */
    boolean isCompatibleTo(PluginInfo otherPlugin) {
        if(otherPlugin == null)
            return false // The value is not set
        else
            return CompatibilityChecker.compatibleTo(otherPlugin.version, version, compatibleVersionIntervals, VersionLevel.PATCH)
    }

    boolean isRevisionOf(PluginInfo otherPlugin) {
        if (null == otherPlugin) {
            return false
        } else {
            return otherPlugin.version.compareTo(pluginVersion, VersionLevel.PATCH) == 0 &&
                    (otherPlugin.version[VersionLevel.REVISION] as Integer) < (pluginVersion[VersionLevel.REVISION] as Integer)
        }
    }

    List<VersionInterval> getCompatibleVersionIntervals() {
        return []
    }

}
