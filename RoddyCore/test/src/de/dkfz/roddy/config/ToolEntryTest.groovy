/*
 * Copyright (c) 2017 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.config

import de.dkfz.roddy.Constants
import de.dkfz.roddy.config.ResourceSet
import de.dkfz.roddy.config.ResourceSetSize
import de.dkfz.roddy.tools.BufferValue
import de.dkfz.roddy.tools.TimeUnit
import groovy.transform.CompileStatic
import static de.dkfz.roddy.config.ResourceSetSize.*
import java.lang.reflect.Method

/**
 * Test class for ToolEntry
 *
 * Fill in more tests when necessary.
 *
 * Created by heinold on 05.07.16.
 */
@CompileStatic
class ToolEntryTest extends GroovyTestCase {

    private ToolEntry createTestToolEntry() {
        def te = new ToolEntry("TestTool", "TestPath", "/SomewhereOverTheRainbow")
        te.getResourceSets().add(new ResourceSet(ResourceSetSize.s, new BufferValue(1), 1, 1, new TimeUnit("01:00"), new BufferValue(1), Constants.DEFAULT, ""));
        te.getResourceSets().add(new ResourceSet(ResourceSetSize.l, new BufferValue(1), 1, 1, new TimeUnit("01:00"), new BufferValue(1), Constants.DEFAULT, ""));
        return te;
    }

    private Configuration createTestConfiguration(final ResourceSetSize resourceSetSize) {
        return new Configuration(null) {
            @Override
            ResourceSetSize getResourcesSize() {
                return resourceSetSize;
            }
        }
    }

    void testHasResourceSets() {
        def entry = createTestToolEntry()
        assert entry.hasResourceSets()
        assert entry.getResourceSets().size() == 2
    }

    void testGetResourceSet() {
        def entry = createTestToolEntry()
        Map<ResourceSetSize, ResourceSetSize> givenAndExpected = [
                (t) : s,    // Select a set which is too small.
                (s) : s,    // Select a set which is available.
                (m) : l,    // Select a set which is in the middle and does not exist.
                (l) : l,    // Select a set which is available.
                (xl): l,    // Select a too large set.
        ].each {
            ResourceSetSize given, ResourceSetSize expected ->
                assert entry.getResourceSet(createTestConfiguration(given)).getSize() == expected
        }
    }


    /////// Tests for ToolFileParameterCheckCondition
    void testNewToolFileParameterCondition() {
        def abc = new ToolFileParameterCheckCondition("conditional:abc")
        assert ! abc.isBoolean()
        assert abc.getCondition()

        def pure = new ToolFileParameterCheckCondition("true")
        assert pure.isBoolean()
        assert pure.evaluate(null)
        assert pure.getCondition() == null

        pure = new ToolFileParameterCheckCondition("false")
        assert pure.isBoolean()
        assert pure.evaluate(null) == false
        assert pure.getCondition() == null
    }

    void testToolConstraintEquals() {
        Method ma = Integer.getMethod("valueOf", int)
        Method mb = Integer.getMethod("bitCount", int)
        Method mc = Integer.getMethod("decode", String)

        ToolEntry.ToolConstraint a = new ToolEntry.ToolConstraint(ma, mb)
        ToolEntry.ToolConstraint b = new ToolEntry.ToolConstraint(ma, mb)
        ToolEntry.ToolConstraint c = new ToolEntry.ToolConstraint(ma, mc)
        ToolEntry.ToolConstraint d = new ToolEntry.ToolConstraint(mb, mc)

        assert a.equals(b)
        assert !a.equals(c)
        assert !a.equals(d)
        assert !b.equals(c)
        assert !b.equals(d)
        assert !c.equals(d)
    }

    void testToolParameterEquals() {
        ToolEntry.ToolParameter a = getToolParameterInstance("ABC")
        ToolEntry.ToolParameter b = getToolParameterInstance("ABC")
        ToolEntry.ToolParameter c = getToolParameterInstance("ABCEF")

        assert a.equals(a)
        assert a.equals(b)
        assert !a.equals(c)
        assert !b.equals(c)
    }

    /**
     * Tool parameter is abstract. Still want to test it.
     * @param parm
     * @return
     */
    private ToolEntry.ToolParameter getToolParameterInstance(String parm) {
        new ToolEntry.ToolParameter(parm) {
            @Override
            ToolEntry.ToolParameter clone() {
                return this
            }
        }
    }
}
