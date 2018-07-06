/*
 * Copyright (c) 2017 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.knowledge.methods

import de.dkfz.roddy.config.*
import de.dkfz.roddy.config.converters.BashConverter
import de.dkfz.roddy.core.ExecutionContext
import de.dkfz.roddy.core.ExecutionContextError
import de.dkfz.roddy.execution.jobs.BEJobResult
import de.dkfz.roddy.execution.jobs.Job
import de.dkfz.roddy.knowledge.files.BaseFile
import de.dkfz.roddy.knowledge.files.FileGroup
import de.dkfz.roddy.knowledge.files.FileObject
import de.dkfz.roddy.knowledge.files.FileObjectTupleFactory
import de.dkfz.roddy.tools.LoggerWrapper
import de.dkfz.roddy.tools.RoddyIOHelperMethods

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

import static de.dkfz.roddy.execution.jobs.JobConstants.*

/**
 * Class for generic, configurable methods
 * This class is very magic and does quite a lot of stuff.
 * Unfortunately it is also very heavy and bulky... So be advised to take your time when digging through this.
 *
 * The class is externally called via two static methods. Internally, for convenience, an object is created
 * and used for the furhter process.
 */
@groovy.transform.CompileStatic
class GenericMethod {

    private static final LoggerWrapper logger = LoggerWrapper.getLogger(GenericMethod.class.getSimpleName());

    /**
     * This method is basically a wrapper around callGenericToolOrToolArray.
     * Both methods were so similar, that it made no sense to keep both.
     * callGenericToolOrToolArray can handle single job calls very well.
     * @param toolName The tool id to call
     * @param input The basic input object TODO should be extended or changed to handle FileGroups as well.
     * @param additionalInput Any additional input to the job. The input must fit the tool i/o specs in your xml file.
     * @return
     */
    // TODO: Roddy 4: Change the interface to use FileObject as input. Currently the method is used throughout plugins. Change now breaks bytecode compatibility.
    @Deprecated
    static <F extends FileObject> F callGenericTool(String toolName, BaseFile input, Object... additionalInput) {
        F result = new GenericMethod(toolName, null, input, null, additionalInput)._callGenericToolOrToolArray();
        return result;
    }

    // TODO: This is a temporary solution. Rename to just call. See above.
    static <F extends FileObject> F callGenericTool_fileObject(String toolName, FileObject input, Object... additionalInput) {
        F result = new GenericMethod(toolName, null, input, null, additionalInput)._callGenericToolOrToolArray();
        return result;
    }

    /**
     * If you need a file group output and the files in the group need an index, then this is the right method to call!
     * @return
     */
    static <F extends FileGroup> F callGenericToolWithFileGroupOutput(String toolName, FileObject input, List<String> indices, Object... additionalInput) {
        F result = new GenericMethod(toolName, null, input, indices, additionalInput)._callGenericToolOrToolArray() as F
        return result
    }

    /**
     * If you need a file group output and the files in the group need an index, then this is the right method to call! This one works with number for the files from 0 .. n -1
     * @return
     */
    static <F extends FileGroup> F callGenericToolWithFileGroupOutput(String toolName, FileObject input, int numericCount, Object... additionalInput) {
        F result = new GenericMethod(toolName, null, input, numericCount, additionalInput)._callGenericToolOrToolArray() as F
        return result
    }

    /**
     * If you need a file group output and the files are already set in the cfg, then this is the right method to call.
     * @return
     */
    static <F extends FileGroup> F callGenericToolWithFileGroupOutput(String toolName, FileObject input, Object... additionalInput) {
        F result = new GenericMethod(toolName, null, input, null, additionalInput)._callGenericToolOrToolArray() as F
        return result
    }

