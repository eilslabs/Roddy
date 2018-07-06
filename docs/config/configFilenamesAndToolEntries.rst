Tools and filenames
===================

The whole workflow structure in Roddy is built around files and filenames.
Files are used to create dependencies between steps in the workflow and files
also enable Roddy to rerun a workflow based on created files.

As Roddy strictly separates code and configuration, filenames are configured.
Of course you are allowed to make exceptions for e.g. initial files but the
standard is to create rules for filenames.

So how do you tie things up?

Filename patterns are used to define a single or a range of names for a file class.

File classes are used as input and output parameters for tool entries. **Filename patterns
are automatically applied to output files!**

Tool entries tell Roddy how a script or a binary is called. Which files and
parameters go in and which files come out and which resources will be used by
jobs running this tool.

A complex tool entry will be shown at the end of this document.

.. Note::
    In our experience, it is a good way to create a workflow and its tools on a step by step base so that:

    1. You create a tool entry, define an initial resource set and i/o parameters.

    2. Integrate the call into your workflow.

    3. Setup filename patterns for the tools output files.

    4. Test the new tool with *testrun* and *testrerun*.

    5. Repeat the steps for the next tool.

    Occasionally it might still be wise to remove the output data and test the whole workflow again.

.. Important::
    Remember, that Roddy does not feature job monitoring. The job structure, file names
    and patterns must be well known before the workflow starts!

Tool entries
------------

.. code-block:: XML

    <tool name='testScript' value='testScriptSleep.sh' basepath='roddyTests'>
      <resourcesets>
          <rset size="l" memory="1" cores="1" nodes="1" walltime="5"/>
      </resourcesets>
      <input type="file" typeof="SimpleTestTextFile" scriptparameter="FILENAME_IN"/>
      <output type="file" typeof="SimpleTestTextFile" scriptparameter="FILENAME_OUT"/>
    </tool>

Each tool entry has a header:

.. code-block:: XML

    <tool name='testScript' value='testScriptSleep.sh' basepath='roddyTests'>

* The value of the *name* attribute is used to call or manage the tool in a workflow.
  Before a workflow starts, the names of all tools are converted to configuration values so that
  you will have easy access to them from your scripts. As explained in the configuration section,
  a job name will be converted from camel case notation to All caps notation using underscore
  as the word separator. In addition *TOOL_* will be used as a prefix. So the tool name *testScript*
  would be named *TOOL_TEST_SCRIPT* in your job.


* The value of the *value* attribute holds the script or binary name of the executed file.

* The value of the *basepath* attribute points to the tools folder in the plugins analyisTools folder.

.. Important::
  You can, but you don't have to add resource sets and input and ouput parameters to a job.
  If you omit resource sets, the job will run with default resource settings. They are explained below.
  If you omit input and output parameters, you need to take care of the job call by yourself. Normally, Roddy will
  take care of this for you. If you create a native workflow, you will lose the rerun feature, if you omit the output parameters!
  Omitting all these parameters might sometimes make sense, when you just want to get easy access to a tool
  in your analysisTools folder.

Resource sets
~~~~~~~~~~~~~

Each tool can have several resource sets.

.. code-block:: XML

    <rset size="l" memory="1" cores="1" nodes="1" walltime="5"/>

* The attribute *size* can be one of *t, xs, s, m, l, xl* and allows you to define
  resource sets for different cases. From extra small to extra large. *t* is a special
  case and can be used for test resources.

* Currently Roddy (or BatchEuphoria) can be used to request the resources *memory, cores, nodes* and *walltime*
  You can set values in different formats:

    - The default for memory is 1GB. Valid strings for it are for example:

      * 1 (which is 1 GB)

      * 1m/g/t

      * 0.5(m/g/t) which would be 500MB

    - The default cores value is 1. Other values are natural numbers in [1; n]

    - The default nodes value is 1. Other values are natural numbers in [1; n]

    - The default walltime is 1 hour. Other values are for example:

      * 00:10:00 which would be 10 minutes.

      * 24:00:00 would be aligned to 01:00:00:00 which is one day. All other values
        will be aligned as well.

      * 1h, 1d, 1h50m ... or other values in human readable format.

  .. Note:: The default size for resource sets used by Roddy is *l*

Input types
~~~~~~~~~~~

A tool can have different input objects:

- Values, like strings or numbers:

    .. code-block:: XML

        <input type="string" setby="callingCode" scriptparameter="SAMPLE"/>

    * The *type* attribute tells Roddy, that a string is expected.

    * The *setby* attribute tells Roddy, that the parameter will be set by the developer
      in the call of the job. Currently only *callingCode* is valid.

    * The *scriptparameter* value tells Roddy that a parameter with this name is
      passed to the job.

