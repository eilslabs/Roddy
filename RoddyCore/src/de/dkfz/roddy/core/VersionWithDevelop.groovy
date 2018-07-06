/*
 * Copyright (c) 2018 German Cancer Research Center (DKFZ).
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */
package de.dkfz.roddy.core

import de.dkfz.roddy.tools.versions.Version
import de.dkfz.roddy.tools.versions.VersionLevel
import groovy.transform.CompileStatic

/** Version class extended by "develop" versions. */
@CompileStatic
class VersionWithDevelop extends Version {

    static final developString = "develop"

    final boolean isDevelop

    private VersionWithDevelop(boolean isDevelop, Version plainVersion) {
        super(plainVersion.major, plainVersion.minor, plainVersion.patch, plainVersion.revision)
        this.isDevelop = isDevelop
    }

    /** Development versions have maximum version number in all fields. Therefore, for development versions, if you access the individual version
     *  levels, you'll always get Integer.MAX_VALUE as return value.
     *
     * @param string
     * @return
     */
    static VersionWithDevelop fromString(String string) {
        if (string.equals(developString)) {
            return new VersionWithDevelop(true, new Version(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE))
        } else {
            return new VersionWithDevelop(false, Version.fromString(string))
        }
    }

    static VersionWithDevelop getDevelop() {
        return VersionWithDevelop.fromString(developString)
    }

    String toString(VersionLevel level = VersionLevel.REVISION) {
        if (isDevelop) {
            return developString
        } else {
            return super.toString(level)
        }
    }

}