    /**
     * This is Roddys most magic method!
     * It uses all the input parameters and the tool description and magically assembles a full job object.
     * The generated tool object is run() at the end.
     *
     * @param toolName The tool id to call
     * @param arrayIndices If it is an array, we need indices
     * @param input The basic input object TODO should be extended or changed to handle FileGroups as well.
     * @param additionalInput Any additional input to the job. The input must fit the tool i/o specs in your xml file.
     * @return
     */
    public static <F extends FileObject> F callGenericToolOrToolArray(String toolName, List<String> arrayIndices, FileObject input, Object... additionalInput) {
        new GenericMethod(toolName, arrayIndices, input, null, additionalInput)._callGenericToolOrToolArray();
    }

    /**
     * The context object by which this GenericMethod object is created
     */
    private final ExecutionContext context

    /**
     * The context configuration (a shortcut)
     */
    private final Configuration configuration

    /**
     * The tools (id) which is called
     */
    private final String toolName

    /**
     * The tool entry taken from the contexts configuration and identified by toolName
     */
    private final ToolEntry calledTool

    /**
     * If this is an array job, this variable contains the arrays indices
     */
    private final List<String> arrayIndices

    /**
     * This field is only set if you need index values for output file group objects (specifically said: String indices!)
     */
    private final List<String> outputFileGroupIndices

    /**
     * The primary input object. It must not be null! The context object is taken from this input object.
     */
    private final FileObject inputObject

    /**
     * Currently supported are filegroups and basefiles as input objects. In both cases this variable is filled being inputObject for BaseFile objects and .getFirst() for FileGroups
     */
    private final BaseFile firstInputFile

    /**
     * Any additional passed input objects like filegroups or string parameters.
     */
    private final Object[] additionalInput

    /**
     * A combined list of all input values. This list also contains entries from e.g. the configuration and e.g. from the command factory.
     */
    private final List<FileObject> allInputValues = []

    /**
     * A list of all input files (e.g. also taken from input file groups.
     */
    private final List<BaseFile> allInputFiles = [];

    /**
     * The final map of parameters which will be passed to the job.
     */
    private final LinkedHashMap<String, Object> parameters = [:]

    /**
     * A list of all created file objects, also the files from file groups.
     */
    private final List<FileObject> allCreatedObjects = [];

    /**
     *
     */
    private Configuration jobConfiguration

    GenericMethod(String toolName, List<String> arrayIndices, FileObject inputObject, int numericCount, Object... additionalInput) {
        this(toolName, arrayIndices, inputObject, (0..numericCount - 1) as List<String>, additionalInput)
        if (numericCount <= 0)
            throw new NegativeArraySizeException("It is not allowed to call GenericMethod with a negative count for a FileGroup output object.")
    }

    GenericMethod(String toolName, List<String> arrayIndices, FileObject inputObject, List<String> outputFileGroupIndices, Object... additionalInput) {
        this.outputFileGroupIndices = outputFileGroupIndices
        if (outputFileGroupIndices != null && outputFileGroupIndices.size() == 0)
            throw new RuntimeException("It is not allowed to call GenericMethod with an empty non null list of file group indices.")

        this.context = inputObject.getExecutionContext()
        this.toolName = toolName
        this.configuration = context.getConfiguration()
        this.calledTool = configuration.getTools().getValue(toolName)

        this.additionalInput = additionalInput
        this.inputObject = inputObject
        this.allInputValues << inputObject
        if (inputObject instanceof FileGroup) {
            this.firstInputFile = (inputObject as FileGroup).getFilesInGroup().get(0) // Might be null at some point... Should we throw something?
        } else if (inputObject instanceof BaseFile) {
            this.firstInputFile = inputObject as BaseFile
        } else {
            // This is not supported yet! Throw an exception.
            throw new RuntimeException("It is not allowed to use GenericMethod objects without input objects.")
        }
        this.arrayIndices = arrayIndices
    }

