/*
 * Copyright (c) 2017 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.config;

import de.dkfz.roddy.knowledge.files.BaseFile;
import de.dkfz.roddy.knowledge.files.FileGroup
import de.dkfz.roddy.knowledge.files.GenericFile;
import de.dkfz.roddy.knowledge.files.GenericFileGroup
import groovy.transform.CompileStatic;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by heinold on 15.03.17.
 */
public class ToolFileGroupParameterTest {

    @Test
    public void equals() throws Exception {
        ToolFileGroupParameter a = new ToolFileGroupParameter(GenericFileGroup.class, BaseFile.class, "ABC");
        ToolFileGroupParameter b = new ToolFileGroupParameter(GenericFileGroup.class, BaseFile.class, "ABC");
        ToolFileGroupParameter c = new ToolFileGroupParameter(GenericFileGroup.class, BaseFile.class, "ABC1");
        ToolFileGroupParameter d = new ToolFileGroupParameter(GenericFileGroup.class, GenericFile.class, "ABC1");

        assert a.equals(b)
        assert !a.equals(c)
        assert !a.equals(d)
        assert !b.equals(c)
        assert !b.equals(d)
        assert !c.equals(d)
    }

}