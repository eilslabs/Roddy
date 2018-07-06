/*
 * Copyright (c) 2018 German Cancer Research Center (DKFZ).
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */
package de.dkfz.roddy.core

import de.dkfz.roddy.tools.versions.Version
import de.dkfz.roddy.tools.versions.VersionLevel
import spock.lang.Specification

class VersionWithDevelopTest extends Specification {

    def "fromString()"() {
        when:
        def version = VersionWithDevelop.fromString("develop")
        then:
        notThrown Exception
    }

    def "isDevelop"() {
        when:
        def version = VersionWithDevelop.fromString("develop")
        then:
        assert version.isDevelop
    }

    def "getAt()"() {
        when:
        def version = VersionWithDevelop.fromString("develop")
        then:
        assert version[VersionLevel.REVISION] == Integer.MAX_VALUE
        assert version[VersionLevel.PATCH] == Integer.MAX_VALUE
        assert version[VersionLevel.MINOR] == Integer.MAX_VALUE
        assert version[VersionLevel.MAX_VALUE] == Integer.MAX_VALUE
    }

    def "toString(VersionLevel)"() {
        when:
        def version = VersionWithDevelop.fromString("develop")
        then:
        assert version.toString() == VersionWithDevelop.developString
        assert version.toString(VersionLevel.REVISION) == VersionWithDevelop.developString
        assert version.toString(VersionLevel.PATCH) == VersionWithDevelop.developString
        assert version.toString(VersionLevel.MINOR) == VersionWithDevelop.developString
        assert version.toString(VersionLevel.MAJOR) == VersionWithDevelop.developString
    }

    def "compareTo(other)"() {
        when:
        def devVersion = VersionWithDevelop.fromString("develop")
        def otherVersion = Version.fromString("1.2.3-4")
        then:
        assert devVersion > otherVersion
    }

}
