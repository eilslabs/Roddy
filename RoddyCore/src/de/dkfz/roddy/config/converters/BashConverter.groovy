/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.config.converters

import de.dkfz.roddy.AvailableFeatureToggles
import de.dkfz.roddy.Constants
import de.dkfz.roddy.Roddy
import de.dkfz.roddy.StringConstants
import de.dkfz.roddy.config.*
import de.dkfz.roddy.config.loader.ConfigurationFactory
import de.dkfz.roddy.core.ExecutionContext
import de.dkfz.roddy.execution.io.fs.BashCommandSet
import de.dkfz.roddy.execution.io.fs.FileSystemAccessProvider
import groovy.transform.CompileStatic

import java.util.logging.Level
import static Constants.RODDY_CONFIGURATION_MAGICSTRING


/**
 * Converts a configuration object to bash script.
 * Created by heinold on 18.06.15.
 */
@groovy.transform.CompileStatic
class BashConverter extends ConfigurationConverter {

    final String separator = Constants.ENV_LINESEPARATOR

    //TODO Use a pipeline converter interface with methods like "convertCValues, convertCValueBundles, convertTools"
    @Override
    String convert(ExecutionContext context, Configuration _cfg) {
        Configuration cfg = new Configuration(null, _cfg)

        cfg.configurationValues.addAll(cfg.tools.allValuesAsList.collect {
            ToolEntry te ->
                new ConfigurationValue(createVariableName("TOOL_", te.getID()),
                        cfg.getProcessingToolPath(context, te.getID()).absolutePath)
        })

        StringBuilder text = createNewDocumentStringBuilder(context, cfg)

        text << appendConfigurationValues(context, cfg)

        text << appendConfigurationValueBundles(context, cfg)

        text << appendDebugVariables(cfg)

        text << appendPathVariables()

        text << separator << ""

        return text.toString()

    }

    StringBuilder createNewDocumentStringBuilder(ExecutionContext context, Configuration cfg) {
        final String separator = Constants.ENV_LINESEPARATOR

        StringBuilder text = new StringBuilder()
        text << "#!/bin/bash" << separator //Add a shebang line

        //TODO The output umask and the group should be taken from a central location.
        String umask = context.getUMask()
        String outputFileGroup = context.getOutputGroupString()
        boolean processSetUserGroup = cfg.getConfigurationValues().getBoolean(ConfigurationConstants.CVALUE_PROCESS_OPTIONS_SETUSERGROUP, true)
        boolean processSetUserMask = cfg.getConfigurationValues().getBoolean(ConfigurationConstants.CVALUE_PROCESS_OPTIONS_SETUSERMASK, true)
        text << separator << separator << new BashCommandSet().getCheckForInteractiveConsoleCommand() << separator << separator
        text << separator << "fi" << separator << separator

        if (processSetUserMask) text << "umask " << umask << separator
        return text
    }

    StringBuilder appendConfigurationValues(ExecutionContext context, Configuration cfg) {
        StringBuilder text = new StringBuilder()
        Map<String, ConfigurationValue> listOfSortedValues = getConfigurationValuesSortedByDependencies(cfg.configurationValues.allValuesAsList)
        for (ConfigurationValue cv : listOfSortedValues.values()) {
            boolean isValidationRule = cv.id.contains("cfgValidationRule")

            if (isValidationRule) {
                text << "# Validation rule!: " + cv.toString() << separator
                continue
            }

            text << convertConfigurationValue(cv, context) << separator
        }
        return text
    }

    StringBuilder appendConfigurationValueBundles(ExecutionContext context, Configuration cfg) {
        StringBuilder text = new StringBuilder()
        Map<String, ConfigurationValueBundle> cvBundles = cfg.getConfigurationValueBundles().getAllValues()
        for (String bKey : cvBundles.keySet()) {
            ConfigurationValueBundle bundle = cvBundles[bKey]
            text << "#<" << bKey << separator
            for (String key : bundle.getKeys()) {
                text << convertConfigurationValue(bundle[key], context) << separator
            }
            text << "#>" << bKey << separator
        }
        return text
    }

