package de.dkfz.roddy.knowledge.brawlworkflows

import de.dkfz.roddy.Roddy
import de.dkfz.roddy.config.ConfigurationError
import de.dkfz.roddy.core.ExecutionContext
import de.dkfz.roddy.core.Workflow
import de.dkfz.roddy.execution.io.ExecutionService
import de.dkfz.roddy.execution.io.fs.FileSystemAccessProvider
import de.dkfz.roddy.plugins.LibrariesFactory
import groovy.transform.CompileStatic

/**
 * Brawl workflows
 */
@CompileStatic
class BrawlCallingWorkflow extends Workflow {

    @Override
    boolean execute(ExecutionContext context) throws ConfigurationError {

        String brawlID = context.getConfigurationValues().get("activeBrawlWorkflow")
        if (!brawlID)
            return false

        File brawlFile = context.configuration.getBrawlWorkflowSourceFile(brawlID)
        List<String> brawlCode = Roddy.getLocalFileSystemAccessProvider().loadTextFile(brawlFile) as List<String>
        brawlCode.add(0, "def wf = BrawlWorkflow.create context, {")
        brawlCode.add(0, "import de.dkfz.roddy.knowledge.brawlworkflows.*")
        brawlCode << "}"
        File temporaryScript = File.createTempFile("roddy", "brawlTemp")
        temporaryScript << brawlCode.join("\n")

        try {
            def cl = LibrariesFactory.instance.getGroovyClassLoader()
            def se = new GroovyScriptEngine(temporaryScript.getAbsolutePath(), cl)
            Binding bind = new Binding()
            bind.setVariable("context", context)
            BrawlWorkflow brawlWorkflow = se.run(temporaryScript.absolutePath, bind) as BrawlWorkflow

            // Brawl workflows contain inline scripts. As the Brawl is set up on load time, its configuration and
            // therefore its inline scripts are not available when writeFilesForExecution is called earlier.
            // This is why we have to call it again. Also, as the target directories / links directly already
            // exist, we need to remove them from the execution directory first, so that they can be created again in
            // a proper way.
            FileSystemAccessProvider.getInstance().removeDirectory(context.getAnalysisToolsDirectory())
            FileSystemAccessProvider.getInstance().checkDirectory(context.getAnalysisToolsDirectory(), true)
            ExecutionService.getInstance().copyAnalysisToolsForContext(context)

            // Now run the workflow
            brawlWorkflow.execute(context)
            return true
        } finally {
            temporaryScript.delete()
        }
        return false
    }
}
