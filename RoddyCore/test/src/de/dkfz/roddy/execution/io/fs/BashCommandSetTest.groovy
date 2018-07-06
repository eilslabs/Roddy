/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.execution.io.fs;

import org.junit.Test

/**
 */
@groovy.transform.CompileStatic
public class BashCommandSetTest {
    @Test
    public void testGetReadabilityTestCommand() throws Exception {
        assert new BashCommandSet().getReadabilityTestCommand(new File("/tmp/test.file")).equals("[[ -e /tmp/test.file && -r /tmp/test.file ]] && echo TRUE || echo FALSE");
    }

    @Test
    public void testGetCheckForInteractiveConsoleCommand() {
        String separator = "\n"
        assert new BashCommandSet().getCheckForInteractiveConsoleCommand() == 'if [[ -z "${PS1-}" ]]; then' + separator + "\t echo \"non interactive process!\" >> /dev/stderr" + separator + "else" + separator + "\t echo \"interactive process\" >> /dev/stderr"
    }

    @Test
    public void testSingleQuote() {
        assert new BashCommandSet().singleQuote("Text") == "'Text'"
    }

    @Test
    public void testDoubleQuote() {
        assert new BashCommandSet().doubleQuote("Text") == '"Text"'
    }

    @Test
    public void testGetShellExecuteCommand() {
        assert new BashCommandSet().getShellExecuteCommand("env") == ["bash", "-c", "env"];
        assert new BashCommandSet().getShellExecuteCommand("echo a;", "echo b;") == ["bash", "-c", "echo a;", "echo b;"];
    }

    @Test
    public void testValidate() {
        // This method can only be tested if /bin/bash is really avaible. The test makes no sense in my oppinion

        def file = new File("/bin/bash")
        assert file.canRead() && file.canExecute() == new BashCommandSet().validate();
    }
}
