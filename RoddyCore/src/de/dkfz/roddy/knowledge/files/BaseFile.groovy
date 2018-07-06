/*
 * Copyright (c) 2017 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.knowledge.files

import de.dkfz.roddy.config.Configuration
import de.dkfz.roddy.config.ConfigurationError
import de.dkfz.roddy.config.DerivedFromFilenamePattern
import de.dkfz.roddy.config.FileStageFilenamePattern
import de.dkfz.roddy.config.FilenamePattern
import de.dkfz.roddy.config.FilenamePatternDependency
import de.dkfz.roddy.config.OnMethodFilenamePattern
import de.dkfz.roddy.config.OnScriptParameterFilenamePattern
import de.dkfz.roddy.config.OnToolFilenamePattern
import de.dkfz.roddy.config.ToolEntry
import de.dkfz.roddy.core.ExecutionContext
import de.dkfz.roddy.core.ExecutionContextLevel
import de.dkfz.roddy.core.Workflow
import de.dkfz.roddy.execution.UnexpectedExecutionResultException
import de.dkfz.roddy.execution.io.ExecutionService
import de.dkfz.roddy.execution.io.fs.FileSystemAccessProvider
import de.dkfz.roddy.execution.jobs.BEJob
import de.dkfz.roddy.execution.jobs.BEJobResult
import de.dkfz.roddy.plugins.LibrariesFactory
import de.dkfz.roddy.tools.LoggerWrapper
import de.dkfz.roddy.tools.RoddyConversionHelperMethods
import de.dkfz.roddy.tools.Tuple2

import java.util.concurrent.ExecutionException

/**
 * Basic class for all processed files. Contains information about the storage
 * location of a file and the associated project. Might contain a list of files
 * which led to the creation of this file. Might also contain a list of files
 * which were created with this file. With this double-list implementation, you
 * can span up a file-creation-tree.
 * <p>
 * Remark: This class cannot be converted to groovy, because groovy will have
 * problems with the constructors. When a stub class is generated, several
 * constructors will match with the generated "null"-calls.
 *
 * @author michael
 */
@groovy.transform.CompileStatic
abstract class BaseFile<FS extends FileStageSettings> extends FileObject {

    private static LoggerWrapper logger = LoggerWrapper.getLogger(BaseFile)

    public static final String STANDARD_FILE_CLASS = "StandardRoddyFileClass"

    static abstract class ConstructionHelperForBaseFiles<T extends ConstructionHelperForBaseFiles> {
        public final ExecutionContext context;
        public final FileStageSettings fileStageSettings;
        public final String selectionTag
        public final BEJobResult jobResult
        public final String indexInFileGroup
        private Configuration jobConfiguration

        protected ConstructionHelperForBaseFiles(ExecutionContext context, FileStageSettings fileStageSettings, String selectionTag,
                                                 String indexInFileGroup, BEJobResult jobResult) {
            this.context = context
            this.fileStageSettings = fileStageSettings
            this.selectionTag = selectionTag
            this.jobResult = jobResult
            this.indexInFileGroup = indexInFileGroup
        }

        T setJobConfiguration(Configuration configuration) {
            this.jobConfiguration = configuration
            return (T) this
        }

        Configuration getJobConfiguration() {
            return jobConfiguration
        }
    }

    static class ConstructionHelperForSourceFiles extends ConstructionHelperForBaseFiles<ConstructionHelperForSourceFiles> {

        public final File path

        ConstructionHelperForSourceFiles(File path, ExecutionContext context, FileStageSettings fileStageSettings, BEJobResult jobResult) {
            super(context, fileStageSettings, null, null, jobResult)
            this.path = path
        }

        File getPath() {
            return path
        }
    }

