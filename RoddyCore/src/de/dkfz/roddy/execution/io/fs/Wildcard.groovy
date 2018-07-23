package de.dkfz.roddy.execution.io.fs

import groovy.transform.CompileStatic

/**
 * API Level 3.2+
 * A value class for loading source files with getSourceFile
 * Of course it can be extended later on.
 */
@CompileStatic
class Wildcard {
    final String wildcard

    Wildcard(String wildcard) {
        this.wildcard = wildcard
    }
}