- Single file objects like:

    .. code-block:: XML

        <input type="file" typeof="de.dkfz.b080.co.files.LaneFile" scriptparameter="RAW_SEQUENCE_FILE" />
        <input type="file" typeof="BasicBamFile" scriptparameter="RAW_SEQUENCE_FILE" />

    * The type attribute tells Roddy that a file object is expected as input.

    * The typeof value tells Roddy the expected type of an input value. This check is
      done within the job call. If the type of the input object does not match, Roddy
      will fail. You're allowed to omit the package structure. Roddy will try to find
      the class in its core code and in the plugin classes. If more than two classes
      match, Roddy will fail and tell you, that this happened.

    .. Important:: You are allowed to put in a non-existent class! If Roddy cannot find
        the class, it will create a synthetic class during runtime. This way, you can
        skip code creation and keep your code lean. You are allowed to use this class
        like any other class. However, you are not able to use the class directly in your
        Java code.

    * Like above, the scriptparameter value tells Roddy that a parameter with this name is
      passed to the job.


- File groups:

    File groups are collections of file objects. By default, file groups are designed to
    store files of the same type.

    .. code-block:: XML

        <input type="filegroup" typeof="de.dkfz.b080.co.files.BamFileGroup" scriptparameter="INPUT_FILES" passas="array"/>
        <input type="filegroup" typeof="GenericFileGroup" scriptparameter="INPUT_FILES2" passas="array"/>

    * Set the *type* to filegroup if you want to use it.

    * *typeof* behaves nearly the same as for file input definitions. However, here you need to put in
      a file group class. If you do not need a specialized or named file group, you can use the GenericFileGroup class.

    * TODO: *classOfContainedFiles*

    * The *passas* attribute defines, how the files in the file group are passed to your job. Allowed values are:

      * *parameters* which will tell Roddy to create a parameter for each file in the group.

      * *array* which will tell Roddy to pass the files as an array in a single string.

    * The *scriptparameter* behaves nearly like the one for files. If you set *array*, the parameter name will be
      used like it is. If you set *parameters* it will be used as a prefix and the .

.. Important::
    The order of the input parameters matters, when you pass parameters to a job.
    Roddy will check this and fail, if:

    * the number of input parameters does not match

    * the type of input parameters does not match

Output types
~~~~~~~~~~~~

The output of a Roddy job is always a file or a group of files. Moreover, you are only allowed to have one top-level output
object in the XML description, but this object might be one which holds other objects like the mentionend file groups.

If your tool does not create output files you can omit those entries. However, it might still be wise to create some sort of checkpoint
for the tool so that Roddys rerun feature will work properly. The syntax for output objects is quite similar to the
syntax for input objects, so we'll skip explanations for known attributes. Valid output objects are:

- Single file objects:

  The single output file syntax is the same like for input files. Just change the tag name to output.

  .. code-block:: XML

      <output type="file" typeof="de.dkfz.b080.co.files.BamFile" scriptparameter="FILENAME" />

  In addition to the basic parameters, you can also add a filename attribute like:

  .. code-block:: XML

      <output type="file" typeof="BasicBamFile" scriptparameter="FILE_IN" filename="/tmp/somefile_${pid}.txt" />

  If you do this, you will create an inline filename pattern of the onScriptParameter type.
  Just see the below section about filename patterns and rules for more info.

  Also you can tell Roddy to omit the file existence check for workflow reruns by adding the check attribute with its value set to "false".

  .. code-block:: XML

      <output type="file" typeof="BasicBamFile" scriptparameter="FILE_IN" check="false" />


- Files with children:

  Files with children are a bit special. They are necessary, if you want to create a file which has some children.
  The main difference to single files is, that you need to create a class file! Then, for each file you want as a
  child, you need to create the field and the set / get accessors. We use this feature only in a handful of cases.

  .. code-block:: XML

      <output type="file" typeof="BasicBamFile" scriptparameter="FILENAME">
        <output type="file" variable="indexFile" typeof="BamIndexFile" scriptparameter="FILENAME_INDEX"/>
      </output>

  The example shows an output entry with one child. You can add more children, if you need.

  The *variable* attribute tells Roddy which field in the parent class is used to store the created child.

- Tuples of files:

  Tuples of files are the easiest way to create collections of file objects. It does not matter which types
  the files have.

  .. code-block:: XML

      <output type="tuple">
        <output type="file" typeof="BasicBamFile" scriptparameter="FILENAME_BAM"/>
        <output type="file" typeof="BamIndexFile" scriptparameter="FILENAME_INDEX"/>
      </output>

  Call in Java code

  .. code-block:: Java

      // Call with output tuple
      Tuple2 fileTuple = (Tuple2) call("testScriptWithMultiOut", someFile)

      // Access output tuple children
      (BasicBamFile)fileTuple.value0
      (BamIndexFile)fileTuple.value1