    /**
     * A helper class specifically for GenericMethod based file creation
     */
    static class ConstructionHelperForGenericCreation<T extends ConstructionHelperForGenericCreation>
            extends ConstructionHelperForBaseFiles<ConstructionHelperForGenericCreation<T>> {

        public final FileObject parentObject
        public final ToolEntry creatingTool
        public final String toolID
        public final String slotID
        public final List<FileObject> parentFiles

        @Deprecated
        ConstructionHelperForGenericCreation(FileObject parentObject, List<FileObject> parentFiles, ToolEntry creatingTool, String toolID,
                                             String slotID, String selectionTag, FileStageSettings fileStageSettings, BEJobResult jobResult) {
            this(parentObject, parentFiles, creatingTool, toolID, slotID, selectionTag, null, fileStageSettings, jobResult)
        }

        ConstructionHelperForGenericCreation(FileObject parentObject, List<FileObject> parentFiles, ToolEntry creatingTool, String toolID,
                                             String slotID, String selectionTag, String indexInFileGroup, FileStageSettings fileStageSettings,
                                             BEJobResult jobResult) {
            super(parentObject.getExecutionContext(), fileStageSettings, selectionTag, indexInFileGroup, jobResult)
            this.parentFiles = parentFiles
            this.slotID = slotID
            this.toolID = toolID
            this.creatingTool = creatingTool
            this.parentObject = parentObject
        }

        ConstructionHelperForGenericCreation(ExecutionContext context, ToolEntry creatingTool, String toolID, String slotID, String selectionTag,
                                             FileStageSettings fileStageSettings, BEJobResult jobResult) {
            super(context, fileStageSettings, selectionTag, null, jobResult)
            this.slotID = slotID
            this.toolID = toolID
            this.creatingTool = creatingTool
        }
    }

    static class ConstructionHelperForManualCreation extends ConstructionHelperForGenericCreation<ConstructionHelperForManualCreation> {
        ConstructionHelperForManualCreation(FileObject parentObject, List<FileObject> parentFiles, ToolEntry creatingTool, String toolID,
                                            String slotID, String selectionTag, String indexInFileGroup, FileStageSettings fileStageSettings,
                                            BEJobResult jobResult) {
            super(parentObject, parentFiles, creatingTool, toolID, slotID, selectionTag, indexInFileGroup, fileStageSettings, jobResult);
        }

        ConstructionHelperForManualCreation(FileObject parentObject, List<FileObject> parentFiles, ToolEntry creatingTool, String toolID,
                                            String slotID, String selectionTag, FileStageSettings fileStageSettings, BEJobResult jobResult) {
            super(parentObject, parentFiles, creatingTool, toolID, slotID, selectionTag, null, fileStageSettings, jobResult);
        }

        ConstructionHelperForManualCreation(ExecutionContext context, ToolEntry creatingTool, String toolID, String slotID, String selectionTag,
                                            FileStageSettings fileStageSettings, BEJobResult jobResult) {
            super(context, creatingTool, toolID, slotID, selectionTag, fileStageSettings, jobResult);
        }
    }


    static BaseFile constructSourceFile(Class<? extends BaseFile> classToConstruct, File path, ExecutionContext context,
                                        FileStageSettings fileStageSettings = null, BEJobResult jobResult = null) {
        return classToConstruct.newInstance(new ConstructionHelperForSourceFiles(path, context, fileStageSettings, jobResult));
    }

    static BaseFile constructGeneric(Class<? extends BaseFile> classToConstruct, FileObject parentObject, List<FileObject> parentFiles,
                                     ToolEntry creatingTool, String toolID, String slotID, String selectionTag, String indexInFileGroup,
                                     FileStageSettings fileStageSettings, BEJobResult jobResult) {
        return classToConstruct.newInstance(new ConstructionHelperForGenericCreation(parentObject, parentFiles, creatingTool, toolID, slotID,
                selectionTag, indexInFileGroup, fileStageSettings, jobResult));
    }

    static BaseFile constructGeneric(Class<? extends BaseFile> classToConstruct, FileObject parentObject, List<FileObject> parentFiles, ToolEntry
            creatingTool, String toolID, String slotID, String selectionTag, FileStageSettings fileStageSettings, BEJobResult jobResult) {
        return classToConstruct.newInstance(new ConstructionHelperForGenericCreation(parentObject, parentFiles, creatingTool, toolID, slotID,
                selectionTag, null, fileStageSettings, jobResult));
    }

    static BaseFile constructGeneric(Class<? extends BaseFile> classToConstruct, ExecutionContext context, ToolEntry creatingTool, String toolID,
                                     String slotID, String selectionTag, FileStageSettings fileStageSettings, BEJobResult jobResult) {
        return classToConstruct.newInstance(new ConstructionHelperForGenericCreation(context, creatingTool, toolID, slotID, selectionTag,
                fileStageSettings, jobResult));
    }

    static BaseFile constructManual(Class<? extends BaseFile> classToConstruct, FileObject parentObject, List<FileObject> parentFiles,
                                    ToolEntry creatingTool, String toolID, String slotID, String selectionTag, FileStageSettings fileStageSettings,
                                    BEJobResult jobResult) {
        return classToConstruct.newInstance(new ConstructionHelperForManualCreation(parentObject, parentFiles,
                creatingTool, toolID, slotID, selectionTag, fileStageSettings, jobResult));
    }

