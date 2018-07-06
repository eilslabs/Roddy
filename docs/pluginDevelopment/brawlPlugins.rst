Brawl workflows
===============

Brawl is Roddys own domain specific language (DSL) for creating workflows. It looks a lot like the DSL of e.g. Snakemake
 or Nextflow.

Brawl workflows can be part of Plugins with or without Jar files. To create them, you create the folder:

.. code-block:: Bash

    resources/brawlWorkflows

Inside, you can create as many Brawl workflows as you like. Brawl workflow files either have the suffix .groovy OR .brawl, e.g.

.. code-block:: Bash

    resources/brawlWorkflow/TestWorkflow.groovy
    or
    resources/brawlWorkflow/TestWorkflow.brawl

However, one mistake you can see in the above example is, that the workflows are named equally. The workflow identifier is directly taken
from the filename itself. So if you want to import the workflow in your project configuration, you'd identify it with "TestWorkflow".

Structure
---------

Brawl workflows are Groovy workflows, so the basic Groovy / Java syntax applies. See the TestWorkflow.groovy,
which is located in the Roddy repository.

.. code-block:: groovy

    // Java variables
    String variable = "abc"

    // "Environment" / Roddy configuration values
    cvalue "valueString", "a text", "string"
    cvalue "valueInteger", 1
    cvalue "valueDouble", 1.0
    cvalue "aBooleanValue", true

    // Explicit workflow
    explicit {
        def file = getSourceFile("/tmp", "TextFile")
        def a = run "ToolA", file
    }

    // Tool / Rule section
    rule "ToolA", {
        input "TextFile", "parameterA"
        output "aClass", "parameterB", "/tmp/someoutputfile"
        shell """
                    #!/bin/bash
                    echo "\$parameterA"
                    echo "\$parameterB"
                    touch \$parameterB

                """
    }

What you need are: Configuration values, Rules and the explicit Closure / Block.


Configuration values
--------------------

Like in "full" Roddy workflows, there are two types of variables: Java variables, which only apply to the workflow file
itself and *cvalue* variables, which will be stored in the workflows configuration and will therefore be available in
the target system environment.

.. note::

    Please note the effects on quoting in Groovy strings! (See below for some more information or look up Groovy docs).
    Groovy will try to replace variables in Strings, if possible. Sometimes it might be necessary to quote the $ to prevent
    this!

Rules
-----

Rules or Tools are what is called with a run command (see below). They can contain input and output parameters, a shell
script OR a file reference and resource options. "rule" and "tool" are the same, you can decide for your preferred identifier.
We'll stick to rule for now. Rules are what will be executed on the target (cluster) system. Although they are configured
here, there is a quite a strict separation between rule scripts and the workflow side! Every value you need in a script
needs to be passed either as a parameter or as a cvalue! Java configuration values are not automatically available, except
if they are directly inserted by Groovy when the workflow is read in.

.. note::

    Every rule / tool you register, will be available on the script side! However, their names are translated. E.g.
    "ToolA" will become the environment variable "TOOL_TOOL_A" on the script side. See :doc:`../config/configFilenamesAndToolEntries`
    **"Tool entries"** for more information.

So which options do you have?

**Simple tool registration:**

.. code-block:: groovy

    rule "ToolA", "myWorkflowTools/scriptName.sh"

This will tell Roddy, that the script *scriptname.sh* exists in the *resources/analysisTools/myWorkflowTools* directory.

**Tool with inline (Bash / Shell) code**

.. code-block:: groovy

    rule "ToolA", {
        shell """
                    #!/bin/bash
                    echo "\$parameterA"
                    echo "\$parameterB"
                    touch \$parameterB

                """
    }

Here, Roddy will create a file called *ToolA* in the *resources/analysisTools/inlineScripts* directory

These are the two basic types of tools: inline and external. But what about input and output parameters? Just add them
to your definition. If the rule does is inline or references an external script does not matter. The same also applies
for resources.

.. code-block:: groovy

    rule "ToolA", {
        file "myWorkflowTools/scriptName.sh"
        input "TextFile", "parameterA"
        output "aClass", "parameterB", "/tmp/someoutputfile"
    }