- File groups:

  Output file groups offer a lot more options than input file groups. This

  .. code-block:: XML

      <output type="filegroup" typeof="GenericFileGroup">
        <output type="file" typeof="" scriptparameter="BAM1"/>
        <output type="file" typeof="" scriptparameter="BAM2"/>
        <output type="file" typeof="" scriptparameter="BAM3"/>
      </output>

- File groups with indices:

  .. code-block:: XML

      <output type="filegroup" passas="array" filename="somefile_${fgindex}.out" />



Filename patterns
-----------------

Filenames in Roddy are rule based. They are defined in the filenames section in your XML file.

.. code-block:: XML

    <filenames package='de.dkfz.roddy.knowledge.examples' filestagesbase='de.dkfz.roddy.knowledge.examples.SimpleFileStage'>
      <filename class='SimpleTestTextFile' onTool='testScript' pattern='${testOutputDirectory}/test_onScript_1.txt'/>
      <filename class='SimpleMultiOutFile' onTool="testScriptWithMultiOut" selectiontag="mout1" pattern="${testOutputDirectory}/test_mout_a.txt" />
      <filename class='SimpleMultiOutFile' onTool="testScriptWithMultiOut" selectiontag="mout2" pattern="${testOutputDirectory}/test_mout_b.txt" />
      <filename class='SimpleMultiOutFile' onTool="testScriptWithMultiOut" selectiontag="mout3" pattern="${testOutputDirectory}/test_mout_c.txt" />
      <filename class='SimpleMultiOutFile' onTool="testScriptWithMultiOut" selectiontag="mout4" pattern="${testOutputDirectory}/test_mout_d.txt" />
    </filenames>

There are several types of triggers for patterns available. Patterns are always linked to a particular *class*. By applying the *selectiontag* attribute to some
of the trigger types, you gain a more fine grained control over pattern selection, if you define output objects of the same class multiple times in a tool.

onScriptParameter trigger
~~~~~~~~~~~~~~~~~~~~~~~~~

  This trigger type links the pattern to the scriptparameter attribute of an output object. Valid trigger values are:

    * [parameter name] - where *parameter name* is linked to the scriptparameter attribute. The trigger is valid for all tools.

    * :[parameter name] - behaves like above.

    * [ANY]:[parameter name] - behaves like above. This is the long form and *[ANY]* is meant to make the syntax more readable.

    * [tool id]:[parameter name] - behaves like above, except that *tool id* restricts the trigger to exactly one tool.

  This trigger type will NOT accept the *selectiontag* attribute.

onMethod trigger
~~~~~~~~~~~~~~~~

  This trigger links the pattern to a method name or a class and a method name. Roddy will search all called methods using the current
  threads stack trace. The search will stop, as soon as the execute method is reached. Valid values are:

    * [methodName] - by specifying only a method name, the pattern will be used for any called method with this name.

    * [simple class name].[methodName] - this will accept all methods in classes with the given class name. The class package will be ignored.

    * [full class name].[methodName] - by setting the class and the package, this pattern will only be applied with a full match.

  This trigger type will accept the *selectiontag* attribute.

onToolID trigger
~~~~~~~~~~~~~~~~

  This trigger will link the pattern to a tool call. If this tool is called and outputs a file of the given class then this pattern might be used.

  This trigger type will accept the *selectiontag* attribute.

derivedfrom trigger
~~~~~~~~~~~~~~~~~~~

  In some cases the name of a new file depends on the name of a parent file, e.g. a Bam Index file depends on a Bam file like
  DATASET_TIMESTAMP.merged.bam -> DATASET_TIMESTAMP.merged.bam.bai.

  This trigger type will accept the *selectiontag* attribute.

generic
~~~~~~~

  To be done... we hardly use it.

.. Important:: Filename patterns are evaluated in a specific order!

    1. First by the type

      - onScriptParameter -> onMethod -> onToolID -> derivedFrom -> generic

    2. By the order in the configuration. First come first serve!



