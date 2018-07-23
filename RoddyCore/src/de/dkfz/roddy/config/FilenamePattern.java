/*
 * Copyright (c) 2017 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.config;

import de.dkfz.roddy.Constants;
import de.dkfz.roddy.Roddy;
import de.dkfz.roddy.knowledge.files.BaseFile;
import de.dkfz.roddy.core.ExecutionContext;
import de.dkfz.roddy.knowledge.files.FileStageSettings;


import java.io.File;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import de.dkfz.roddy.config.FilenamePatternHelper.Command;
import de.dkfz.roddy.config.FilenamePatternHelper.CommandAttribute;
import de.dkfz.roddy.tools.RoddyIOHelperMethods;

import static de.dkfz.roddy.Constants.DEFAULT;
import static de.dkfz.roddy.StringConstants.EMPTY;

/**
 * Filename patterns are stored in a configuration file. They are project specific and should be fully configurable.
 */
public abstract class FilenamePattern implements RecursiveOverridableMapContainer.Identifiable {

    private static final de.dkfz.roddy.tools.LoggerWrapper logger = de.dkfz.roddy.tools.LoggerWrapper.getLogger(FilenamePattern.class.getSimpleName());
    public static final String PLACEHOLDER_SOURCEFILE_ATOMIC_PREFIX = "${sourcefileAtomicPrefix";
    public static final String PLACEHOLDER_SOURCEFILE_PROPERTY = "${sourcefileProperty";
    public static final String PLACEHOLDER_CVALUE = "${cvalue";
    public static final String PLACEHOLDER_JOBPARAMETER = "${jobParameter";

    protected final Class<BaseFile> cls;
    protected final String pattern;
    protected final String selectionTag;
    protected boolean acceptsFileArrays;
    protected int enforcedArraySize;

    public FilenamePattern(Class<BaseFile> cls, String pattern, String selectionTag) {
        this.cls = cls;
        this.pattern = pattern;
        this.selectionTag = selectionTag != null ? selectionTag : DEFAULT;
    }

    public Class<BaseFile> getCls() {
        return cls;
    }

    public String getPattern() {
        return pattern;
    }

    public boolean hasSelectionTag() {
        return !selectionTag.equals(DEFAULT);
    }

    public String getSelectionTag() {
        return selectionTag;
    }

    public boolean doesAcceptFileArrays() {
        return acceptsFileArrays;
    }

    public boolean hasEnforcedArraySize() {
        return enforcedArraySize > 0;
    }

    public int getEnforcedArraySize() {
        return enforcedArraySize;
    }

    public abstract FilenamePatternDependency getFilenamePatternDependency();

    protected abstract BaseFile getSourceFile(BaseFile[] baseFiles);

    /**
     * Fills special values collected from the first parent file.
     * <p>
     * ${sourcefile}                            - Full name and path
     * ${sourcefileAtomic}                      - Only the name
     * ${sourcefileProperty,[VALUE]}            - Call a property from the source files.
     * The method tries to assemble the property getter name: get[Uppercase:V]alue
     * retrieve this via reflection and call it.
     * ${sourcefileAtomicPrefix,[delimiter]}    - Name of the source file until first occurrence of delimiter
     * ${sourcepath}                            - The path of the source file
     *
     * @param temp
     * @param baseFiles
     * @return
     * @throws Exception
     */
    String fillValuesFromSourceFile(String temp, BaseFile[] baseFiles) throws Exception {
        BaseFile sourceFile = getSourceFile(baseFiles);
        if (sourceFile != null) {
            File sourcepath = sourceFile.getPath();
            if (null == sourcepath) {
                throw new ConfigurationError("Path for source-file unknown. BaseFiles = " + RoddyIOHelperMethods.joinArray(baseFiles, ", "), (String) null);
            }
            temp = temp.replace("${sourcefile}", sourcepath.getAbsolutePath());
            temp = temp.replace("${sourcefileAtomic}", sourcepath.getName());
            if (temp.contains(PLACEHOLDER_SOURCEFILE_PROPERTY)) { //Replace the string with a property value
                Command command = FilenamePatternHelper.extractCommand( PLACEHOLDER_SOURCEFILE_PROPERTY, temp);
                String pName = command.attributes.keySet().toArray()[0].toString();

                String accessorName = "get" + pName.substring(0, 1).toUpperCase() + pName.substring(1);
                Method accessorMethod = sourceFile.getClass().getMethod(accessorName);
                String value = accessorMethod.invoke(sourceFile).toString();
                temp = temp.replace(command.fullString, value);
            }

            if (temp.contains(PLACEHOLDER_SOURCEFILE_ATOMIC_PREFIX)) {
                Command command = FilenamePatternHelper.extractCommand( PLACEHOLDER_SOURCEFILE_ATOMIC_PREFIX, temp);
                CommandAttribute att = command.attributes.get("delimiter");
                if (att != null) {
                    String sourcename = sourcepath.getName();
                    temp = temp.replace(command.fullString, sourcename.substring(0, sourcename.lastIndexOf(att.value)));
                }
            }

            temp = temp.replace("${sourcepath}", sourcepath.getParent());

        }
        return temp;
    }

