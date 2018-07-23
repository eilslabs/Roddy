/*
 * Copyright (c) 2017 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.config.loader

import de.dkfz.roddy.Constants
import de.dkfz.roddy.config.OnScriptParameterFilenamePattern
import de.dkfz.roddy.config.ResourceSet
import de.dkfz.roddy.config.ResourceSetSize
import de.dkfz.roddy.config.Configuration
import de.dkfz.roddy.config.FilenamePattern
import de.dkfz.roddy.config.ToolEntry
import de.dkfz.roddy.config.ToolFileGroupParameter
import de.dkfz.roddy.config.ToolFileParameter
import de.dkfz.roddy.config.ToolFileParameterCheckCondition
import de.dkfz.roddy.config.ToolStringParameter
import de.dkfz.roddy.config.ToolTupleParameter
import de.dkfz.roddy.knowledge.files.BaseFile
import de.dkfz.roddy.knowledge.files.FileGroup
import de.dkfz.roddy.knowledge.files.FileObjectTupleFactory
import de.dkfz.roddy.knowledge.files.GenericFileGroup
import de.dkfz.roddy.plugins.LibrariesFactory
import de.dkfz.roddy.tools.BufferValue
import de.dkfz.roddy.tools.LoggerWrapper
import de.dkfz.roddy.tools.RoddyConversionHelperMethods
import de.dkfz.roddy.tools.RoddyIOHelperMethods
import de.dkfz.roddy.tools.TimeUnit
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import groovy.util.slurpersupport.NodeChild
import groovy.util.slurpersupport.NodeChildren

import static de.dkfz.roddy.config.loader.ConfigurationFactory.extractAttributeText

/**
 * Class to load a processing tool entry from a node slurper
 * Created by heinold on 02.05.17.
 */
@CompileStatic
class ProcessingToolReader {

    static LoggerWrapper logger = LoggerWrapper.getLogger(de.dkfz.roddy.config.loader.ProcessingToolReader)

    NodeChild tool

    Configuration config

    List<ConfigurationLoadError> loadErrors = []

    String toolID

    ProcessingToolReader(NodeChild tool, Configuration config) {
        this.tool = tool
        this.config = config
    }

    boolean hasErrors() {
        return loadErrors // Auto boolean to true or false
    }

    void addLoadErr(String desc, Exception ex = null) {
        loadErrors << new ConfigurationLoadError(config, getClass().simpleName + (toolID ? " - tool: ${toolID}" : " - The id of the processed tool could not be read, is the tag toolID set?"), desc, ex)
    }

    @groovy.transform.CompileStatic(TypeCheckingMode.SKIP)
    String readAttribute(NodeChild node, String id, String _default = null) {
        String text = node.@"$id".text()
        if (text)
            return text
        else if (_default)
            text = _default
        else
            addLoadErr("Could not get attribute value for '${id}'", null)
        return text
    }

    /**
     * Read a value if it is set, otherwise return ""
     * No error is reported.
     * @param node
     * @param id
     * @return
     */
    @groovy.transform.CompileStatic(TypeCheckingMode.SKIP)
    String readAttributeOrDefault(NodeChild node, String id) {
        String text = node.@"$id".text()
        if (text)
            return text
        return ""
    }

    @groovy.transform.CompileStatic(TypeCheckingMode.SKIP)
    Collection<NodeChild> readCollection(NodeChild node, String id) {
        def collection = node."$id" as Collection<NodeChild>
        return collection
    }

    @groovy.transform.CompileStatic(TypeCheckingMode.SKIP)
    Collection<NodeChild> readCollection(NodeChildren node, String id) {
        def collection = node."$id" as Collection<NodeChild>
        return collection
    }