.. code-block:: Java

    "<filename class='TestFileWithParent' derivedFrom='TestParentFile' pattern='/tmp/onderivedFile'/>"
    "<filename class='TestFileWithParent' derivedFrom='TestParentFile' pattern='/tmp/onderivedFile'/>"
    "<filename class='TestFileWithParentArr' derivedFrom='TestParentFile[2]' pattern='/tmp/onderivedFile'/>"
    "<filename class='TestFileOnMethod' onMethod='de.dkfz.roddy.knowledge.files.BaseFile.getFilename' pattern='/tmp/onMethod'/>"
    "<filename class='TestFileOnMethod' onMethod='BaseFile.getFilename' pattern='/tmp/onMethodwithClassName'/>"
    "<filename class='TestFileOnMethod' onMethod='getFilename' pattern='/tmp/onMethod'/>"
    "<filename class='TestFileOnTool' onTool='testScript' pattern='/tmp/onTool'/>"
    "<filename class='FileWithFileStage' fileStage=\"GENERIC\" pattern='/tmp/filestage'/>"
    "<filename class='TestOnScriptParameter' onScriptParameter='testScript:BAM_INDEX_FILE' pattern='/tmp/onScript' />"
    "<filename class='TestOnScriptParameter' onScriptParameter='BAM_INDEX_FILE2' pattern='/tmp/onScript' />"
    "<filename class='TestOnScriptParameter' onScriptParameter=':BAM_INDEX_FILE3' pattern='/tmp/onScript' />"
    "<filename class='TestOnScriptParameter' onScriptParameter='[ANY]:BAM_INDEX_FILE4' pattern='/tmp/onScript' />"
    "<filename class='TestOnScriptParameter' onScriptParameter='[AffY]:BAM_INDEX_FILE5' pattern='/tmp/onScript' />" // Error!!
    "<filename onScriptParameter='testScript:BAM_INDEX_FILE6' pattern='/tmp/onScript' />"



Automatic filenames
-------------------

Synthetic classes
-----------------

Synthetic classes are a mechanism which allows you to use Roddys built-in type checking system without the need to create class files. Synthetic classes are automatically
created during runtime in the following cases:

- A filename pattern requires a specific non-existent class.

- A tool i/o parameter needs a specific non-existent class.

- Programmatically, if you request Roddy to load a non-existent class with the LibrariesFactory:

  .. code-block:: Java

      LibrariesFactory.getInstance().loadRealOrSyntheticClass(String classOfFileObject, String baseClassOfFileObject)
      LibrariesFactory.getInstance().loadRealOrSyntheticClass(String classOfFileObject, Class<FileObject> constructorClass)
      LibrariesFactory.getInstance().forceLoadSyntheticClassOrFail(String classOfFileObject, Class<FileObject> constructorClass = BaseFile.class)
      LibrariesFactory.getInstance().generateSyntheticFileClassWithParentClass(String syntheticClassName, String constructorClassName, GroovyClassLoader classLoader = null)

  or via the ClassLoaderHelper

  .. code-block:: Java

      LibrariesFactory.getInstance().getClassLoaderHelper().loadRealOrSyntheticClass(String classOfFileObject, String baseClassOfFileObject)
      LibrariesFactory.getInstance().getClassLoaderHelper().loadRealOrSyntheticClass(String classOfFileObject, Class<FileObject> constructorClass)
      LibrariesFactory.getInstance().getClassLoaderHelper().generateSyntheticFileClassWithParentClass(String syntheticClassName, String constructorClassName, GroovyClassLoader classLoader = null)

Example tool entry and filename patterns
----------------------------------------

.. code-block:: XML

    <a/>

Overriding tool entries
-----------------------

Sometimes, the initial specification might not be right for you. In this case, you are always allowed to override
the existing tool entry. There are basically two ways: Override the resource sets only or redefine the whole tool.

If you want to override the whole tool, just do it. The only thing to remember is, that you probably have to
match the in and output parameter count or even the types and you have to make sure, that you put the new
tool definition to the proper level in your configuration file hierarchy.

.. code-block:: XML

    <tool name='testScript' value='testScriptSleep.sh' basepath='roddyTests'>
      <resourcesets>
          <rset size="l" memory="1" cores="1" nodes="1" walltime="5"/>
      </resourcesets>
      <input type="file" typeof="SimpleTestTextFile" scriptparameter="FILENAME_IN"/>
      <output type="file" typeof="SimpleTestTextFile" scriptparameter="FILENAME_OUT"/>
    </tool>

Now, if you just need to adapt the resources, you can use the *overrideresourcesets*=**"true"** attribute.

.. code-block:: XML

    <tool name='testScript' value='testScriptSleep.sh' basepath='roddyTests' overrideresourcesets="true">
      <resourcesets>
          <rset size="l" memory="1" cores="1" nodes="1" walltime="5"/>
      </resourcesets>
    </tool>

The in- and output entries will be inherited and you'll have your tools setup with the new resources. Be aware
that all of the old resource entries will void!
