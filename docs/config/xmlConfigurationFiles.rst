XML configuration files
=======================

Structure / Sections
--------------------

Each configuration file is built up after the following pattern

.. code-block:: XML

  <configuration name='test' description='Example.' >
        <availableAnalyses />
        <configurationvalues />
        <processingTools />
        <filenames />
        <enumerations>
        <subconfigurations />
  </configuration>

However, keep in mind, that not every section makes sense for every type
of XML file. E.g. *availableAnalyses* only makes sense in project XML
files, whereas filenames and processing tools will moste likely only be
used within analysis XML files.

Header
------

Different file types use different XML headers. This is necessary to set
the different behaviours of those file types. Furthermore, for analysis
configuration files, headers differ for the various workflow types.
Let’s start with generic configuration files.

Brawl based workflows
~~~~~~~~~~~~~~~~~~~~~

Script based workflows
~~~~~~~~~~~~~~~~~~~~~~

.. code-block:: XML

  <configuration configurationType='analysis'
               name='dellyAnalysisBrawl' description='An example Brawl analysis.'
               class='de.dkfz.roddy.core.Analysis' brawlWorkflow='BrawlTest'
               brawlBaseWorkflow='WorkflowUsingMergedBams'
               imports='commonCOWorkflowsSettings' listOfUsedTools='script1,script2'
               usedToolFolders='scripts,tools'>


.. code-block:: XML

  <configuration configurationType='analysis'
               name='testAnalysisNative' 
               description='A test analsis invoking a native pbs workflow.'
               class='de.dkfz.roddy.core.Analysis' 
               workflowClass='de.dkfz.roddy.knowledge.nativeworkflows.NativeWorkflow'
               listOfUsedTools="nativeWorkflow" 
               usedToolFolders="roddyTests"
               nativeWorkflowTool="nativeWorkflow" 
               targetCommandFactory="de.dkfz.roddy.execution.jobs.cluster.pbs.PBSCommandFactory">


Java based workflows
~~~~~~~~~~~~~~~~~~~~

.. code-block:: XML

  <configuration configurationType='analysis' 
               name='testAnalysis' description='A test analysis for local and remote roddy workflow tests.'
               class='de.dkfz.roddy.core.Analysis' 
               workflowClass='de.dkfz.roddy.knowledge.examples.SimpleWorkflow'
               listOfUsedTools="testScript,testScriptExitBad,testFileWithChildren" 
               usedToolFolders="devel"
               cleanupScript="cleanupScript">


Project configurations
~~~~~~~~~~~~~~~~~~~~~~

.. code-block:: XML

  <configuration configurationType='project' name='coWorkflowsTestProject'
               description='A test project for the purity estimation analysis.' imports="coBaseProject"
               usedresourcessize="s">


Generic / common configurations
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Generic configuration files keep a minimal header, which might even just contain the name.  That's it.


.. code-block:: XML

  <configuration name='cofilenames' description='This file contains patterns for filename generation and default configured paths for our computational oncology file structure.' >


Configuration values
--------------------

Usually you will change configuration values. When Roddy executes a workflow, a shell script will be created where all the configuration values are stored. This script can then be imported by workflow scripts.

Configuration values are embedded in a configuration values section like:

.. code-block:: XML

    <configurationvalues>
        <cvalue name='analysisMethodNameOnInput' value='testAnalysis' type='string'/>
        <cvalue name='analysisMethodNameOnOutput' value='testAnalysis' type='string'/>

        <cvalue name="testAOutputDirectory" value="testfiles" type="path"/>
        <!--<cvalue name="valuec" value="${valuea}"/>-->
        <!--<cvalue name="valuea" value="${valueb}"/>-->
        <!--<cvalue name="valueb" value="${valuea}"/>-->
        <cvalue name="testOutputDirectory" value="${outputAnalysisBaseDirectory}/testfiles" type="path"/>
        <cvalue name="testInnerOutputDirectory" value="${testOutputDirectory}/testfilesw2"/>
    </configurationvalues>


The configuration value itself is defined as a cvalue element. Each element can have several tags:

* *name* - The tag is used to identify the value both in Roddy and in the job scripts.

* *description* - If you want to describe a value, do it with this tag.

* *value* - The actual value is store here. You can set dependencies to other values by enclosing the referenced value
  like ${targetValue}. Roddy will evaluate the dependency, as soon as it is necessary.

* *type* - There exist several types for configuration values. The default value is string. Note, that the selection of
  the type will influence, how variables are interpreted and evaluated / converted.

  - *string* will

  - *int* will accept integer values only. E.g. 1, 2, 3 or 4.

  - *float* will accept float values in differnt formats. E.g. 1.2f 1.2

  - *double* will accept



  - *boolean*

Special values
~~~~~~~~~~~~~~

For future releases of Roddy and also for better readability of XML files, Roddy offers "special" variables like:

**Run flags** which look like runPostProcessing, runFlagstats, runScript

and

**Binaries** which look like BWA_BINARY, MBUFFER_BINARY, PYTHON_BINARY and so on.

Run flags are always considered to be boolean and are e.g. used smartly in Brawl based workflows. Binary variables are or are supposed to be checked on workflow validation and startup in future versions. If you want to exchange a binary in a fast way or set a fixed binary for your scripts, it is also wise to store everything in configuration values. 

Tool entries and filename patterns
----------------------------------