    /**
     * Read a tool node
     * @return
     */
    ToolEntry readProcessingTool() {
        String toolID
        try {
            toolID = readAttribute(tool, "name") //tool.@name.text()
            this.toolID = toolID
            String path = readAttribute(tool, "value")
            String basePathId = readAttribute(tool, "basepath")
            boolean overrideresourcesets = extractAttributeText(tool, "overrideresourcesets", "false").toBoolean()
            boolean useAutoCheckpoint = readAttribute(tool, "useAutoCheckpoint", "false")
            ToolEntry currentEntry = new ToolEntry(toolID, basePathId, path)
            if (overrideresourcesets)
                currentEntry.setOverridesResourceSets()
            if (useAutoCheckpoint)
                currentEntry.setUseAutoCheckpoint()
            int noOfChildren = tool.children().size()
            if (noOfChildren > 0) {
                List<ToolEntry.ToolParameter> inputParameters = []
                List<ToolEntry.ToolParameter> outputParameters = []
                List<ResourceSet> resourceSets = new LinkedList<>()
                int noOfInputParameters = 0
                int noOfOutputParameters = 0
                boolean allParametersValid = true
                for (NodeChild child in (tool.children() as List<NodeChild>)) {
                    String cName = child.name()

                    if (cName == "resourcesets") {
                        for (NodeChild rset in readCollection(child, "rset")) {
                            ResourceSet tempSet = parseToolResourceSet(rset, config)
                            if (tempSet)
                                resourceSets << tempSet
                        }
                    } else if (cName == "input") {
                        inputParameters << parseToolParameter(toolID, child)
                        noOfInputParameters++
                        allParametersValid &= inputParameters.last() != null
                    } else if (cName == "output") {
                        outputParameters << parseToolParameter(toolID, child)
                        noOfOutputParameters++
                        allParametersValid &= outputParameters.last() != null
                    } else if (cName == "script") {
                        if (readAttribute(child, "value") != "") {
                            currentEntry.setInlineScript(child.text().trim().replaceAll('<!\\[CDATA\\[', "").replaceAll(']]>', ""))
                            currentEntry.setInlineScriptName(readAttribute(child, "value"))
                        }
                    } else {
                        addLoadErr("Invalid child name '${cName}'")
                    }
                }

                if (noOfInputParameters != inputParameters.size())
                    addLoadErr("The number of read input parameters does not match to the input parameters in the configuration.", null)
                if (noOfOutputParameters != outputParameters.size())
                    addLoadErr("The number of read output parameters does not match to the output parameters in the configuration.", null)

                def allparameters = (inputParameters + outputParameters).collect {
                    if (!it) // Error is already catched in parseToolParameter, just skip it here.
                        return
                    if (it instanceof ToolTupleParameter) {
                        ((ToolTupleParameter) it).allFiles.collect { it.scriptParameterName }
                    } else if (it instanceof ToolFileGroupParameter) {
                        ToolFileGroupParameter tfg = (ToolFileGroupParameter) it
                        if (tfg.genericFileClass)
                            it.scriptParameterName
                        else
                            tfg.allFiles.collect { it.scriptParameterName }
                    } else {
                        it.scriptParameterName
                    }
                }.flatten()

                if (allparameters.size() != allparameters.unique(false).size())
                    addLoadErr("There were duplicate i/o script parameter names. This is not allowed.")
                currentEntry.setGenericOptions(inputParameters, outputParameters, resourceSets)
            }
            if (hasErrors())
                throw new ConfigurationLoaderException("There were ${loadErrors.size()} errors")
            return currentEntry
        } catch (ConfigurationLoaderException ex) {

            // In this case we actually do not need a message. ConfigurationLoaderErrors are properly handled
            // exceptions.
            return null
        } catch (Exception ex) {
            addLoadErr("ToolEntry ${toolID} could not be read with an unexpected error:\n        " +
                    (hasErrors() ? loadErrors.join("\n        ") + "\n        " : "") +
                    RoddyIOHelperMethods.getStackTraceAsString(ex), ex)
            throw ex
        }
    }

