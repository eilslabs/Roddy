package de.dkfz.roddy.knowledge.files

import de.dkfz.roddy.core.ContextResource
import groovy.transform.CompileStatic
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

@CompileStatic
class FileGroupTest {

    @ClassRule
    final public static ContextResource contextResource = new ContextResource()

    static List<BaseFile> listOfFiles

    @BeforeClass
    static void setup() {
        listOfFiles = [
                createFileObject("a"),
                createFileObject("b"),
                createFileObject("c"),
                createFileObject("d"),
        ]
    }

    static BaseFile createFileObject(String suffix) {
        BaseFile.getSourceFile(contextResource.createSimpleContext(FileGroupTest.class.name), "/tmp/somefile_${suffix}")
    }

    FileGroup<BaseFile> getTestFileGroup() {
        return new GenericFileGroup<BaseFile>(listOfFiles)
    }

    @Test
    void testGetFileGroup() {
        def fg = getTestFileGroup()
        assert fg.size() == 4
        assert fg[0].path ==  new File("/tmp/somefile_a")
        assert fg[1].path ==  new File("/tmp/somefile_b")
        assert fg[2].path ==  new File("/tmp/somefile_c")
        assert fg[3].path ==  new File("/tmp/somefile_d")
    }

    @Test
    void testGroovyIterator() {
        int i = 0
        getTestFileGroup().each {
            assert it == listOfFiles[i]
            i++
        }
    }

    @Test
    void testJavaForIn() {
        int i = 0
        for (BaseFile bf : getTestFileGroup()) {
            assert bf == listOfFiles[i]
            i++
        }
    }

    @Test
    void testForEach() {
        getTestFileGroup().forEach {
            assert it instanceof BaseFile
        }
    }

    @Test
    void testAdd() {
        FileGroup fg = getTestFileGroup()
        BaseFile file = createFileObject("e")
        fg.add(file)
        assert file.fileGroups.size() == 1
        assert file.fileGroups[0] == fg
        assert fg.size() == 5
    }

    @Test
    void testLeftShiftOperator() {
        FileGroup fg = getTestFileGroup()
        BaseFile file = createFileObject("e")
        fg << file
        assert file.fileGroups.size() == 1
        assert file.fileGroups[0] == fg
        assert fg.size() == 5    }

    @Test
    void testPlusOperator() {
        FileGroup fg = getTestFileGroup()
        BaseFile file = createFileObject("e")
        fg += file
        assert file.fileGroups.size() == 1
        assert file.fileGroups[0] == fg
        assert fg.size() == 5
    }

    @Test
    void testAddPlusWithAnotherFileGroup() {
        FileGroup fg1 = getTestFileGroup()
        FileGroup fg2 = getTestFileGroup()
        fg1 += fg2
        assert fg1.size() == 8
        assert fg1[4] == fg2[0]
        assert fg1[5] == fg2[1]
        assert fg1[6] == fg2[2]
        assert fg1[7] == fg2[3]
    }

    @Test
    void testAddFile() {
        FileGroup fg = getTestFileGroup()
        BaseFile file = createFileObject("e")
        fg.add(file)
        assert file.fileGroups.size() == 1
        assert file.fileGroups[0] == fg
        assert fg.size() == 5
    }

    @Test
    void testAddFiles() {
        FileGroup fg = getTestFileGroup()
        BaseFile file1 = createFileObject("e")
        BaseFile file2 = createFileObject("f")
        fg.addFiles([file1, file2])
        assert fg.size() == 6
    }

    @Test
    void testGetAt() {
        FileGroup fg = getTestFileGroup()
        assert fg[0].path == new File("/tmp/somefile_a")
        assert fg[3].path == new File("/tmp/somefile_d")
    }

    @Test
    void testGet() {
        FileGroup fg = getTestFileGroup()
        assert fg[0].path == new File("/tmp/somefile_a")
        assert fg[3].path == new File("/tmp/somefile_d")
    }

    @Test
    void testGetFilesInGroup() {
        FileGroup fg = getTestFileGroup()
        assert fg.filesInGroup == listOfFiles
    }
}