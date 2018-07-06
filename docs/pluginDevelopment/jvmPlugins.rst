JVM plugins
============

Java or Groovy based plugins are the default plugin type for Roddy, as both provide a lot of checks when the plugin is build.
E.g. variable type errors and misspelled variables. Brawl based workflows will be converted to Groovy workflows during runtime.
Here we will focus on the development of a new empty plugin. All you need is the basic setup described in `pluginDevelopersGuide`.
The code shown here can be found in the TestPluginWithJarFile plugin.

..  Note::
    There are some basic and test workflows available in the Roddy distribution folder. You can always take a look at them, if you need some examples.

Initial workflow
----------------

To start the development, you need to setup a package structure and put in a class which extends the Workflow class and an initial analysis configuration file.

Here comes the Java workflow class:

.. code-block:: Java

  package de.dkfz.roddy.knowledge.examples;

  import de.dkfz.roddy.core.Workflow;

  class SimpleWorkflow extends Workflow {
    @Override
    public boolean execute(ExecutionContext context) {
    }
  }

What you can see is a workflow class which overrides the execution method from Workflow.
There are other methods which you can override or use:

- checkExecutability - which returns a boolean value and

And here is the initial XML file:

.. code-block:: XML

  <configuration name='testAnalysis' description=''
   configurationType='analysis'
   class='de.dkfz.roddy.core.Analysis'
   workflowClass='de.dkfz.roddy.knowledge.examples.SimpleWorkflow'
   runtimeServiceClass="de.dkfz.roddy.knowledge.examples.SimpleRuntimeService"
   listOfUsedTools=""
   usedToolFolders="devel"
   cleanupScript="cleanupScript">

  </configuration>

What you have to do here is to set:

- The name attribute -> This is used as the analysis identifier.

- The workflowClass attribute -> This is the workflow class which we created above.

- And finally the runtimeServiceClass -> This class and its descendants is used to handle file and directory name issues.

That's it! This workflow could already be run though it would not produce any files.


Load a source file from storage
-------------------------------

Before you are able to start a job, You will need to load a file from storage. Roddy does not feature file loading by
a pattern or wildcards, but you have several other ways to get files from storage. While we say "load" or "get" a file,
we mean, that we do create a file object of the type *BaseFile*. The actual content of the files are not loaded! The
file does not even need to exist! Checking files is done in a separate step.

So which possibilities do you have:
- Construct the file manually (not recommended)
- Call Workflow.getSourceFile or BaseFile.getSourceFile / BaseFile.fromStorage
- Call Workflow.getSourceFilesUsingTool (or ExecutionService.getInstance().executeTool() and do the

.. code-block:: Java

    package de.dkfz.roddy.knowledge.examples;

    import de.dkfz.roddy.core.ExecutionContext;
    import de.dkfz.roddy.core.Workflow;
    import de.dkfz.roddy.knowledge.files.Tuple4;

    /**
     */
    public class TestWorkflow extends Workflow {
        @Override
        public boolean execute(ExecutionContext context) {
            SimpleRuntimeService srs = (SimpleRuntimeService) context.getRuntimeService();
            SimpleTestTextFile initialTextFile = srs.createInitialTextFile(context);
            SimpleTestTextFile textFile1 = initialTextFile.test1();
            FileWithChildren fileWithChildren = initialTextFile.testFWChildren();
            SimpleTestTextFile textFile2 = textFile1.test2();
            SimpleTestTextFile textFile3 = textFile2.test3();
            Tuple4 mout = (Tuple4) call("testScriptWithMultiOut", textFile3);
            return true;
        }
    }


Call a tool
-----------

Tool definition
===============


Actual call
===========

Now, let's extend the workflow to call a tool.
At first we need to get some files from storage with which we can work. Roddy works
with explicitely defined dependencies. Job dependencies are automatically created, when
an output file is used as an input to another job. Initially we do not have any files,
so we need to get at least one from storage.

.. code-block:: Java

  package de.dkfz.roddy.knowledge.examples;

  import de.dkfz.roddy.core.Workflow;

  class SimpleWorkflow extends Workflow {

    BaseFile createInitialTextFile(ExecutionContext ec) {
        BaseFile tf = BaseFile.constructSourceFile(
            new File(ec.runtimeService.getOutputAnalysisFolder(ec.getDataSet(),ec.getAnalysis()).getAbsolutePath(),
              "textBase.txt"),
            ec,
            new SimpleFileStageSettings(ec.getDataSet(), "100", "R001"),
          null)
        )
        if (!FileSystemAccessProvider.getInstance().checkFile(tf.getPath()))
            FileSystemAccessProvider.getInstance().createFileWithDefaultAccessRights(true, tf.getPath(), ec, true)
        return tf
    }

    @Override
    public boolean execute(ExecutionContext context) {

    }
  }
