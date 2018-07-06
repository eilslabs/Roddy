/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.plugins;

import de.dkfz.roddy.core.RuntimeService;
import de.dkfz.roddy.tools.LoggerWrapper;
import de.dkfz.roddy.tools.RoddyConversionHelperMethods;
import de.dkfz.roddy.tools.RuntimeTools;
import de.dkfz.roddy.tools.versions.CompatibilityChecker;
import de.dkfz.roddy.tools.versions.Version;
import de.dkfz.roddy.tools.versions.VersionLevel;
import org.apache.commons.io.filefilter.WildcardFileFilter;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFileAttributes;
import java.util.*;


/**
 * An informational class for loaded plugins.
 */
public class PluginInfo {

    private static LoggerWrapper logger = LoggerWrapper.getLogger(PluginInfo.class);

    public enum PluginInfoConnection {
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

    protected String name;
    protected File directory;
    protected File developmentDirectory;
    protected String prodVersion;
    protected final String roddyAPIVersion;
    protected final String jdkVersion;
    protected Map<String, String> dependencies;
    protected final File zipFile;

    /**
     * Stores the next entry in the plugin chain or null if there is nor further plugin available.
     */
    private PluginInfo nextInChain = null;
    /**
     * Stores the previous entry in the plugin chain or null if there is no precursor.
     */
    private PluginInfo previousInChain = null;
    /**
     * Stores the connection type of this PI object relative to its precursor.
     */
    private PluginInfoConnection previousInChainConnectionType = PluginInfoConnection.INCOMPATIBLE;

    private boolean isBetaPlugin = false;

    protected final Map<String, File> listOfToolDirectories = new LinkedHashMap<>();

    private final List<String> errors = new LinkedList<>();

    public PluginInfo(String name, File directory, String version, String roddyAPIVersion,
                      String jdkVersion, Map<String, String> dependencies) {
        this(name, null, directory, null, version, roddyAPIVersion, jdkVersion, dependencies);
    }

    @Deprecated
    public PluginInfo(String name, File zipFile, File directory, File developmentDirectory, String prodVersion, String roddyAPIVersion,
                      String jdkVersion, Map<String, String> dependencies) {
        this.name = name;
        this.directory = directory;
        this.developmentDirectory = developmentDirectory;
        this.prodVersion = prodVersion;
        this.roddyAPIVersion = roddyAPIVersion;
        this.jdkVersion = jdkVersion;
        this.dependencies = dependencies;
        this.zipFile = zipFile;
        fillListOfToolDirectories();
    }

    protected void fillListOfToolDirectories() {
        File toolsBaseDir = null;
        toolsBaseDir = getToolsDirectory();

        if (toolsBaseDir != null && toolsBaseDir.exists() && toolsBaseDir.isDirectory()) { //Search through the default folders, if possible.
            for (File file : toolsBaseDir.listFiles()) {
                PosixFileAttributes attr;
                try {
                    attr = Files.readAttributes(file.toPath(), PosixFileAttributes.class);
                } catch (IOException ex) {
                    errors.add("An IOException occurred while accessing '" + file.getAbsolutePath() + "': " + ex.getMessage());
                    continue;
                }

                if (!attr.isDirectory() || file.isHidden()) {
                    continue;
                }

                String toolsDir = file.getName();
                listOfToolDirectories.put(toolsDir, file);
            }
        }
    }

    public List<String> getErrors() {
        return errors;
    }

    public File getToolsDirectory() {
        // Had a bad side effect...
//        if (directory != null && directory.exists()) {
        return new File(new File(directory, RuntimeService.DIRNAME_RESOURCES), RuntimeService.DIRNAME_ANALYSIS_TOOLS);
//        }
//        return null;
    }

    public File getBrawlWorkflowDirectory() {
        return new File(new File(directory, RuntimeService.DIRNAME_RESOURCES), RuntimeService.DIRNAME_BRAWLWORKFLOWS);
    }

    public File getConfigurationDirectory() {
        return new File(new File(directory, RuntimeService.DIRNAME_RESOURCES), RuntimeService.DIRNAME_CONFIG_FILES);
    }

    public List<File> getConfigurationFiles() {
        File configPath = getConfigurationDirectory();
        List<File> configurationFiles = new LinkedList<>();
        configurationFiles.addAll(Arrays.asList(configPath.listFiles((FileFilter) new WildcardFileFilter(new String[]{"*.sh", "*.xml"}))));
        if (getBrawlWorkflowDirectory().exists() && getBrawlWorkflowDirectory().canRead()) {
            File[] files = getBrawlWorkflowDirectory().listFiles((FileFilter) new WildcardFileFilter(new String[]{"*.brawl", "*.groovy"}));
            configurationFiles.addAll(Arrays.asList(files));
        }
        return configurationFiles;
    }

    public String getName() {
        return name;
    }

    public boolean isBetaPlugin() {
        return isBetaPlugin;
    }

    public void setIsBetaPlugin(boolean betaPlugin) {
        isBetaPlugin = betaPlugin;
    }

    public File getDirectory() {
        return directory;
    }

    protected void setDirectory(File f) {
        directory = f;
    }

    public String getProdVersion() {
        return prodVersion;
    }

    public String getRoddyAPIVersion() {
        return roddyAPIVersion;
    }

    public String getJdkVersion() {
        return jdkVersion;
    }

    public Map<String, File> getToolsDirectories() {
        return listOfToolDirectories;
    }

    public Map<String, String> getDependencies() {
        return dependencies;
    }

    public void setNextInChain(PluginInfo nextInChain) {
        this.nextInChain = nextInChain;
    }

    public PluginInfo getNextInChain() {
        return nextInChain;
    }

    public void setPreviousInChain(PluginInfo previousInChain) {
        this.previousInChain = previousInChain;
    }

    public PluginInfo getPreviousInChain() {
        return previousInChain;
    }

    public void setPreviousInChainConnectionType(PluginInfoConnection previousInChainConnectionType) {
        this.previousInChainConnectionType = previousInChainConnectionType;
    }

    public PluginInfoConnection getPreviousInChainConnectionType() {
        return previousInChainConnectionType;
    }

    public String getFullID() {
        return name + ":" + prodVersion;
    }

    public String getMajorAndMinor() {
        return prodVersion.split("[-]")[0];
    }

    /** Plugins are compatible to the current Roddy version, if they have the plugin requires the same major version and the same or lower minor level. */
    public boolean isCompatibleToRuntimeSystem() {
        return CompatibilityChecker.isBackwardsCompatibleTo(
                Version.fromString(RuntimeTools.getRoddyRuntimeVersion()),
                Version.fromString(getRoddyAPIVersion()),
                VersionLevel.MINOR);
    }

    public int getRevision() {
        int result = 0;
        try {
            String[] split = prodVersion.split("[-]");
            if (split.length == 1)
                return 0;
            else
                result = RoddyConversionHelperMethods.toInt(split[1], 0);
        } catch (Exception e) {
            System.out.println("Error for revision fetch of " + this.name + " in " + this.directory);
        }
        return result;
    }

    @Override
    public String toString() {
        return getFullID();
    }
}
