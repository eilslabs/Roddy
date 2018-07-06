/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy;

import com.github.markusbernhardt.proxy.ProxySearch;
import com.github.markusbernhardt.proxy.util.PlatformUtil;
import de.dkfz.roddy.client.RoddyStartupModeScopes;
import de.dkfz.roddy.client.RoddyStartupModes;
import de.dkfz.roddy.client.RoddyStartupOptions;
import de.dkfz.roddy.client.cliclient.CommandLineCall;
import de.dkfz.roddy.client.cliclient.RoddyCLIClient;
import de.dkfz.roddy.client.rmiclient.RoddyRMIServer;
import de.dkfz.roddy.config.*;
import de.dkfz.roddy.core.Initializable;
import de.dkfz.roddy.execution.BEExecutionService;
import de.dkfz.roddy.execution.io.ExecutionService;
import de.dkfz.roddy.execution.io.LocalExecutionHelper;
import de.dkfz.roddy.execution.io.fs.BashCommandSet;
import de.dkfz.roddy.execution.io.fs.FileSystemAccessProvider;
import de.dkfz.roddy.execution.io.fs.ShellCommandSet;
import de.dkfz.roddy.execution.jobs.BatchEuphoriaJobManager;
import de.dkfz.roddy.execution.jobs.JobManagerOptions;
import de.dkfz.roddy.execution.jobs.direct.synchronousexecution.DirectSynchronousExecutionJobManager;
import de.dkfz.roddy.plugins.LibrariesFactory;
import de.dkfz.roddy.tools.*;
import groovy.transform.CompileStatic;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import static de.dkfz.roddy.RunMode.CLI;
import static de.dkfz.roddy.config.ConfigurationConstants.*;

/**
 * This is the main class for the Roddy command line application.
 * <p>
 * The class has several purposes:
 * - It initializes all service classes and the client interface (command line or gui).
 * - It is responsible to initialize proxy settings and to keep track of settings and settings files.
 * - It parses any special parameter passed to roddy on the command line like i.e. --useconfig.
 * - It provides access to application folders.
 *
 * @author michael
 */
public class Roddy {

    private static String SETTINGS_DIRECTORY_NAME = ".roddy";

    private static final LoggerWrapper logger = LoggerWrapper.getLogger(Roddy.class.getSimpleName());
    private static RoddyAppConfig applicationProperties = new RoddyAppConfig();

    private static RunMode runMode;
    private static boolean mainStarted = false;
    /**
     * This option is for command line execution only.
     * If set, Roddy will call the command factory to wait for all created jobs to finish.
     */
    private static boolean waitForJobsToFinish = false;
    private static String customPropertiesFile = null;

    /**
     * If you start roddy with autosubmit[=n], then roddy starts one dataset after another
     * until the maxim of n running datasets is reached. If one dataset is finished it starts
     * the next one. So it is some sort of controlled submit mode where you can i.e. leave
     * roddy lalone over night.
     * <p>
     * This feature was planned and might still be of interest. However, most users created
     * their own scripts and it might be more feasible to use such a script than a permanent
     * running Roddy instance.
     */
    private static boolean autosubmitMode = false;
    private static int autosubmitMaxBatchCount = 4;

    private static boolean repeatJobSubmission = false;
    private static int repeatJobSubmissionAmount = -1;
    private static int repeatJobSubmissionWait = 10;

    /**
     * Enable this, if you want Roddy to keep track only of the current users jobs. This is automatically enabled
     * for command line and disabled for GUI
     */
    private static boolean trackUserJobsOnly;
    private static boolean trackOnlyStartedJobs;

    private static boolean useCustomIODirectories;
    private static boolean useSingleIODirectory;
    private static String baseInputDirectory;
    private static String baseOutputDirectory;

    private static final File applicationDirectory = new File(System.getProperty("user.dir"));
    private static final File applicationBundleDirectory = new File(applicationDirectory, "bundledFiles");

    private static CommandLineCall commandLineCall;

    /**
     * An option specifically for listworkflows.
     * Prints a list in this way:
     * ICGCeval@genome
     * ICGCeval@exome
     * ICGCeval.dbg@genome
     * ICGCeval.dbg@exome
     */
    private static boolean displayShortWorkflowList;

    /**
     * For test reasons, the user can disable this value. Then Roddy won't call System.exit().
     */
    private static boolean exitAllowed = true;

    private static AppConfig featureToggleConfig;

    public static boolean isStrictModeEnabled() {
        return getFeatureToggleValue(AvailableFeatureToggles.StrictMode);
    }

    public static int getRepeatSubmissionAmount() {
        if (!repeatJobSubmission)
            return 1;
        return repeatJobSubmissionAmount <= 0 ? Integer.MAX_VALUE : repeatJobSubmissionAmount;
    }

    public static int getRepeatSubmissionWait() {
        return repeatJobSubmissionWait <= 1 ? 2 : repeatJobSubmissionWait;
    }

    public static boolean isTrackingOfUserJobsEnabled() {
        return trackUserJobsOnly;
    }

    public static boolean queryOnlyStartedJobs() {
        return trackOnlyStartedJobs;
    }

    public static boolean useCustomIODirectories() {
        return useCustomIODirectories;
    }

