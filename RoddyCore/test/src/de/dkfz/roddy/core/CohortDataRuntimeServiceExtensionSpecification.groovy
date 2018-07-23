package de.dkfz.roddy.core

import de.dkfz.roddy.config.Configuration
import de.dkfz.roddy.config.ConfigurationError
import de.dkfz.roddy.tools.Tuple2
import groovy.transform.CompileStatic
import spock.lang.Shared
import spock.lang.Specification

class CohortDataRuntimeServiceExtensionSpecification extends Specification {

    @Shared
    Configuration configuration = new Configuration()

    def setupSpec() {
        configuration.configurationValues.put("sc1", "c1,c2,c3")
        configuration.configurationValues.put("c1", "d1,d2,d3,d4")
        configuration.configurationValues.put("c2", "d11,d12,d13,d14")
        configuration.configurationValues.put("c3", "d21,d22,d23,d24")
        configuration.configurationValues.put("sc2", "c4")
        configuration.configurationValues.put("c4", "d31,d32,d33,d34")
        configuration.configurationValues.put("sc5", "UNGUELTIG;UNGUELTIG;")
        configuration.configurationValues.put("sc6", "c1,c2,c5")
        configuration.configurationValues.put("sc7", "c6")
        configuration.configurationValues.put("c6", "#349jf#;9849#")
    }

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

    def "test super cohort parser methods"(String input, Tuple2<String, LinkedHashMap<String, List<String>>> result) {
        when:
        def cdrse = new CohortDataRuntimeServiceExtension(null)

        then:
        cdrse.matchSuperCohort(input) == result

        where:
        input                        | result
        "s[c:ADDD]"                  | new Tuple2<>("ADDD", ["ADDD": ["ADDD"]])
        "s[c:a]"                     | new Tuple2<>("a", ["a": ["a"]])
        "s[c:a;b;d]"                 | new Tuple2<>("a_b_d", ["a_b_d": ["a", "b", "d"]])
        "s[c:a;b]"                   | new Tuple2<>("a_b", ["a_b": ["a", "b"]])
        "s[c:a;b|c:d;e]"             | new Tuple2<>("a_b_d_e", ["a_b": ["a", "b"], "d_e": ["d", "e"]])
        "s[c:a;b;d|c:a;e]"           | new Tuple2<>("a_b_d_a_e", ["a_b_d": ["a", "b", "d"], "a_e": ["a", "e"]])
        "s[c:a;b;d|c:a;e|c:f;p?*]"   | new Tuple2<>("a_b_d_a_e_f_p?*", ["a_b_d": ["a", "b", "d"], "a_e": ["a", "e"], "f_p?*": ["f", "p?*"]])
        's:SuperCohortName:[c:ADDD]' | new Tuple2<>("SuperCohortName", ["ADDD": ["ADDD"]])
        's[c:CohortName:ADDD]'       | new Tuple2<>("CohortName", ["CohortName": ["ADDD"]])
    }

    def "test checkIfListOnlyContainsSCIdentifiers"(List<String> superCohortIdentifiers, boolean result) {
        expect:
        new CohortDataRuntimeServiceExtension(null).containsOnlySupercohortIds(superCohortIdentifiers) == result

        where:
        superCohortIdentifiers | result
        ["s:[c:a;b;c]", "sc1"] | false
        ["sc1", "sc2"]         | true
    }

    def "test invalid super cohort cvalues or fail"(List<String> supercohortIdentifiers, Class<Throwable> expectedException, String expectedMessage) {

        when:
        new CohortDataRuntimeServiceExtension(null).assertValidSuperCohortCValues(configuration, supercohortIdentifiers)

        then:
        def error = thrown(expectedException)
        error.message == expectedMessage

        where:
        supercohortIdentifiers | expectedException  | expectedMessage
        ["sc4"]                | ConfigurationError | "One or more super cohorts strings are not present in the configuration:\n\tsc4"
        ["sc5"]                | ConfigurationError | "One or more super cohort strings in the configuration are invalid:\n\tsc5 => UNGUELTIG;UNGUELTIG;"
        ["sc6"]                | ConfigurationError | "One or more cohort strings are not present in the configuration:\n\tCohort cvalue c5 is missing"
        ["sc7"]                | ConfigurationError | "One or more cohort configuration values are malformed:\n\tsc7, c6 => #349jf#;9849# is malformed"

    }

    def "test convert list of pid filters to parseable super cohort string"(List<String> supercohortIdentifiers, List<String> result) {

        expect:
        new CohortDataRuntimeServiceExtension(null).convertListPidFiltersToParseable(configuration, supercohortIdentifiers) == result

        where:
        supercohortIdentifiers | result
        ["sc1"]                | ["s:sc1:[c:c1:d1;d2;d3;d4|c:c2:d11;d12;d13;d14|c:c3:d21;d22;d23;d24]"]
        ["sc2"]                | ["s:sc2:[c:c4:d31;d32;d33;d34]"]
        ["sc1", "sc2"]         | ["s:sc1:[c:c1:d1;d2;d3;d4|c:c2:d11;d12;d13;d14|c:c3:d21;d22;d23;d24]", "s:sc2:[c:c4:d31;d32;d33;d34]"]
    }

}