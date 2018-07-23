/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.execution.io.fs

import de.dkfz.roddy.config.converters.ConfigurationConverter
import groovy.io.FileType

/**
 */
@groovy.transform.CompileStatic
abstract class ShellCommandSet {

    abstract String getFileExistsTestCommand(File f)

    abstract String getDirectoryExistsTestCommand(File f)

    abstract String getReadabilityTestCommand(File f)

    abstract String getWriteabilityTestCommand(File f)

    abstract String getExecutabilityTestCommand(File f)

    abstract String getReadabilityTestPositiveResult()

    abstract String getUserDirectoryCommand()

    abstract String getWhoAmICommand()

    abstract String getListOfGroupsCommand()

    abstract String getMyGroupCommand()

    abstract String getGetUsermaskCommand()

    abstract String getGroupIDCommand(String groupID)

    abstract String getOwnerOfPathCommand(File f)

    abstract String getCheckForInteractiveConsoleCommand()

    abstract String getSetPathCommand()

    abstract String getCheckDirectoryCommand(File f)

    abstract String getCheckDirectoryCommand(File file, boolean createMissing, String onCreateAccessRights, String onCreateFileGroup)

    abstract String getCheckAndCreateDirectoryCommand(File f, String onCreateAccessRights, String onCreateFileGroup)

    abstract String getCheckChangeOfPermissionsPossibilityCommand(File f, String group)

    abstract String getSetAccessRightsCommand(File f, String rightsForFiles, String fileGroup)

    abstract String getSetAccessRightsRecursivelyCommand(File f, String rightsForDirectories, String rightsForFiles, String fileGroup)

    abstract String getCheckCreateAndReadoutExecCacheFileCommand(File f)

    abstract String getReadOutTextFileCommand(File f)

    abstract String getReadLineOfFileCommand(File file, int lineIndex)

    abstract String getListDirectoriesInDirectoryCommand(File file)

    abstract String getListDirectoriesInDirectoryCommand(File file, List<String> filters)

    abstract String getListFilesInDirectoryCommand(File file)

    abstract String getListFilesInDirectoryCommand(File file, List<String> filters)

    @Deprecated
    String getListFullDirectoryContentRecursivelyCommand(File f, int depth, boolean onlyDirectories) {
        this.getListFullDirectoryContentRecursivelyCommand(f, depth, onlyDirectories ? FileType.DIRECTORIES : FileType.ANY, true)
    }

    /**
     * API Level 3.2+
     * Return a command to create a tree of files and / or directories for a given directory
     * @param directory     The base folder to look into
     * @param depth         The maximum depth to go into
     * @param selectedType  ANY, FILES or DIRECTORIES, where ANY == All files and directories
     * @param detailed      Get more info than the filename (e.g. size, owner etc.). Highly dependent on the backend
     *                      (e.g. the BashCommandSet uses find -ls for details), results need to be parsed accordingly.
     * @return A list of files and directories or a detailed list of files and directories. Paths are fully set.
     */
    abstract String getListFullDirectoryContentRecursivelyCommand(File directory, int depth, FileType selectedType, boolean detailed)

    @Deprecated
    String getListFullDirectoriesContentRecursivelyCommand(List<File> directories, List<Integer> depth, boolean onlyDirectories) {
        this.getListFullDirectoriesContentRecursivelyCommand(directories, depth, onlyDirectories ? FileType.DIRECTORIES : FileType.ANY, true)
    }

    /**
     * API Level 3.2+
     * Return a command to create a tree of files and / or directories for a range of directories.
     * @param directories   The base folders to look into
     * @param depth         The maximum depth to go into for each directory
     *                      Be careful: If depth is not set properly, -1 (no depth) will be used for every directory!
     * @param selectedType  ANY, FILES or DIRECTORIES, where ANY == All files and directories
     * @param detailed      Get more info than the filename (e.g. size, owner etc.). Highly dependent on the backend
     *                      (e.g. the BashCommandSet uses find -ls for details), results need to be parsed accordingly.
     * @return A list of files and directories or a detailed list of files and directories. Paths are fully set.
     */
    abstract String getListFullDirectoriesContentRecursivelyCommand(List<File> directories, List<Integer> depth, FileType selectedType, boolean detailed)

    abstract String getFindFilesUsingWildcardsCommand(File baseFolder, String wildcards)

    abstract FileSystemInfoObject parseDetailedDirectoryEntry(String line)

    abstract String getPathSeparator()

    abstract String getNewLineString()

    abstract String getCopyFileCommand(File _in, File _out)

    abstract String getCopyDirectoryCommand(File _in, File _out)

    abstract String getMoveFileCommand(File _from, File _to)

    abstract String getLockedAppendLineToFileCommand(File file, String line)

    abstract String getDefaultUMask()

    abstract String getDefaultAccessRightsString()

    abstract String getRemoveDirectoryCommand(File file)

    abstract String getRemoveFileCommand(File file)

    abstract ConfigurationConverter getConfigurationConverter()

    abstract String getExecuteScriptCommand(File file)

    abstract String singleQuote(String text)

    abstract String doubleQuote(String text)

    abstract List<String> getShellExecuteCommand(String... commands)

    abstract boolean validate()

    abstract String getFileSizeCommand(File file)

}
