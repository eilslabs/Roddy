package de.dkfz.roddy.execution.io.fs

import de.dkfz.roddy.RunMode
import de.dkfz.roddy.core.ContextResource
import de.dkfz.roddy.execution.io.ExecutionService
import de.dkfz.roddy.execution.io.LocalExecutionService
import org.junit.ClassRule
import spock.lang.Shared
import spock.lang.Specification

import static de.dkfz.roddy.execution.io.fs.FileSystemAccessProvider.RegexSearchDepth.Filename
import static de.dkfz.roddy.execution.io.fs.FileSystemAccessProvider.RegexSearchDepth.AbsolutePath
import static de.dkfz.roddy.execution.io.fs.FileSystemAccessProvider.RegexSearchDepth.RelativeToSearchFolder

class FileSystemAccessProviderSpecification extends Specification {

    @ClassRule
    static ContextResource contextResource = new ContextResource()

    @Shared
    File baseFolder

    @Shared
    List<File> files

    @Shared
    def fsap = new FileSystemAccessProvider()

    def setupSpec() {
        ExecutionService.initializeService(LocalExecutionService, RunMode.CLI)
        contextResource.tempFolder.create()
        baseFolder = contextResource.tempFolder.newFolder("images")

        files = ["abc.png", "bbb.png", "abc.txt", "aaa.png"].collect { new File(baseFolder, it) << "" }
    }

    def listFilesUsingWildcards(String wildcards, List<File> result) {
        expect:
        fsap.listFilesUsingWildcards(baseFolder, wildcards) == result.sort()

        where:
        wildcards | result
        "*.png"   | [files[0], files[1], files[3]]
        "*.txt"   | [files[2]]
        "*b*.png" | [files[0], files[1]]
    }

    def listFilesUsingRegex(File folder, String regex, FileSystemAccessProvider.RegexSearchDepth regexOnWholePath, List<File> result) {
        expect:
        fsap.listFilesUsingRegex(folder, regex, regexOnWholePath).sort() == result.sort()

        where:
        folder                          | regex                           | regexOnWholePath       | result
        baseFolder                      | "[a-z]*.png"                    | Filename               | [files[0], files[1], files[3]]
        baseFolder                      | "[a-z]*.txt"                    | Filename               | [files[2]]
        baseFolder                      | "[a-z]b[a-z].png"               | Filename               | [files[0], files[1]]
        contextResource.tempFolder.root | "images/[a-z]*.png"             | RelativeToSearchFolder | [files[0], files[1], files[3]]
        contextResource.tempFolder.root | "images/[a-z]*.txt"             | RelativeToSearchFolder | [files[2]]
        contextResource.tempFolder.root | "images/[a-z]b[a-z].png"        | RelativeToSearchFolder | [files[0], files[1]]
        baseFolder                      | "${baseFolder}/[a-z]*.png"      | AbsolutePath           | [files[0], files[1], files[3]]
        baseFolder                      | "${baseFolder}/[a-z]*.txt"      | AbsolutePath           | [files[2]]
        baseFolder                      | "${baseFolder}/[a-z]b[a-z].png" | AbsolutePath           | [files[0], files[1]]
    }
}