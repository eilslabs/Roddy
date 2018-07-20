package de.dkfz.roddy.core

import org.junit.ClassRule
import spock.lang.Shared
import spock.lang.Specification

class RuntimeServiceSpecification extends Specification {

    @ClassRule
    static ContextResource contextResource = new ContextResource()

    @Shared
    ExecutionContext context

    def setupSpec() {
        context = contextResource.createSimpleContext(RuntimeServiceSpecification.class.name)
    }

    def "test selectDatasetsFromPattern"() {


//        expect:


//        where:

    }
}