    /**
     * Will be put to the file base configuration
     *
     * @param temp
     * @param baseFile
     * @return
     */
    @Deprecated
    String fillFileGroupIndex(String temp, BaseFile baseFile) {
        if (baseFile.hasIndexInFileGroup())
            temp = temp.replace("${fgindex}", baseFile.getIdxInFileGroup());
        return temp;
    }

    /**
     * Should this not be part of the variable replacement?
     *
     * @param src
     * @param context
     * @return
     */
    @Deprecated
    String fillDirectories(String src, ExecutionContext context) {
        Configuration cfg = context.getConfiguration();

        //Different output folder
        String oPath = context.getOutputDirectory().getAbsolutePath();
        src = src.replace("${outputAnalysisBaseDirectory}", oPath);

        //Replace output directories containing OutputDirectory
        final String odComp = "OutputDirectory";
        if (src.contains(odComp)) {
            String[] split = src.split(File.separator);
            for (String s : split) {
                if (s.contains(odComp)) {
                    String cvalID = s.substring(2, s.length() - 1);
                    ConfigurationValue cval = cfg.getConfigurationValues().get(cvalID);

                    String pathSup = cval.getType().equals("path") ? cval.toFile(context).getAbsolutePath() : cval.toString();
                    pathSup = pathSup.replace(Roddy.getApplicationDirectory().getAbsolutePath() + "/", ""); //Remove Roddy application folder from path...
                    src = src.replace(s, pathSup);
                }
            }
        }
        return src;
    }

    /**
     * Effectively try to resolve all the unresolved variables and ${cvalue,} marked variables
     *
     * @param src
     * @return
     */
    String fillConfigurationVariables(String src, Configuration cfg) {
        RecursiveOverridableMapContainerForConfigurationValues configurationValues = cfg.getConfigurationValues();

        for (boolean valueChanged = true; valueChanged && src.contains(PLACEHOLDER_CVALUE); ) {
            String oldValue = src;

            Command command = FilenamePatternHelper.extractCommand(PLACEHOLDER_CVALUE, src);
            CommandAttribute name = command.attributes.get("name");
            CommandAttribute defaultValue = command.attributes.get(DEFAULT);

            if (name != null) {
                ConfigurationValue cv;

                if (defaultValue != null) {
                    // If a default value is set, get the cvalue with the alternate default value.
                    cv = configurationValues.get(name.value, defaultValue.value);
                } else if (configurationValues.hasValue(name.value)) {
                    // If there is no default value set, a default one with "null" as the value might be returned.
                    // If it is null, set the cv to null.
                    cv = configurationValues.get(name.value, null);
                } else {
                    cv = null;
                }

                if (cv != null) {
                    // Available, non-null values will be inserted.
                    src = src.replace(command.fullString, cv.toString());
                } else {
                    // Log out a message, that a variable was not found and leave in the original value.
                    // In some cases, this is appropriate (like for parameters).
                    logger.postSometimesInfo("A variable could not be resolved " + command.rawName + " for a filename pattern. Replace part with the variable name.");
                    src = src.replace(command.fullString, "${" + command.rawName + "}");
                }

            }
            
            valueChanged = !oldValue.equals(src);
        }

        /** Try and resolve the leftover ${someKindOfValue}, stop, when nothing changed. **/
        boolean somethingChanged = true;
        Map<String, String> blacklist = new LinkedHashMap<>();
        int blacklistID = 1000;
        while (src.contains("${") && somethingChanged) {
            somethingChanged = false; //Reset
            Command command = FilenamePatternHelper.extractCommand(EMPTY, src);

            // The simple form for cvalue substitution does not allow name, default or other tags.
            String oldValue = src;
            // Only change it, if the value is in the configuration.
            if(configurationValues.hasValue(command.rawName)) {
                src = src.replace(command.fullString, configurationValues.get(command.rawName).toString());
            } else {
                String value = "###" + blacklistID + "###";
                blacklist.put(value, command.fullString);
                src = src.replace(command.fullString, value);
                blacklistID++;
                somethingChanged = true;
            }
            if (oldValue != src) somethingChanged = true;
        }

        for(String key : blacklist.keySet()) {
            String original = blacklist.get(key);
            src = src.replace(key, original);
        }

        return src;
    }

