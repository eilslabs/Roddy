/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.plugins

import de.dkfz.roddy.Roddy
import de.dkfz.roddy.StringConstants
import de.dkfz.roddy.core.VersionWithDevelop
import de.dkfz.roddy.tools.LoggerWrapper
import de.dkfz.roddy.tools.Tuple2

import java.text.ParseException

/**
 * A container / map for loaded / available plugins found by e.g. librariesFactory.loadMapOfAvailablePluginsForInstance();
 *
 * Created by heinold on 27.06.16.
 */
@groovy.transform.CompileStatic
class PluginInfoMap {

    private static LoggerWrapper logger = LoggerWrapper.getLogger(LibrariesFactory.class.getSimpleName())

    /**
     * A map stuffed with (detected) PluginInfo object.
     * Keys are:
     * mapOfPlugins[plugin id][plugin version]
     *
     * Contents are e.g.:
     * [ "BasePlugin" : [ "1.0.1", "1.0.2", "develop" ]
     */
    Map<String, Map<String, PluginInfo>> mapOfPlugins

    PluginInfoMap(Map<String, Map<String, PluginInfo>> mapOfPlugins) {
        this.mapOfPlugins = mapOfPlugins
        if (!mapOfPlugins) this.mapOfPlugins = [:] // By default, create an empty map.
    }

    /**
     * Returns the internal object of the map, if it is available. It is not a copy!
     * Access the map with the plugin id.
     * @param element
     * @return
     */
    public Map<String, PluginInfo> getAt(String element) {
        return mapOfPlugins[element];
    }

    /**
     * calls asBoolean of mapOfPlugins / LinkedHashMap / Map
     * @return
     */
    public boolean asBoolean() {
        return mapOfPlugins != null && mapOfPlugins.size() > 0;
    }

    /**
     * Get an object based on a plugin string like:
     * BasePlugin:1.0.12 or so...
     *
     * If no plugin is set "develop" will be returned.
     */
    PluginInfo getPluginInfoWithPluginString(String pluginString) {
        return LibrariesFactory.parseCandidatePluginString(pluginString).map { pluginStringData ->
            getPluginInfo(pluginStringData.x, pluginStringData.y.toString())
        }.orElse {
            throw new ParseException("Could not parse plugin data from '$pluginString'", 0)
        } as PluginInfo
    }

    /**
     * Get a plugin object with id and version
     * @param pluginID
     * @param version
     * @return
     */
    public PluginInfo getPluginInfo(String pluginID, String version) {
        if (!version) version = LibrariesFactory.PLUGIN_VERSION_DEVELOP;
        String additionalMessage = "\nPlease check whether you have proper access to all plugin directories. Roddy tries to load plugins from the following directories:\n\t"
        additionalMessage += Roddy.getPluginDirectories().collect { it.getAbsolutePath() }.join("\n\t") +"\n"
        if (!mapOfPlugins[pluginID]) //Can this case occur?
            throw new PluginLoaderException("Plugin '${pluginID}' is not available, available are:\n\t" + mapOfPlugins.keySet().join("\n\t") + additionalMessage)
        if (!mapOfPlugins[pluginID][version]){
            additionalMessage += "There were errors for the plugin:\n\t" + LibrariesFactory.getErrorsForPlugin(pluginID + ":" + version).join("\n\t")
            throw new PluginLoaderException("Version '${version}' of plugin '${pluginID}' is not available. Known versions are:\n\t" + mapOfPlugins[pluginID].keySet().join("\n\t") + additionalMessage)
        }
        return mapOfPlugins[pluginID][version]
    }

    public boolean checkExistence(String pluginID, String version) {
        if (!version) version = LibrariesFactory.PLUGIN_VERSION_DEVELOP
        if (!mapOfPlugins[pluginID]) //Can this case occur?
            return false;
        if (!mapOfPlugins[pluginID][version])
            return false;
        return true;
    }

    /**
     * Number of entries in the internal map
     * @return
     */
    public int size() {
        return mapOfPlugins.size()
    }
}
