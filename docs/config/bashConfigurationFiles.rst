Bash configuration files
========================

Bash configuration files are, compared to XML files, very lightweight. They offer only a subset of configuration options (namely configuration values and analysis imports)
and are ideally used for small project or generic configurations.

.. code-block:: Bash

    #name aConfig
    #imports anotherConfig
    #description aConfig
    #usedresourcessize m
    #analysis A,aAnalysis,TestPlugin:develop
    #analysis B,bAnalysis,TestPlugin:develop
    #analysis C,aAnalysis,TestPlugin:develop

    outputBaseDirectory=/data/michael/temp/roddyLocalTest/testproject/rpp
    UNZIPTOOL=gunzip
    ZIPTOOL_OPTIONS="-c"
    sampleDirectory=/data/michael/temp/roddyLocalTest/testproject/vbp/A100/${sample}/${SEQUENCER_PROTOCOL}*

As you can see in the example, a Bash configuration needs a header and a body.

.. code-block:: Bash

    #name aConfig
    #imports anotherConfig
    #description aConfig
    #usedresourcessize m
    #analysis A,aAnalysis,TestPlugin:develop
    #analysis B,bAnalysis,TestPlugin:develop
    #analysis C,aAnalysis,TestPlugin:develop

The header must contain the *name* of the configuration and may contain *imports*, a *description*,
the *usedresourcessize* attribute and several *analysis* tags. The *analysis* tags need to be set
like [id],[analysis config id],[plugin name]:[plugin version]. Please see :doc:`xmlConfigurationFiles` for a
detailed description of the tags and attributes.

After the header comes the configuration values section.

.. code-block:: Bash

    outputBaseDirectory=/data/michael/temp/roddyLocalTest/testproject/rpp
    UNZIPTOOL=gunzip
    ZIPTOOL_OPTIONS="-c"
    sampleDirectory=/data/michael/temp/roddyLocalTest/testproject/vbp/A100/${sample}/${SEQUENCER_PROTOCOL}*

The syntax for configuration values is the regular Bash syntax for variables. Of course, you can also use comments.
