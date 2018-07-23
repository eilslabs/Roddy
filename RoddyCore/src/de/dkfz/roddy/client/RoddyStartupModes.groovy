/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.client

import de.dkfz.roddy.Constants
import static de.dkfz.roddy.client.RoddyStartupOptions.*
import static de.dkfz.roddy.client.RoddyStartupModeScopes.*

/**
 * Contains the possible startup modes for Roddy.
 */
@groovy.transform.CompileStatic
enum RoddyStartupModes {

    importworkflow(SCOPE_REDUCED),

    help(SCOPE_CLI),

    showreadme(SCOPE_REDUCED),

    showfeaturetoggles(SCOPE_REDUCED),

    printappconfig(SCOPE_REDUCED, [useconfig]),

    showconfigpaths(SCOPE_REDUCED, [useconfig, verbositylevel]),

    prepareprojectconfig(SCOPE_REDUCED),

    printpluginreadme(SCOPE_CLI, [useconfig]),

    printanalysisxml(SCOPE_CLI, [useconfig]),

    plugininfo(SCOPE_REDUCED, [useconfig]),

    compileplugin(SCOPE_CLI, [useconfig]),

    updateplugin(SCOPE_CLI, [useconfig]),

    validateconfig(SCOPE_REDUCED, [useconfig]),

    printruntimeconfig(SCOPE_FULL, [useconfig, showentrysources, extendedlist]),

    printidlessruntimeconfig(SCOPE_REDUCED, [useconfig]),

    listworkflows(SCOPE_REDUCED, [useconfig, shortlist]),

    listdatasets(SCOPE_FULL, [useconfig]),

    autoselect(SCOPE_REDUCED, [useconfig]),

    run(SCOPE_FULL_WITHJOBMANAGER, [test, useconfig, verbositylevel, debugOptions, waitforjobs, useiodir, disabletrackonlyuserjobs, trackonlystartedjobs, resubmitjobonerror, autosubmit, autocleanup, run, dontrun] as List<RoddyStartupOptions>),

    rerun(SCOPE_FULL_WITHJOBMANAGER, [test, run, dontrun, useconfig, verbositylevel, debugOptions, waitforjobs, useiodir, disabletrackonlyuserjobs, trackonlystartedjobs, resubmitjobonerror, autosubmit, autocleanup] as List<RoddyStartupOptions>),

    testrun(SCOPE_FULL, [useconfig, verbositylevel, debugOptions, waitforjobs, useiodir, disabletrackonlyuserjobs, trackonlystartedjobs, resubmitjobonerror, autosubmit, autocleanup, run, dontrun] as List<RoddyStartupOptions>),

    testrerun(SCOPE_FULL, [useconfig, verbositylevel, debugOptions, waitforjobs, useiodir, disabletrackonlyuserjobs, trackonlystartedjobs, resubmitjobonerror, autosubmit, autocleanup, run, dontrun] as List<RoddyStartupOptions>),

    rerunstep(SCOPE_FULL_WITHJOBMANAGER, [useconfig, verbositylevel, debugOptions, waitforjobs, useiodir, resubmitjobonerror] as List<RoddyStartupOptions>), // rerun a single step of an executed dataset.

    checkworkflowstatus(SCOPE_FULL_WITHJOBMANAGER, [useconfig, verbositylevel, detailed] as List<RoddyStartupOptions>), // Show the (last) status of an executed dataset.

    cleanup(SCOPE_FULL_WITHJOBMANAGER, [useconfig, verbositylevel]),

    abort(SCOPE_FULL_WITHJOBMANAGER, [useconfig, verbositylevel]),

    rmi(SCOPE_FULL, [useconfig]),

    compile(SCOPE_CLI),

    pack(SCOPE_CLI),

    packplugin(SCOPE_CLI, [useconfig]),

    showconfig(SCOPE_REDUCED, [useconfig]),

    createworkflow(SCOPE_REDUCED, [useconfig]),

//    setup(SCOPE_CLI)

    public final RoddyStartupModeScopes scope

    /**
     * A list of valid startup options for this startup mode.
     */
    private List<RoddyStartupOptions> validOptions

    RoddyStartupModes(RoddyStartupModeScopes scope, List<RoddyStartupOptions> validOptions = []) {
        this.validOptions = validOptions
        this.scope = scope
    }

    private static void printCommand(RoddyStartupModes option, String parameters) {
        System.out.println(String.format("  %s %s " + Constants.ENV_LINESEPARATOR, option.toString(), parameters))
    }