    public static boolean useSingleIODirectory() {
        return useSingleIODirectory;
    }

    public static boolean displayShortWorkflowList() {
        return displayShortWorkflowList;
    }

    public static String getCustomBaseInputDirectory() {
        return baseInputDirectory;
    }

    public static String getCustomBaseOutputDirectory() {
        return baseOutputDirectory;
    }


    public static ResourceSetSize getUsedResourcesSize() {
        // Do not set, when null!
        if (commandLineCall == null) {
            return null;
        }

        // Return null, when it is not available
        if (!commandLineCall.isOptionSet(RoddyStartupOptions.usedresourcessize)) {
            return null;
        }

        // Return the parsed value or null.
        try {
            return ResourceSetSize.valueOf(commandLineCall.getOptionValue(RoddyStartupOptions.usedresourcessize));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static boolean isMetadataCLOptionSet() {
        return getCommandLineCall().isOptionSet(RoddyStartupOptions.usemetadatatable);
    }

    /**
     * Main method which calls startup and wraps it into a try catch block.
     * This is done for bug finding
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        try {
//            Thread.sleep(3000);
            //Check if Roddy is called from the right directory.
            //TODO Think about a better way to get Roddys base directory.
            String[] list = applicationDirectory.list((dir, name) -> name.equals("roddy.sh"));
            if (list == null || list.length != 1) {
                System.out.println("You must call roddy from the right location! (Base roddy folder where roddy.sh resides). You used: '" +
                        applicationDirectory + "'");
                System.exit(255);
            }

            if (args.length == 0) {
                args = new String[]{RoddyStartupModes.help.toString()};
            }

            if (mainStarted == true)
                return;
            mainStarted = true;
            startup(args);
        } catch (Exception e) {

            // When Roddy is getting closed due to an exception, it will output this exception
            // on the command line. However, when you use GroovyServ to start Roddy, GroovyServ
            // always throws its own SystemExitException. As we know this, we will explicitely
            // prevent Roddy from printing this! However, we also do not want to build in a new
            // dependency to GroovyServ (it might not be downloadable or availbable). Therefore
            // we check for this particular exception by using its simple class name!
            if (!e.getClass().getName().endsWith("SystemExitException"))
                e.printStackTrace();
            exit(1);
        }
    }

    public static void resetMainStarted() {
        mainStarted = false;
    }

    private static long t1 = 0;
    private static long t2 = 0;

    private static void time(String info) {
        t2 = System.nanoTime();
        if (info != null) logger.postSometimesInfo(RoddyIOHelperMethods.printTimingInfo(info, t1, t2));
        t1 = t2;
    }


    private static void startup(String[] args) {

        time(null);

        List<String> list = Arrays.asList(args);
        CommandLineCall clc = new CommandLineCall(list);
        commandLineCall = clc;
        if (clc.isMalformed())
            exit(1);

        // Initialize the logger with an initial setup. At this point we don't know about things like the logger settings
        // or the used app ini file. However, all the following methods rely on an existing valid logger setup.
        LoggerWrapper.setup(getApplicationLogDirectory());

        logger.postAlwaysInfo("Roddy version " + Constants.APP_CURRENT_VERSION_STRING);

        time("initial checks");
        if (!roddyExecutionRequirementsFulfilled())
            exit(1);

        time("ftoggleini");
        initializeFeatureToggles();
        time("initialize mode, print arguments");
        performInitialSetup(args, clc.startupMode);
        time("initial");
        parseAdditionalStartupOptions(clc);
        time("parseopt");
        loadPropertiesFile();
        time("loadprop");

        // Reset the logger with the new settings but keep the old logfile.
        File clf = LoggerWrapper.getCentralLogFile();
        LoggerWrapper.setup(applicationProperties);
        LoggerWrapper.setCentralLogFile(clf);

        if (!initializeServices(clc.startupMode)) {
            logger.postAlwaysInfo("Could not initialize services. Roddy will not execute jobs.");
            performCLIExit(clc.startupMode, 1);
        }

        if (!jobExecutionRequirementsFulfilled())
            exit(1);

        time("initserv");
        parseRoddyStartupModeAndRun(clc);
        time("parsemode");
        performCLIExit(clc.startupMode);

        time("exit");
    }

    public static boolean jobExecutionRequirementsFulfilled() {
        List<String> errors = new LinkedList<>();
        errors.add("Requirements for job execution are not fulfilled:");
        ExecutionService jobSubmissionExecutionService = ExecutionService.getInstance();

        if (!jobSubmissionExecutionService.execute("which unzip").isSuccessful())
            errors.add("\tTool unzip not found.");

        if (!jobSubmissionExecutionService.execute("which lockfile").isSuccessful())
            errors.add("\tTool lockfile not found. lockfile can be found e.g. in the package procmail.");

        if (errors.size() == 1) return true;

        logger.severe(RoddyIOHelperMethods.joinArray(errors.toArray(new String[0]), "\n"));
        logger.severe("Please make sure that the dependencies are installed on the submission and execution hosts.");
        return false;
    }


    public static boolean roddyExecutionRequirementsFulfilled() {
        List<String> errors = new LinkedList<>();
        errors.add("Roddy cannot run:");
        if (!LocalExecutionHelper.executeCommandWithExtendedResult("which jar").isSuccessful())
            errors.add("\tTool jar not found.");
        if (!LocalExecutionHelper.executeCommandWithExtendedResult("which zip").isSuccessful())
            errors.add("\tTool zip not found.");
        if (!LocalExecutionHelper.executeCommandWithExtendedResult("which unzip").isSuccessful())
            errors.add("\tTool unzip not found.");

        if (errors.size() == 1) return true;

        logger.severe(RoddyIOHelperMethods.joinArray(errors.toArray(new String[0]), "\n"));
        logger.severe("Please make sure that the dependencies are installed on the submission host and also on the execution hosts.");
        return false;
    }

    public static void performInitialSetup(String[] args, RoddyStartupModes option) {

        runMode = CLI;
//        if (option == RoddyStartupModes.ui) runMode = RunMode.UI;
        if (option == RoddyStartupModes.rmi) runMode = RunMode.RMI;

        trackUserJobsOnly = runMode == CLI ? true : false; //Auto enable or disable trackuserjobs

        for (int i = 0; i < args.length; i++) {
            logger.postRareInfo(String.format("[%d] - %s", i, args[i]));
        }
    }

    protected static void parseAdditionalStartupOptions(CommandLineCall clc) {

        try {
            //Parse different options out of the args back from behind
            displayShortWorkflowList = clc.isOptionSet(RoddyStartupOptions.shortlist);
            if (clc.isOptionSet(RoddyStartupOptions.useconfig))
                customPropertiesFile = clc.getOptionValue(RoddyStartupOptions.useconfig);
            else if (clc.isOptionSet(RoddyStartupOptions.c))
                customPropertiesFile = clc.getOptionValue(RoddyStartupOptions.c);

            for (RoddyStartupOptions startupOption : clc.getOptionList()) {

                if (startupOption == (RoddyStartupOptions.v)) {
                    LoggerWrapper.setVerbosityLevel(LoggerWrapper.VERBOSITY_MEDIUM);
                }

                if (startupOption == (RoddyStartupOptions.vv)) {
                    LoggerWrapper.setVerbosityLevel(LoggerWrapper.VERBOSITY_HIGH);
                }

                if (startupOption == (RoddyStartupOptions.verbositylevel)) {
                    int level = RoddyConversionHelperMethods.toInt(clc.getOptionValue(startupOption), 5);
                    LoggerWrapper.setVerbosityLevel(level);
                }

                //Enable the setup of all debug options via the command line.
                if (startupOption == (RoddyStartupOptions.debugOptions)) {
                    String[] options = clc.getOptionList(startupOption).toArray(new String[0]);

                }

                if (runMode.isCommandLineMode()) {
                    // Instead of terminating, Roddy waits for all submitted jobs to finish.
                    if (startupOption == (RoddyStartupOptions.waitforjobs)) {
                        waitForJobsToFinish = true;
                    }

                    if (startupOption == (RoddyStartupOptions.useiodir)) {
                        useCustomIODirectories = true;

                        List<String> directories = clc.getOptionList(startupOption);
                        if (directories.size() == 0 || directories.size() > 2) {
                            throw new RuntimeException("Arguments for --useiodir are wrong");
                        }

                        useSingleIODirectory = directories.size() == 1;
                        baseInputDirectory = directories.get(0);
                        baseOutputDirectory = useSingleIODirectory ? baseInputDirectory : directories.get(1);
                    }

                    if (startupOption == (RoddyStartupOptions.disabletrackonlyuserjobs)) {
                        trackUserJobsOnly = false;
                    }

                    if (startupOption == (RoddyStartupOptions.trackonlystartedjobs)) {
                        trackOnlyStartedJobs = true;
                    }

                    // When a job is not submitted successfully, Roddy will wait try to do it again
                    if (startupOption == (RoddyStartupOptions.resubmitjobonerror)) {
                        repeatJobSubmission = true;
                        List<String> options = clc.getOptionList(startupOption);
                        if (options.size() > 0)
                            repeatJobSubmissionAmount = RoddyConversionHelperMethods.toInt(options.get(0));
                        if (options.size() > 1)
                            repeatJobSubmissionWait = RoddyConversionHelperMethods.toInt(options.get(1));
                    }

                    if (startupOption == (RoddyStartupOptions.autosubmit)) {
                        autosubmitMode = true;
                        if (clc.getOptionValue(startupOption) != null)
                            autosubmitMaxBatchCount = RoddyConversionHelperMethods.toInt(clc.getOptionValue(startupOption));
                    }

                    //Enable setup of workflow run flags from the command line
                    if (startupOption == (RoddyStartupOptions.run)) {
                        String[] flags = clc.getOptionList(startupOption).toArray(new String[0]);
                    }

                    if (startupOption == (RoddyStartupOptions.dontrun)) {
                        String[] flags = clc.getOptionList(startupOption).toArray(new String[0]);
                    }
                }
            }
        } catch (RuntimeException e) {
            logger.severe("Parsing startup options failed.");
            exit(1);
        }
    }

    private static void initializeFeatureToggles() {
        File toggleIni = null;
        if (commandLineCall.isOptionSet(RoddyStartupOptions.usefeaturetoggleconfig)) {
            toggleIni = new File(commandLineCall.getOptionValue(RoddyStartupOptions.usefeaturetoggleconfig));
            logger.postSometimesInfo("Trying to load alternative feature toggles file: " + toggleIni);
            if (toggleIni == null || !toggleIni.exists()) {
                logger.postAlwaysInfo("Cannot find requested toggle file.");
                exit(1);
            }

        } else {
            toggleIni = new File(getSettingsDirectory(), "featureToggles.ini");
        }

        if (toggleIni != null && toggleIni.exists()) { // Use default values, if the file is not available
            logger.postAlwaysInfo("Loading feature toggle file " + toggleIni.getAbsolutePath());
            AppConfig appConfig = new AppConfig(toggleIni);
            featureToggleConfig = appConfig;
        } else {
            featureToggleConfig = new AppConfig();
            Arrays.asList(AvailableFeatureToggles.values()).forEach(toggle -> {
                featureToggleConfig.setProperty(toggle.name(), "" + toggle.defaultValue);
            });
        }

        //Override toggles in ini
        boolean successful = true;
        successful &= retrieveAndSetToggleValuesFromCLI(RoddyStartupOptions.enabletoggles, true);
        successful &= retrieveAndSetToggleValuesFromCLI(RoddyStartupOptions.disabletoggles, false);

        // TODO:STRICT In Strict mode we should exit after all toggles were checked.
        if (!successful) {
            String toggleNames = RoddyIOHelperMethods.joinArray(AvailableFeatureToggles.values(), "\n\t");
            logger.severe("Available toggle values are:\n\t" + toggleNames);
            if (isStrictModeEnabled())
                exit(1);
        }
    }

    private static boolean retrieveAndSetToggleValuesFromCLI(RoddyStartupOptions opt, final boolean enabled) {
        if (!(opt == RoddyStartupOptions.enabletoggles || opt == RoddyStartupOptions.disabletoggles)) {
            // If the method is misused, we just exit. This is a programmatic error and should not punish the user.
            return true;
        }

        // Stores if an error happened
        boolean error = false;
        if (commandLineCall.isOptionSet(opt)) {
            List<String> listOfToggles = commandLineCall.getOptionList(opt);
            for (String toggle : listOfToggles) {
                try {
                    if (AvailableFeatureToggles.valueOf(toggle) != null)
                        featureToggleConfig.setProperty(toggle, "" + enabled);
                } catch (IllegalArgumentException e) {
                    // Just catch and tell the user what's wrong.
                    logger.severe("Toggle with name " + toggle + " is either misspelled or not available.");
                    error = true;
                }
            }
        }
        return !error;
    }

    public static boolean getFeatureToggleValue(AvailableFeatureToggles toggle) {
        if (featureToggleConfig == null) return toggle.defaultValue;
        return RoddyConversionHelperMethods.toBoolean(featureToggleConfig.getProperty(toggle.name(), null), toggle.defaultValue);
    }

    public static boolean isOptionSet(RoddyStartupOptions opt) {
        return getCommandLineCall().isOptionSet(opt);
    }

    public static boolean isStrictWithFeature(RoddyStartupOptions opt) {
        return Roddy.isStrictModeEnabled() && Roddy.getCommandLineCall().isOptionSet(opt);
    }

    /**
     * Initializes basic Roddy services
     * The execution service has two configuration settings, one for command line interface and one for the ui based instance.
     */
    public static final boolean initializeServices(RoddyStartupModes mode) {
        String currentStep = "Initialize proxy settings";
        try {

            // Configure a proxy for internet connection. Used i.e. for codemirror
            initializeProxySettings();
            time("init proxy");

            currentStep = "Initialize file system access provider";
            FileSystemAccessProvider.initializeProvider(mode.needsJobManager());
            time("init fsap");

            //Do not touch the calling order, execution service must be set before BatchEuphoriaJobManager.
            currentStep = "Initialize execution service";
            ExecutionService.initializeService(mode.needsJobManager());
            time("init execserv");

            currentStep = "Initialize command factory";
            initializeJobManager(mode);
            time("init cmd fac");
            return true;
        } catch (Exception ex) {
            logger.severe("initializeServices failed with an unhandled error. The step in which the error occurred was: " + currentStep + "\nSee the following stacktrace for more details.", ex);
            logger.severe(RoddyIOHelperMethods.getStackTraceAsString(ex));
            return false;
        }
    }

    /**
     * Auto detect and select a proxy provider.
     * TODO Does this work from Windows?
     * This uses the proxy vole library.
     */
    private static void initializeProxySettings() throws ConfigurationError {
        boolean useProxyForInternetConnection = applicationProperties.getOrSetBooleanApplicationProperty(Constants.APP_PROPERTY_NET_USEPROXY, false);
        if (useProxyForInternetConnection) {

            System.setProperty("java.net.useSystemProxies", "true");
            System.out.println("detecting proxies");

            ProxySearch proxySearch = new ProxySearch();
            if (PlatformUtil.getCurrentPlattform() == PlatformUtil.Platform.WIN) {
                proxySearch.addStrategy(ProxySearch.Strategy.IE);
                proxySearch.addStrategy(ProxySearch.Strategy.FIREFOX);
                proxySearch.addStrategy(ProxySearch.Strategy.JAVA);
            } else if (PlatformUtil.getCurrentPlattform() == PlatformUtil.Platform.LINUX) {
                proxySearch.addStrategy(ProxySearch.Strategy.GNOME);
                proxySearch.addStrategy(ProxySearch.Strategy.KDE);
                proxySearch.addStrategy(ProxySearch.Strategy.FIREFOX);
            } else {
                proxySearch.addStrategy(ProxySearch.Strategy.OS_DEFAULT);
            }

            ProxySelector proxySelector = proxySearch.getProxySelector();
            ProxySelector.setDefault(proxySelector);

            List l = null;
            try {
                l = ProxySelector.getDefault().select(new URI("http://codemirror.net"));
            } catch (URISyntaxException e) {
                e.printStackTrace();
                System.exit(1);
            }

            if (l != null) {
                for (Iterator iter = l.iterator(); iter.hasNext(); ) {
                    java.net.Proxy proxy = (java.net.Proxy) iter.next();
                    System.out.println("proxy hostname : " + proxy.type());

                    InetSocketAddress addr = (InetSocketAddress) proxy.address();

                    if (addr == null) {
                        System.out.println("No Proxy");
                    } else {
                        System.out.println("proxy hostname : " + addr.getHostName());
                        System.setProperty("http.proxyHost", addr.getHostName());
                        System.out.println("proxy port : " + addr.getPort());
                        System.setProperty("http.proxyPort", Integer.toString(addr.getPort()));
                    }
                }
            }
        }
    }


    private static Configuration applicationSpecificConfiguration = null;

    private static void setDefaultRoddyJobIdVariable(RecursiveOverridableMapContainerForConfigurationValues configurationValues) {
        configurationValues.add(new ConfigurationValue(CVALUE_PLACEHOLDER_RODDY_JOBID_RAW, "$" + Roddy.jobManager.getJobIdVariable()));
    }

    private static void setDefaultRoddyJobNameVariable(RecursiveOverridableMapContainerForConfigurationValues configurationValues) {
        configurationValues.add(new ConfigurationValue(CVALUE_PLACEHOLDER_RODDY_JOBNAME_RAW, "$" + Roddy.jobManager.getJobNameVariable()));
    }

    private static void setDefaultRoddyQueueVariable(RecursiveOverridableMapContainerForConfigurationValues configurationValues) {
        configurationValues.add(new ConfigurationValue(CVALUE_PLACEHOLDER_RODDY_QUEUE_RAW, "$" + Roddy.jobManager.getQueueVariable()));
    }

    @Deprecated
    private static void warnDeprecatedRoddyScriptVariable() {
        String deprecatedDefaultScratchDir = applicationProperties.getOrSetApplicationProperty(ConfigurationConstants.CVALUE_DEFAULT_SCRATCH_DIR, "");
        if (!deprecatedDefaultScratchDir.equals("")) {
            System.err.println("WARNING: You did set the deprecated " + ConfigurationConstants.CVALUE_DEFAULT_SCRATCH_DIR + " to '" +
                    deprecatedDefaultScratchDir + ".\nPlease use " + Constants.APP_PROPERTY_SCRATCH_BASE_DIRECTORY + ".");
        }
    }

    /** Copy the scratchBaseDirectory value from the applicationProperties.ini into the configuration. Set the default value for RODDY_SCRATCH
     *  to $scratchBaseDirectory/$RODDY_JOBID.
     * @return
     */
    private static void setScratchDirectory(RecursiveOverridableMapContainerForConfigurationValues configurationValues) {
        warnDeprecatedRoddyScriptVariable();
        String scratchBaseDir = applicationProperties.getOrSetApplicationProperty(Constants.APP_PROPERTY_SCRATCH_BASE_DIRECTORY, "");
        if (scratchBaseDir.equals("")) {
            File propertyFilePath = null;
            try {
                propertyFilePath = Roddy.getPropertiesFilePath();
                System.err.println(Constants.APP_PROPERTY_SCRATCH_BASE_DIRECTORY +
                        " is not defined. Please add it to your application properties file: '" + propertyFilePath + "'");
                System.exit(1);
            } catch (FileNotFoundException e) {
                // The file must have existed, because we are accessing the values in it.
                scratchBaseDir = System.getenv("CURRENT_PWD");
                if (scratchBaseDir == null) {
                    scratchBaseDir = System.getProperty("user.dir");
                }
            }

        }
        configurationValues.add(new ConfigurationValue(Constants.APP_PROPERTY_SCRATCH_BASE_DIRECTORY, scratchBaseDir));
        String scratchDir = new File ("$" + Constants.APP_PROPERTY_SCRATCH_BASE_DIRECTORY,
                "$" + ConfigurationConstants.CVALUE_PLACEHOLDER_RODDY_JOBID_RAW).toString();
        configurationValues.add(new ConfigurationValue(CVALUE_PLACEHOLDER_RODDY_SCRATCH_RAW, scratchDir));
    }

    public static Configuration getApplicationSpecificConfiguration() {
        if (applicationSpecificConfiguration == null) {
            applicationSpecificConfiguration = new Configuration(null);
            RecursiveOverridableMapContainerForConfigurationValues configurationValues = applicationSpecificConfiguration.getConfigurationValues();

            if (Roddy.jobManager != null) {
                setDefaultRoddyJobIdVariable(configurationValues);
                setDefaultRoddyJobNameVariable(configurationValues);
                setDefaultRoddyQueueVariable(configurationValues);
            } else {
                logger.warning("No job manager specific variables available because no job manager is set.");
            }

            setScratchDirectory(configurationValues);

            // Add custom command line values to the project configuration.
            List<ConfigurationValue> externalConfigurationValues = getCommandLineCall().getSetConfigurationValues();

            configurationValues.addAll(externalConfigurationValues);

            if (useCustomIODirectories()) {
                configurationValues.add(new ConfigurationValue(CFG_INPUT_BASE_DIRECTORY, Roddy.getCustomBaseInputDirectory(), "path"));
                configurationValues.add(new ConfigurationValue(CFG_OUTPUT_BASE_DIRECTORY, Roddy.getCustomBaseOutputDirectory(), "path"));
            }

            if (getUsedResourcesSize() != null) {
                configurationValues.add(new ConfigurationValue(CFG_USED_RESOURCES_SIZE, Roddy.getUsedResourcesSize().toString(), "string"));
            }

        }
        return applicationSpecificConfiguration;
    }

    @CompileStatic
    public static void initializeJobManager(RoddyStartupModes mode)
            throws ClassNotFoundException, IllegalAccessException, InvocationTargetException,
            InstantiationException, NoSuchMethodException, FileNotFoundException, ConfigurationError {
        logger.postSometimesInfo("public static void initializeFactory(boolean fullSetup)");

        if (!mode.needsJobManager()) return;

        ClassLoader classLoader;
        String jobManagerClassID = "";
        Class jobManagerClass = null;

        try {

            classLoader = LibrariesFactory.getGroovyClassLoader();
            if (mode.scope == RoddyStartupModeScopes.SCOPE_FULL_WITHJOBMANAGER) {
                jobManagerClassID = applicationProperties.getOrSetApplicationProperty(Constants.APP_PROPERTY_JOB_MANAGER_CLASS);
                if (RoddyConversionHelperMethods.isNullOrEmpty(jobManagerClassID)) jobManagerClassID = "UNSET";
                jobManagerClass = classLoader.loadClass(jobManagerClassID);
            } else
                jobManagerClass = DirectSynchronousExecutionJobManager.class;
            logger.always("Roddy will try to use the " + jobManagerClass.getSimpleName() + " job manager class to manage jobs.");
        } catch (ClassNotFoundException e) {
            StringBuilder available = new StringBuilder();
            for (AvailableClusterSystems acs : AvailableClusterSystems.values()) {
                available.append("\n\t" + acs.getClassName());
            }
            logger.severe("Could not find job manager class: " + jobManagerClassID + ", available are: " + available.toString() + "\nPlease set the jobManagerClass entry in your application ini file: " + getPropertiesFilePath().getAbsolutePath() + "");
            exit(1);
        }

        /** Get the constructor which comes with no parameters */
        Constructor first = jobManagerClass.getDeclaredConstructor(BEExecutionService.class, JobManagerOptions.class);
        jobManager = (BatchEuphoriaJobManager) first.newInstance(ExecutionService.getInstance()
                , JobManagerOptions.create()
                        .setCreateDaemon(true)
                        .setStrictMode(false)
                        .setTrackUserJobsOnly(trackUserJobsOnly)
                        .setPassEnvironment(applicationProperties.getOrSetBooleanApplicationProperty(Constants.APP_PROPERTY_JOB_MANAGER_PASS_ENVIRONMENT, false))
                        .setTrackOnlyStartedJobs(trackOnlyStartedJobs)
                        .setHoldJobIsEnabled(applicationProperties.getOrSetBooleanApplicationProperty(Constants.APP_PROPERTY_JOB_MANAGER_HOLDJOBS_ON_SUBMISSION, true))
                        .setUserIdForJobQueries(FileSystemAccessProvider.getInstance().callWhoAmI()).build());
    }

    private static BatchEuphoriaJobManager jobManager;

    public static BatchEuphoriaJobManager getJobManager() {
        return jobManager;
    }

    private static void parseRoddyStartupModeAndRun(CommandLineCall clc) {
        if (clc.startupMode == RoddyStartupModes.rmi)
            RoddyRMIServer.startServer(clc);
        else
            RoddyCLIClient.parseStartupMode(clc);
    }

    private static void performCLIExit(RoddyStartupModes option) {
        performCLIExit(option, 0);
    }

    private static void performCLIExit(RoddyStartupModes option, int exitCode) {
        if (commandLineCall.getOptionList().contains(RoddyStartupOptions.disallowexit))
            return;

        if (!option.needsJobManager())
            return;

        if (jobManager != null) {
            if (jobManager.executesWithoutJobSystem() && waitForJobsToFinish) {
                exitCode = waitForJobs();
            } else {
                // TODO: #167 (https://github.com/eilslabs/Roddy/issues/167)
                exit(0);
            }
        }
        exit(exitCode);
    }

    private static int waitForJobs() {
        try {
            Thread.sleep(15000); //Sleep at least 15 seconds to let any job scheduler handle things...
            return jobManager.waitForJobsToFinish();
        } catch (Exception ex) {
            return 250;
        }

    }

    public static void exit() {
        exit(0);
    }

    public static void exit(int ecode) {
        Initializable.destroyAll();
        System.exit(ecode <= 250 ? ecode : 250); //Exit codes should be in range from 0 .. 255
    }

    public static void loadPropertiesFile() {
        File file = null;
        try {
            file = getPropertiesFilePath();
        } catch (FileNotFoundException e) {
            logger.postAlwaysInfo("Could not load the application properties file: " + e.getMessage() + ". Roddy will exit.");
            exit(1);
        }
        logger.postAlwaysInfo("Loading properties file " + file.getAbsolutePath() + ".");

        applicationProperties = new RoddyAppConfig(file);

        // Load some default properties
        applicationProperties.getOrSetApplicationProperty(RoddyStartupOptions.useRoddyVersion.toString(), LibrariesFactory.PLUGIN_VERSION_DEVELOP);
        applicationProperties.getOrSetApplicationProperty("usePluginVersion");
        applicationProperties.getOrSetApplicationProperty("pluginDirectories");
        applicationProperties.getOrSetApplicationProperty("configurationDirectories");
    }

    public static RoddyAppConfig getApplicationConfiguration() {
        return applicationProperties;
    }

    public static RunMode getRunMode() {
        return CLI;// runMode;
    }

    /**
     * Returns the path to Roddys application settings directory.
     * This directory contains the application properties file and subfolders like i.e. file caches.
     *
     * @return
     */
    public static File getSettingsDirectory() {
        String userHome = System.getProperty("user.home");
        if (userHome == null) {
            throw new IllegalStateException("user.home==null");
        }
        File home = new File(userHome);
        File settingsDirectory = new File(home, SETTINGS_DIRECTORY_NAME);
        if (!settingsDirectory.exists()) {
            if (!settingsDirectory.mkdir()) {
                throw new IllegalStateException(settingsDirectory.toString());
            }
        }
        return settingsDirectory;
    }

    public static File getApplicationLogDirectory() {
        File logDir = new File(getSettingsDirectory(), "logs");
        if (!logDir.exists())
            logDir.mkdir();
        return logDir;
    }

    /**
     * If the filename is a path, e.g. a filename in the local directory (starting with ./), return exactly this file. If the file does not exist,
     * throw an exception.
     * If just a filename is given, return the first existing file in the current directory, the settingsDirectory or the applicationDirectory.
     * If none of these files exists, throw an exception.
     *
     * @return
     */
    public static File getPropertiesFilePath() throws FileNotFoundException {
        if (customPropertiesFile == null) customPropertiesFile = Constants.APP_PROPERTIES_FILENAME;

        File propertiesFile = new File(customPropertiesFile);

        if (propertiesFile.getName() != propertiesFile.toString()) {
            if (!propertiesFile.exists()) {
                throw new FileNotFoundException(propertiesFile.toString());
            }
            return propertiesFile;
        } else {
            File settingsDirPropertiesFile = new File(getSettingsDirectory(), propertiesFile.toString());
            File applicationDirPropertiesFile = new File(getApplicationDirectory(), propertiesFile.toString());

            List<File> files = Arrays.asList(propertiesFile, settingsDirPropertiesFile, applicationDirPropertiesFile);
            for (File cpf : files)
                if (cpf.exists())
                    return cpf;

            throw new FileNotFoundException(RoddyIOHelperMethods.joinArray(files.toArray(), ", "));
        }
    }

    public static File getCompressedAnalysisToolsDirectory() {
        return new File(getSettingsDirectory(), "compressedAnalysisTools");
    }

    public static File getNewCompressedAnalysisToolsFile() {
        return new File(getCompressedAnalysisToolsDirectory(), String.format("roddyCompressedAnalysisToolsArchive_%d", System.currentTimeMillis()));
    }

    public static File getCompressedAnalysisToolsOverviewFile() {
        return new File(getCompressedAnalysisToolsDirectory(), "listOfCompressedFiles.txt");
    }

    private static List<File> loadFolderListFromConfiguration(RoddyStartupOptions option, String configurationConstant) throws ConfigurationError {
        String[] split;
        if (getCommandLineCall().isOptionSet(option))
            split = getCommandLineCall().getOptionList(option).toArray(new String[0]);
        else
            split = applicationProperties.getOrSetApplicationProperty(configurationConstant, "").split("[,:]");

        List<File> folders = new LinkedList<>();
        for (String s : split) {
            if (s.equals("")) continue;
            File f = new File(s);
            if (f.exists() && f.isDirectory() && f.canRead() && f.canExecute()) {
                folders.add(f);
            } else {
                throw new ConfigurationError("'" + s + "' is not an accessible directory", s);
            }
        }
        return folders;
    }

    public static List<File> getConfigurationDirectories() throws ConfigurationError {
        List<File> list = loadFolderListFromConfiguration(RoddyStartupOptions.configurationDirectories, Constants.APP_PROPERTY_CONFIGURATION_DIRECTORIES);
        list.add(getFolderForConfigurationFreeMode());
        list.add(new File(Roddy.getApplicationDirectory(), "configurationfiles"));
        return list;
    }

    public static List<File> getPluginDirectories() throws ConfigurationError {
        List<File> folders = loadFolderListFromConfiguration(RoddyStartupOptions.pluginDirectories, Constants.APP_PROPERTY_PLUGIN_DIRECTORIES);

        // TODO For backward compatibility the following plugin folders will be used in an hardcoded way
        for (File folder : Arrays.asList(
                RoddyIOHelperMethods.assembleLocalPath(getApplicationDirectory(), "plugins"),
                RoddyIOHelperMethods.assembleLocalPath(getApplicationDirectory(), "dist", "plugins"),
                RoddyIOHelperMethods.assembleLocalPath(getApplicationDirectory(), "dist", "plugins_R" + getShortVersionString())
        )) {
            if (folder.exists() && !folders.contains(folder)) folders.add(folder);
        }
        return folders;
    }

    public static String getShortVersionString() {
        String[] complete = Constants.APP_CURRENT_VERSION_STRING.split("[.]");
        return complete[0] + "." + complete[1];
    }

    public static File getFileCacheDirectory() {
        return checkAndCreateFolder(getSettingsDirectory(), "caches" + File.separator + "filecache");
    }

    public static File getApplicationDirectory() {
        return applicationDirectory;
    }

    public static File getBundledFilesDirectory() {
        return applicationBundleDirectory;
    }

    public static File getFolderForConvertedNativePlugins() {
        return checkAndCreateFolder(getSettingsDirectory(), "convertedNativePlugins");
    }

    public static File getFolderForConfigurationFreeMode() {
        return checkAndCreateFolder(getSettingsDirectory(), "configurationFreeModeFiles");
    }

    private static File checkAndCreateFolder(File baseFolder, String subFolder) {
        File newDir = new File(baseFolder, subFolder);
        if (!newDir.exists())
            newDir.mkdirs();
        return newDir;
    }

    public static String getUsedRoddyVersion() {
        String result = "";
        CommandLineCall clc = getCommandLineCall();
        for (RoddyStartupOptions opt : Arrays.asList(
                RoddyStartupOptions.useRoddyVersion,
                RoddyStartupOptions.useroddyversion,
                RoddyStartupOptions.rv)) {
            if (clc.isOptionSet(opt))
                result = clc.getOptionValue(opt);
        }
        if (!RoddyConversionHelperMethods.isNullOrEmpty(result)) return result;
        return applicationProperties.getOrSetApplicationProperty(RoddyStartupOptions.useRoddyVersion.toString(), LibrariesFactory.PLUGIN_VERSION_DEVELOP);
    }

    public static File getRoddyBinaryFolder() {
        String roddyVersion = getUsedRoddyVersion();
        return RoddyIOHelperMethods.assembleLocalPath(applicationDirectory, "dist", "bin", roddyVersion);
    }

    public static String[] getPluginVersionEntries() {
        String[] pluginVersionEntries;
        if (getCommandLineCall().isOptionSet(RoddyStartupOptions.usePluginVersion))
            pluginVersionEntries = getCommandLineCall().getOptionValue(RoddyStartupOptions.usePluginVersion).split(StringConstants.SPLIT_COMMA);
        else
            pluginVersionEntries = applicationProperties.getOrSetApplicationProperty(RoddyStartupOptions.usePluginVersion.name()).split(StringConstants.SPLIT_COMMA);
        return pluginVersionEntries;
    }

    public static String getUsedPluginVersion(String pluginID) {

        Map<String, String> pluginVersions = new LinkedHashMap<>();
        for (String pluginVersionEntry : getPluginVersionEntries()) {
            if (RoddyConversionHelperMethods.isNullOrEmpty(pluginVersionEntry)) continue;
            String[] versionString = pluginVersionEntry.split(StringConstants.SPLIT_COLON);
            pluginVersions.put(versionString[0], versionString[1]);
        }
        if (pluginVersions.containsKey(pluginID))
            return pluginVersions.get(pluginID);
        return LibrariesFactory.PLUGIN_VERSION_DEVELOP;
    }

    public static FileSystemAccessProvider getLocalFileSystemAccessProvider() {
        return new FileSystemAccessProvider();
    }

    public static ShellCommandSet getLocalCommandSet() {
        String localCommandSet = applicationProperties.getOrSetApplicationProperty("localCommandSet");

        // Handle default case, the local commandset ist not set.
        if (RoddyConversionHelperMethods.isNullOrEmpty(localCommandSet)) {
            logger.postSometimesInfo("Using BashCommandSet, localCommandSet ist not set.");
            BashCommandSet bcs = new BashCommandSet();
            bcs.validate();
            return bcs;
        }

        Class<ShellCommandSet> cls = null;
        try {
            cls = (Class<ShellCommandSet>) LibrariesFactory.getGroovyClassLoader().loadClass(localCommandSet);
            ShellCommandSet shellCommandSet = cls.newInstance();
            if (shellCommandSet.validate())
                return shellCommandSet;
            throw new RuntimeException("The selected ShellCommandSet '${localCommandSet}' could not validate.");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Could not find localCommandSet class '${localCommandSet}'. Check you ini file.");
        } catch (InstantiationException e) {
            throw new RuntimeException("Could not create object of type: " + cls.getName());
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Could not access default constructor for class: " + cls.getName());
        }
    }

    public static CommandLineCall getCommandLineCall() {
        if (commandLineCall == null)
            return new CommandLineCall(new LinkedList<>());
        return commandLineCall;
    }
}
