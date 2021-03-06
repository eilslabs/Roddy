/*
 * Copyright (c) 2016 German Cancer Research Center (Deutsches Krebsforschungszentrum, DKFZ).
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/TheRoddyWMS/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.config.validation;

import de.dkfz.roddy.config.Configuration;

import java.io.File;

/**
 */
public abstract class ScriptChecker {

    protected final File file;

    protected final String toolID;

    protected final ScriptValidator scriptValidator;

    protected final Configuration configuration;

    protected ScriptChecker(File file, String toolID, ScriptValidator scriptValidator) {
        this.file = file;
        this.scriptValidator = scriptValidator;
        this.configuration = scriptValidator.getConfiguration();
        this.toolID = toolID;
    }

    public abstract void validateScript();
}