    private static void printCommand(RoddyStartupModes option, String parameters, String... description) {
        StringBuilder formattedDescription = new StringBuilder()
        if (description.length > 0) {
            formattedDescription.append(description[0])
            for (int i = 1; i < description.length; i++) {
                formattedDescription.append(Constants.ENV_LINESEPARATOR).append("\t").append(description[i])
            }
        }
        System.out.println(String.format("  %s %s " + Constants.ENV_LINESEPARATOR + "\t%s" + Constants.ENV_LINESEPARATOR, option.toString(), parameters, formattedDescription.toString()))
    }

    static void printCommands(List<List<Object>> commands) {
        commands.each { List<Object> it ->
            printCommand(it[0] as RoddyStartupModes, it[1] as String, it[2..-1] as String[])
        }
    }

    static void printCommandLineOptions() {
        System.out.println([
                Constants.ENV_LINESEPARATOR
                , "Roddy is supposed to be a rapid development and management platform for cluster based workflows."
                , "The current supported ways of execution are:"
                , " - job submission using qsub with PBS or SGE"
                , " - monolithic, direct execution of jobs via Roddy"
                , " - submission or execution on the local machine or via SSH"
                , Constants.ENV_LINESEPARATOR
                , "  To support you with your workflows, Roddy offers you several options:"].join(Constants.ENV_LINESEPARATOR))
        System.out.println()

        printCommands([
                [RoddyStartupModes.help, "", "Shows a list of available configuration files in all configured paths."],
                [RoddyStartupModes.printappconfig, "[--useconfig={file}]", "Prints the currently loaded application properties ini file."],
                [RoddyStartupModes.showconfigpaths, "[--useconfig={file}]", "Shows a list of available configuration files in all configured paths."],
                [RoddyStartupModes.showfeaturetoggles, "", "Shows a list of available feature toggles."],
                [RoddyStartupModes.prepareprojectconfig, "", "Create or update a project xml file and an application properties ini file."],
                [RoddyStartupModes.plugininfo, "[--useconfig={file}]", "Shows details about the available plugins."],
                [RoddyStartupModes.printpluginreadme, "(configuration@analysis) [--useconfig={file}]", "Prints the readme file of the currently selected workflow."],
                [RoddyStartupModes.printanalysisxml, "(configuration@analysis) [--useconfig={file}]", "Prints the analysis xml file of the currently selected workflow."],
                [RoddyStartupModes.validateconfig, "(configuration@analysis) [--useconfig={file}]", "Tries to find errors in the specified configuration and shows them."],
                [RoddyStartupModes.listworkflows, "[filter word] [--shortlist] [--useconfig={file}]", "Shows a list of available configurations and analyses.", "If a filter word is specified, then the whole configuration tree is only printed", "if at least one configuration id in the tree contains the word."],
                [RoddyStartupModes.listdatasets, "(configuration@analysis) [--useconfig={file}]", "Lists the available datasets for a configuration."],
                [RoddyStartupModes.printruntimeconfig, "(configuration@analysis) [pid_0,..,pid_n] [--useconfig={file}] [--extendedlist] [--showentrysources]", "Basically calls testrun but prints out the converted / prepared runtime configuration script content.", "--extendedlist shows all stored values (also e.g. tool entries. Works only in combination with --showentrysources", "--showentrysources shows the source file of the entry in addition to the value."],
                [RoddyStartupModes.printidlessruntimeconfig, "(configuration@analysis) [--useconfig={file}] [--extendedlist] [--showentrysources]", "Like printruntimeconfig, but no dataset ID needs to be provided. All ID-related fields will be left empty."],
                [RoddyStartupModes.testrun, "(configuration@analysis) [pid_0,..,pid_n] [--useconfig={file}]", "Displays the current workflow status for the given datasets."],
                [RoddyStartupModes.testrerun, "(configuration@analysis) [pid_0,..,pid_n] [--useconfig={file}]", "Displays the current workflow status for the given datasets."],
                [RoddyStartupModes.run, "(configuration@analysis) [pid_0,..,pid_n] [--waitforjobs] [--useconfig={file}]", "Runs a workflow with the configured Jobfactory.", "Does not check if the workflow is already running on the cluster."],
                [RoddyStartupModes.rerun, "(configuration@analysis) [pid_0,..,pid_n] [--waitforjobs] [--useconfig={file}]", "Reruns a workflow starting only the parts which did not produce valid files.", "Does not check if the workflow is already running on the cluster."],
                [RoddyStartupModes.cleanup, "(configuration@analysis) [pid_0,..,pid_n] [--useconfig={file}]", "Calls a workflows cleanup method or a setup cleanup script to clean (i.e. remove or set to file size zero) output files."],
                [RoddyStartupModes.abort, "(configuration@analysis) [pid_0,..,pid_n] [--useconfig={file}]", "Aborts the running jobs of a workflow for a pid."],
                [RoddyStartupModes.checkworkflowstatus, "(configuration@analysis) [pid_0,..,pid_n] [--detailed] [--useconfig={file}]", "Shows a generic overview about all datasets for a configuration", "If some datasets are selected, a more detailed output is generated.", "If detailed is set, information about all started jobs and their status is shown."],
//                [RoddyStartupModes.setup, "[--useconfig={file}]", "Sets up Roddy for command line execution."],
//                [RoddyStartupModes.ui, "[--useconfig={file}]", "Open Roddys graphical user interface."]
        ])

        println("== Advanced developer options ==\n")
        printCommands([
                [RoddyStartupModes.compile, "", "Compiles the roddy library / application and updates the dist/bin/develop directory."],
                [RoddyStartupModes.pack, "", "Creates a copy of the development version and puts the version number to the file name."],
                [RoddyStartupModes.compileplugin, "(plugin ID) [--useconfig={file}]", "Compiles a plugin."],
                [RoddyStartupModes.packplugin, "(plugin ID) [--useconfig={file}]", "Packages the compiled plugin in dist/plugins and creates a version number for it.", "Please note that you can indeed override contents of a zip file if you do not update / compile the plugin jar!"]
        ])

        println("================================")
        System.out.println(Constants.ENV_LINESEPARATOR + Constants.ENV_LINESEPARATOR + "Common additional options")
        System.out.println([
                "    --useconfig={file}              - Use {file} as the application configuration.",
                "    --c={file}                         The order is: full path, .roddy folder, Roddy directory.",
                "    --verbositylevel={1,3,5}        - Set how much Roddy will print to the console, 1 is default, 3 is more, 5 is a lot.",
                "    --v                                Set verbosity to 3.",
                "    --vv                               Set verbosity to 5.",
                "    --useiodir=[fileIn],{fileOut}   - Use fileIn/fileOut as the base input and output directories for your project.",
                "                                       If fileOut is not specified, fileIn is used for that as well.",
                "                                       format can be: tsv, csv or excel",
                "    --usemetadatatable={file},[format]",
                "                                    - Tell Roddy to use an input table to load metadata and input data and available datasets.",
                "                                      Format can be 'tsv', 'csv' or 'excel'. By default 'tsv' is assumed.",
                "    --waitforjobs                   - Let Roddy wait for all submitted jobs to finish.",
                "    --disabletrackonlyuserjobs      - By default, Roddy will only track jobs of the current user. The switch tells Roddy to track all jobs.",
                "    --disablestrictfilechecks       - Tell Roddy to ignore missing files. By default, Roddy checks if all necessary files exist.",
                "    --ignoreconfigurationerrors     - Tell Roddy to ignore configuration errors. By default, Roddy will exit if configuration errors are detected.",
                "    --ignorecvalueduplicates        - Tell Roddy to ignore duplicate configuration values within the same configuration value block.",
                "                                       errors. By default, Roddy will exit if duplicates are found.",
                "    --forcenativepluginconversion   - Tell Roddy to override any existing converted Native plugin. By default Roddy will prevent this.",
                "    --forcekeepexecutiondirectory   - Tell Roddy to keep execution directories. By default Roddy will delete them, if no jobs were executed in a run.",
                "    --useRoddyVersion=(version no)  - Use a specific roddy version.",
                "    --usefeaturetoggleconfig={file} - Some development and backward-compatibility options can be set here. See showfeaturetoggles mode.",
                "    --rv=(version no)",
                "    --usePluginVersion=(...,...)    - Supply a list of used plugins and versions.",
                "    --configurationDirectories={path},... ",
                "                                    - Supply a list of configurationdirectories.",
                "    --pluginDirectories={path},...  - Supply a list of plugin directories."
        ].join(Constants.ENV_LINESEPARATOR)
        )
        System.out.println(Constants.ENV_LINESEPARATOR + Constants.ENV_LINESEPARATOR + "Roddy version " + Constants.APP_CURRENT_VERSION_STRING + " build at " + Constants.APP_CURRENT_VERSION_BUILD_DATE)
    }

    boolean needsJobManager() {
        return scope.needsJobManager
    }

    @Override
    String toString() {
        return this.name().replace("_", "")
    }
}