    static BaseFile constructManual(Class<? extends BaseFile> classToConstruct, ExecutionContext context,
                                    ToolEntry creatingTool, String toolID, String slotID, String selectionTag, FileStageSettings fileStageSettings,
                                    BEJobResult jobResult) {
        return classToConstruct.newInstance(new ConstructionHelperForManualCreation(context, creatingTool, toolID, slotID, selectionTag,
                fileStageSettings, jobResult));
    }

    static BaseFile constructManual(Class<? extends BaseFile> classToConstruct, FileObject parentFile) {
        return constructManual(classToConstruct, parentFile, null, null, null, null, null, null, null);
    }

    /**
     * To "load" a source file from storage.
     *
     * This is basically a convenience method for constructSourceFile!
     *
     * @param path Path to the file (remote or local)
     * @param _class The class of the file, omit it and it will be of the synthetic class StandardFile
     * @return
     */
    static BaseFile getSourceFile(ExecutionContext context, String path, String _class = STANDARD_FILE_CLASS) {
        if (RoddyConversionHelperMethods.isNullOrEmpty(_class)) _class = STANDARD_FILE_CLASS
        Class<BaseFile> fileClass = LibrariesFactory.getInstance().loadRealOrSyntheticClass(_class, BaseFile as Class<FileObject>) as Class<BaseFile>
        return constructSourceFile(fileClass, new File(path), context)
    }

    /**
     * Just another name for getSourceFile()
     * @param context
     * @param path
     * @param _class
     * @return
     */
    static BaseFile fromStorage(ExecutionContext context, String path, String _class = STANDARD_FILE_CLASS) {
        return getSourceFile(context, path, _class)
    }

    /**
     * Call constructManual in a short way
     */
    static BaseFile getFile(BaseFile parentFile, String _class = STANDARD_FILE_CLASS) {
        if (RoddyConversionHelperMethods.isNullOrEmpty(_class)) _class = STANDARD_FILE_CLASS
        Class<BaseFile> fileClass = LibrariesFactory.getInstance().loadRealOrSyntheticClass(_class, BaseFile as Class<FileObject>) as Class<BaseFile>
        return constructManual(fileClass, parentFile)
    }

    /**
     * Derive a file from another file. Effectively call getFile, but enforce parentFile to be non-null.
     * @param parentFile
     * @param _class
     * @return
     */
    static BaseFile deriveFrom(BaseFile parentFile, String _class = STANDARD_FILE_CLASS) {
        assert parentFile
        getFile(parentFile, _class)
    }

    /** Run a tool to return a filename that will be instantiated as file object of STANDARD_FILE_CLASS by default.
     *  The tool should return exactly one line with the path to the file. Otherwise the method throws an UnexpectedExecutionResultException.
     *  If the command could not get successfully executed an ExecutionException is propagated. */
    static BaseFile getSourceFileUsingTool(ExecutionContext context, String toolID, String _class = STANDARD_FILE_CLASS)
        throws UnexpectedExecutionResultException, ExecutionException {
        List<String> executionOutput = ExecutionService.instance.runDirect(context, toolID)
        if (executionOutput.size() != 1) {
            throw new UnexpectedExecutionResultException("SourceFile instantiation from tool output failed", executionOutput)
        } else {
            return getSourceFile(context, executionOutput[0], _class)
        }
    }

    /** Run a tool to returns a list of filenames that will be instantiated as file objects of STANDARD_FILE_CLASS by default.
     *  The tool should return an arbitary number of lines with filenames. If no line is returned, an empty list is returned. */
    static List<BaseFile> getSourceFilesUsingTool(ExecutionContext context, String toolID, String _class = STANDARD_FILE_CLASS)
        throws ExecutionException {
        return ExecutionService.instance.runDirect(context, toolID).collect {
            getSourceFile(context, it, _class)
        } as List<BaseFile>
    }

    protected File path;

    protected final List<BaseFile> parentFiles = new LinkedList<>();

    /**
     * A BaseFile object can be grouped into one or more groups.
     */
    protected final List<FileGroup> fileGroups = new LinkedList<FileGroup>();

    protected FS fileStageSettings;

    private boolean valid;

    /**
     * This list keeps a reference to all jobs which tried to create this file.
     * A file can exist only once but the attempt to create it could be done in multiple previous jobs!
     */
    private final List<BEJob> listOfParentJobs = new LinkedList<BEJob>();