    StringBuilder appendToolEntries(ExecutionContext context, Configuration cfg) {
        StringBuilder text = new StringBuilder()
        //Store tools
        for (ToolEntry te : cfg.getTools().getAllValuesAsList()) {
            String id = te.getID()
            String valueName = createVariableName("TOOL_", id)
            text << valueName << '="' << cfg.getProcessingToolPath(context, id) << '"' << separator
        }
        return text
    }

    StringBuilder appendDebugVariables(Configuration cfg) {
        StringBuilder text = new StringBuilder()
        text << separator << separator

        String wrappedScriptDebugOptions = ""

        for (List bashFlag in [
                [ConfigurationConstants.DEBUG_OPTIONS_USE_VERBOSE_OUTPUT, true, "-v"],
                [ConfigurationConstants.DEBUG_OPTIONS_USE_EXECUTE_OUTPUT, true, "-x"],
                [ConfigurationConstants.DEBUG_OPTIONS_USE_UNDEFINED_VARIABLE_BREAK, false, "-u"],
                [ConfigurationConstants.DEBUG_OPTIONS_USE_EXIT_ON_ERROR, false, "-e"],
                [ConfigurationConstants.DEBUG_OPTIONS_PARSE_SCRIPTS, false, "-n"],
                [ConfigurationConstants.DEBUG_OPTIONS_USE_PIPEFAIL, true, "-o pipefail"],
                ]) {
            boolean val = cfg.getConfigurationValues().getBoolean(bashFlag[0] as String, bashFlag[1] as Boolean)
            if(val)
                wrappedScriptDebugOptions += "${bashFlag[2]} "
        }

        text << separator << "declare -x WRAPPED_SCRIPT_DEBUG_OPTIONS=\"$wrappedScriptDebugOptions\"" << separator

        for (List bashFlag in [
                [ConfigurationConstants.DEBUG_OPTIONS_USE_EXTENDED_EXECUTE_OUTPUT, false, "export PS4='+(\${BASH_SOURCE}:\${LINENO}): \${FUNCNAME[0]: +\$ { FUNCNAME[0] }():}'"],
                [ConfigurationConstants.CVALUE_PROCESS_OPTIONS_QUERY_ENV, false, "env"],
                [ConfigurationConstants.CVALUE_PROCESS_OPTIONS_QUERY_ID, false, "id"],
        ]) {
            if (cfg.getConfigurationValues().getBoolean(bashFlag[0] as String, bashFlag[1] as Boolean)) text << separator << bashFlag[2] as String
        }
        return text
    }

    StringBuilder appendPathVariables() {
        StringBuilder text = new StringBuilder()

        //Set a path if necessary.
        text << separator << new BashCommandSet().getSetPathCommand()
        return text
    }

    /**
     * Effectively calls getConfigurationValuesSortedByDependenciesAndUnresolvable but return a joined map of the result.
     * @param values
     * @return A map of resolved and unresolved values. Unresolved are found at the end of the (linked!) map.
     */
    Map<String, ConfigurationValue> getConfigurationValuesSortedByDependencies(List<ConfigurationValue> values) {
        def tuple = getConfigurationValuesSortedByDependenciesAndUnresolvable(values)
        return tuple.first + tuple.second
    }

