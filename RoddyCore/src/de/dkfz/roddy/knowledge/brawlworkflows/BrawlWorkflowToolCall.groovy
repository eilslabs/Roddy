package de.dkfz.roddy.knowledge.brawlworkflows

import de.dkfz.roddy.config.ConfigurationError
import de.dkfz.roddy.config.OnScriptParameterFilenamePattern
import de.dkfz.roddy.config.ResourceSet
import de.dkfz.roddy.config.ResourceSetSize
import de.dkfz.roddy.config.ToolEntry
import de.dkfz.roddy.config.ToolFileParameter
import de.dkfz.roddy.config.ToolFileParameterCheckCondition
import de.dkfz.roddy.knowledge.files.BaseFile
import de.dkfz.roddy.knowledge.files.FileObject
import de.dkfz.roddy.plugins.LibrariesFactory
import de.dkfz.roddy.tools.BufferValue
import de.dkfz.roddy.tools.TimeUnit
import groovy.transform.CompileStatic

@CompileStatic
class BrawlWorkflowToolCall {

    private BrawlWorkflow workflow
    private TimeUnit timeUnit
    private String script
    private float memory
    private int threads
    private String toolID
    private String basePath
    private List<ToolFileParameter> inputParameters = []
    private List<ToolFileParameter> outputParameters = []

    BrawlWorkflowToolCall(BrawlWorkflow wf, String toolID, String path = null) {
        workflow = wf
        this.toolID = toolID
    }

    ToolFileParameter getToolFileParameter(String fileclass, String parameter) {
        Class loadedFileClass = LibrariesFactory.instance.loadRealOrSyntheticClass(fileclass, BaseFile.class as Class<FileObject>)
        new ToolFileParameter(loadedFileClass as Class<BaseFile>, [], parameter, new ToolFileParameterCheckCondition(true))
    }

    void input(String fileclass, String parameter, String pattern = "") {
        inputParameters << getToolFileParameter(fileclass, parameter)
        // For later...
        //        new OnScriptParameterFilenamePattern(loadedFileClass, toolID, parameter, pattern)
    }

    void output(String fileclass, String parameter, String pattern) {
        def fileParameter = getToolFileParameter(fileclass, parameter)
        outputParameters << fileParameter
        workflow.context.configuration.filenamePatterns << new OnScriptParameterFilenamePattern(fileParameter.fileClass as Class<BaseFile>, toolID, parameter, pattern)
    }

    void threads(int threads) {

        this.threads = threads
    }

    void cores(int cores) {
        this.threads(cores)
    }

    void memory(float memory) {

        this.memory = memory
    }

    void walltime(String walltime) {
        timeUnit = new TimeUnit(walltime)
    }

    void path(String path) {
        basePath = path
    }

    void shell(String script) {
        this.script = script
    }

    void file(String file) {

    }

    ToolEntry toToolEntry() {

        if (basePath == null && script == null)
            throw new ConfigurationError("The rule ${toolID} in your workflow script needs either a reference to a file OR inline code.", "BRAWLWORKFLOW")

        if(script) { // Inline code
            ToolEntry entry = new ToolEntry(toolID, "inlineScripts", toolID)
            entry.setInlineScript(script)
            entry.setInlineScriptName(toolID)
            entry.getResourceSets().add(new ResourceSet(ResourceSetSize.l, new BufferValue(memory), threads, 1, timeUnit, null, null, null))
            entry.inputParameters.addAll(inputParameters)
            entry.outputParameters.addAll(outputParameters)
            return entry
        } else { // No inline code
            ToolEntry entry = new ToolEntry(toolID, basePath, toolID)
        }
    }
}