    private void createJobConfigurationFromParameterMap() {
        jobConfiguration = context.createJobConfiguration()
        RecursiveOverridableMapContainer configurationValues = jobConfiguration.configurationValues
        configurationValues.putAll(parameters.findAll { k, v ->
            // If FileObjects would go into ConfigurationValue (as String) they would no get their variables evaluated.
            // Furthermore input files do not need dependency on job-specific parameters, because they were created by
            // earlier jobs.
            ! (v instanceof FileObject)
        }.collectEntries { k, v ->
                [(k), new ConfigurationValue(k, v as String)]
        } as Map<String, ConfigurationValue>)
    }

    private updateParameters() {
        parameters.putAll(parameters.findAll { k, v ->
            // If FileObjects would go into ConfigurationValue (as String) they would not get their variables evaluated.
            // Furthermore input files do not need dependency on job-specific parameters, because they were created by
            // earlier jobs.
            ! (v instanceof FileObject)
        }.keySet().collectEntries { k ->
            [k, jobConfiguration.configurationValues.getString(k)]
        } as Map<String, String>)
    }

    public <F extends FileObject> F _callGenericToolOrToolArray() {

        context.setCurrentExecutedTool(calledTool);

        // Check if method may be executed
        if (!calledTool.isToolGeneric()) {
            logger.severe("Tried to call a non generic tool via the generic call method: " + toolName);
            throw new RuntimeException("Not able to context tool " + toolName);
        }

        assembleJobParameters()

        assembleInputFilesAndParameters()

        applyParameterConstraints()

        createJobConfigurationFromParameterMap()

        updateParameters()

        // TODO Allow for (multiple) groups in tuples.
        F outputObject = createOutputObject()

        List<BaseFile> filesToVerify = fillListOfCreatedObjects(outputObject)

        F result = createAndRunJob(filesToVerify, outputObject) as F

        // Finally check the result and append an error to the context.
        if (result == null) {
            def errMsg = "The job callGenericTool(${toolName}, ...) returned null."
            logger.warning(errMsg);
            context.addErrorEntry(ExecutionContextError.EXECUTION_JOBFAILED.expand(errMsg))
        }

        return result;
    }

    private void assembleJobParameters() {
        // Assemble initial parameters
        parameters[PRM_WORKFLOW_ID] = context.analysis.configuration.getName()
        if (toolName) {
            parameters[PRM_TOOL_ID] = toolName
            parameters[PRM_TOOLS_DIR] = configuration.getProcessingToolPath(context, toolName).getParent()
        }

        // Assemble additional parameters
        for (Object entry in additionalInput) {
            if (entry instanceof BaseFile)
                // assert(((BaseFile) entry).isEvaluated)
                allInputValues << (BaseFile) entry
            else if (entry instanceof FileGroup) {
                //Take a group and store all files in that group.
                allInputValues << (FileGroup) entry
            } else if (entry instanceof Map) {
                (entry as Map).forEach { k, v ->
                    if (v instanceof List)
                        parameters[k.toString()] = BashConverter.convertListToBashArrayString(v)
                    else if (v instanceof Map)
                        parameters[k.toString()] = BashConverter.convertMapToBashMapString(v)
                    else
                        parameters[k.toString()] = v.toString()
                }
            } else {               // Catch-all, in case one still wants to use a string with '=' to define a parameter (deprecated).
                String[] split = entry.toString().split("=")
                if (split.length != 2)
                    throw new RuntimeException("Unable to convert entry ${entry.toString()} to parameter.")
                parameters[split[0]] = split[1]
                // Put null for things which are treated as "normal" parameters (parameter file).
                // The null entry is a placeholder and will be ignored in followup steps.
                allInputValues << null
            }
        }
    }