    /**
     * This list keeps a reference to all processes which tried to create this file.
     * A file can exist only once but the attempt to create it could be done in multiple previous processes!
     */
    private transient final List<ExecutionContext> listOfParentProcesses = new LinkedList<ExecutionContext>();
    /**
     * Upon execution a file may be marked as temporary. This means that a file
     * might be deleted automatically by Roddy with some sort of cleanup job or
     * cleanup process and that it will not be recreated upon rerun.
     * <p>
     * TODO Upon rerun and a change to the pipeline the file might be necessary
     * for new jobs. In this case it would currently be recreated but also
     * all the existing files! One solution could be to store information
     * about created files in a logfile (size, name, md5?, source and
     * targetfile) and to evaluate this file on rerun. But this can also
     * lead to problems.
     */
    private boolean isTemporaryFile;

    private Boolean isReadable = null;

    private boolean isSourceFile = false;

    private FilenamePattern appliedFilenamePattern = null;

    private String idxInFileGroup = null;

    private ConstructionHelperForBaseFiles helperObject = null

    protected BaseFile(ConstructionHelperForBaseFiles helper) {
        super(helper.context);
        executionContext.addFile(this);
        idxInFileGroup = helper.indexInFileGroup
        this.helperObject = helper

        if (helper instanceof ConstructionHelperForGenericCreation) { //Manual creation is currently intrinsic.
            ConstructionHelperForGenericCreation _helper = helper as ConstructionHelperForGenericCreation;
            if (_helper.parentObject instanceof FileGroup) {
                parentFiles.addAll((_helper.parentObject as FileGroup).getFilesInGroup());
                this.fileStageSettings = (FS) _helper.fileStageSettings
            } else if (_helper.parentObject instanceof BaseFile) {
                parentFiles.add(_helper.parentObject as BaseFile);
                this.fileStageSettings = (FS) (_helper.fileStageSettings ?: (_helper.parentObject as BaseFile)?.fileStageSettings ?: null);
            }
            Tuple2<File, FilenamePattern> fnresult = getFilename(this, _helper.selectionTag);
            if (fnresult) {
                this.path = fnresult.x
                this.appliedFilenamePattern = fnresult.y
            }
        } else if (helper instanceof ConstructionHelperForSourceFiles) {
            ConstructionHelperForSourceFiles _helper = helper as ConstructionHelperForSourceFiles;

            this.fileStageSettings = (FS) _helper.fileStageSettings;
            this.path = _helper.getPath();
            setAsSourceFile();
        } else {
            //Do not allow custom classes.
            throw new RuntimeException("It is not allowed to use custom Construction Helper classes for BaseFile objects");
        }

        this.setCreatingJobsResult(helper.jobResult);
        determineFileValidityBasedOnContextLevel(executionContext);
    }

    /** A copy constructor for something like "extending" classes **/
    protected BaseFile(BaseFile parent) {
        super(parent.executionContext);
        this.fileStageSettings = parent.fileStageSettings;
        this.path = parent.path;
        this.parentFiles += parent.parentFiles;
        this.fileGroups += parent.fileGroups;
        this.valid = parent.valid;
        this.listOfParentJobs += parent.listOfParentJobs;
        this.listOfParentProcesses += parent.listOfParentProcesses;
        this.isTemporaryFile = parent.isTemporaryFile;
        this.isReadable = parent.isReadable;
        this.isSourceFile = parent.isSourceFile;
        this.appliedFilenamePattern = parent.appliedFilenamePattern;
        this.idxInFileGroup = parent.idxInFileGroup
    }

    private void determineFileValidityBasedOnContextLevel(ExecutionContext executionContext) {
        switch (executionContext.getExecutionContextLevel()) {
            case ExecutionContextLevel.QUERY_STATUS:
                valid = !checkFileValidity();
                break;
            case ExecutionContextLevel.RERUN:
                valid = !checkFileValidity();
                break;
            case ExecutionContextLevel.TESTRERUN:
                valid = !checkFileValidity();
                break;
            case ExecutionContextLevel.RUN:
                valid = true;
                break;
        }
    }

    final Configuration getConfiguration() {
        if (helperObject.jobConfiguration) return helperObject.jobConfiguration
        return executionContext.getConfiguration()
    }

    final void addFileGroup(FileGroup fg) {
        this.fileGroups.add(fg);
    }

    final List<FileGroup> getFileGroups() {
        return this.fileGroups;
    }

    final List<BaseFile> getNeighbourFiles() {
        List<BaseFile> neighbourFiles = new LinkedList<BaseFile>();
        for (FileGroup fg : fileGroups) {
            neighbourFiles.addAll(fg.getFilesInGroup());
        }
        return neighbourFiles;
    }

