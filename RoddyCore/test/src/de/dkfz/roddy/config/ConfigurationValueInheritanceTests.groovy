/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.config

import groovy.transform.CompileStatic
import org.junit.Test

/**
 * Test cases for configuration and configuration value inheritance
 * Created by heinold on 14.07.16.
 */
@CompileStatic
class ConfigurationValueInheritanceTests {

    /**
     * Given are two configurations:
     * B extends A
     * =>   A -> B
     *
     * Configurations contain variables a and b
     * A = { a = 'abc', b = 'abc${a}' }         <br/> Idea will try to pull A B C on the same line on code format.
     * B = { a = 'def' }                        <br/>
     * expected C.b = 'klm'
     * expected B.b = 'hij'
     */
    @Test
    void testCValueSubstitutionWithCfgDualChain() {
        Configuration A = new Configuration(null)
        Configuration B = new Configuration(null, A)

        def cvA = A.getConfigurationValues()
        def cvB = B.getConfigurationValues()

        cvA.put('a', 'abc')
        cvA.put('b', 'abc${a}')

        cvB.put('a', 'def')

        assert cvB['a'].value == 'def'
        assert cvB['b'].value == 'abc${a}'
        assert cvB['b'].toString() == 'abcdef'
    }

    /**
     * Given are three configurations:
     * C extends B, B extends A
     * =>   A -> B -> C
     *
     * Configurations contain variables a and b
     * A = { a = 'abc', b = 'def' }             <br/> Idea will try to pull A B C on the same line on code format.
     * B = { a = 'hij', b = '$a' }              <br/>
     * C = { a = 'klm' }                        <br/>
     * expected C.b = 'klm'
     * expected B.b = 'hij'
     */
    @Test
    void testCValueSubstitutionWithCfgTripleChain() {
        Configuration A = new Configuration(null)
        Configuration B = new Configuration(null, A)
        Configuration C = new Configuration(null, B)

        def cvA = A.getConfigurationValues()
        def cvB = B.getConfigurationValues()
        def cvC = C.getConfigurationValues()

        cvA.put('a', 'abc')
        cvA.put('b', 'def')

        cvB.put('a', 'hij')
        cvB.put('b', '${a}')

        cvC.put('a', 'klm')

        assert cvB['a'].value == 'hij'
        assert cvB['b'].value == '${a}'
        assert cvB['b'].toString() == 'hij'
        assert cvC['b'].toString() == 'klm'
    }

    /**
     * Test a complex hierarchy
     * C1 extends B1 and B2, B1 extends A1 and A2
     * B2 has a higher priority than B1
     * A2 has a higher priority than A1
     *
     * =>   {{A1, A2} -> B1, B2} -> C1
     *
     * A1 = { a = 'A1.a', b = '${a}' }              <br/> Idea will try to pull A B C on the same line on code format.
     * A2 = { a = 'A2.a', b = 'A2.b' }              <br/>
     * B1 = { c = 'B1.c', d = '${a}' }              <br/>
     * B2 = { a = '${b} + ${c}', b = 'B2.b' }       <br/>
     * C1 = { c = 'C.c' }                           <br/>
     *
     * A1.b = 'A1.a'     // evaluation within same configuration
     * A2.b = 'A2.b'
     *
     * B1.a = 'A2.a'     // eval. taking value from parent of the highest priority (rightmost in parent list parameter)
     * B1.b = 'A2.b'     // eval. taking value from parent of the highest priority (rightmost in parent list parameter)
     * B1.d = 'A2.a'     // eval ${a} to the parent of highest priority
     *
     * B2.a = 'B2.b + ${c}'  // c is not set in A* or B* and therefore will not be evaluated
     *
     * C1.a = 'B2.b + C.c'
     * C1.b = 'B2.b'
     * C1.c = 'C.c'
     * C1.d = 'B2.b + C.c'
     */
    @Test
    void testCValueSubstitutionWithComplexHierarchy() {
        Configuration A1 = new Configuration(null)
        Configuration A2 = new Configuration(null)
        Configuration B1 = new Configuration(null, [A1, A2])
        Configuration B2 = new Configuration(null)
        Configuration C = new Configuration(null, [B1, B2])

        def cvA1 = A1.configurationValues
        def cvA2 = A2.configurationValues
        def cvB1 = B1.configurationValues
        def cvB2 = B2.configurationValues
        def cvC1 = C.configurationValues

        cvA1.put('a', 'A1.a')
        cvA1.put('b', '${a}')

        cvA2.put('a', 'A2.a')
        cvA2.put('b', 'A2.b')

        cvB1.put('c', 'B1.c')
        cvB1.put('d', '${a}')

        cvB2.put('a', '${b} + ${c}')
        cvB2.put('b', 'B2.b')

        cvC1.put('c', 'C.c')

        assert cvA1['b'].toString() == 'A1.a'
        assert cvA2['b'].toString() == 'A2.b'

        assert cvB1['a'].toString() == 'A2.a'
        assert cvB1['b'].toString() == 'A2.b'
        assert cvB1['d'].toString() == 'A2.a'

        assert cvB2['a'].toString() == 'B2.b + ${c}'   // c cannot be resolved and will stay as it is.

        assert cvC1['a'].toString() == 'B2.b + C.c'
        assert cvC1['b'].toString() == 'B2.b'
        assert cvC1['c'].toString() == 'C.c'
        assert cvC1['d'].toString() == 'B2.b + C.c'
    }

    @Test(expected = Exception)
    void testCyclicDependenciesSimple() {
        Configuration A1 = new Configuration(null)
        def cvA1 = A1.configurationValues
        cvA1.put('a', '${b}')
        cvA1.put('b', '${a}')

        cvA1.get("a").toString()
    }

    @Test(expected = Exception)
    void testCyclicDependenciesComplex() {
        Configuration A1 = new Configuration(null)
        def cvA1 = A1.configurationValues
        cvA1.put('a', '${b}')
        cvA1.put('b', '${c}')
        cvA1.put('c', '${d}')
        cvA1.put('d', '${a}')

        cvA1["a"].toString()
    }
}
