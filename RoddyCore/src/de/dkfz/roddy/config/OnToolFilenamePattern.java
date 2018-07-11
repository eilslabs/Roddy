/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.config;

import de.dkfz.roddy.knowledge.files.BaseFile;

/**
 * Created by heinold on 14.01.16.
 */
public class OnToolFilenamePattern extends FilenamePattern {

    private final String toolID;

    public OnToolFilenamePattern(Class<BaseFile> cls, String script, String pattern, String selectionTag) {
        super(cls, pattern, selectionTag);
        this.toolID = script;
    }

    @Override
    public String getID() {
        return String.format("%s::onS_%s[%s]", cls.getName(), toolID, selectionTag);
    }

    public String getToolID() { return toolID; }

    @Override
    public FilenamePatternDependency getFilenamePatternDependency() {
        return FilenamePatternDependency.onTool;
    }

    protected BaseFile getSourceFile(BaseFile[] baseFiles) {
        return null;
    }

}
