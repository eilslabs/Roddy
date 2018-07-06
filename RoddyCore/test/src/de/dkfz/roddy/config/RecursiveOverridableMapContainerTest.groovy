package de.dkfz.roddy.config

import spock.lang.Specification

class RecursiveOverridableMapContainerTest extends Specification {

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
    Configuration makeConfig() {
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

        return C
    }


    def "GetAllValues"() {
        when:
        Configuration cfg = makeConfig()
        Map<String, ConfigurationValue> values = cfg.getConfigurationValues().getAllValues()

        then:
        values['a'].toString() == 'B2.b + C.c'
        values['b'].toString() == 'B2.b'
        values['c'].toString() == 'C.c'
        values['d'].toString() == 'B2.b + C.c'
    }
}
