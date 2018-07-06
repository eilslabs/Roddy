/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.execution.io.fs

import de.dkfz.roddy.config.converters.ConfigurationConverter

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

    abstract String getListFullDirectoryContentRecursivelyCommand(File f, int depth, boolean onlyDirectories)

    abstract String getListFullDirectoryContentRecursivelyCommand(List<File> directories, List<Integer> depth, boolean onlyDirectories)

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
