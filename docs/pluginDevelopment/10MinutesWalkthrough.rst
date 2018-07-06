Workflow development primer
===========================

Following the instructions on this page, you should be able to setup and run a basic workflow within ten minutes.
At the end of this page you'll find all commands in one code block. This guide assumes that you will be developing for
Roddy version 3.0.x and that you will create a JVM based workflow.

1. Setup a plugins folder
-------------------------

The plugins folder is the folder, where you will store your (self-created) plugins.

.. code-block:: bash

    mkdir ~/RoddyPlugins

2. Prepare the plugin folder
----------------------------

Now create a folder in which you will store your new plugin.

.. code-block:: bash

    cd ~/RoddyPlugins
    mkdir NewPlugin
    cd NewPlugin

3. Create the first files and folders
-------------------------------------

This will create the basic structure which is necessary for your plugin. See the :doc:`pluginDevelopersGuide` for more information about plugin structures.
We are

.. code-block:: bash

    mkdir -p resources/analysisTools/workflowTools
    mkdir -p resources/configurationFiles

    echo 0.0 > buildversion.txt
    echo 0 >> buildversion.txt

    echo "dependson=PluginBase:1.0.29" > buildversion.txt
    echo "dependson=DefaultPlugin:1.0.34" > buildversion.txt
    echo "RoddyAPIVersion=3.0" > buildversion.txt
    echo "JDKVersion=1.8" >> buildversion.txt
    echo "GroovyVersion=2.4" >> buildversion.txt

4. Create the src folder and the inital java package
----------------------------------------------------

We'll use our package structure for this example, change it as you need it.
You'll need the src structure, if you want to compile the plugin using Roddy.

.. code-block:: bash

    mkdir -p src/de/dkfz/roddy/newplugin
    cd src/de/dkfz/roddy/newplugin

In this directory, create the file *NewPlugin.java* and put in the following code.

.. code-block:: java

    package de.dkfz.roddy.newplugin;

    import de.dkfz.roddy.plugins.BasePlugin;

    public class TestPlugin extends BasePlugin {
        public static final String CURRENT_VERSION_STRING = "0.0.0";
        public static final String CURRENT_VERSION_BUILD_DATE = "NotBuildYet";

        @Override
        public String getVersionInfo() {
            return "Roddy plugin: " + this.getClass().getName() + ", V " + CURRENT_VERSION_STRING + " built at " + CURRENT_VERSION_BUILD_DATE;
        }
    }

There you are, next step is...

5. Create a workflow class
--------------------------

In this directory, create the file *NewWorkflow.java* and put in the following code.

.. code-block:: Java

    package de.dkfz.roddy.newplugin;

    import de.dkfz.roddy.core.ExecutionContext;
    import de.dkfz.roddy.core.Workflow;

    public class NewWorkflow extends Workflow {
        @Override
        public boolean execute(ExecutionContext context) {
            return true;
        }
    }

6. Create your analysis XML file
--------------------------------

The next step is the creation of your analysis XML file, which will make the workflow
available to Roddy. If the XML file is setup properly, you can import the analysis in your
project configuration or call it in configuration-free mode.

.. code-block:: bash

    cd ~/RoddyPlugins/NewPlugin/resources/configurationFiles

.. code-block:: XML

    <configuration name='newAnalysis' description=''
               configurationType='analysis'
               class='de.dkfz.roddy.core.Analysis'
               workflowClass='de.dkfz.roddy.newplugin.NewWorkflow'
               runtimeServiceClass="de.dkfz.roddy.core.RuntimeService"
               listOfUsedTools="testScript" usedToolFolders="workflowTools">
      <configurationvalues>
        <cvalue name="firstValue" value="FillIt" type="string" />
        <cvalue name="testOutputDirectory" value="${outputAnalysisBaseDirectory}/testfiles" type="path"/>
      </configurationvalues>
      <processingTools>
        <tool name='testScript' value='testScriptSleep.sh' basepath='workflowTools'>
          <resourcesets>
            <rset size="l" memory="1" cores="1" nodes="1" walltime="5"/>
          </resourcesets>
          <input type="file" typeof="SimpleTestTextFile" scriptparameter="FILENAME_IN"/>
          <output type="file" typeof="SimpleTestTextFile" scriptparameter="FILENAME_OUT"/>
        </tool>
      </processingTools>
      <filenames package='de.dkfz.roddy.knowledge.examples' filestagesbase='de.dkfz.roddy.knowledge.examples.SimpleFileStage'>
        <filename class='SimpleTestTextFile' onTool='testScript' pattern='${testOutputDirectory}/test_onScript_1.txt'/>
      </filenames>
    </configuration>

There you are. You now have a tool which you can call from your workflow.

7. Extend the workflow
----------------------

Open up the workflow class again and change the execute method so that it calls the tool *"testScript"*.
For that to work, you need to load one SimpleTestTextFile.

.. code-block:: Java

        public boolean execute(ExecutionContext context) {

            SimpleTestTextFile textFile = (SimpleTestTextFile)loadSourceFile("/tmp/someTextFile.txt");
            SimpleTestTextFile result = call("testScript", textFile);
            return true;
        }

Successful Roddy workflows will return true. If you detect an error, you can return false or throw an exception.
Only one thing is missing, before you try out your new workflow.

8. Create the first script
--------------------------

.. code-block:: bash

    cd ~/RoddyPlugins/NewPlugin/resources/analysisTools/workflowTools

    echo 'sleep 10' > testScriptSleep.sh
    echo 'cat $FILENAME_IN > $FILENAME_OUT' > testScriptSleep.sh

    chmod 770 testScriptSleep.sh

9. Create a new properties file for Roddy
-----------------------------------------

There is a skeleton application properties file in your Roddy folder.
Copy the file [RODDY]/dist/bin/develop/helperScripts/skeletonAppProperties.ini
to a location of your choice. Open it and add the folder ~/RoddyPlugins to the
pluginDirectories entry. Also change the jobManager class to
*DirectSynchronousExecutedJobManager*. Just comment the currently active line
and uncomment the new jobManager.

10. Last steps
--------------

The last step you need to do is to compile and run the script.

.. code-block:: Bash

    [RODDY_DIRECTORY]/roddy.sh compileplugin NewPlugin --c=[YOUR_INI_FILE]

If you stuck to the example code, everything should be fine now and
you can call it. We'll use to the configurations-free mode here. Therefore
we call the testrun mode with the pattern

.. code-block:: Bash

    [RODDY_DIRECTORY]/roddy.sh testrun [PluginName]_[PluginVersion]:[ConfigurationName]Analysis

Note that the "ConfigurationName" is the name attribute in the workflow configuration
in the plugin, however without the "Analysis" suffix. The suffix is re-added by Roddy.

Project configuration files are explained in :doc:`../config/configurationFiles`.
If you use a project configuration file, put in a directory of your choice (e.g.
where you put your ini file from the step before).


.. code-block:: Bash

    [RODDY_DIRECTORY]/roddy.sh testrun NewPlugin_develop:test --c=[YOUR_INI_FILE]




Command code block
------------------