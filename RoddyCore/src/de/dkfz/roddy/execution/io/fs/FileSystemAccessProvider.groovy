/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.execution.io.fs

import de.dkfz.roddy.Constants
import de.dkfz.roddy.Roddy
import de.dkfz.roddy.config.converters.ConfigurationConverter
import de.dkfz.roddy.plugins.LibrariesFactory
import de.dkfz.roddy.tools.ComplexLine
import de.dkfz.roddy.tools.LoggerWrapper
import de.dkfz.roddy.tools.RoddyIOHelperMethods
import de.dkfz.roddy.core.ExecutionContext
import de.dkfz.roddy.execution.io.ExecutionResult
import de.dkfz.roddy.execution.io.ExecutionService
import de.dkfz.roddy.execution.io.FileAttributes
import de.dkfz.roddy.knowledge.files.BaseFile
import groovy.io.FileType
import org.apache.commons.io.filefilter.WildcardFileFilter

import java.util.concurrent.locks.ReentrantLock

/**
 * This class provides access to the file system and collects information for easy access.
 *
 * TODO: A lot of the methods below do not work with the standard java file system classes.
 * I.e. setting file access rights is only available with SSH. This needs to be completed!
 */
@groovy.transform.CompileStatic
public class FileSystemAccessProvider {
    private static LoggerWrapper logger = LoggerWrapper.getLogger(FileSystemAccessProvider.class.getName());
    private static FileSystemAccessProvider fileSystemAccessProvider = null;

    /**
     * Do you wonder why this is protected?
     * Well this  value is to ensure backward compatibility for older Roddy workflows.
     * Formerly, this class was called FileSystemInfoProvider which is just not the right name for the class.
     * So we decided to rename it to FileSytemAccessProvider. This name covers everything!
     * However, the FileSystemAccessProvider class still resides for backward compatibility.
     * The lock is used in this class and in the FileSystemAccessProvider. It is not on a package level and also should not be!
     * Also it must not be public or private.
     * So don't whonder why this one is protected static.
     */
    protected static ReentrantLock fileSystemAccessProviderLock = new ReentrantLock();

    /**
     * This is the command set assembler for the current target file system.
     * As currently only Linux target fs are supported it is assigned by default.
     * TODO Initialize this in a different way!
     */
    public final ShellCommandSet commandSet = new BashCommandSet();

    /**
     * The current users user name (logon on local or target system)
     */
    protected String _userName;

    /**
     * The first group id in a list of groups on the target system.
     */
    protected String _groupID;

    /**
     * The current users home directory (logon on local or target system)
     */
    protected File _userHome;

    /**
     * The cached umask
     */
    protected Integer _default_umask

    protected Map<String, Integer> _groupIDsByGroup = [:];

    protected final Map<String, String> uidToUserCache = new HashMap<>();

    protected Object _appendLineToFileLock = new Object();

    public FileSystemAccessProvider() {
    }

    public static void initializeProvider(boolean fullSetup) {
        logger.postSometimesInfo("public static void initializeProvider(boolean fullSetup)")
        fileSystemAccessProviderLock.lock();
        try {
            if (!fullSetup) {
                fileSystemAccessProvider = new NoNoFileSystemAccessProvider();
            }
            try {
                Class fisClz = LibrariesFactory.getGroovyClassLoader().loadClass(Roddy.applicationConfiguration.getOrSetApplicationProperty(Roddy.getRunMode(), Constants.APP_PROPERTY_FILESYSTEM_ACCESS_MANAGER_CLASS, FileSystemAccessProvider.class.getName()))
                fileSystemAccessProvider = (FileSystemAccessProvider) fisClz.getConstructors()[0].newInstance();
            } catch (Exception e) {
                logger.warning("Falling back to default file system info provider");
                fileSystemAccessProvider = new FileSystemAccessProvider();
            }
        } finally {
            fileSystemAccessProviderLock.unlock();
        }
    }

