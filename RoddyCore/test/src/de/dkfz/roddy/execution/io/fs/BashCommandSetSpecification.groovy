package de.dkfz.roddy.execution.io.fs

import groovy.io.FileType
import spock.lang.Shared
import spock.lang.Specification

import static groovy.io.FileType.ANY
import static groovy.io.FileType.DIRECTORIES
import static groovy.io.FileType.FILES

class BashCommandSetSpecification extends Specification {

    @Shared
    BashCommandSet b = new BashCommandSet()

    /**
     * A non existing file. No r/w ops will be performed on it.
     */
    @Shared
    File tmpa = new File("/tmp/a")

    @Shared
    File tmpb = new File("/tmp/b")

    @Shared
    File tmpc = new File("/tmp/c")

    def testGetListFullDirectoryContentRecursivelyCommand(File directory, int depth, FileType selectedType, boolean complexList, String result) {
        expect:
        b.getListFullDirectoryContentRecursivelyCommand(directory, depth, selectedType, complexList) == result

        where:
        directory | depth | selectedType | complexList | result
        tmpa      | -1    | ANY          | false       | 'find "/tmp/a"'
        tmpa      | -1    | FILES        | false       | 'find "/tmp/a" -type f'
        tmpa      | -1    | DIRECTORIES  | true        | 'find "/tmp/a" -type d -ls'
        tmpa      | 0     | ANY          | false       | 'find "/tmp/a"'
        tmpa      | 1     | FILES        | false       | 'find "/tmp/a" -maxdepth 1 -type f'
        tmpa      | 2     | DIRECTORIES  | true        | 'find "/tmp/a" -maxdepth 2 -type d -ls'
    }

    def testGetListFullDirectoriesContentRecursivelyCommand(List<File> directories, List<Integer> depth, FileType selectedType, boolean complexList, String result) {
        expect:
        b.getListFullDirectoriesContentRecursivelyCommand(directories, depth, selectedType, complexList) == result

        where:
        directories  | depth  | selectedType | complexList | result
        [tmpa]       | [-1]   | ANY          | false       | 'find "/tmp/a"'
        [tmpa, tmpa] | [0, 0] | ANY          | false       | 'find "/tmp/a"'
        [tmpa, tmpb] | [1, 1] | FILES        | false       | 'find "/tmp/a" -maxdepth 1 -type f && find "/tmp/b" -maxdepth 1 -type f'
        [tmpb, tmpc] | [2, 2] | DIRECTORIES  | true        | 'find "/tmp/b" -maxdepth 2 -type d -ls && find "/tmp/c" -maxdepth 2 -type d -ls'
    }

    def testGetFindFilesUsingWildcardsCommand(File baseFolder, String wildcards, String result) {
        expect:
        b.getFindFilesUsingWildcardsCommand(baseFolder, wildcards) == result

        where:
        baseFolder | wildcards      | result
        tmpa       | '*.png'        | 'for f in $(ls "/tmp/a/"*.png | sort); do echo "${f}"; done'
        tmpb       | "*sub*/*.png"  | 'for f in $(ls "/tmp/b/"*sub*/*.png | sort); do echo "${f}"; done'
        tmpc       | "??\\ abc.png" | 'for f in $(ls "/tmp/c/"??\\ abc.png | sort); do echo "${f}"; done'
    }
}