    /**
     * Run the default operations for a file.
     */
    @Override
    void runDefaultOperations() throws ConfigurationError {
    }

    /**
     * This method can be overridden and is called on each isValid check
     *
     * @return
     */
    boolean checkFileValidity() {
        return true;
    }

    boolean isSourceFile() {
        return isSourceFile;
    }

    void setAsSourceFile() {
        isSourceFile = true;
    }

    boolean hasIndexInFileGroup() {
        return idxInFileGroup != null
    }

    String getIdxInFileGroup() {
        return idxInFileGroup
    }
//
//    void setIndexInFileGroup(String value) {
//        this.indexInFileGroup = value
//    }

    /**
     * Check if the file exists on disk. The query might be buffered, so don't
     * delete the files after the check!
     *
     * @return
     */
    final boolean isFileReadable() {
        if (isReadable == null)
            isReadable = FileSystemAccessProvider.getInstance().isReadable(this);
        return isReadable;
    }

    final void isFileReadable(boolean readable) {
        isReadable = readable;
    }

    /**
     * As the isFileValid method can take a while the value is cached.
     * <p>
     * The value will not change during one run, so caching is ok.
     */
    private Boolean _cacheIsFileValid = null;

    /**
     * Combines isFileReadable and isFileValid.
     * <p>
     * A file is valid if it exists, if it was not created recently (within this
     * run) and if it could be validated (i.e. by its size or content, no
     * lengthy operation!).
     *
     * @return
     */
    final boolean isFileValid() {
        //TODO Check for file sizes < 200Byte!
        if (_cacheIsFileValid == null) {
            _cacheIsFileValid = getExecutionContext().getRuntimeService().isFileValid(this);
        }
        return _cacheIsFileValid;
    }

    final void setFileIsValid() {
        _cacheIsFileValid = true;
    }

    void setFileStage(FS fileStage) {
        this.fileStageSettings = fileStage;
    }

    FS getFileStage() {
        return fileStageSettings;
    }

    File getPath() {
        return path;
    }

    /**
     * (Re-)Sets the path for this file object.
     *
     * @param path
     */
    void setPath(File path) {
        this.path = path;
    }

    String getAbsolutePath() {
        return path.getAbsolutePath();
    }

    String getContainingFolder() {
        return path.getParent();
    }

    /**
     * Returns a copy of the list of parent files.
     *
     * @return
     */
    List<BaseFile> getParentFiles() {
        return new LinkedList<BaseFile>(parentFiles);
    }

    void setParentFiles(List<BaseFile> parentfiles) {
        setParentFiles(parentfiles, false);
    }

    void setParentFiles(List<BaseFile> parentfiles, boolean resetFilename) {
        this.parentFiles.clear();
        this.parentFiles.addAll(parentfiles);
        if (resetFilename) {
            File temp = path;
            Tuple2<File, FilenamePattern> fnresult = getFilename(this);
            this.path = fnresult?.x;
            this.appliedFilenamePattern = fnresult?.y;
            if (path == null) {
                //TODO Also this should be handled somehow else. It is occurring much too often.
//                getExecutionContext().addErrorEntry(ExecutionContextError.EXECUTION_FILECREATION_PATH_NOTSET.expand("Setting a new filename with parent files returned null for " + this.getClass().getName() + " returning to " + (temp != null ? temp.getAbsolutePath() : "null"), Level.WARNING));
                path = temp;
            }
        }
    }

    private BaseFile findAncestorOfType(Class clzType) {
        for (BaseFile bf : parentFiles) {
            if (bf.getClass() == clzType) {
                return bf;
            } else {
                return bf.findAncestorOfType(clzType);
            }
        }
        return null;
    }

    /**
     * Tell Roddy that this file is temporary and can be deleted if dependent files are valid.
     */
    void setAsTemporaryFile() {
        this.isTemporaryFile = true;
    }

    /**
     * Query if this file is temporary
     *
     * @return
     */
    boolean isTemporaryFile() {
        return isTemporaryFile;
    }

    /**
     * Call this method after you created a basefile to reset the path with the passed selectionTag.
     * getFilename() will be rerun
     *
     * @param selectionTag
     */
    void overrideFilenameUsingSelectionTag(String selectionTag) {
        Tuple2<File, FilenamePattern> fnresult = getFilename(this, selectionTag)
        File _path = fnresult.x
        this.appliedFilenamePattern = fnresult.y
        this.setPath(_path)
    }

