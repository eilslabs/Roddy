Command line options
====================

Roddy has a wide range of run modes and options which will be explained here.
The run modes are basically divided into user options and extended developer options.
You can view all options by running Roddy without any parameters.

User options
------------

If you do not intend to develop Roddy or Roddy plugins, you can stop reading after this part.

.. csv-table:: "Title"
    :Header: "Option", "Additional", "Description"
    :Widths: 5, 20, 20

    "help", "Shows a list of available configuration files in all configured paths.",""
    "printappconfig        ", "[--useconfig={file}]", "Prints the currently loaded application properties ini file."
    "showconfigpaths       ", "[--useconfig={file}]", "Shows a list of available configuration files in all configured paths."
    "showfeaturetoggles    ", "                    ", "Shows a list of available feature toggles."
    "prepareprojectconfig  ", "                    ", "Create or update a project xml file and an application properties ini file."
    "plugininfo            ", "[--useconfig={file}]", "Shows details about the available plugins."
    "printpluginreadme     ", "(configuration\@analysis) \n[--useconfig={file}]   ", "Prints the readme file of the currently selected workflow."
    "printanalysisxml      ", "(configuration\@analysis) \n[--useconfig={file}]   ", "Prints the analysis xml file of the currently selected workflow."
    "validateconfig        ", "(configuration\@analysis) \n[--useconfig={file}]   ", "Tries to find errors in the specified configuration and shows them."
    "listworkflows         ", "[filter word] [--shortlist] \n[--useconfig={file}]", "Shows a list of available configurations and analyses. If a filter word is specified, then the whole configuration tree is only printed, if at least one configuration id in the tree contains the word."
    "listdatasets          ", "(configuration\@analysis) \n[--useconfig={file}]   ", "Lists the available datasets for a configuration."
    "printruntimeconfig    ", "(configuration\@analysis) \n[--useconfig={file}] [--extendedlist] [--showentrysources] ", "Basically calls testrun but prints out the converted / prepared runtime configuration script content. --extendedlist shows all stored values (also e.g. tool entries. Works only in combination with --showentrysources --showentrysources shows the source file of the entry in addition to the value."
    "testrun               ", "(configuration\@analysis) \n[pid_0,..,pid_n] [--useconfig={file}]                ", "Displays the current workflow status for the given datasets."
    "testrerun             ", "(configuration\@analysis) \n[pid_0,..,pid_n] [--useconfig={file}]                ", "Displays the current workflow status for the given datasets."
    "run                   ", "(configuration\@analysis) \n[pid_0,..,pid_n] [--waitforjobs] [--useconfig={file}]", "Runs a workflow with the configured Jobfactory. Does not check if the workflow is already running on the cluster."
    "rerun                 ", "(configuration\@analysis) \n[pid_0,..,pid_n] [--waitforjobs] [--useconfig={file}]", "Reruns a workflow starting only the parts which did not produce valid files. Does not check if the workflow is already running on the cluster."
    "cleanup               ", "(configuration\@analysis) \n[pid_0,..,pid_n] [--useconfig={file}]                ", "Calls a workflows cleanup method or a setup cleanup script to clean (i.e. remove or set to file size zero) output files. Aborts the running jobs of a workflow for a pid."
    "checkworkflowstatus   ", "(configuration\@analysis) \n[pid_0,..,pid_n] [--detailed] [--useconfig={file}]   ", "Shows a generic overview about all datasets for a configuration. If some datasets are selected, a more detailed output is generated. If detailed is set, information about all started jobs and their status is shown."

Common additional options
-------------------------

These modes can be parametrized in various ways. Here is a summary of all options, but note that not all of them make sense in all modes.

.. list-table:: "Title"
    :Widths: 5, 20, 20

    *   - Option
        - Argument
        - Description
    *   - --useconfig
        - {file}
        - Use {file} as the application configuration.
    *   - --c
        - {file}
        - The order is: full path, .roddy folder, Roddy directory.
    *   - --verbositylevel
        - {1,3,5}
        - Set how much Roddy will print to the console, 1 is default, 3 is more, 5 is a lot.
    *   - --v
        -
        - Set verbosity to 3.
    *   - --vv
        -
        - Set verbosity to 5.
    *   - --useiodir
        - [fileIn],{fileOut}
        - Use fileIn/fileOut as the base input and output directories for your project. If fileOut is not specified, fileIn is used for that as well. The format specifier can be one of: tsv, csv or excel
    *   - --usemetadatatable
        - {file},[format]
        - Tell Roddy to use an input table to load metadata and input data and available datasets.
    *   - --waitforjobs
        -
        - Let Roddy wait for all submitted jobs to finish.
    *   - --disabletrackonlyuserjobs
        -
        - By default, Roddy will only track jobs of the current user. The switch tells Roddy to track all jobs.
    *   - --disablestrictfilechecks
        -
        - Tell Roddy to ignore missing files. By default, Roddy checks if all necessary files exist.
    *   - --ignoreconfigurationerrors
        -
        - Tell Roddy to ignore configuration errors. By default, Roddy will exit if configuration errors are detected.
    *   - --ignorecvalueduplicates
        -
        - Tell Roddy to ignore duplicate configuration values within the same configuration value block. errors. By default, Roddy will exit if duplicates are found.
    *   - --forcenativepluginconversion
        -
        - Tell Roddy to override any existing converted Native plugin. By default Roddy will prevent this.
    *   - --forcekeepexecutiondirectory
        -
        - Tell Roddy to keep execution directories. By default Roddy will delete them, if no jobs were executed in a run.
    *   - --useRoddyVersion
        - (version no)
        - Use a specific roddy version.
    *   - --rv
        - (version no)
        - Like --useRoddyVersion
    *   - --usePluginVersion
        - (...,...)
        - Supply a list of used plugins and versions.
    *   - --configurationDirectories
        - {path},...
        - Supply a list of configurationdirectories.
    *   - --pluginDirectories
        - {path},...
        - Supply a list of plugin directories.

Developer options
-----------------

A good way to compile Roddy is to use just

.. code-block:: bash

    ./gradlew build

Roddy also provides means to compile itself (basically using gradlew again) but additionally increasing the build version number. To some 
extend Roddy can compile and package plugins for you. For these actions the following modes are available:

.. csv-table:: "Title"
    :Header: "Option", "Additional", "Description"
    :Widths: 5, 20, 20

        "compile", "", "Compiles the roddy library / application."
        "pack", "", "Creates a copy of the 'develop' version and puts the version number to the file name."
        "compileplugin", "(plugin ID) [--useconfig={file}]", "Compiles a plugin."
        "packplugin", "(plugin ID) [--useconfig={file}]", "Packages the compiled plugin in dist/plugins and creates a version number for it. Please note that you can indeed override contents of a zip file if you do not update / compile the plugin jar!"
