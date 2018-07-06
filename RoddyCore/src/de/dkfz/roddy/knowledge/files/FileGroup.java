/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.knowledge.files;

import de.dkfz.roddy.config.ConfigurationError;
import de.dkfz.roddy.core.ExecutionContext;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Spliterator;
import java.util.function.Consumer;

/**
 * Filegroups group multiple files into one group. I.e. BamFiles can be grouped
 * to a BamFileGroup which can then be merged to a single BamFile
 *
 * @author michael
 */
public abstract class FileGroup<F extends BaseFile> extends FileObject implements Iterable<F> {

    // In some cases a group has another group as a parent.
    protected FileGroup parentGroup;

    protected final List<F> filesInGroup = new LinkedList<F>();

    public FileGroup(List<F> files) {
        super(null);
        if (files != null) {
            addFiles(files);
        }
    }

    @Override
    public Iterator<F> iterator() {
        return filesInGroup.iterator();
    }

    @Override
    public void forEach(Consumer<? super F> action) {
        filesInGroup.forEach(action);
    }

    /**
     * Calls runDefaultOperations on the contained files.
     */
    @Override
    public void runDefaultOperations() throws ConfigurationError {
        for (F file : filesInGroup) {
            file.runDefaultOperations();
        }
    }

    @Override
    public ExecutionContext getExecutionContext() {
        if (filesInGroup.size() > 0) {
            return filesInGroup.get(0).getExecutionContext();
        } else {
            throw new RuntimeException("Cannot call getExecutionContext if FileGroup has no files in it.");
        }
    }

    public final void add(F file) {
        addFile(file);
    }

    public final void leftShift(F file) {
        addFile(file);
    }

    public final FileGroup<F> plus(F file) {
        addFile(file);
        return this;
    }

    public final FileGroup<F> plus(FileGroup<F> filegroup) {
        addFiles(filegroup.filesInGroup);
        return this;
    }

    public final void addFile(F file) {
        filesInGroup.add(file);
        file.addFileGroup(this);
    }

    public final void addFiles(List<F> files) {
        filesInGroup.addAll(files);
        for(BaseFile file : files)
            file.addFileGroup(this);
    }

    public final F getAt(int index) {
        return filesInGroup.get(index);
    }

    public final F get(int index) {
        return filesInGroup.get(index);
    }

    public List<F> getFilesInGroup() {
        return new LinkedList<F>(filesInGroup);
    }

    public FileGroup getParentGroup() {
        return parentGroup;
    }

    public void setParentGroup(FileGroup parentGroup) {

        this.parentGroup = parentGroup;
    }
}
