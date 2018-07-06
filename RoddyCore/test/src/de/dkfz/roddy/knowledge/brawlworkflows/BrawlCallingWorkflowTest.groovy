package de.dkfz.roddy.knowledge.brawlworkflows

import de.dkfz.roddy.Constants
import de.dkfz.roddy.Roddy
import de.dkfz.roddy.RunMode
import de.dkfz.roddy.config.Configuration
import de.dkfz.roddy.config.ConfigurationError
import de.dkfz.roddy.config.ConfigurationValue
import de.dkfz.roddy.config.PreloadedConfiguration
import de.dkfz.roddy.config.ResourceSetSize
import de.dkfz.roddy.config.RoddyAppConfig
import de.dkfz.roddy.config.ToolEntry
import de.dkfz.roddy.core.ExecutionContext
import de.dkfz.roddy.core.ContextResource
import de.dkfz.roddy.execution.io.ExecutionService
import de.dkfz.roddy.execution.io.LocalExecutionService
import de.dkfz.roddy.execution.io.fs.FileSystemAccessProvider
import de.dkfz.roddy.execution.jobs.Job
import de.dkfz.roddy.tools.RoddyIOHelperMethods
import groovy.transform.CompileStatic
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

import java.lang.reflect.Field

@CompileStatic
class BrawlCallingWorkflowTest {

    @Rule
    final public ContextResource contextResource = new ContextResource()

    @Ignore @Test
    void testExecute() {
        ExecutionService.initializeService(LocalExecutionService, RunMode.CLI)
        FileSystemAccessProvider.initializeProvider(true)

        File temp = File.createTempFile("RODDYTEST", ".brawlworkflow")
        temp << '''
                /**
                 * Test workflow for the new Brawl Workflow DSL
                 */
                // Configuration
                String variable = "abc"
                
                cvalue "valueString", "a text", "string"
                cvalue "valueInteger", 1
                cvalue "valueDouble", 1.0
                cvalue "aBooleanValue", true
                
                // Explicit workflow ?
                explicit {
                    def file = getSourceFileUsingTool("ToolA")
                    println("Got a file " + file)
                    def outfile = run "ToolB", file
                }
                
                // Tool / Rule section
                tool "ToolA", {
                    shell """
                        #!/bin/bash
                        echo /tmp/aFile               
                        """
                }
                
                // Tool / Rule section
                tool "ToolB", {
                    input "TextFile", "IN_FILE"
                    output "TextFile", "OUT_FILE", "/tmp/abc"
                    shell """
                        #!/bin/bash
                        echo "\\${OUT_FILE}"
                        """
                }
                
                
                // Or implicit like Snakemake? Later maybe
            '''
        def preloaded = new PreloadedConfiguration(null, Configuration.ConfigurationType.OTHER, "Myname", "", "", null, null, ResourceSetSize.l, null, null, null, null)
        Configuration cfg = new Configuration(
                preloaded
        ) {

            @Override
            String getName() {
                return "Blabla"
            }

            @Override
            File getBrawlWorkflowSourceFile(String brawlName) {
                return temp
            }

            @Override
            File getSourceToolPath(String tool) throws ConfigurationError {
                return new File("/tmp/brawlWorkflows/${tool}.sh")
            }

            @Override
            File getProcessingToolPath(ExecutionContext context, String tool) throws ConfigurationError {
                return RoddyIOHelperMethods.assembleLocalPath(context.getAnalysisToolsDirectory(), "brawlWorkflow", "${tool}.sh")
            }

            @Override
            ResourceSetSize getResourcesSize() {
                return ResourceSetSize.l
            }
        }
        def context = contextResource.createSimpleContext(BrawlCallingWorkflowTest, cfg)

        def toolsDir = new File(context.getAnalysisToolsDirectory(), "brawlWorkflow")
        toolsDir.mkdirs()
        Field appProperties = Roddy.class.getDeclaredField("applicationProperties")
        appProperties.setAccessible(true)
        appProperties.set(null, new RoddyAppConfig())
        Roddy.getApplicationConfiguration().setApplicationProperty(Constants.APP_PROPERTY_SCRATCH_BASE_DIRECTORY, context.getTemporaryDirectory().absolutePath);
        Field cPreloaded = context.analysis.configuration.class.superclass.superclass.getDeclaredField("preloadedConfiguration")
        cPreloaded.setAccessible(true)
        cPreloaded.set(context.analysis.configuration, preloaded)
        // Create tools in storage. This is not done automatically in tests.
        def wrapper = cfg.getProcessingToolPath(context, Job.TOOLID_WRAPIN_SCRIPT)
        def entry = new ToolEntry(Job.TOOLID_WRAPIN_SCRIPT, "brawlWorkflow", wrapper.absolutePath)
        entry.inlineScript = """
            source \$PARAMETER_FILE 
            echo "######################################################### Starting wrapped script ###########################################################"
            source \$WRAPPED_SCRIPT
            echo "######################################################### Wrapped script ended ##############################################################"
         """
        wrapper << entry.inlineScript

        cfg.getProcessingToolPath(context, "ToolA") << """
                        #!/bin/bash
                        echo /tmp/aFile                   
                        """

        cfg.getProcessingToolPath(context, "ToolB") << """
                        #!/bin/bash
                        echo "\${OUT_FILE}"
                        """

        wrapper.setExecutable(true)
        cfg.getProcessingToolPath(context, "ToolA").setExecutable(true)
        cfg.getProcessingToolPath(context, "ToolB").setExecutable(true)

        cfg.tools << entry
        cfg.configurationValues << new ConfigurationValue("activeBrawlWorkflow", temp.absolutePath)

        try {
            def wf = new BrawlCallingWorkflow()
            wf.execute(context)
        } finally {
            temp.delete()
        }
    }
}