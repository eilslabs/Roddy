package de.dkfz.roddy.execution.io.fs

/**
 * API Level 3.2+
 * A value class for loading source files with getSourceFile
 * Of course it can be extended later on.
 */
class Regex {
    final String regex

    Regex(String regex) {
        this.regex = regex
    }
}
