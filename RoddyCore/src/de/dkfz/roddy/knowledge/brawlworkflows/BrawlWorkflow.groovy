package de.dkfz.roddy.knowledge.brawlworkflows;

import de.dkfz.roddy.config.ConfigurationError
import de.dkfz.roddy.config.ConfigurationValue
import de.dkfz.roddy.core.ExecutionContext
import de.dkfz.roddy.core.ExecutionContextError
import de.dkfz.roddy.core.Workflow
import de.dkfz.roddy.execution.UnexpectedExecutionResultException
import de.dkfz.roddy.execution.io.ExecutionService
import de.dkfz.roddy.knowledge.files.BaseFile
import de.dkfz.roddy.knowledge.files.FileGroup
import de.dkfz.roddy.knowledge.files.FileObject
import de.dkfz.roddy.knowledge.methods.GenericMethod
import de.dkfz.roddy.tools.LoggerWrapper
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import sun.reflect.generics.reflectiveObjects.NotImplementedException

import java.util.concurrent.ExecutionException
import java.util.logging.Level

@CompileStatic
class BrawlWorkflow extends Workflow {

    final static LoggerWrapper logger = LoggerWrapper.getLogger(BrawlWorkflow)

    final BrawlWorkflow THIS = this

    /**
     * This is more or less the execute method of a normal workflow. It is set by the DSL method explicit.
     * Additionally we might implement implicit workflows, which will then be used to run things automatically.
     * Currently, however, we only support the strict explicit syntax. (aka you tell Roddy what to do)
     */
    Closure explicitWorkflow


    static BrawlWorkflow create(
            ExecutionContext context,
            @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = BrawlWorkflow) Closure script
    ) {
        def workflow = new BrawlWorkflow()
        workflow.setContext(context)
        script.resolveStrategy = Closure.DELEGATE_FIRST
        script.delegate = workflow
        script()
        return workflow
    }

    /**
     * This is used to create ToolEntry objects for the workflow configuration
     * @param name
     * @param script
     * @return
     */
    BrawlWorkflowToolCall rule(
            String name,
            @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = BrawlWorkflowToolCall) Closure script
    ) {
        if (name == null)
            throw new ConfigurationError("A rule in your workflow script does not have a name.", "BRAWLWORKFLOW")

        def rule = new BrawlWorkflowToolCall(THIS, name)
        script.resolveStrategy = Closure.DELEGATE_FIRST
        script.delegate = rule
        script()
        context.configuration.tools << rule.toToolEntry()
        rule
    }

    BrawlWorkflowToolCall tool(String name, @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = BrawlWorkflowToolCall) Closure script
    ) {
        rule(name, script)
    }

    /**
     * Run the workflow
     * @param context
     * @return
     * @throws ConfigurationError
     */
    @Override
    boolean execute(ExecutionContext context) throws ConfigurationError {
        if (explicitWorkflow)
            explicitWorkflow.call()
        else {
            context.addErrorEntry(ExecutionContextError.EXECUTION_SCRIPT_INVALID.expand("Implicit Brawl workflows are not yet supported. You have to use the explicit method to create the workflow.", Level.SEVERE))
            throw new NotImplementedException()
        }
    }

    /**
     * Set the explicit execute closure.
     * @param script
     */
    void explicit(Closure script) {
        explicitWorkflow = script
    }

}

