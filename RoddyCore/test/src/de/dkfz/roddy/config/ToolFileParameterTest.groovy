/*
 * Copyright (c) 2017 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.config

import de.dkfz.roddy.knowledge.files.BaseFile
import groovy.transform.CompileStatic;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by heinold on 08.03.17.
 */
@CompileStatic
public class ToolFileParameterTest {
    @Test
    public void testCloneAndEquals() throws Exception {
        def src = new ToolFileParameter(BaseFile, [], "ABC", new ToolFileParameterCheckCondition(true), "DEF", null, "abc")
        def clone = src.clone()
        assert clone.equals(src)
    }
}