    private void assembleInputFilesAndParameters() {
        for (FileObject fo in allInputValues) {
            if (fo instanceof BaseFile)
                allInputFiles << (BaseFile) fo;
            else if (fo instanceof FileGroup)
                allInputFiles.addAll(((FileGroup) fo).getFilesInGroup());
        }

        for (int i = 0; i < calledTool.getInputParameters(context).size(); i++) {
            ToolEntry.ToolParameter toolParameter = calledTool.getInputParameters(context)[i];
            if (toolParameter instanceof ToolFileParameter) {
                ToolFileParameter _tp = (ToolFileParameter) toolParameter;
                if(allInputValues.size() <= i ) {
                    throw new ConfigurationError("Not all input parameters were set for ${calledTool.id}. Expected ${calledTool.getInputParameters(context).size()}. Breaking loop and trying to go on.", "GenericMethod")
                }
                if(allInputValues[i] == null){
                    logger.severe("There is an input mismatch for the tool ${calledTool.id}. Expected ${_tp.fileClass} but got null. Trying to go on.")
                    continue
                }
                if (!allInputValues[i].class == _tp.fileClass){
                    throw new ConfigurationError("Class mismatch for ${allInputValues[i]} should be of class ${_tp.fileClass}.", "GenericMethod");
                }
                if (_tp.scriptParameterName) {
                    parameters[_tp.scriptParameterName] = ((BaseFile) allInputValues[i]);
                }
                _tp.constraints.each {
                    ToolEntry.ToolConstraint constraint ->
                        constraint.apply(firstInputFile);
                }
            } else if (toolParameter instanceof ToolTupleParameter) {
                ToolTupleParameter _tp = (ToolTupleParameter) toolParameter;
                logger.severe("Tuples must not be used as an input parameter for tool ${toolName}.");
            } else if (toolParameter instanceof ToolFileGroupParameter) {
                ToolFileGroupParameter _tp = toolParameter as ToolFileGroupParameter;
                if (!allInputValues[i].class == _tp.groupClass)
                    logger.severe("Class mismatch for ${allInputValues[i]} should be of class ${_tp.groupClass}.");
                if (_tp.passOptions == ToolFileGroupParameter.PassOptions.parameters) {
                    int cnt = 0;
                    for (BaseFile bf in (List<BaseFile>) ((FileGroup) allInputValues[i]).getFilesInGroup()) {
                        parameters[_tp.scriptParameterName + "_" + cnt] = bf;
                        cnt++;
                    }
                } else { //Arrays
                    parameters[_tp.scriptParameterName] = (FileGroup) allInputValues[i]
                }
            }
        }
    }

    private void applyParameterConstraints() {
        for (int i = 0; i < calledTool.getOutputParameters(context.getConfiguration()).size(); i++) {
            ToolEntry.ToolParameter toolParameter = calledTool.getOutputParameters(context.getConfiguration())[i];
            if (toolParameter instanceof ToolFileParameter) {
                ToolFileParameter _tp = (ToolFileParameter) toolParameter;
                for (ToolEntry.ToolConstraint constraint in _tp.constraints) {
                    constraint.apply(firstInputFile);
                }
            }
        }
    }

    private <F extends FileObject> F createOutputObject(String arrayIndex = null) {
        F outputObject = null;
        def configuration = firstInputFile.getExecutionContext().getConfiguration()
        if (calledTool.getOutputParameters(configuration).size() == 1) {
            ToolEntry.ToolParameter tparm = calledTool.getOutputParameters(configuration)[0];
            if (tparm instanceof ToolFileParameter) {
                outputObject = createOutputFile(tparm as ToolFileParameter) as F

            } else if (tparm instanceof ToolTupleParameter) {
                outputObject = createOutputTuple(tparm as ToolTupleParameter) as F

            } else if (tparm instanceof ToolFileGroupParameter) {
                outputObject = createOutputFileGroup(tparm as ToolFileGroupParameter) as F
            }
        }

        return outputObject;
    }

