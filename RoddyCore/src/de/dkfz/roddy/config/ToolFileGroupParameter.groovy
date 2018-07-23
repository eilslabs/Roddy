/*
 * Copyright (c) 2017 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.config

import de.dkfz.roddy.knowledge.files.BaseFile
import de.dkfz.roddy.knowledge.files.FileGroup
import groovy.transform.CompileStatic

/**
 * Parameters for generic tools (tools which are not programmatically set!).
 * Parameters can be for in and for output.
 * Parameters can have constraints.
 *
 * File groups are supposed to contain multiple files of the same type. A good example would be
 * a group of .bed files for the different chromosomes.
 */
@CompileStatic
class ToolFileGroupParameter extends ToolEntry.ToolParameterOfFiles {
    public final Class<FileGroup> groupClass
    public final Class<BaseFile> genericFileClass
    public final List<ToolFileParameter> files
    public final PassOptions passOptions
    public final IndexOptions indexOptions
    public final String selectiontag

    enum PassOptions {
        parameters,
        array
    }

    enum IndexOptions {
        numeric,
        strings
    }

    @Deprecated
    ToolFileGroupParameter(Class<FileGroup> groupClass, List<ToolFileParameter> children, String scriptParameterName, PassOptions passas = PassOptions.parameters, IndexOptions indexOptions = IndexOptions.numeric, String selectiontag) {
        super(scriptParameterName)
        this.groupClass = groupClass
        this.passOptions = passas
        this.genericFileClass = null
        this.indexOptions = indexOptions
        this.files = children ?: new LinkedList<ToolFileParameter>()
        this.selectiontag = selectiontag
    }

    ToolFileGroupParameter(Class<FileGroup> groupClass, Class<BaseFile> genericFileClass, String scriptParameterName, PassOptions passas = PassOptions.parameters, IndexOptions indexOptions = IndexOptions.numeric, String selectiontag) {
        super(scriptParameterName)
        this.groupClass = groupClass
        this.passOptions = passas
        this.genericFileClass = genericFileClass
        this.indexOptions = indexOptions
        this.files = new LinkedList<ToolFileParameter>()
        this.selectiontag = selectiontag
    }

    /**
     * Copy constructor
     */
    @Deprecated
    ToolFileGroupParameter(String scriptParameterName, Class<FileGroup> groupClass, Class<BaseFile> genericFileClass, PassOptions passOptions, IndexOptions indexOptions, List<ToolFileParameter> files, String selectiontag) {
        super(scriptParameterName)
        this.selectiontag = selectiontag
        this.groupClass = groupClass
        this.genericFileClass = genericFileClass
        this.passOptions = passOptions
        this.indexOptions = indexOptions
        this.files = files ?: new LinkedList<ToolFileParameter>()
    }

    @Override
    public ToolFileGroupParameter clone() {
        return new ToolFileGroupParameter(scriptParameterName, groupClass, genericFileClass, passOptions, indexOptions, files?.collect { ToolFileParameter tfp -> tfp.clone() }, selectiontag)
    }

    @Override
    boolean equals(o) {
        //Backed by test
        if (this.is(o)) return true
        if (getClass() != o.class) return false
        if (!super.equals(o)) return false

        ToolFileGroupParameter that = o as ToolFileGroupParameter

        if (files != that.files) return false
        if (genericFileClass != that.genericFileClass) return false
        if (groupClass != that.groupClass) return false
        if (indexOptions != that.indexOptions) return false
        if (passOptions != that.passOptions) return false
        if (selectiontag != that.selectiontag) return false

        return true
    }

    int hashCode() {
        int result = super.hashCode()
        result = 31 * result + (groupClass != null ? groupClass.hashCode() : 0)
        result = 31 * result + (genericFileClass != null ? genericFileClass.hashCode() : 0)
        result = 31 * result + (files != null ? files.hashCode() : 0)
        result = 31 * result + (passOptions != null ? passOptions.hashCode() : 0)
        result = 31 * result + (indexOptions != null ? indexOptions.hashCode() : 0)
        result = 31 * result + (selectiontag != null ? selectiontag.hashCode() : 0)
        return result
    }

    boolean isGeneric() {
        return genericFileClass != null
    }

    String getGenericClassString() {
        if (isGeneric()) {
            return groupClass.getName() + "<" + genericFileClass.getName() + ">"
        } else {
            return groupClass.getName()
        }
    }

    @Override
    public boolean hasSelectionTag() {
        return false
    }

    @Override
    public List<ToolEntry.ToolParameterOfFiles> getAllFiles() {
        return files.collect { it.getAllFiles() }.flatten() as List<ToolEntry.ToolParameterOfFiles>
    }

    @Override
    public List<ToolEntry.ToolParameterOfFiles> getFiles() {
        return files as List<ToolEntry.ToolParameterOfFiles>
    }


}
