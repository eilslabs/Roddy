/*
 * Copyright (c) 2017 German Cancer Research Center (DKFZ).
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.core

import de.dkfz.roddy.config.ConfigurationError
import de.dkfz.roddy.config.ConfigurationValue
import de.dkfz.roddy.execution.UnexpectedExecutionResultException
import de.dkfz.roddy.execution.io.ExecutionService
import de.dkfz.roddy.knowledge.files.BaseFile
import de.dkfz.roddy.knowledge.files.FileGroup
import de.dkfz.roddy.knowledge.files.FileObject
import de.dkfz.roddy.knowledge.methods.GenericMethod

import java.lang.reflect.Method
import java.util.concurrent.ExecutionException

/**
 * A worklow can be created and executed to process a set of data.
 *
 * @author michael
 */
abstract class Workflow {

    /**
     * Keep this private so it cannot be overriden. Setter allows to set it ONCE.
     */
    private ExecutionContext context

    Workflow() {
    }

    ExecutionContext getContext() {
        return context
    }

    void setContext(ExecutionContext context) {
        if (this.context == null)
            this.context = context
        else if (this.context != context)
            throw new RuntimeException("It is not allowed to reset the context object of a workflow.") // This is a programming error
    }

    /**
     * Workflow specific setups can be created here.
     * This includes i.e. the creation of paths.
     */
    boolean setupExecution(ExecutionContext context) {
        true
    }

    boolean setupExecution() {
        return setupExecution(context)
    }

    boolean finalizeExecution(ExecutionContext context) {
        true
    }

    /**
     * Override this method to enable initial checks for a workflow run.
     * I.e. check if input files can be found.
     *
     * @param context
     * @return
     */
    boolean checkExecutability(ExecutionContext context) {
        return true
    }

    boolean checkExecutability() {
        return checkExecutability(context)
    }

    abstract boolean execute(ExecutionContext context) throws ConfigurationError

    boolean hasCleanupMethod() {
        for (Method m : this.getClass().getMethods()) {
            if (m.getName().equals("cleanup") && m.getDeclaringClass() == this.getClass()) {
                return true
            }
        }
        return false
    }

    boolean cleanup(DataSet dataset) {
        return false
    }

    boolean cleanup() {
        return cleanup(context.dataSet)
    }

    @Deprecated
    final boolean getflag(String flagID, boolean defaultValue = true) {
        getFlag(flagID, defaultValue)
    }

    /**
     * Convenience method to get a boolean runflag from the context config, defaults to defaultValue
     *
     * @param flagID
     * @param defaultValue Defaults to true
     * @return
     */
    final boolean getFlag(String flagID, boolean defaultValue = true) {
        return context.getConfiguration().getConfigurationValues().getBoolean(flagID, defaultValue)
    }

    final void setEnv(String id, String value, String type) { cvalue(id, value, type)  }

    final void setEnvInt(String id, int value) { cvalue(id, value) }

    final void setEnvFloat(String id, float value) { cvalue(id, value) }

    final void setEnvDouble(String id, double value) { cvalue(id, value) }

    final void setEnvBoolean(String id, boolean value) { cvalue(id, value) }

    final void setEnvBashArray(String id, String value) { cvalue(id, value, "bashArray") }

    def getEnv(String id) {
        ConfigurationValue value = context.configuration.configurationValues[id]
        if(value.type == "integer") {
            return value.toInt()
        } else if(value.type == "float") {
            return value.toFloat()
        } else if(value.type == "double") {
            return value.toDouble()
        } else if(value.type == "boolean") {
            return value.toBoolean()
        } else if(value.type == "bashArray") {
            return value.toStringList()
        } else  {
            return value.toString()
        }
    }

    final int getEnvInt(String id) { return context.configuration.configurationValues[id].toInt() }

    final float getEnvFloat(String id) { return context.configuration.configurationValues[id].toFloat() }

    final double getEnvDouble(String id) { return context.configuration.configurationValues[id].toDouble() }

    final boolean getEnvBoolean(String id) { return context.configuration.configurationValues[id].toBoolean() }