    private FileObject createOutputFile(ToolFileParameter tparm) {
        ToolFileParameter fileParameter = tparm;
        BaseFile bf = convertToolFileParameterToBaseFile(fileParameter)
        for (ToolFileParameter childFileParameter in fileParameter.getFiles()) {
            try {
                if (childFileParameter.parentVariable == null) {
                    continue;
                }
                Field _field = null;
                Method _method = null;
                try {
                    _field = bf.getClass().getField(childFileParameter.parentVariable);
                } catch (Exception ex) {
                }
                try {
                    String setterMethod = "set" + childFileParameter.parentVariable[0].toUpperCase() + childFileParameter.parentVariable[1..-1];
                    _method = bf.getClass().getMethod(setterMethod, childFileParameter.fileClass);
                } catch (Exception ex) {
                }
                if (_field == null && _method == null) {
                    try {
                        context.addErrorEntry(ExecutionContextError.EXECUTION_FILECREATION_FIELDINACCESSIBLE.expand("Class ${bf.getClass().getName()} field ${childFileParameter.parentVariable}"));
                    } catch (Exception ex) {
                    }
                    continue;
                }
                BaseFile childFile = convertToolFileParameterToBaseFile(childFileParameter, null, bf, [bf]);
                allCreatedObjects << childFile;
                if (_field)
                    _field.set(bf, childFile);
                else
                    _method.invoke(bf, childFile);
            } catch (Exception ex) {
                logger.warning(ex as String)
            }
        }
        return fileParameter.fileClass.cast(bf) as FileObject;
    }

    FileObject createOutputTuple(ToolTupleParameter tfg) {
        //TODO Auto recognize tuples?
        List<FileObject> filesInTuple = [];
        for (ToolFileParameter fileParameter in tfg.files) {
            BaseFile bf = convertToolFileParameterToBaseFile(fileParameter);
            filesInTuple << bf;
            allCreatedObjects << bf;
        }
        return FileObjectTupleFactory.createTuple(filesInTuple);
    }

    FileObject createOutputFileGroup(ToolFileGroupParameter tfg) {
        List<BaseFile> filesInGroup = [];
        if (tfg.files) {
            // Actually this is more like a tuple.
            /**
             * This can only be the case if files is set. Otherwise we need a different way to identify files. E.g. based on index values... How do we set them?
             */
            for (ToolFileParameter fileParameter in tfg.files) {
                BaseFile bf = convertToolFileParameterToBaseFile(fileParameter)
                filesInGroup << bf;
                allCreatedObjects << bf;
            }
        } else {

            // Indices need to be set! Otherwise throw an Exception
            if (!outputFileGroupIndices) {
                throw new RuntimeException("A tool which outputs a filegroup with index values needs to be called properly! Pass index values in the call.")
            }
            ToolFileParameter autoToolFileParameter = new ToolFileParameter(tfg.genericFileClass, [], tfg.scriptParameterName, new ToolFileParameterCheckCondition(true))
            for (Object index in outputFileGroupIndices) {
                BaseFile bf = convertToolFileParameterToBaseFile(autoToolFileParameter, index.toString())
                filesInGroup << bf
                allCreatedObjects << bf
            }
            parameters.remove(tfg.scriptParameterName)
        }
        if (tfg.passOptions == ToolFileGroupParameter.PassOptions.parameters) {
            int cnt = 0;
            for (BaseFile bf in (List<BaseFile>) filesInGroup) {
                parameters[tfg.scriptParameterName + "_" + cnt] = bf;
                cnt++;
            }
        } else { //Arrays
            parameters[tfg.scriptParameterName] = filesInGroup
        }

        Constructor cGroup = tfg.groupClass.getConstructor(List.class);
        FileGroup bf = cGroup.newInstance(filesInGroup);
        return tfg.groupClass.cast(bf) as FileObject;
    }

    private List<BaseFile> fillListOfCreatedObjects(FileObject outputObject) {
        allCreatedObjects << outputObject;

        //Verifiable output files:
        return allCreatedObjects.findAll { FileObject fo -> fo instanceof BaseFile && !((BaseFile) fo).isTemporaryFile() } as List<BaseFile>
    }

