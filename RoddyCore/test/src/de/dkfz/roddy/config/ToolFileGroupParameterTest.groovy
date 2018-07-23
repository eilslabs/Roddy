/*
 * Copyright (c) 2017 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.config

import de.dkfz.roddy.Constants
import de.dkfz.roddy.knowledge.files.BaseFile
import de.dkfz.roddy.knowledge.files.FileGroup
import de.dkfz.roddy.knowledge.files.GenericFile
import de.dkfz.roddy.knowledge.files.GenericFileGroup
import groovy.transform.CompileStatic
import org.junit.Test

import static de.dkfz.roddy.Constants.DEFAULT
import static org.junit.Assert.*

/**
 * Created by heinold on 15.03.17.
 */
@CompileStatic
class ToolFileGroupParameterTest {

    @Test
    void equals() throws Exception {
        ToolFileGroupParameter a = new ToolFileGroupParameter(GenericFileGroup.class as Class<FileGroup>, BaseFile.class, "ABC", DEFAULT)
        ToolFileGroupParameter b = new ToolFileGroupParameter(GenericFileGroup.class as Class<FileGroup>, BaseFile.class, "ABC", DEFAULT)
        ToolFileGroupParameter c = new ToolFileGroupParameter(GenericFileGroup.class as Class<FileGroup>, BaseFile.class, "ABC1", DEFAULT)
        ToolFileGroupParameter d = new ToolFileGroupParameter(GenericFileGroup.class as Class<FileGroup>, GenericFile.class as Class<BaseFile>, "ABC1", DEFAULT)

        assert a.equals(b)
        assert !a.equals(c)
        assert !a.equals(d)
        assert !b.equals(c)
        assert !b.equals(d)
        assert !c.equals(d)
    }

}