    final List getEnvBashArray(String id) { return context.configuration.configurationValues[id].toStringList() }

    /**
     * Add a "generic" configuration value.
     * @param id
     * @param value
     * @param type
     */
    final void cvalue(String id, String value, String type) {
        context.configurationValues << new ConfigurationValue(context.configuration, id, value, type)
    }

    /**
     * Add an integer configuration value
     * @param id
     * @param value
     */
    final void cvalue(String id, int value) {
        context.configurationValues << new ConfigurationValue(context.configuration, id, value.toString(), "integer")
    }

    /**
     * Add a float configuration value
     * @param id
     * @param value
     */
    final void cvalue(String id, float value) {
        context.configurationValues << new ConfigurationValue(context.configuration, id, value.toString(), "float")
    }

    /**
     * Add a double configuration value
     * @param id
     * @param value
     */
    final void cvalue(String id, double value) {
        context.configurationValues << new ConfigurationValue(context.configuration, id, value.toString(), "double")
    }

    /**
     * Add a boolean configuration value
     * @param id
     * @param value
     */
    final void cvalue(String id, boolean value) {
        context.configurationValues << new ConfigurationValue(context.configuration, id, value.toString(), "boolean")
    }

    /**
     * Convenience method to call GenericMethod.callGenericTool()
     *
     * @param toolName
     * @param input
     * @param additionalInput
     * @return
     */
    // TODO: Roddy 4: Change to FileObject input. Currently this change would break bytecode compatibility.
    @Deprecated
    final FileObject call(String toolName, BaseFile input, Object... additionalInput) {
        return GenericMethod.callGenericTool_fileObject(toolName, input, additionalInput)
    }

    // TODO: This is a temporary solution. Rename to just call. See above.
    final FileObject call_fileObject(String toolName, FileObject input, Object... additionalInput) {
        return GenericMethod.callGenericTool_fileObject(toolName, input, additionalInput)
    }

    final FileGroup callWithOutputFileGroup(String toolName, FileObject input, int numericCount, Object... additionalInput) {
        return GenericMethod.callGenericToolWithFileGroupOutput(toolName, input, numericCount, additionalInput)
    }

    final FileGroup callWithOutputFileGroup(String toolName, FileObject input, List<String> indices, Object... additionalInput) {
        return GenericMethod.callGenericToolWithFileGroupOutput(toolName, input, indices, additionalInput)
    }

    /**
     * Call the tool toolID with parameters synchronously and using the wrapInScript.
     *
     * @param toolID
     * @param parameters
     * @return
     */
    final List<String> callDirect(String toolID, Map<String, Object> parameters) {
        return ExecutionService.getInstance().runDirect(context, toolID, parameters)
    }

    /**
     * Introduced in BrawlWorkflow because of some issues with Groovy and the call method. Just for code completeness
     */
    final FileObject run(String toolName, FileObject input, Object... additionalInput) {
        return GenericMethod.callGenericTool_fileObject(toolName, input, additionalInput);
    }

    /**
     * Introduced in BrawlWorkflow because of some issues with Groovy and the call method. Just for code completeness
     */
    final FileGroup runWithOutputFileGroup(String toolName, FileObject input, int numericCount, Object... additionalInput) {
        return GenericMethod.callGenericToolWithFileGroupOutput(toolName, input, numericCount, additionalInput);
    }

    /**
     * Introduced in BrawlWorkflow because of some issues with Groovy and the call method. Just for code completeness
     */
    final FileGroup runWithOutputFileGroup(String toolName, FileObject input, List<String> indices, Object... additionalInput) {
        return GenericMethod.callGenericToolWithFileGroupOutput(toolName, input, indices, additionalInput);
    }

    /**
     * Introduced in BrawlWorkflow because of some issues with Groovy and the call method. Just for code completeness
     */
    final List<String> runDirect(String toolID, Map<String, Object> parameters) {
        return ExecutionService.getInstance().runDirect(context, toolID, parameters);
    }
    
