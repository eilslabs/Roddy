/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.knowledge.examples

import de.dkfz.roddy.Roddy
import de.dkfz.roddy.config.Configuration
import de.dkfz.roddy.core.ExecutionContext
import de.dkfz.roddy.core.RuntimeService
import de.dkfz.roddy.execution.io.fs.FileSystemAccessProvider
import de.dkfz.roddy.knowledge.files.BaseFile
import de.dkfz.roddy.knowledge.files.BaseFile.ConstructionHelperForSourceFiles

/**
 */
@groovy.transform.CompileStatic
class SimpleRuntimeService extends RuntimeService {
    @Override
    Map<String, Object> getDefaultJobParameters(ExecutionContext context, String toolID) {
        Configuration cfg = context.getConfiguration()
        String pid = context.getDataSet().toString()
        Map<String, Object> parameters = new LinkedHashMap<>()
        parameters.put("pid", (Object) pid)
        parameters.put("PID", (Object) pid)
        parameters.put("PARAMETER_FILE", getConfigurationFile(context).getAbsolutePath())
        parameters.put("ANALYSIS_DIR", context.getOutputDirectory().getParentFile().getParent())
        if (toolID != null && toolID.length() > 0) {
            parameters.put("PRM_TOOLS_DIR", cfg.getProcessingToolPath(context, toolID).getParent())
        }
        return parameters
    }

    @Override
    String createJobName(ExecutionContext executionContext, BaseFile file, String TOOLID, boolean reduceLevel) {
        return "RoddyTest_${TOOLID}"
    }

    @Override
    boolean isFileValid(BaseFile baseFile) {
        //Parents valid?
        boolean parentsValid = true
        for (BaseFile bf in baseFile.parentFiles) {
            if (bf.isTemporaryFile()) continue //We do not check the existence of parent files which are temporary.
            if (bf.isSourceFile()) continue //We do not check source files.
            if (!bf.isFileValid()) {
                return false
            }
        }

        boolean result = true

        //Source files should be marked as such and checked in a different way. They are assumed to be valid.
        if(baseFile.isSourceFile())
            return true

        //Temporary files are also considered as valid.
        if(baseFile.isTemporaryFile())
            return true

        try {
            //Was freshly created?
            if (baseFile.creatingJobsResult != null && baseFile.creatingJobsResult.successful) {
                result = false
            }
        } catch (Exception ex) {
            result = false
        }

        try {
            //Does it exist and is it readable?
            if (result && !baseFile.isFileReadable()) {
                result = false
            }
        } catch (Exception ex) {
            result = false
        }

        try {
            //Can it be validated?
            //TODO basefiles are always validated!
            if (result && !baseFile.checkFileValidity()) {
                result = false
            }
        } catch (Exception ex) {
            result = false
        }

// If the file is not valid then also temporary parent files should be invalidated! Or at least checked.
        if (!result) { }

        return result
    }

    SimpleTestTextFile createInitialTextFile(ExecutionContext ec) {
        SimpleTestTextFile tf = new SimpleTestTextFile(new ConstructionHelperForSourceFiles(new File(getOutputAnalysisBaseDirectory(ec.getDataSet(), ec.getAnalysis()).getAbsolutePath(), "textBase.txt"), ec, new SimpleFileStageSettings(ec.getDataSet(), "100", "R001"), null))
        if (!FileSystemAccessProvider.getInstance().checkFile(tf.getPath()))
            FileSystemAccessProvider.getInstance().createFileWithDefaultAccessRights(true, tf.getPath(), ec, true)
        return tf
    }
}
