package de.dkfz.roddy.core

import de.dkfz.roddy.StringConstants
import de.dkfz.roddy.tools.Tuple2
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.junit.Test
import spock.lang.Specification

@CompileStatic
class CohortDataRuntimeServiceExtensionSpockTest extends Specification {

    @CompileStatic(TypeCheckingMode.SKIP)
    def "test validate spec string"(String input, Boolean result) {
        when:
        def cdrse = new CohortDataRuntimeServiceExtension(null)

        then:
        cdrse.validateCohortDataSetLoadingString(input) == result

        where:
        input                                             | result
        "s[c:ADDD]"                                       | true
        "s[c:PID_0]"                                      | true
        "s[c:PID_0;PID_1;Pid-1]"                          | true
        "s[c:PID_0;PID_1]"                                | true
        "s[c:PID_0;PID_1;Pid-1|c:PID_0;Pid1]"             | true
        "s[c:PID_0;PID_1;Pid-1|c:PID_0;Pid1|c:PID-2;p?*]" | true
        's:SuperCohortName:[c:ADDD]'                      | true
        's[c:CohortName:ADDD]'                            | true
        "s[c:PID_0;]"                                     | false
        "c:PID_0"                                         | false
        "PID_0;PID_1"                                     | false
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    def "test super cohort parser methods"(String input, Tuple2<String, LinkedHashMap<String, List<String>>> result) {
        when:
        def cdrse = new CohortDataRuntimeServiceExtension(null)

        then:
        cdrse.matchSuperCohort(input) == result

        where:
        input                        | result
        "s[c:ADDD]"                  | new Tuple2<>("ADDD", ["ADDD": ["ADDD"]])
        "s[c:a]"                 | new Tuple2<>("a", ["a": ["a"]])
        "s[c:a;b;d]"                 | new Tuple2<>("a_b_d", ["a_b_d": ["a", "b", "d"]])
        "s[c:a;b]"                   | new Tuple2<>("a_b", ["a_b": ["a", "b"]])
        "s[c:a;b|c:d;e]"             | new Tuple2<>("a_b_d_e", ["a_b": ["a", "b"], "d_e": ["d", "e"]])
        "s[c:a;b;d|c:a;e]"           | new Tuple2<>("a_b_d_a_e", ["a_b_d": ["a", "b", "d"], "a_e": ["a", "e"]])
        "s[c:a;b;d|c:a;e|c:f;p?*]"   | new Tuple2<>("a_b_d_a_e_f_p?*", ["a_b_d": ["a", "b", "d"], "a_e": ["a", "e"], "f_p?*": ["f", "p?*"]])
        's:SuperCohortName:[c:ADDD]' | new Tuple2<>("SuperCohortName", ["ADDD": ["ADDD"]])
        's[c:CohortName:ADDD]'       | new Tuple2<>("CohortName", ["CohortName": ["ADDD"]])
    }

}