    /**
     * Convenience method which calls getFilename with the default selection tag.
     *
     * @param baseFile
     * @return
     */
    static Tuple2<File, FilenamePattern> getFilename(BaseFile baseFile) {
        return getFilename(baseFile, FilenamePattern.DEFAULT_SELECTION_TAG);
    }

    private
    static Map<Class, LinkedHashMap<FilenamePatternDependency, LinkedList<FilenamePattern>>> _classPatternsCache = new LinkedHashMap<>();

    static LinkedHashMap<FilenamePatternDependency, LinkedList<FilenamePattern>> loadAvailableFilenamePatterns(BaseFile baseFile, ExecutionContext context) {
        Configuration cfg = context.getConfiguration();
        LinkedHashMap<FilenamePatternDependency, LinkedList<FilenamePattern>> availablePatterns = new LinkedHashMap<>();
        if (!_classPatternsCache.containsKey(baseFile.getClass())) {
            availablePatterns = new LinkedHashMap<>();
            _classPatternsCache.put(baseFile.getClass(), availablePatterns);
            FilenamePatternDependency.values().each { FilenamePatternDependency it -> availablePatterns[it] = new LinkedList<FilenamePattern>() }
            for (FilenamePattern fp : cfg.getFilenamePatterns().getAllValuesAsList()) {
                if (fp.getCls() == baseFile.getClass()) {
                    availablePatterns[fp.getFilenamePatternDependency()] << fp;
                }
            }
            Collection<FilenamePattern> allFoundPatterns = availablePatterns.values().sum() as Collection<FilenamePattern>
            if (allFoundPatterns.size() > 0) {
                logger.sometimes("Found ${allFoundPatterns.size()} filename patterns for file class ${baseFile.class.name}")
                logger.rare("\t\n" + allFoundPatterns.collect { it.class.simpleName + ": " + it.pattern }.join("\t\n"))
            } else {
                logger.severe("Could not find any matching filename patterns for file class ${baseFile.class.name}")
            }
        } else {
            availablePatterns = _classPatternsCache.get(baseFile.getClass());
        }
        return availablePatterns
    }

    /**
     * Looks for the right filename pattern for the new file.
     * <p>
     * Find the correct pattern:
     * Look if filename patterns for this class are available
     * If not throw an exception
     * Look if there is only one available: Easy, use this
     * Else priority is onMethod, sourcefile, filestage
     *
     * @param baseFile
     * @return
     */
    static Tuple2<File, FilenamePattern> getFilename(BaseFile baseFile, String selectionTag) {
        if (!selectionTag)
            selectionTag = "default";
        Tuple2<File, FilenamePattern> patternResult = new Tuple2<>(null, null);
        try {

            //Find the correct pattern:
            // Look if filename patterns for this class are available
            // If not throw an exception
            // Look if there is only one available: Easy, use this
            // onMethod, sourcefile, filestage
            ExecutionContext context = baseFile.getExecutionContext();
            LinkedHashMap<FilenamePatternDependency, LinkedList<FilenamePattern>> availablePatterns = loadAvailableFilenamePatterns(baseFile, context);

            patternResult = findFilenameFromOnScriptParameterPatterns(baseFile, availablePatterns[FilenamePatternDependency.onScriptParameter], selectionTag) ?:
                    findFilenameFromOnMethodPatterns(baseFile, availablePatterns[FilenamePatternDependency.onMethod], selectionTag) ?:
                            findFilenameFromOnToolIDPatterns(baseFile, availablePatterns[FilenamePatternDependency.onTool], selectionTag) ?:
                                    findFilenameFromSourcefilePatterns(baseFile, availablePatterns[FilenamePatternDependency.derivedFrom], selectionTag) ?:
                                            findFilenameFromGenericPatterns(baseFile, availablePatterns[FilenamePatternDependency.FileStage], selectionTag);

            // Do some further checks for selection tags.
            // Two cases. If the selectiontag is default and if it is not. If the selectiontag is not null
            if ((selectionTag.equals("default") && (!patternResult || patternResult.x == null)) || patternResult.x == null) {

                StringBuilder sb = new StringBuilder("Could not find any filename pattern for a file object of class ${baseFile.class.simpleName}\n")

                sb << ["The following patterns are available for this file class:\n"]
                sb << availablePatterns.findAll { it }.collect {
                    FilenamePatternDependency k, List<FilenamePattern> v ->
                        v.collect {
                            FilenamePattern value ->
                                "${k.name()} : ${value}"
                        }
                }.flatten().join("\n\t")

                throw new ConfigurationError(sb.toString(), baseFile.executionContext.configuration);
            } else {
                //Check if the path exists and create it if necessary.
                if (context.getExecutionContextLevel().allowedToSubmitJobs && !FileSystemAccessProvider.getInstance().checkDirectory(patternResult.x.getParentFile(), context, true)) {
                    throw new IOException("Output path could not be created for file: " + baseFile);
                }
            }
        } catch (IOException ex) {
            // baseFile.getExecutionContext().addErrorEntry(ExecutionContextError.EXECUTION_PATH_INACCESSIBLE.expand(baseFile.absolutePath))
            // } catch (RuntimeException ex) {
        } finally {
            return patternResult;
        }
    }