    /**
     * @param cfg Configuration object. Basically a tree of configuration values that may additionally contain cross-references in the values.
     *
     * @param If returnLeftOverCValuesOnly is set, only the configuration values referencing undeclared variables are returned.
     *
     * @return As the name says, the method will return a sorted map of configuration values. The values are sorted by their dependencies so e.g.:
     *
     *   1.  valueA=abc
     *   2.  valueB=def
     *   3.  valueC=${valueA}*
     *   4.  valueD=${valueB}*
     *   5.  valueE=${valueA}_${valueB}*
     *
     * Note, that values are not sorted by their id! They are initially sorted by load order and this will be kept as far as it is possible.
     *
     * Values with unresolved dependencies (i.e. variables that are not declared in the configuration tree) are put at the end.
     */
    Tuple2<Map<String, ConfigurationValue>, Map<String, ConfigurationValue>> getConfigurationValuesSortedByDependenciesAndUnresolvable(List<ConfigurationValue> values) {

        Map<String, ConfigurationValue> listOfUnsortedValues = values.collectEntries { [it.id, it] }
        Map<String, ConfigurationValue> listOfSortedValues = new LinkedHashMap<String, ConfigurationValue>()

        // Would have like to do this with a simple do .. while loop, but Groovy does not know that yet.
        boolean listOfSortedValuesChanged = true
        while (listOfSortedValuesChanged) {
            Map<String, ConfigurationValue> foundValues = [:]

            for (ConfigurationValue cv in listOfUnsortedValues.values().findAll { !isValidationRule(it) && !isComment(it) }) {

                List<String> dependenciesToOtherValues = cv.getIDsForParentValues()

                // Check, how many dependencies to other values are set and find out for each dependency, if
                // it was resolved in a former loop run or if it is a blacklisted value.
                int noOfDependencies = dependenciesToOtherValues.size()
                int resolvedOrBlacklistedCount = dependenciesToOtherValues.findAll { listOfSortedValues.containsKey(it) }.size()

                // Continue, if the value cannot be resolved fully and at least one dependency is left.
                if (noOfDependencies - resolvedOrBlacklistedCount > 0)
                    continue

                // Otherwise, put the value to the list of found values and to the list of already "sorted" values.
                foundValues[cv.id] = cv
                listOfSortedValues[cv.id] = cv
            }

            // Remove any found value from the list of unsorted / not found values.
            // Remove the values as a block, the list is used in the loop before and should not be touched during the loop
            listOfUnsortedValues -= foundValues
            listOfSortedValuesChanged = foundValues.values().size() > 0
        }

        // Finally put the leftover values to the end of the list. and return this.
        return new Tuple2<>(listOfSortedValues, listOfUnsortedValues)
    }

    static boolean isComment(ConfigurationValue cv) {
        cv.value != null && cv.value.trim().startsWith("#")
    }

    static boolean isValidationRule(ConfigurationValue cv) {
        cv.id.contains("cfgValidationRule")
    }

    static boolean isQuoted(String string) {
        (string.startsWith("'") && string.endsWith("'")) || (string.startsWith('"') && string.endsWith('"'))
    }

    /** Dependent on the settings and whether the string is quoted, return the quoted string. */
    static String addQuotesIfRequested(String value, Boolean doQuote = true) {
        if (isQuoted(value) || !doQuote)
            return value
        else
            return '"' + value + '"'
    }

    static String convertListToBashArrayString(List list) {
        "(${list.collect { it.toString() }.join(" ")})" as String
    }

    static String convertMapToBashMapString(Map map) {
        "(${map.collect { k, v -> "[$k]=$v"}.join(" ")})" as String
    }

    /** To convert a simple Map of String keys to String values (possibly pre-converted!), this method should be used.
     *
     * @param map
     * @param doDeclare
     * @param quoteSomeScalarConfigValues
     * @param doQuote
     * @return
     */
    static List<String> convertStringMapToDeclarationList(Map<String, String> map, Boolean doDeclare = true,
                                                          Boolean quoteSomeScalarConfigValues = true, Boolean doQuote = true) {
        String declareString = ""
        if (doDeclare) {
            declareString = "declare -x   "
        }
        return map.collect { String k, String v ->
            String tmp
            if (v.startsWith("-") || v.startsWith("*")) {
                tmp = '"' + v + '"'
            } else if (quoteSomeScalarConfigValues && !isQuoted(v) && v =~ /[\s\t\n;]/) {
                tmp = '"' + v + '"'
            } else {
                tmp = addQuotesIfRequested(v, doQuote)
            }
            "${declareString} ${k}=${tmp}\n".toString()
        }
    }

    /** To convert a simple Map of String keys to String values (possibly pre-converted!), this method should be used.
     *
     * @param map
     * @param doDeclare
     * @param quoteSomeScalarConfigValues
     * @param doQuote
     * @return
     */
    static String convertStringMapToDeclarations(Map<String, String> map, Boolean doDeclare = true,
                                                 Boolean quoteSomeScalarConfigValues = true, Boolean doQuote = true) {
        return convertStringMapToDeclarationList(map, doDeclare, quoteSomeScalarConfigValues, doQuote).join("")
    }