    ResourceSet parseToolResourceSet(NodeChild rset, Configuration config) {
        ResourceSet tempSet = null
        try {
            ResourceSetSize rsetSize = readAttribute(rset, "size")
            //Is it short defined or long defined?
            String valueList = extractAttributeText(rset, "values", "")
            if (!valueList) { //Must be fully specified.
                // Only parse the memory value, if it is set.
                BufferValue rsetUsedMemory
                String _rsetUsedMemory = extractAttributeText(rset, "memory", null)
                if (_rsetUsedMemory != null) rsetUsedMemory = new BufferValue(_rsetUsedMemory)

                Integer rsetUsedCores = extractAttributeText(rset, "cores", null)?.toInteger()
                Integer rsetUsedNodes = extractAttributeText(rset, "nodes", null)?.toInteger()

                TimeUnit rsetUsedWalltime
                String _rsetUsedWalltime = extractAttributeText(rset, "walltime", null)
                if (_rsetUsedWalltime != null) rsetUsedWalltime = new TimeUnit(_rsetUsedWalltime)

                String rsetUsedQueue = extractAttributeText(rset, "queue", null)
                String rsetUsedNodeFlag = extractAttributeText(rset, "nodeflag", null)
                tempSet = new ResourceSet(rsetSize, rsetUsedMemory, rsetUsedCores, rsetUsedNodes, rsetUsedWalltime, null, rsetUsedQueue, rsetUsedNodeFlag)
            }
        } catch (Exception ex) {
            if (config != null) addLoadErr("Resource set could not be read", ex)
        }
        return tempSet
    }

    /**
     * Load tool parameters like group, files and constraints...
     * @param child
     * @return
     */
    ToolEntry.ToolParameter parseToolParameter(String toolID, NodeChild child) {
        String type = readAttribute(child, "type")
        if (type == "file") { //Load a file
            return parseFile(child, toolID)
        } else if (type == "tuple") {
            return parseTuple(child, toolID)
        } else if (type == "filegroup") {
            return parseFileGroup(child, toolID)
        } else if (type == "string") {
            ToolStringParameter.ParameterSetbyOptions setby = Enum.valueOf(ToolStringParameter.ParameterSetbyOptions.class, extractAttributeText(child, "setby", ToolStringParameter.ParameterSetbyOptions.callingCode.name()))

            String pName = readAttribute(child, "scriptparameter")
            ToolStringParameter tsp
            if (setby == ToolStringParameter.ParameterSetbyOptions.callingCode) {
                tsp = new ToolStringParameter(pName)
            } else {
                tsp = new ToolStringParameter(pName, extractAttributeText(child, "cValueID"))
            }

            return tsp
        } else {
            addLoadErr("The type attribute of a parameter was invalid (${type}) for tool ${toolID}\n" + ConfigurationFactory.ERROR_PRINTOUT_XML_LINEPREFIX + RoddyConversionHelperMethods.toFormattedXML(child, "\n" + ConfigurationFactory.ERROR_PRINTOUT_XML_LINEPREFIX))
            return null
        }
    }

    ToolFileParameter parseFile(NodeChild child, String toolID) {
        String cls = readAttribute(child, "typeof")
        Class _cls = LibrariesFactory.getInstance().loadRealOrSyntheticClass(cls, BaseFile.class.name)

        String pName = readAttribute(child, "scriptparameter")
        String fnPattern = readAttributeOrDefault(child, "filename")
        String fnpSelTag = extractAttributeText(child, "selectiontag", extractAttributeText(child, "fnpatternselectiontag", Constants.DEFAULT))
        String parentFileVariable = extractAttributeText(child, "variable", null) //This is only the case for child files.
        ToolFileParameterCheckCondition check = new ToolFileParameterCheckCondition(extractAttributeText(child, "check", "true"))

        List<ToolEntry.ToolConstraint> constraints = new LinkedList<ToolEntry.ToolConstraint>()
        for (constraint in readCollection(child, "constraint")) {
            String method = readAttribute(constraint, "method")
            String methodonfail = readAttribute(constraint, "methodonfail")
            constraints << new ToolEntry.ToolConstraint(_cls.getMethod(methodonfail), _cls.getMethod(method))
        }

        // A file can have several defined child files
        List<ToolFileParameter> subParameters = new LinkedList<ToolFileParameter>()
        for (NodeChild fileChild in (child.children() as List<NodeChild>)) {
            subParameters << (ToolFileParameter) parseToolParameter(toolID, fileChild)
        }
        ToolFileParameter tp = new ToolFileParameter(_cls, constraints, pName, check, fnpSelTag, subParameters, parentFileVariable)

        if (fnPattern) {
            config.getFilenamePatterns().add(new OnScriptParameterFilenamePattern(_cls, toolID, pName, fnPattern))
        }

        return tp
    }