    final BaseFile file(String path, String _class = BaseFile.STANDARD_FILE_CLASS) {
        return getSourceFile(path, _class)
    }

    final BaseFile getFile(String path, String _class = BaseFile.STANDARD_FILE_CLASS) {
        return getSourceFile(path, _class)
    }

    /**
     * Instantiate a source file object representing a file on storage.
     *
     * @param path Pathname string to the file (remote or local)
     * @param _class The class name of the new file object. This may be an existing or a new class (which will then be created).
     *               Defaults to BaseFile.STANDARD_FILE_CLASS
     * @return
     */
    final BaseFile getSourceFile(String path, String _class = BaseFile.STANDARD_FILE_CLASS) {
        return BaseFile.fromStorage(context, path, _class)
    }

    /**
     * Instantiate a single source file object representing a file on storage.
     *
     * @param toolID String representing a tool ID.
     * @param _class The class name of the file object to return. Defaults to BaseFile.STANDARD_FILE_CLASS
     * @return
     */
    final BaseFile getSourceFileUsingTool(String toolID, String _class = BaseFile.STANDARD_FILE_CLASS)
            throws ExecutionException, UnexpectedExecutionResultException {
        return BaseFile.getSourceFileUsingTool(context, toolID, _class)
    }

    /**
     * Like getSourceFileUsingToo(ExecutionContext, String, String) but returning multiple file objects.
     *
     * @param toolID
     * @param _class Defaults to BaseFile.STANDARD_FILE_CLASS
     * @return
     */
    final List<BaseFile> getSourceFilesUsingTool(String toolID, String _class = BaseFile.STANDARD_FILE_CLASS)
            throws ExecutionException {
        return BaseFile.getSourceFilesUsingTool(context, toolID, _class)
    }

    /**
     * Easily create a file which inherits from another file.
     *
     * @param parent The file from which the new file object inherits
     * @param _class The class of the new file object. This may be an existing or a new class (which will then be created) Defaults to BaseFile.STANDARD_FILE_CLASS
     * @return
     */
    final BaseFile getDerivedFile(BaseFile parent, String _class = BaseFile.STANDARD_FILE_CLASS) {
        return BaseFile.deriveFrom(parent, _class)
    }

    //////////////////////////////////////////////////
    // Methods will be removed at some point. With 4.0
    //////////////////////////////////////////////////

    @Deprecated
    final BaseFile getSourceFile(ExecutionContext context, String path, String _class) {
        return BaseFile.fromStorage(context, path, _class)
    }

    @Deprecated
    final BaseFile getSourceFile(ExecutionContext context, String path) {
        return getSourceFile(context, path, BaseFile.STANDARD_FILE_CLASS)
    }

    @Deprecated
    final boolean getflag(ExecutionContext context, String flagID) {
        return getflag(context, flagID, true)
    }

    @Deprecated
    final boolean getflag(ExecutionContext context, String flagID, boolean defaultValue) {
        return context.getConfiguration().getConfigurationValues().getBoolean(flagID, defaultValue)
    }

    @Deprecated
    final BaseFile getSourceFileUsingTool(ExecutionContext context, String toolID)
            throws ExecutionException, UnexpectedExecutionResultException {
        return getSourceFileUsingTool(context, toolID, BaseFile.STANDARD_FILE_CLASS)
    }

    @Deprecated
    final BaseFile getSourceFileUsingTool(ExecutionContext context, String toolID, String _class)
            throws ExecutionException, UnexpectedExecutionResultException {
        return BaseFile.getSourceFileUsingTool(context, toolID, _class)
    }

    @Deprecated
    final List<BaseFile> getSourceFilesUsingTool(ExecutionContext context, String toolID, String _class)
            throws ExecutionException {
        return BaseFile.getSourceFilesUsingTool(context, toolID, _class)
    }

    @Deprecated
    final List<BaseFile> getSourceFilesUsingTool(ExecutionContext context, String toolID)
            throws ExecutionException {
        return getSourceFilesUsingTool(context, toolID, BaseFile.STANDARD_FILE_CLASS)
    }
}