This will configure *ToolA* to have one input and one output parameter. The input parameter will be accessible in the
script / environment with the variable *parameterA* (*parameterB* as well). The input parameter is configured to be of
the type *TextFile*. You can put in what you want and even use the same type for all i/o parameters, but the type will
allow Roddy to check for i/o compatibility between tools. The output is of type *aClass* and will be placed in
"/tmp/someoutputfile". The location of the output file is actually a filename pattern. All filename pattern rules apply,
please read the filename pattern section to get more information about this.

Like in XML tool definitions, it is of course possible to have more than one input or output parameter:

.. code-block:: groovy

    rule "ToolA", {
        file "myWorkflowTools/scriptName.sh"
        input "TextFile", "FileParameterA"
        input "TextFile", "FileParameterB"
        input "string", "StringParameter"
        output "VCFFile", "parameterB", "/tmp/\${StringParameter}_A_vs_B.vcf.gz"
        output "TextFile", "parameterB", "/tmp/\${StringParameter}_AnotherFile.txt"
    }

Here you have three input and two output values. Multiple output values are always bundled and stored into a tuple object!
We'll see later, how you can access it.

.. note::

    Brawl workflows are Groovy! Therefore please note, that the $ sign needs to be escaped in many cases! The example
    above uses \${StringParameter} to include the variable StringParameter in the file names. Depending on your requirements,
    you could also quote the filenames with a single tick ' to avoid the escape. However, you would then lose the ability
    to use Groovy variable values from the top part of the workflow (See configuration in the example workflow above).

**Resources**

As rules can be submitted to a compute cluster, you should make sure, that they don't consume too many resources. Therefore,
it is possible to configure them:

.. code-block:: groovy

    rule "ToolA", {
        file        "myWorkflowTools/scriptName.sh"
        input       "TextFile", "parameterA"
        output      "aClass", "parameterB", "/tmp/someoutputfile"
        walltime    "10h"
        memory      2.0
        cores       5           // Alternatively you can use threads if you like
    }

So what's above: cores, walltime and memory all define resources which might be required by your rule. Please look up the
tool entries in the configuration section for more information.

explicit {}
-----------

Soooo finally, this is the part where you run your workflow. You can use all of Roddys capabilities inside in this little
closure. However, we offer a shortcut and convenience methods, which might help you.

**Get a file where you know the path**

.. code-block:: groovy

    explicit {
        def file = getSourceFile("/tmp", "TextFile")
        [..]
    }

If you know the path of a source file, e.g. because you passed it as a configuration value or it has a fixed position
like in the above example, you can call *getSourceFile*. The second parameter is optional and, like in the examples with
the rules, sets the type of the file. The type, again, will be used by Roddy for type checks.

**Get one ore more source files using a tool**

This is different from the previous approach. Using the methods *getSourceFileUsingTool* or *getSourceFileUsingTool*
will allow you to run a tool on the target system which will then return a single file or a list of file objects.

.. code-block:: groovy

    explicit {
        def file = getSourceFileUsingTool("ToolForSingleFile", "TextFile")
        def files = getSourceFilesUsingTool("ToolForMultipleFiles", "TextFile")

        // OR

        BaseFile file = getSourceFileUsingTool("ToolForSingleFile", "TextFile")
        List<BaseFile> files = getSourceFilesUsingTool("ToolForMultipleFiles", "TextFile")

        [..]
    }

As previously mentioned, we are dealing with Groovy code. This way, you can always use the **def** keyword to declare
variables. Another way would be to to use the BaseFile class OR, if you defined a class file somewhere, you can of course
use this as well. Keep in mind, that the class file has to match the class in the method call!

.. important::

    File loader scripts are special. You need to define the tool rule like described above. Otherwise it won't work.
    **ALSO, DO NOT USE ANY DEBUG OUTPUT!** Roddy will directly create file objects for every line of output of your script!

**Get files which derive from other files**

**Call a tool**

**Call a tool with an output filegroup**

**Get run flags from your configuration**