    ToolTupleParameter parseTuple(NodeChild child, String toolID) {
        int tupleSize = child.children().size()
        if (!FileObjectTupleFactory.isValidSize(tupleSize)) {
            logger.severe("Tuple is of wrong size for tool ${toolID}.")
        }
        List<ToolEntry.ToolParameterOfFiles> subParameters = []
        for (NodeChild fileChild in (child.children() as List<NodeChild>)) {
            subParameters << (ToolEntry.ToolParameterOfFiles)parseToolParameter(toolID, fileChild)
        }
        return new ToolTupleParameter(subParameters)
    }

    @Deprecated
    boolean isInputFileGroup(NodeChild groupNode) {
        return groupNode.name() == "input"
    }

    ToolFileGroupParameter parseFileGroup(NodeChild groupNode, String toolID) {
        String cls = extractAttributeText(groupNode, "typeof", GenericFileGroup.name)
        Class<FileGroup> filegroupClass = LibrariesFactory.getInstance().loadRealOrSyntheticClass(cls, FileGroup.class.name)
        if (!filegroupClass)
            filegroupClass = GenericFileGroup

        ToolFileGroupParameter.PassOptions passas = Enum.valueOf(ToolFileGroupParameter.PassOptions.class, extractAttributeText(groupNode, "passas", ToolFileGroupParameter.PassOptions.parameters.name()))
        ToolFileGroupParameter.IndexOptions indexOptions = Enum.valueOf(ToolFileGroupParameter.IndexOptions.class, extractAttributeText(groupNode, "indices", ToolFileGroupParameter.IndexOptions.numeric.name()))
        String selectiontag = extractAttributeText(groupNode, "selectiontag", extractAttributeText(groupNode, "fnpatternselectiontag", Constants.DEFAULT))

        String fileclass = extractAttributeText(groupNode, "fileclass", null)
        int childCount = groupNode.children().size()

        if (fileclass) {
            // The parameter name is only used if no children are set.
            String pName = readAttribute(groupNode, "scriptparameter")
            if (!pName) {
                addLoadErr("You have to set both the parametername and the fileclass attribute for filegroup i/o parameter in ${toolID}")
            }

            Class<BaseFile> genericFileClass = LibrariesFactory.getInstance().loadRealOrSyntheticClass(fileclass, BaseFile.class.name)
            ToolFileGroupParameter tpg = new ToolFileGroupParameter(filegroupClass, genericFileClass, pName, passas, indexOptions, selectiontag)
            return tpg
        } else if (childCount) {
            return parseChildFilesForFileGroup(groupNode, passas, toolID, null, filegroupClass, indexOptions, selectiontag)
        } else {
            String pName = readAttribute(groupNode, "scriptparameter")
            if (!isInputFileGroup(groupNode)) { // TODO: Enforce fileclass attributes for output filegroup <https://eilslabs-phabricator.dkfz.de/T2015>
                addLoadErr("Either the fileclass or a list of child files need to be set for a filegroup in ${toolID}")
                return null
            } else {
                return new ToolFileGroupParameter(filegroupClass, BaseFile.class, pName, passas, indexOptions, selectiontag)
            }
        }
    }

    @Deprecated
    ToolFileGroupParameter parseChildFilesForFileGroup(NodeChild groupNode, ToolFileGroupParameter.PassOptions passas, String toolID, String pName, Class filegroupClass, ToolFileGroupParameter.IndexOptions indexOptions, String selectiontag) {
        int childCount = groupNode.children().size()
        List<ToolFileParameter> children = new LinkedList<ToolFileParameter>()
        if (childCount == 0 && passas != ToolFileGroupParameter.PassOptions.array)
            logger.severe("No files in the file group. Configuration is not valid.")
        for (Object fileChild in groupNode.children()) {
            children << (parseToolParameter(toolID, fileChild as NodeChild) as ToolFileParameter)
        }
        return new ToolFileGroupParameter(filegroupClass, children, pName, passas, indexOptions, selectiontag)
    }
}