    private FileObject createAndRunJob(List<BaseFile> filesToVerify, FileObject outputObject) {
        BEJobResult jobResult = new Job(context, context.createJobName(firstInputFile, toolName), toolName, parameters, allInputFiles, filesToVerify).run()

        if (allCreatedObjects) {
            for (FileObject fo in allCreatedObjects) {
                if (fo == null)
                    continue
                fo.setCreatingJobsResult(jobResult)
            }
        }
        return outputObject;
    }

    BaseFile convertToolFileParameterToBaseFile(ToolFileParameter fileParameter) {
        convertToolFileParameterToBaseFile(fileParameter, null, firstInputFile, allInputFiles)
    }

    BaseFile convertToolFileParameterToBaseFile(ToolFileParameter fileParameter, String fileGroupIndexValue) {
        convertToolFileParameterToBaseFile(fileParameter, fileGroupIndexValue, firstInputFile, allInputFiles)
    }

    /**
     *
     * @param fileParameter
     * @param fileGroupIndexValue May be null or empty! Is set, when a filegroup and its children are created. Holds the index value within the filegroup (numeric or a string)
     * @param firstInputFile
     * @param allInputFiles
     * @return
     */
    BaseFile convertToolFileParameterToBaseFile(ToolFileParameter fileParameter, String fileGroupIndexValue, BaseFile firstInputFile, List<BaseFile> allInputFiles) {
        Constructor c = searchBaseFileConstructorForConstructionHelperObject(fileParameter.fileClass);
        BaseFile bf;
        try {
            if (c == null) {
                // Actually this must not be the case! If a developer has its custom class, it must provide this constructor.

                //TODO URGENT
                //The underlying error is that a configuration file has e.g. a typo, or not? Such kind of errors are user errors, where there is a clear cause (line X in file Y contains garbage Z). Ideally we would just display an error message with as much information possible to allow the user to fix the error, but no stack trace.

                context.addErrorEntry(ExecutionContextError.EXECUTION_FILECREATION_NOCONSTRUCTOR.expand("File object of type ${fileParameter?.fileClass} with input ${firstInputFile?.class} needs a constructor which takes a ConstuctionHelper object."));
                throw new RuntimeException("Could not find valid constructor for type  ${fileParameter?.fileClass} with input ${firstInputFile?.class}.");
            } else {
                BaseFile.ConstructionHelperForGenericCreation helper = new BaseFile.ConstructionHelperForGenericCreation(firstInputFile, allInputFiles as List<FileObject>, calledTool, toolName, fileParameter.scriptParameterName, fileParameter.filenamePatternSelectionTag, fileGroupIndexValue, firstInputFile.fileStage, null);
                if(jobConfiguration) helper.setJobConfiguration(jobConfiguration)
                bf = c.newInstance(helper);
            }
        } catch (Exception ex) {
            context.addErrorEntry(ExecutionContextError.EXECUTION_FILECREATION_NOCONSTRUCTOR.expand("Error during constructor call."));
            throw (ex);
        }

        if (!fileParameter.checkFile.evaluate(context))
            bf.setAsTemporaryFile();

        if (allInputFiles.size() > 1)
            bf.setParentFiles(allInputFiles, true);

        if (fileParameter.scriptParameterName) {
            parameters[fileParameter.scriptParameterName] = bf;
        }
        bf
    }

    /**
     * Get the BaseFile extending classes constructor for Construction Helper Objects
     * @param classToSearch
     * @return
     */
    static Constructor<BaseFile> searchBaseFileConstructorForConstructionHelperObject(Class classToSearch) {
        try {
            return classToSearch.getConstructor(BaseFile.ConstructionHelperForBaseFiles);
        } catch (Exception e) {
            logger.severe("There was no valid constructor found for class ${classToSearch?.name}! Roddy needs a constructor which accepts a construction helper object.")
            logger.postSometimesInfo(e.message)
            logger.postSometimesInfo(RoddyIOHelperMethods.getStackTraceAsString(e))
            return null
        }
    }
}