    /** ConfigurationValue provides meta-data about types for the values. To exploit this specialized conversion function exists.
     *
     * @param cv
     * @param context
     * @param quoteSomeScalarConfigValues
     * @param autoQuoteArrays
     * @return
     */
    StringBuilder convertConfigurationValue(ConfigurationValue cv, ExecutionContext context,
                                            Boolean quoteSomeScalarConfigValues, Boolean autoQuoteArrays) {
        StringBuilder text = new StringBuilder()
        String declareVar = ""
        String declareInt = ""
        if (context.getFeatureToggleStatus(AvailableFeatureToggles.UseDeclareFunctionalityForBashConverter)) {
            declareVar = "declare -x   "
            declareInt = "declare -x -i"
        }
        if (cv.toString().startsWith("#COMMENT")) {
            text << cv.toString()
        } else {
            String tmp

            if (cv.type && cv.type.toLowerCase() == "basharray") {
                return new StringBuilder("${declareVar} ${cv.id}=${addQuotesIfRequested(cv.toString(), autoQuoteArrays)}".toString())
            } else if (cv.type && cv.type.toLowerCase() == "integer") {
                return new StringBuilder("${declareInt} ${cv.id}=${cv.toString()}".toString())
            } else if (cv.type && ["double", "float"].contains(cv.type.toLowerCase())) {
                return new StringBuilder("${declareVar} ${cv.id}=${cv.toString()}".toString())
            } else if (cv.type && cv.type.toLowerCase() == "path") {
                tmp = "${cv.toFile(context)}".toString()
            } else {
                if (cv.value.startsWith("-") || cv.value.startsWith("*")) {
                    tmp = "\"${cv.toString()}\"".toString()
                } else if (quoteSomeScalarConfigValues && !isQuoted(cv.value) && cv.value =~ /[\s\t\n;]/) {
                    tmp = "\"${cv.toString()}\"".toString()
                } else {
                    tmp = "${cv.toString()}".toString()
                }
            }
            text << "${declareVar} ${cv.id}="
            //TODO Important, this is a serious hack! It must be removed soon
            if (tmp.startsWith("bundledFiles/")) {
                text << Roddy.getApplicationDirectory().getAbsolutePath() << FileSystemAccessProvider.getInstance().getPathSeparator()
            }
            text << tmp
        }
        return text
    }

    @Override
    @CompileStatic
    StringBuilder convertConfigurationValue(ConfigurationValue cv, ExecutionContext context) {
        convertConfigurationValue(
                cv
                , context
                , context.getFeatureToggleStatus(AvailableFeatureToggles.QuoteSomeScalarConfigValues)
                , context.getFeatureToggleStatus(AvailableFeatureToggles.AutoQuoteBashArrayVariables)
        )
    }

    Configuration loadShellScript(File configurationFile) {
        return loadShellScript(configurationFile.getAbsolutePath())
    }