.. Links for this section
.. _Filenames and tool entries`: config/configFilenamesAndToolEntries

.. NOTE::
  Because of the importance and complexity of both entry types, they are covered in their own section :doc:`configFilenamesAndToolEntries`.

These sections are started like this:


.. code-block:: XML

    <processingTools>
        <tool name='compressionDetection' value='determineFileCompressor.sh' basepath='roddyTools'/>
        <tool name='createLockFiles' value='createLockFiles.sh' basepath='roddyTools'/>
        <tool name='streamBuffer' value='streamBuffer.sh' basepath='roddyTools'/>
        <tool name='wrapinScript' value='wrapInScript.sh' basepath='roddyTools'/>
        <tool name='nativeWorkflowScriptWrapper' value='nativeWorkflowScriptWrapper.sh' basepath='roddyTools'/>
    </processingTools>
    <filenames package='de.dkfz.roddy.knowledge.examples' filestagesbase='de.dkfz.roddy.knowledge.examples.SimpleFileStage'>
        <filename class='SimpleTestTextFile' onMethod='test1' pattern='${testOutputDirectory}/test_method_1.txt'/>
        <filename class='SimpleTestTextFile' onMethod='test2' pattern='${outputAnalysisBaseDirectory}/${testAOutputDirectory}/test_method_2.txt'/>
        <filename class='SimpleTestTextFile' onMethod='test3' pattern='${testInnerOutputDirectory}/test_method_3.txt'/>

        <filename class='FileWithChildren' onMethod='SimpleTestTextFile.testFWChildren' pattern='${testOutputDirectory}/filewithchildren.txt'/>
        <filename class='SimpleTestTextFile' onMethod='SimpleTestTextFile.testFWChildren' pattern='${testOutputDirectory}/test_method_child0.txt'/>
        <filename class='SimpleTestTextFile' onMethod='SimpleTestTextFile.testFWChildren' selectiontag="file1" pattern='${testOutputDirectory}/test_method_child1.txt'/>
    </filenames>


They contain a list and resource definitions for included workflow tools and patterns to create filenames based on different rules.


Tool entry names are automatically converted to configuration variables. For this to work, you need to set the tool id in camel case notation: camelCase. If this is done, Roddy will convert the id e.g. to TOOL_CAMEL_CASE. For the above example, you'd get TOOL_COMPRESSION_DETECTION out of compressionDetection and e.g. TOOL_WRAPIN_SCRIPT, TOOL_CREATE_LOCK_FILES, TOOL_STREAM_BUFFER and finally TOOL_NATIVE_WORKFLOW_SCRIPT_WRAPPER.

Here comes a list of stuff taken from an old config file. It's just taken over and not reworked. However, a lot of the possibilities for filename patterns is listed here:

.. code-block:: XML

  <!-- Filenames are always stored in the pid's output folder -->
        <!-- Different variables can be used:
            - ${sourcefile}, use the name and the path of the file from which the new name is derived
            - ${sourcefileAtomic}, use the atomic name of which the file is derived
            - ${sourcefileAtomicPrefix,delimiter=".."}, use the atomic name's prefix (without file-ending like .txt/.paired.bam...
                                                        of which the file is derived, set the delimiter option to define the delimiter default is "_"
                                                        the delimiter has to be placed inside "" as this is used to find the delimiter!
            - ${sourcepath}, use the path in which the source file is stored
            - ${outputbasepath}, use the output path of the pid
            - ${[nameofdir]OutputDirectory}

            NOTICE: If you use options for a variable your are NOT allowed to use ","! It is used to recognize options.

            - ${pid}
            - ${sample}
            - ${run}
            - ${lane}
            - ${laneindex}
            - You can put in configuration values to do this use:
              ${cvalue,name=[name of the value],default=".."} where default is optional.
            - ${fileStageID} use the id String of the file's stage to build up the name.
            -->
        <!-- A filename can be derived from another file, use derivedFrom='shortClassName/longClassName'
             A filename can also be specified for a level, use fileStage='PID/SAMPLE/RUN/LANE/INDEXEDLANE', refer to BaseFile.FileStage
             A filename can be specified for all levels, the name is then build up with the ${fileStageID} value
             A filename can be created using the file's called method's name
             A filename can be created using the used tool's name
             -->


Special: Autofilenames and Autofiletypes
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Just to mention it (it is also covered in detail in the full guide), Roddy supports some sort of autofilenames and types. This means, if you just want to get things running, you can specify a tool with input and output files. If no filename patterns and file classes exist, Roddy will take care of this for you. However, the autofilenames are not the nicest things to have, so you should go on and create rules, if needed.

Enumerations
~~~~~~~~~~~~

Enumerations are there to specify data types and validators for configuration values. 


.. code-block:: XML

  <enumeration name='cvalueType' description='various types of configuration values' extends="">
    <value id='path' valueTag="de.dkfz.roddy.config.validation.FileSystemValidator" description="Value type is a file system path (fully or with wildcards like ~, *"/>
    <value id='bashArray' valueTag="de.dkfz.roddy.config.validation.BashValidator" description="A bash array."/>
    <value id='boolean' valueTag="de.dkfz.roddy.config.validation.DefaultValidator" description="A boolean value containing true or false."/>
    <value id='integer' valueTag="de.dkfz.roddy.config.validation.DefaultValidator" description="A positive or negative integer value."/>
    <value id='float' valueTag="de.dkfz.roddy.config.validation.DefaultValidator" description="A single precision floating point value."/>
    <value id='double' valueTag="de.dkfz.roddy.config.validation.DefaultValidator" description="A double precision floating point value."/>
    <value id='string' valueTag="de.dkfz.roddy.config.validation.DefaultValidator" description="The default type of no type is set. The value will be stored unchecked."/>
  </enumeration>


Looking at the default configuration value type configuration, you can see e.g. that path objects are validated with the FileSystemValidator class. 
