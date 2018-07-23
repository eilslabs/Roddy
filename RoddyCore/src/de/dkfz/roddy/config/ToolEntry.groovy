/*
 * Copyright (c) 2017 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.config

import de.dkfz.roddy.config.ToolEntry.ToolParameter
import de.dkfz.roddy.core.ExecutionContext
import de.dkfz.roddy.knowledge.files.BaseFile
import groovy.transform.CompileStatic

import java.lang.reflect.Method
import java.util.stream.Collectors

/**
 * @author michael
 */
@CompileStatic
class ToolEntry implements RecursiveOverridableMapContainer.Identifiable {

    /**
     * A constraint for a parameter.
     * If the constraints checkMethod fails onFailMethod is called on the
     */
    static class ToolConstraint {
        public final Method onFailMethod;
        public final Method checkMethod;

        ToolConstraint(Method onFailMethod, Method checkMethod) {
            this.onFailMethod = onFailMethod;
            this.checkMethod = checkMethod;
        }

        void apply(BaseFile p) {
            try {
                boolean valid = (Boolean) checkMethod.invoke(p);
                if (!valid)
                    onFailMethod.invoke(p);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        ToolConstraint clone() {
            return new ToolConstraint(onFailMethod, checkMethod);
        }

        @Override
        public boolean equals(Object o) {
            // Is backed by test!
            if (this.is(o)) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ToolConstraint that = (ToolConstraint) o;

            if (onFailMethod != null ? !onFailMethod.equals(that.onFailMethod) : that.onFailMethod != null) return false;
            return checkMethod != null ? checkMethod.equals(that.checkMethod) : that.checkMethod == null;
        }

        @Override
        public int hashCode() {
            int result = onFailMethod != null ? onFailMethod.hashCode() : 0;
            result = 31 * result + (checkMethod != null ? checkMethod.hashCode() : 0);
            return result;
        }
    }

    static abstract class ToolParameter<T extends ToolParameter> {
        public final String scriptParameterName;

        ToolParameter(String scriptParameterName) {
            this.scriptParameterName = scriptParameterName;
        }

        @Override
        boolean equals(Object o) {
            // Is backed by test!
            if (this.is(o)) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ToolParameter<ToolParameter> that = (ToolParameter<ToolParameter>) o;

            return scriptParameterName != null ? scriptParameterName.equals(that.scriptParameterName) : that.scriptParameterName == null;
        }

        @Override
        int hashCode() {
            return scriptParameterName != null ? scriptParameterName.hashCode() : 0;
        }

        abstract T clone();
    }

    static abstract class ToolParameterOfFiles extends ToolParameter<ToolParameterOfFiles> {
        ToolParameterOfFiles(String scriptParameterName) {
            super(scriptParameterName);
        }

        abstract boolean hasSelectionTag();
        /**
         * The childFiles methods only return the possibly empty set of children. The files methods
         * return all children and possibly (for everything but pure aggregate parameters) the file
         * itself and all its children. The getAllChildFiles returns all children recursively.
         */
        abstract List<ToolParameterOfFiles> getAllFiles();

        abstract List<ToolParameterOfFiles> getFiles();
    }

    public final String id;
    public final String basePathId;
    public final String path;
    public final String computationResourcesFlags;
    private String inlineScript;
    private String inlineScriptName;
    final List<ToolParameter> inputParameters = new LinkedList<>();
    final List<ToolParameter> outputParameters = new LinkedList<>();
    final List<ResourceSet> resourceSets = new LinkedList<>();
    boolean overridesResourceSets;
    boolean useAutoCheckpoint;

    ToolEntry(String id, String basePathId, String path) {
        this.id = id;
        this.basePathId = basePathId;
        this.path = path;
        this.computationResourcesFlags = "";
    }

    ToolEntry(String id, String basePathId, String path, String computationResourcesFlags) {
        this.id = id;
        this.basePathId = basePathId;
        this.path = path;
        this.computationResourcesFlags = computationResourcesFlags != null ? computationResourcesFlags : "";
    }

    boolean isToolGeneric() {
        return overridesResourceSets || (inputParameters.size() > 0 || outputParameters.size() > 0);
    }

    void setGenericOptions(List<ToolParameter> input, List<ToolParameter> output, List<ResourceSet> resourceSets) {
        inputParameters.addAll(input);
        outputParameters.addAll(output);
        this.resourceSets.addAll(resourceSets);
    }

    ToolEntry clone() {
        List<ToolParameter> _inp = new LinkedList<ToolParameter>();
        List<ToolParameter> _outp = new LinkedList<ToolParameter>();
        List<ResourceSet> _rsets = new LinkedList<>();
        for (ToolParameter tp : inputParameters) {
            _inp.add(tp.clone());
        }
        for (ToolParameter tp : outputParameters) {
            _outp.add(tp.clone());
        }
        for (ResourceSet rs : resourceSets) {
            _rsets.add(rs.clone());
        }
        ToolEntry te = new ToolEntry(id, basePathId, path, computationResourcesFlags);
        te.setGenericOptions(_inp, _outp, _rsets);
        if (overridesResourceSets) te.setOverridesResourceSets();
        return te;
    }

    String getLocalPath() {
        return path;
    }

    void setInlineScript(String script) {
        this.inlineScript = script;
    }

    String getInlineScript() {
        return this.inlineScript;
    }

    void setInlineScriptName(String scriptName) {
        this.inlineScriptName = scriptName;
    }

    String getInlineScriptName() {
        return this.inlineScriptName;
    }

    boolean hasInlineScript() {
        if (inlineScript != null)
            return true
        else
            return false
    }

    boolean hasResourceSets() {
        return resourceSets.size() > 0
    }

    ResourceSet getResourceSet(Configuration configuration) {
        ResourceSetSize key = configuration.getResourcesSize();
        int size = key.ordinal()

        if (resourceSets.size() == 1) { // Only one set exists.
            return resourceSets.get(0)
        }

        if (resourceSets.size() == 0)
            return new EmptyResourceSet()

        ResourceSet first = resourceSets.get(0);
        ResourceSet last = resourceSets.get(resourceSets.size() - 1);
        if (size <= first.getSize().ordinal()) {  // The given key is smaller than the available keys. Return the first set.
            return first
        }
        if (size >= last.getSize().ordinal()) {  // The given key is larger than the available keys. Return the last set.
            return last;
        }
        for (ResourceSet resourceSet : resourceSets) {  // Select the appropriate set
            if (resourceSet.getSize() == key)
                return resourceSet
        }
        //Still no set, take the largest set, which comes after the given ordinal.
        for (ResourceSet resourceSet : resourceSets) {
            if (resourceSet.getSize().ordinal() > size)
                return resourceSet
        }

        return new EmptyResourceSet()
    }

    void setOverridesResourceSets() {
        overridesResourceSets = true
    }

    boolean doesOverrideResourceSets() {
        return overridesResourceSets
    }

    void setUseAutoCheckpoint() {
        useAutoCheckpoint = true
    }

    boolean usesAutoCheckpoint() {
        return useAutoCheckpoint
    }

    List<ToolParameter> getInputParameters(ExecutionContext context) {
        return getInputParameters(context.getConfiguration());
    }

    List<ToolParameter> getInputParameters(Configuration configuration) {
        if (overridesResourceSets) {
            List<ToolEntry> containerParents = configuration.getTools().getInheritanceList(this.id)
            if (containerParents.size() == 1)
                return inputParameters
            for (int i = containerParents.size() - 2; i >= 0; i--) {
                if (!containerParents.get(i).overridesResourceSets)
                    return containerParents.get(i).inputParameters
            }
        }
        return inputParameters;
    }

    List<ToolParameter> getOutputParameters(Configuration configuration) {
        if (overridesResourceSets) {
            List<ToolEntry> containerParents = configuration.getTools().getInheritanceList(this.id);
            if (containerParents.size() == 1)
                return outputParameters;
            for (int i = containerParents.size() - 2; i >= 0; i--) {
                if (!containerParents.get(i).overridesResourceSets)
                    return containerParents.get(i).outputParameters
            }
        }
        return outputParameters;
    }

    List<ToolParameterOfFiles> getOutputFileParameters(Configuration configuration) {
        return getOutputParameters(configuration).
                stream().
                filter({ ToolParameterOfFiles.isInstance(it) }).
                map({ it as ToolParameterOfFiles }).
                collect(Collectors.toList());
    }

    List<ResourceSet> getResourceSets() {
        return resourceSets;
    }

    @Override
    String getID() {
        return id;
    }

    @Override
    String toString() {
        return "ToolEntry " + id;
    }
}