    Configuration loadShellScript(String configurationFile) {
        if (!configurationFile) {
            throw new IOException("Configuration file must be specified.")
        }
        File cf = new File(configurationFile)
        if (!cf.canRead()) {
            throw new IOException("Configuration file is not readable.")
        }

        //Filter input from configuration file
        int commentCnt = 0
        List<String> cfFiltered = []
        HashMap<String, List<String>> valueBundles = new HashMap<String, List<String>>()
        List<String> currentBundle = null
        String currentBundleName = ""

        cf.readLines().each { String line ->
            String l = line.trim()
            if (!l) return
            if (l.startsWith("#<")) {
                currentBundle = new ArrayList<String>()
                currentBundleName = l[2..-1]
            } else if (l.startsWith("#>")) {
                //cfFiltered << ";VALUE_BUNDLE;" + currentBundle[0];
                valueBundles[currentBundleName] = currentBundle
                currentBundle = null
            } else if (l[0] == "#") {
                //TODO Also store comments
                if (currentBundle != null) {
                    currentBundle << String.format("#COMMENT_%04d=%s", commentCnt, l)
                } else {
                    cfFiltered << String.format("#COMMENT_%04d=%s", commentCnt, l)
                }
                commentCnt++
            } else if (l.indexOf("=") > 0) {
                if (currentBundle != null) {
                    currentBundle << l
                } else {
                    cfFiltered << l
                }
            }
        }

        Map<String, Integer> doubletteCounter = new HashMap<String, Integer>()
        PreloadedConfiguration userConfig = new PreloadedConfiguration(null, Configuration.ConfigurationType.OTHER, "userconfig_INVALIDNAME", "An imported configuration, please change the name and this description. Also set the classname and type as necessary.", null, null, "", null, null, "")
        Configuration newCfg = new Configuration(userConfig, (Map<String, Configuration>) null)
        Map<String, ConfigurationValue> cValues = newCfg.configurationValues.getMap()
        Map<String, ConfigurationValueBundle> cValueBundles = newCfg.configurationValueBundles.getMap()

        for (String cval in cfFiltered) {
            String[] cvarr = cval.split("=")
            String key = cvarr[0]
            int keyLen = key.length() + 1
            String value = cval[keyLen..-1]

            try {
                String k2 = key
                if (!cValues.containsKey(key)) {
                    doubletteCounter[key] = 0
                } else {
                    doubletteCounter[key] = doubletteCounter[key] + 1
                    k2 = String.format("%s_%04d", key, doubletteCounter[key])
                }

                cValues[k2] = new ConfigurationValue(newCfg, k2, value)
            } catch (Exception ex) {
                logger.log(Level.SEVERE, ex.toString())
            }
        }

        valueBundles.each() {
            String bundleName, List<String> bundle ->
                Map<String, ConfigurationValue> bundleValues = new LinkedHashMap<String, ConfigurationValue>()
                try {
                    for (String cval in bundle) {
                        String[] cvarr = cval.split("=")
                        String key = cvarr[0]
                        bundleValues[key] = new ConfigurationValue(newCfg, key, cval[key.length() + 1..-1])
                    }
                    cValueBundles[bundleName] = new ConfigurationValueBundle(bundleName, bundleValues)
                } catch (Exception ex) {
                    logger.log(Level.SEVERE, ex.toString())
                }
        }


        return newCfg
    }