    private
    static Tuple2<File, FilenamePattern> findFilenameFromGenericPatterns(BaseFile baseFile, LinkedList<FilenamePattern> availablePatterns, String selectionTag) {
        Tuple2<File, FilenamePattern> result = null;
        File filename = null;
        FilenamePattern appliedPattern = null;

        if (!availablePatterns) return result;

        for (FilenamePattern _fp : availablePatterns) {
            FileStageFilenamePattern fp = _fp as FileStageFilenamePattern;
            if ((fp.getFileStage() == FileStage.GENERIC || fp.getFileStage() == baseFile.getFileStage().getStage()) && fp.getSelectionTag().equals(selectionTag)) {
                filename = new File(fp.apply(baseFile));
                appliedPattern = fp;
                break;
            }
        }

        if (!filename || !appliedPattern) return null;
        return new Tuple2<>(filename, appliedPattern);
    }

    private
    static Tuple2<File, FilenamePattern> findFilenameFromSourcefilePatterns(BaseFile baseFile, LinkedList<FilenamePattern> availablePatterns, String selectionTag) {
        Tuple2<File, FilenamePattern> result = null;
        File filename = null;
        FilenamePattern appliedPattern = null;

        if (availablePatterns && baseFile.parentFiles.size() > 0) {
            for (FilenamePattern fp : availablePatterns) {
                for (BaseFile bf : (List<BaseFile>) baseFile.parentFiles) {
                    if (bf.getClass() == ((DerivedFromFilenamePattern) fp).getDerivedFromCls() && fp.getSelectionTag().equals(selectionTag)) {
                        if (fp.doesAcceptFileArrays()) {
                            filename = new File(fp.apply([baseFile] as BaseFile[]));
                        } else {
                            filename = new File(fp.apply((BaseFile) baseFile));
                        }
                        appliedPattern = fp;
                        break;
                    }
                }
            }
        }
        if (!filename || !appliedPattern) return null;
        return new Tuple2<>(filename, appliedPattern);
    }

    /**
     * Tries to find a filename from onMethod patterns.
     * <p>
     * If none was found, null will be returned.
     *
     * @param baseFile
     * @param availablePatterns
     * @param selectionTag
     * @return
     */
    private
    static Tuple2<File, FilenamePattern> findFilenameFromOnMethodPatterns(BaseFile baseFile, LinkedList<FilenamePattern> availablePatterns, String selectionTag) {
        Tuple2<File, FilenamePattern> result = null;
        File filename = null;
        FilenamePattern appliedPattern = null;

        //Find the called basefile method, if on_method patterns are available.
        if (!availablePatterns) return result;

        List<StackTraceElement> steByMethod = getAndFilterStackElementsToWorkflowInstance();

        traceLoop:
        for (StackTraceElement ste : steByMethod) {
            for (FilenamePattern _fp : availablePatterns) {
                OnMethodFilenamePattern fp = _fp as OnMethodFilenamePattern;

                String cmName = fp.getCalledMethodsName().getName();
                String cmClass = fp.getCalledMethodsClass().getName();
                if (cmName.equals(ste.getMethodName())
                        && cmClass.equals(ste.getClassName())
                        && fp.getSelectionTag().equals(selectionTag)) {
                    //OOOOOH LOOK try this!
                    File tempFN = new File(fp.apply(baseFile));
                    appliedPattern = fp;
                    filename = tempFN;
                    break traceLoop;
                }
            }
        }
        if (!filename || !appliedPattern) return null;
        return new Tuple2<>(filename, appliedPattern);
    }

