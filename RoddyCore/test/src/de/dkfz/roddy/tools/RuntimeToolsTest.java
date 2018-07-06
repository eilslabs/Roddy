/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.tools;

import de.dkfz.roddy.Roddy;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * This test class does runtime environment specific tests. It is not very well written and uses some hardcoded elements.
 * However, the dependency to the runtime system makes it necessary to somehow hardcode things :(... Don't currently know, how to make it better.
 * Created by heinold on 20.06.16.
 */
public class RuntimeToolsTest {
    @Test
    public void getRoddyRuntimeVersion() throws Exception {
        assertEquals("3.1", RuntimeTools.getRoddyRuntimeVersion());
    }

    @Test
    public void getJavaRuntimeVersion() throws Exception {
        assertEquals("1.8", RuntimeTools.getJavaRuntimeVersion());
    }

    @Test
    public void testGetBuildinfoFile() {
        String buildinfoTextFile = RuntimeTools.getBuildinfoFile().getAbsolutePath();
        String estimatedBuildInfoTextFile = RoddyIOHelperMethods.assembleLocalPath(Roddy.getApplicationDirectory(), "dist", "bin", "develop", "buildinfo.txt").getAbsolutePath();
        assertEquals(estimatedBuildInfoTextFile, buildinfoTextFile);
    }

    @Test
    public void testGetDevelopmentDistFolder() {
        assertEquals(
                RoddyIOHelperMethods.assembleLocalPath(Roddy.getApplicationDirectory(), "dist", "bin", "develop").getAbsolutePath(),
                RuntimeTools.getDevelopmentDistFolder().getAbsolutePath());
    }

}