    String fillVariablesFromSourceFileValues(BaseFile baseFile, String temp) {
        FileStageSettings fs = baseFile.getFileStage();
        String _temp = temp;
        if (fs != null)
            temp = fs.fillStringContent(temp);
        if (temp == null)
            temp = _temp; //TODO Look why temp gets null. This should not be the case.
        //pid, sample, run...
        if (fs != null)
            temp = temp.replace("${fileStageID}", fs.getIDString());
        temp = temp.replace("${pid}", baseFile.getDataSet().toString()); // TODO: Move to plugin.
        temp = temp.replace("${dataSet}", baseFile.getDataSet().toString());
        return temp;
    }

    String fillVariablesFromSourceFileArrayValues(BaseFile[] baseFiles, String src) {
        if (!acceptsFileArrays || (enforcedArraySize != -1 && (enforcedArraySize == -1 || enforcedArraySize != baseFiles.length))) {
            return src;
        }
        for (int i = 0; i < baseFiles.length; i++) {
            BaseFile baseFile = baseFiles[i];

            //PID / Dataset id and filestage
            FileStageSettings fs = baseFile.getFileStage();
            src = src.replace(String.format("${fileStageID[%d]}", i), fs.getIDString());
            src = src.replace(String.format("${pid[%d]}", i), baseFile.getDataSet().toString()); // TODO: Move to plugin.
            src = src.replace(String.format("${dataSet[%d]}", i), baseFile.getDataSet().toString());
            src = fs.fillStringContentWithArrayValues(i, src);
        }
        return src;
    }

    /**
     * Applies the pattern to a filename.
     * If the pattern derives the name from a file, put in the sourcefile.
     * If the pattern uses the filestage, provide the targetfile.
     *
     * @param baseFile Either the source or the target file, depending on the patterns writeConfigurationFile.
     * @return
     */
    public String apply(BaseFile baseFile) {
        return apply(new BaseFile[]{baseFile});
    }

    /**
     * Applies this pattern to several filenames. Only derivedFrom is supported as dependency!
     *
     * @param baseFiles
     * @return
     */
    public String apply(BaseFile[] baseFiles) {

        String temp = pattern;
        try {
            BaseFile baseFile = baseFiles[0];

            // There is one source file existing, so source file based options can be applied.
            temp = fillValuesFromSourceFile(temp, baseFiles);
            temp = fillFileGroupIndex(temp, baseFile);
            temp = fillDirectories(temp, baseFile.getExecutionContext());
            temp = fillConfigurationVariables(temp, baseFile.getConfiguration());
            temp = fillVariablesFromSourceFileValues(baseFile, temp);
            temp = fillVariablesFromSourceFileArrayValues(baseFiles, temp);
        } catch (Exception e) {
            logger.severe("Could not apply filename pattern " + pattern + " for file " + baseFiles[0]);
            logger.rare(RoddyIOHelperMethods.getStackTraceAsString(e));
        }
        return temp;
    }


}