    /**
     * TODO The current premise for this method is, that it will only work if GenericMethod was used to run it. The current process / context / run needs an extension, so that we have something like
     * a jobState machine where the developer can always query the current context and store context specific things. Maybe this can be bound to the current Thread or so... In this case, a Thread could also
     * have a link to several context objects (which are called one after another.). The jobState machine will always know the current active context, the current active job and so on. It might be complicated
     * but maybe worth it. For now, let's just get the job id from the Generic Method call.
     *
     * @param baseFile
     * @param availablePatterns
     * @param selectionTag
     * @return
     */
    private
    static Tuple2<File, FilenamePattern> findFilenameFromOnToolIDPatterns(BaseFile baseFile, LinkedList<FilenamePattern> availablePatterns, String selectionTag) {
        Tuple2<File, FilenamePattern> result = null;
        File filename = null;
        FilenamePattern appliedPattern = null;

        //Find the called basefile method, if on_method patterns are available.
        if (!availablePatterns) return result;

        String id = baseFile.getExecutionContext().getCurrentExecutedTool().getID();
        for (FilenamePattern _fp : availablePatterns) {
            OnToolFilenamePattern fp = _fp as OnToolFilenamePattern;
            if (fp.getCalledScriptID().equals(id) && fp.selectionTag == selectionTag) {
                appliedPattern = fp;
                filename = new File(fp.apply(baseFile));
                break;
            }
        }
        if (!filename || !appliedPattern) return null;
        return new Tuple2<>(filename, appliedPattern);
    }

    /**
     * @param baseFile
     * @param availablePatterns
     * @param selectionTag
     * @return
     */
    private
    static Tuple2<File, FilenamePattern> findFilenameFromOnScriptParameterPatterns(BaseFile baseFile, LinkedList<FilenamePattern> availablePatterns, String selectionTag) {
        Tuple2<File, FilenamePattern> result = null;

        //Find the called basefile method, if on_method patterns are available.
        if (!availablePatterns) return result;

        ConstructionHelperForGenericCreation helper = baseFile.helperObject as ConstructionHelperForGenericCreation

        if (helper == null)
            throw new RuntimeException("To use on script parameter patterns, an object of type ConstructionHelperForGenericCreation is needed.")

        FilenamePattern appliedPattern = availablePatterns.find {
            FilenamePattern _fp ->
                OnScriptParameterFilenamePattern fp = _fp as OnScriptParameterFilenamePattern
                boolean parameterFound = fp.calledParameterId == helper.slotID
                boolean scriptValid = fp.toolName ? fp.toolName == helper.toolID : true

                return parameterFound && scriptValid
        }
        File filename = new File(appliedPattern.apply(baseFile))

        if (!filename || !appliedPattern) return null;
        return new Tuple2<>(filename, appliedPattern);
    }

    /**
     * As the method name states, the method fetches a filtered list of all stack trace elements until the workflows execute method.
     * @return
     */
    private static List<StackTraceElement> getAndFilterStackElementsToWorkflowInstance() {
        String calledBaseFileMethodName = null;
        //Walk through the stack to get the method.
        List<StackTraceElement> steByMethod = new LinkedList<>();
        boolean constructorPassed = false;
        for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
            try {
                //Skip several methods

                String methodName = ste.getMethodName();
                if (methodName.equals("<init>")
                        || methodName.endsWith("getFilename")
                        || methodName.endsWith("getStackTrace")
                ) {
                    continue;
                }
                //Abort when the workflows execute method is called.
                if ((ste.getClassName().equals(ExecutionContext.class.getName())
                        || Workflow.class.isAssignableFrom(LibrariesFactory.getGroovyClassLoader().loadClass(ste.getClassName())))
                        && methodName.equals("execute"))
                    break;

                //In all other cases add the method.
//                    if (constructorPassed)
                steByMethod.add(ste);
            } catch (Exception ex) {

            }
        }
        return steByMethod;
    }

    /**
     * Adds a job to the list of the parent jobs.
     *
     * @param job
     */
    void addCreatingJob(BEJob job) {
        listOfParentJobs.add(job);
    }

    /**
     * Adds a process to the end of the list of parent processes.
     *
     * @param process
     */
    void addCreatingProcess(ExecutionContext process) {
        listOfParentProcesses.add(process);
    }

    /**
     * Returns a copy of the list of parent jobs.
     * last entry is the most current entry.
     *
     * @return
     */
    List<BEJob> getListOfParentJobs() {
        return new LinkedList<BEJob>(listOfParentJobs);
    }

    /**
     * Returns a copy of the list of parent processes.
     * last entry is the most current entry.
     *
     * @return
     */
    List<ExecutionContext> getListOfParentProcesses() {
        return new LinkedList<ExecutionContext>(listOfParentProcesses);
    }

    @Override
    String toString() {
        return "BaseFile of type " + getClass().getName() + " with path " + path;
    }
}
