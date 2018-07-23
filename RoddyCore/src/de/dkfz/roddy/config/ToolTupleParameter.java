/*
 * Copyright (c) 2017 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.config;

import java.util.LinkedList;
import java.util.List;

import static de.dkfz.roddy.Constants.NO_VALUE;

/**
 * This class is supposed to contain output objects from a method call.
 * You can create tuples of different sizes containt file groups and files.
 *
 * Tuples are supposed to contain multiple, equally important files of different types.
 */
public class ToolTupleParameter extends ToolEntry.ToolParameterOfFiles {
    public final List<ToolEntry.ToolParameterOfFiles> files;

    public ToolTupleParameter(List<ToolEntry.ToolParameterOfFiles> files) {
        super(NO_VALUE); //Tuples are not passed
        this.files = files;
    }

    @Override
    public ToolTupleParameter clone() {
        List<ToolEntry.ToolParameterOfFiles> _files = new LinkedList<>();
        for (ToolEntry.ToolParameterOfFiles tf : files) _files.add(tf.clone());
        return new ToolTupleParameter(_files);
    }

    @Override
    public List<ToolEntry.ToolParameterOfFiles> getFiles() {
        return files;
    }

    @Override
    public List<ToolEntry.ToolParameterOfFiles> getAllFiles() { return files; }

    @Override
    public boolean hasSelectionTag() {
        return false;
    }

}