    /**
     * Check if the first or second line in a file starts with /^#\s*Roddy\sconfiguration\s*$/.
     * @param file
     * @return
     */
    boolean isBashConfigFile(File file) {
        def lines = file.readLines()
        if (!lines) return false

        // First line should be the pattern.
        if (lines[0] && lines[0] =~ /^#\s*${RODDY_CONFIGURATION_MAGICSTRING}\s*$/) return true

        // If the first line is a shebang line, try matching the second line.
        if (lines.size() > 1 && lines[1] && lines[1] =~ /^#\s*${RODDY_CONFIGURATION_MAGICSTRING}\s*$/) return true
        return false
    }

    /**
     * The easy Bash config format.
     *
     * This is a very generic helper script which just converts a bash cfg to xml.
     * Not everything is covered, it's not built in yet and it needs a lot of improvement.
     * But for now, it works.
     * To run it faster from within IDEA, copy the content to a groovy console and start it.
     *
     * Basically a file which contains some info in the header and only config values.
     *
     * Example:
     * #!/shebang...
     * # Roddy configuration
     * #name aConfig
     * #imports anotherConfig
     * #description aConfig
     * #usedresourcessize m
     * #analysis A,aAnalysis,TestPlugin:develop
     * #analysis B,bAnalysis,TestPlugin:develop
     * #analysis C,aAnalysis,TestPlugin:develop
     *
     * outputBaseDirectory=/data/michael/temp/roddyLocalTest/testproject/rpp
     * UNZIPTOOL=gunzip
     * ZIPTOOL_OPTIONS="-c"
     * sampleDirectory=/data/michael/temp/roddyLocalTest/testproject/vbp/A100/${sample}/${SEQUENCER_PROTOCOL}*
     *
     * Note, that Roddy Bash files need at least the line "# Roddy configuration" as either the first or second line
     * of the file! Otherwise Roddy will not load the file.
     *
     * @param file
     * @return An xml string ready to be converted by Roddys XML converter OR an empty string.
     */
    String convertToXML(File file) {
        if (!isBashConfigFile(file))
            return ""

        String previousLine = ""
        List<String> allLines = file.readLines()
        List<String> header = extractHeader(allLines)

        List<String> xmlLines = []

        String type = ""
        String additionalEntries = ""
        if (file.name.startsWith("projects")) {
            type = 'configurationType="project"'
        } else if (file.name.startsWith("analysis")) {
            type = 'configurationType="analysis"'
            additionalEntries = "class='de.dkfz.roddy.core.Analysis' workflowClass='de.dkfz.roddy.knowledge.nativeworkflows.NativeWorkflow' runtimeServiceClass=\"de.dkfz.roddy.knowledge.examples.SimpleRuntimeService\""
        } else {
        }

        xmlLines << "<configuration ${type} ${additionalEntries} name='${getHeaderValue(header, "name", file.parentFile.name + "Analysis")}'".toString()

        xmlLines += convertHeader(header)

        xmlLines += convertConfigurationValues(allLines, header, previousLine)

        xmlLines << "</configuration>"

        return xmlLines.join("\n")
    }

    private List<String> extractHeader(List<String> allLines) {
        List<String> header = []
        for (int i = 0; i < allLines.size(); i++) {
            if (!allLines[i]) continue
            if (isBashComment(allLines[i]))
                header << allLines[i]
            else
                break
        }

//        if (!header)
//            throw new IOException("Simple Bash configuration files need a valid header")
        header
    }

    /** Convert the Bash header **/
    private List<String> convertHeader(List<String> header) {
        List<String> xmlLines = []
        xmlLines << "imports='" + getHeaderValue(header, "imports", "") + "'"
        xmlLines << "description='" + getHeaderValue(header, "description", "") + "'"
        xmlLines << "usedresourcessize='" + getHeaderValue(header, "usedresourcessize", "l") + "' >"

        def headerValues = getHeaderValues(header, "analysis", [])
        xmlLines << "  <availableAnalyses>"

        headerValues.each {
            String analysis ->
                String[] split = analysis.split(StringConstants.COMMA)
                if (split.size() < 3) {
                    ConfigurationFactory.logger.severe("The analysis string ${analysis} in the Bash configuration file is malformed.")
                    return
                }
                String id = split[0]
                String cfg = split[1]
                String plg = split[2]
                xmlLines << "    <analysis id='${id}' configuration='${cfg}' useplugin='${plg}' />".toString()
        }
        xmlLines << "  </availableAnalyses>"
        return xmlLines
    }

    /** Convert the configuration values.**/
    private List<String> convertConfigurationValues(List<String> allLines, List<String> header, String previousLine) {
        List<String> xmlLines = []
        xmlLines << "  <configurationvalues>"

        allLines[header.size()..-1].each { String line ->
            String it = line.trim()
            String[] s = it.trim().split("[=]")
            if (!it) return
            if (!s) return

            if (isBashPipe(it)) {

            } else if (isBashComment(it)) {
                xmlLines << ("""<!-- ${it} -->""").toString()
            } else {
                if (s.size() == 2)
                    xmlLines << ("""    <cvalue name='${s[0].trim()}' value='${s[1]}' type='string' />""").toString()
                else if (s.size() == 1)
                    xmlLines << ("""    <cvalue name='${s[0].trim()}' value='' type='string' />""").toString()
                else
                    xmlLines << ("""    <cvalue name='${s[0].trim()}' value='${s[1..-1].join("=")}' type='string' />""").toString()
            }
            previousLine = it
        }
        xmlLines << "  </configurationvalues>"
        return xmlLines
    }

    static String getHeaderValue(List<String> header, String id, String defaultValue) {
        def found = header.find { String line -> line.startsWith("#${id}") }
        if (!found) return defaultValue
        return found.split(StringConstants.WHITESPACE)[1]
    }

    static List<String> getHeaderValues(List<String> header, String id, List<String> defaultValue) {
        def found = header.findAll { String line -> line.startsWith("#${id}") }
        if (!found) return defaultValue
        return found.collect { String entry -> entry.split(StringConstants.WHITESPACE)[1] }
    }

    static boolean isBashComment(String it) {
        it.trim().startsWith("#")
    }

    static boolean isBashPipe(String it) {
        it.trim().startsWith("###")
    }
}