    public static File writeTextToTempFile(String text) {
        File tempFile = File.createTempFile("roddy_sshservice", ".tmp");
        tempFile.deleteOnExit();
        BufferedWriter bw = new BufferedWriter(new FileWriter(tempFile));
        bw.write(text);
        bw.flush();
        bw.close();
        return tempFile;
    }

    public static File serializeObjectToTempFile(Serializable serializable) {
        File tempFile = File.createTempFile("roddy_sshservice", ".tmp");
        ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(tempFile));
        oos.writeObject(serializable);
        oos.flush();
        oos.close();
        return tempFile;
    }

    public static Object deserializeObjectFromFile(File f) {
        if (f == null || !f.exists())
            return null;
        ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f));
        return ois.readObject();
    }

    public static FileSystemAccessProvider getInstance() {
        FileSystemAccessProvider provider;
        fileSystemAccessProviderLock.lock()
        try {
            provider = fileSystemAccessProvider;
        } finally {
            fileSystemAccessProviderLock.unlock();
        }
        return provider;
    }

    public static void resetFileSystemAccessProvider(FileSystemAccessProvider provider) {
        fileSystemAccessProviderLock.lock()
        try {
            this.fileSystemAccessProvider = provider;
        } finally {
            fileSystemAccessProviderLock.unlock();
        }
    }

    ShellCommandSet getCommandSet() {
        return commandSet;
    }

    /**
     * Wrapper for isReadable(File)
     * @param bf
     * @return
     */
    public boolean isReadable(BaseFile bf) {
        return isReadable(bf.path);
    }

    private boolean runFileTestCommand(String cmd) {
        ExecutionResult er = ExecutionService.getInstance().execute(cmd);
        return er.firstLine == commandSet.getReadabilityTestPositiveResult();
    }

    public boolean isCachingAllowed(File file) {
        List<String> filters = [".roddyExecCache", ".roddy", "jobStateLogfile", "JobCalls", "zippedAnalysesMD5.txt"];

        for (String f in filters) {
            if (file.absolutePath.contains(f))
                return false;
        }
        return true;
    }

    public boolean fileExists(File f) {
        if (ExecutionService.getInstance().canQueryFileAttributes()) {
            return ExecutionService.getInstance().fileExists(f);
        }
        return runFileTestCommand(commandSet.getFileExistsTestCommand(f));
    }

    Long fileSize(File f) {
        def res = ExecutionService.instance.execute(commandSet.getFileSizeCommand(f))
        if (res.successful)
            return Long.parseLong(res.firstLine)
        else
            return -1
    }

    public boolean directoryExists(File f) {
        if (ExecutionService.getInstance().canQueryFileAttributes()) {
            return ExecutionService.getInstance().directoryExists(f);
        }
        return runFileTestCommand(commandSet.getDirectoryExistsTestCommand(f));
    }

    /**
     * Tests if a file is readable
     * @param f
     * @return
     */
    public boolean isReadable(File f) {
        if (ExecutionService.getInstance().canQueryFileAttributes()) {
            return ExecutionService.getInstance().isFileReadable(f);
        }
        return runFileTestCommand(commandSet.getReadabilityTestCommand(f));
    }

    public boolean isWritable(BaseFile f) {
        return isWritable(f.path);
    }

    public boolean isWritable(File f) {
        if (ExecutionService.getInstance().canQueryFileAttributes()) {
            return ExecutionService.getInstance().isFileWriteable(f);
        }
        return runFileTestCommand(commandSet.getWriteabilityTestCommand(f));
    }

    public boolean isExecutable(File f) {
        ExecutionService eService = ExecutionService.getInstance()
        if (eService.canQueryFileAttributes()) {
            return eService.isFileExecutable(f);
        } else {
            if (eService.isLocalService())
                return f.canExecute();
            else
                return eService.execute(commandSet.getExecutabilityTestCommand(f)).firstLine == commandSet.getReadabilityTestPositiveResult();
        }
    }

    /**
     * Lists all directory in a directory.
     * @param f
     * @return
     */
    public List<File> listDirectoriesInDirectory(File f, List<String> filters = null) {
        List<File> allFiles = []
        List<File> folders = []
        List<String> res;

        if (!ExecutionService.getInstance().canListFiles()) {
            String cmd = commandSet.getListDirectoriesInDirectoryCommand(f, filters);
            ExecutionResult er = ExecutionService.getInstance().execute(cmd);
            res = er.resultLines;
            res.each({ String folder -> folders.add(new File("${folder}")) })
        } else {
            folders = ExecutionService.getInstance().listFiles(f, filters);
        }

        for (File folderIn in folders) {
            File folderFound = null;
            for (File folderExisting in allFiles) {
                if (folderIn.absolutePath == folderExisting.absolutePath) {
                    folderFound = folderIn;
                }
            }

            if (folderFound == null)
                allFiles.add(folderIn);
        }
        return allFiles;
    }
    /**
     * Lists all files in a directory.
     * @param f
     * @return
     */
    public List<File> listFilesInDirectory(File f, List<String> filters = null) {
        if (filters == null) {
            filters = ["*"];
        }

        ExecutionService eService = ExecutionService.getInstance()
        if (!eService.canListFiles()) {
            if (eService.isLocalService())
                return Arrays.asList(f.listFiles(new FilenameFilter() {
                    @Override
                    boolean accept(File dir, String name) {
                        return new WildcardFileFilter(filters).accept(dir, name);
                    }
                }))
            else {
                List<File> files = [];
                String cmd = commandSet.getListFilesInDirectoryCommand(f, filters);
                ExecutionResult er = eService.execute(cmd);
                if (er.successful) {
                    for (String l : er.resultLines) {
                        files << new File(l);
                    }
                }
                return files;
            }
        } else {
            return eService.listFiles(f, filters);
        }
    }

    List<File> listFilesUsingWildcards(File baseFolder, String wildcards) {
        ExecutionResult result = ExecutionService.instance.execute(commandSet.getFindFilesUsingWildcardsCommand(baseFolder, wildcards))
        result.resultLines.collect {
            new File(it)
        } as List<File>
    }

    /**
     * Scope for regular expressions. Used in listFilesUsingRegex
     */
    enum RegexSearchDepth {
        AbsolutePath,
        RelativeToSearchFolder,
        Filename
    }

    List<File> listFilesUsingRegex(File baseFolder, String regex, RegexSearchDepth scope) {
        ExecutionResult result = ExecutionService.instance.execute(commandSet.getListFullDirectoryContentRecursivelyCommand(baseFolder, -1, FileType.FILES, false))

        List<File> foundFiles = result.resultLines.collect { new File(it) } as List<File>

        foundFiles.findAll {
            String comparable
            if (scope == RegexSearchDepth.AbsolutePath) {
                comparable = it.absolutePath
            } else if (scope == RegexSearchDepth.RelativeToSearchFolder) {
                comparable = it.absolutePath[baseFolder.absolutePath.length() + 1..-1]
            } else if (scope == RegexSearchDepth.Filename)
                comparable = it.name
            comparable ==~ regex
        }
    }

    public boolean checkDirectories(List<File> files, ExecutionContext context, boolean createMissing) {
        boolean result = true;
        for (File f in files) {
            result &= checkDirectory(f, context, createMissing);
        }
        return result;
    }

    public boolean checkDirectory(File f, boolean createMissing = false) {
        if (ExecutionService.getInstance().canReadFiles()) {
            return ExecutionService.getInstance().isFileReadable(f);
        } else {
            if (ExecutionService.getInstance().isLocalService()) {
                boolean result = f.canRead() && f.isDirectory();
                if (!result && createMissing) {
                    f.mkdirs();
                    result = checkDirectory(f, false)
                }
                return result;
            } else
                throw new RuntimeException("Not implemented yet!");
        }
    }

    /**
     * Checks if a directory is existing and accessible.
     * @param f The directory which should be checked.
     * @param createMissing true? Then create the directory if it not there.
     * @return true if the directory exists or false if not.
     */
    public boolean checkDirectory(File f, ExecutionContext context, boolean createMissing) {
        final String path = f.absolutePath
        boolean directoryIsAccessible
        String cmd
        if (createMissing) {
            String outputAccessRightsForDirectories = context.getOutputDirectoryAccess();
            String outputFileGroup = context.getOutputGroupString()
            cmd = commandSet.getCheckDirectoryCommand(f, true, outputFileGroup, outputAccessRightsForDirectories);
        } else {
            cmd = commandSet.getCheckDirectoryCommand(f)
        }
        ExecutionResult er = ExecutionService.getInstance().execute(cmd);
        directoryIsAccessible = (er.firstLine == commandSet.getReadabilityTestPositiveResult());
        return directoryIsAccessible
    }

    public boolean checkBaseFiles(BaseFile... filesToCheck) {
        for (BaseFile bf : filesToCheck) {
            if (!checkFile(bf.getPath()))
                return false;
        }
        return true;
    }

    public boolean checkFile(File f, boolean create, ExecutionContext context) {
        if (checkFile(f))
            return true;
        if (!create)
            return false;
        createFileWithDefaultAccessRights(true, f, context, true);
        return true;
    }

    /**
     * Checks if a file exists and if it is a file or link to a file...
     * @param f
     * @return
     */
    public boolean checkFile(File f) {
        if (ExecutionService.getInstance().canReadFiles()) {
            return ExecutionService.getInstance().isFileReadable(f);
        } else {
            if (ExecutionService.getInstance().isLocalService()) {
                return f.canRead() && f.isFile();
            } else
                throw new RuntimeException("Not implemented yet!");
        }
    }

    /**
     * The method returns the directory of the current user (or i.e. the ssh user's directory on the target system).
     * This method is called so often, that it is cached by default!
     * @return
     */
    public File getUserDirectory() {
        if (_userHome == null) {
            if (ExecutionService.getInstance().isLocalService()) {
                String jHomeVar = System.getProperty("user.home");
                _userHome = new File(jHomeVar)
            } else {
                String cmd = commandSet.getUserDirectoryCommand();
                ExecutionResult er = ExecutionService.getInstance().execute(cmd);
                _userHome = new File(er.resultLines[0]);
            }
        }
        return _userHome;
    }

    private String getSingleCommandValueOnValueIsNull(String _value, String command, String cacheEventID) {
        if (_value == null) {
            ExecutionResult er = ExecutionService.getInstance().execute(command);
            _value = er.resultLines[0];
        }
        return _value;
    }

    /**
     * The method returns the current user's name (or i.e. the ssh user's name on the target system).
     * This method is called so often, that it is cached by default!
     * @return
     */
    public String callWhoAmI() {
        if (_userName == null) {
            if (ExecutionService.getInstance().doesKnowTheUsername())
                _userName = ExecutionService.getInstance().getUsername();
            else
                _userName = getSingleCommandValueOnValueIsNull(_userName, commandSet.getWhoAmICommand(), "userName");
        }
        return _userName;
    }

    List<String> getListOfGroups() {
        // Result could be a single line or several lines. So just combine and split again. This way, we are safe.
        new ComplexLine(ExecutionService.getInstance().execute(commandSet.getListOfGroupsCommand()).resultLines.join(" ")).splitBy(" ") as List<String>
    }

    public String getMyGroup() {
        _groupID = getSingleCommandValueOnValueIsNull(_groupID, commandSet.getMyGroupCommand(), "groupID");
        return _groupID;
    }

    int getGroupID() {
        return getGroupID(getMyGroup());
    }

    int getGroupID(String groupID) {
        synchronized (_groupIDsByGroup) {
            if (!_groupIDsByGroup.containsKey(groupID)) {
                ExecutionResult er = ExecutionService.getInstance().execute(commandSet.getGroupIDCommand(groupID));
                _groupIDsByGroup[groupID] = er.resultLines[0].toInteger();
            }

            return _groupIDsByGroup[groupID];
        }
    }

    boolean isGroupAvailable(String groupID) {
        getListOfGroups().find { it == groupID }
    }

    private String _getOwnerOfPath(File file) {
        String cmd = commandSet.getOwnerOfPathCommand(file);
        ExecutionResult er = ExecutionService.getInstance().execute(cmd);
        return er.resultLines[0];
    }

    /**
     * Returns the creator / current owner of a path.
     */
    public String getOwnerOfPath(File file) {
        if (ExecutionService.getInstance().canQueryFileAttributes()) {
            FileAttributes attributes = ExecutionService.getInstance().queryFileAttributes(file);
            if (attributes == null || attributes.userID == null)
                return _getOwnerOfPath(file);
            if (attributes.userID.isInteger()) {
                if (!uidToUserCache.containsKey(attributes.userID)) {
                    uidToUserCache[attributes.userID] = _getOwnerOfPath(file);
                }
                return uidToUserCache[attributes.userID]
            }
            return attributes.userID;
        } else
            return _getOwnerOfPath(file);
    }


    public boolean writeTextFile(File file, String text, ExecutionContext context) {
        return writeTextFile(file, text) &&
                setDefaultAccessRights(file, context);
    }

    public boolean writeTextFile(File file, String text) {
        ExecutionService eService = ExecutionService.getInstance()
        if (eService.canWriteFiles()) { //Let the execution service do this.
            return eService.writeTextFile(file, text);
        } else if (eService.isLocalService()) {
            if (!file.getParentFile().exists())
                file.getParentFile().mkdirs();
            file.write(text);
            return true;
        } else
            throw new RuntimeException("Not implemented yet!");
    }

    public boolean writeBinaryFile(File file, Serializable serializable, ExecutionContext context) {
        return writeBinaryFile(file, serializable) &&
                setDefaultAccessRights(file, context);
    }

    public boolean writeBinaryFile(File file, Serializable serializable) {
        if (ExecutionService.getInstance().canWriteFiles()) { //Let the execution service do this.
            return ExecutionService.getInstance().writeBinaryFile(file, serializable);
        }
        throw new RuntimeException("Not implemented yet!");
    }

    public boolean copyFile(File _in, File _out, ExecutionContext context) {
        return copyFile(_in, _out) && setDefaultAccessRights(_out, context);
    }

    public boolean copyFile(File _in, File _out) {
        ExecutionService eService = ExecutionService.getInstance();
        if (eService.canCopyFiles()) { //Let the execution service do this.
            return eService.copyFile(_in, _out);
        } else {
            return eService.execute(commandSet.getCopyFileCommand(_in, _out));
        }
    }

    public boolean moveFile(File _from, File _to, ExecutionContext context) {
        return moveFile(_from, _to) && setDefaultAccessRights(_to, context);
    }

    public boolean moveFile(File _from, File _to) {
        ExecutionService eService = ExecutionService.getInstance();
        if (eService.canCopyFiles()) { //Let the execution service do this.
            return eService.moveFile(_from, _to);
        } else {
            return eService.execute(commandSet.getMoveFileCommand(_from, _to));
        }
    }

    public boolean copyDirectory(File _in, File _out, ExecutionContext context) {
        return copyDirectory(_in, _out) && setDefaultAccessRightsRecursively(_out, context);
    }

    public boolean copyDirectory(File _in, File _out) {
        ExecutionService eService = ExecutionService.getInstance()
        if (eService.canCopyFiles()) { //Let the execution service do this.
            return eService.copyDirectory(_in, _out);
        } else {

            def executionResult = eService.execute(commandSet.getCopyDirectoryCommand(_in, _out))
            return executionResult.successful
        };

    }

    /**
     * This check tries to find out whether access rights can be set on the target file system (or not).
     * This check can be quite hard, because not all folders and files for a context might be on the same
     * file system or root folder. Thus the check concentrates on files and folders in the project output
     * folder.
     *
     * The test is also performed in such a way that it tests both permissions and owner / group changes.
     * If one of both is not working, then false is returned.
     * @param context
     * @return
     */
    public boolean checkIfAccessRightsCanBeSet(ExecutionContext context) {

        def outputDirectory = context.getOutputDirectory()

        ExecutionService eService = ExecutionService.getInstance();
        String command = commandSet.getCheckChangeOfPermissionsPossibilityCommand(outputDirectory, getMyGroup());
        def executionResult = eService.execute(command, true);
        return executionResult.successful && executionResult.firstLine && executionResult.firstLine == BashCommandSet.TRUE;
    }

    public boolean setDefaultAccessRightsRecursively(File path, ExecutionContext context) {
        if (context == null) {
            return setAccessRightsRecursively(path, commandSet.getDefaultAccessRightsString(), commandSet.getDefaultAccessRightsString(), getMyGroup())
        } else {
            return setAccessRightsRecursively(path, context.outputDirectoryAccess, context.outputFileAccessRights, context.outputGroupString)
        };
    }

    /**
     * Sets the rights for all files in the path and ints subpaths.
     * @param path
     * @param accessString
     * @return
     */
    public boolean setAccessRightsRecursively(File path, String accessStringDirectories, String accessString, String group) {
        return ExecutionService.getInstance().execute(commandSet.getSetAccessRightsRecursivelyCommand(path, accessStringDirectories, accessString, group), false);
    }

    boolean setDefaultAccessRights(File file, ExecutionContext context) {
        if (!context.isAccessRightsModificationAllowed())
            return true // an economic decision ...
        return setAccessRights(file, context.getOutputFileAccessRights(), context.getOutputGroupString())
    }

    public boolean setAccessRights(File file, String accessString, String groupID) {
        ExecutionService eService = ExecutionService.getInstance()
        if (eService.canModifyAccessRights()) {
            return ExecutionService.getInstance().modifyAccessRights(file, accessString, groupID);
        } else {
            return eService.execute(commandSet.getSetAccessRightsCommand(file, accessString, groupID))
        }
    }

    public Object loadBinaryFile(File file) {
        if (ExecutionService.getInstance().canReadFiles()) { //Let the execution service do this.
            return ExecutionService.getInstance().loadBinaryFile(file);
        }
        //TODO: Implement for the file system info provider.
        throw new RuntimeException("Load binary file not supported by the file system info provider.");
    }

    /**
     *
     */
    public File getTemporaryFileForFile(File file) {
        if (ExecutionService.getInstance().canReadFiles()) {
            return ExecutionService.getInstance().getTemporaryFileForFile(file);
        }
    }

    String[] loadTextFile(File file) {
        try {
            if (ExecutionService.getInstance().canReadFiles()) { //Let the execution service do this.
                return ExecutionService.getInstance().loadTextFile(file)
            }

            if (ExecutionService.getInstance().isLocalService()) {
                return file.readLines().toArray(new String[0])
            } else {
                return ExecutionService.getInstance().execute(commandSet.getReadOutTextFileCommand(file), true).resultLines.toArray(new String[0])
            }
        } catch (Exception ex) {
            logger.postAlwaysInfo("There was an error while trying to load file " + file)
            return null
        }
    }

    public String getLineOfFile(File file, int lineIndex) {
        try {
            return ExecutionService.getInstance().execute(commandSet.getReadLineOfFileCommand(file, lineIndex), true).resultLines[0];
        } catch (Exception ex) {
            logger.postAlwaysInfo("There was an error while trying to read a line of file " + file);
        }
    }

    boolean createFileWithDefaultAccessRights(boolean atomic, File filename, ExecutionContext context, boolean blocking) {
        try {
            if (ExecutionService.getInstance().canWriteFiles()) {
                String accessRights = context.getOutputFileAccessRights();
                String groupID = context.getOutputGroupString();
                return ExecutionService.getInstance().createFileWithRights(atomic, filename, accessRights, groupID, blocking);
            } else {
                if (ExecutionService.getInstance().isLocalService()) {
                    boolean created = filename.createNewFile();
                    if (!created)
                        throw new IOException("The file ${filename.absolutePath} could not be created.")
                    return created
                } else
                    throw new RuntimeException("Not implemented yet!");
            }
        } catch (Exception ex) {
            logger.postAlwaysInfo("There was an error while trying to create file " + filename);
        }
    }

    boolean appendLinesToFile(boolean atomic, File filename, List<String> lines, boolean blocking) {
        if (ExecutionService.getInstance().canWriteFiles()) {
            return ExecutionService.getInstance().appendLinesToFile(atomic, filename, lines, blocking);
        } else {
            lines.each {
                String line -> appendLineToFile(atomic, filename, line, blocking);
            }
            throw new RuntimeException("Not implemented yet!");
        }
    }


    boolean appendLineToFile(boolean atomic, File filename, String line, boolean blocking) {
        try {
            ExecutionService eService = ExecutionService.getInstance()
            if (atomic) { // Work very safe and use a lockfile
                // TODO This also needs the lockfile command from the configuration.
                // TODO As we always used lockfile, we'll do it here as well for now
                eService.execute(commandSet.getLockedAppendLineToFileCommand(filename, line))
            } else { // Use possibly faster methods
                if (eService.canWriteFiles()) {
                    return eService.appendLineToFile(atomic, filename, line, blocking);
                } else {
                    if (eService.isLocalService())
                        synchronized (_appendLineToFileLock) {
                            return RoddyIOHelperMethods.appendLineToFile(filename, line)
                        }
                    else
                        throw new RuntimeException("Not implemented yet!");
                }
            }
        } catch (Exception ex) {
            logger.postAlwaysInfo("There was an error during the attempt to append to file " + filename);
        }
    }

    void validateAllFilesInContext(ExecutionContext context) {
        if (ExecutionService.getInstance().canListFiles()) {
            Map<File, BaseFile> baseFilesPerFile = new LinkedHashMap<>();
            context.getAllFilesInRun().each { BaseFile bf -> baseFilesPerFile[bf.path] = bf; }
            Map<File, Boolean> readability = ExecutionService.getInstance().getReadabilityOfAllFiles(context.getAllFilesInRun().collect { BaseFile bf -> return bf.getPath(); })

            baseFilesPerFile.each {
                File file, BaseFile bf ->
                    boolean exists = readability.get(file);
                    bf.isFileReadable(exists);
            }
        } else {
            //throw new RuntimeException("Not implemented yet!");
        }

        for (BaseFile bf : context.allFilesInRun) {
            bf.isFileValid();
        }

    }

    public boolean removeDirectory(File directory) {
        if (ExecutionService.getInstance().canDeleteFiles()) {
            return ExecutionService.getInstance().removeDirectory(directory);
        } else {
            return ExecutionService.getInstance().execute(commandSet.getRemoveDirectoryCommand(directory));
        }
    }

    public String getPathSeparator() {
        return commandSet.getPathSeparator();
    }

    public String getNewLineString() {
        return commandSet.getNewLineString();
    }

    ConfigurationConverter getConfigurationConverter() {
        return commandSet.getConfigurationConverter();
    }

    int getDefaultUserMask() {
        if (!_default_umask) {
            String command = commandSet.getGetUsermaskCommand();
            ExecutionResult executionResult = ExecutionService.getInstance().execute(command);
            _default_umask = Integer.decode(executionResult.firstLine)
        }
        return _default_umask
    }
}
