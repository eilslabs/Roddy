/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy;

/**
 * Several publically available constants for the Roddy framework
 * The build strings are automatically updated on command line compilation.
 */
public class Constants {

    /////////////////////////
    // Application constants
    /////////////////////////

    public static final String APP_CURRENT_VERSION_STRING = "2.4.1";
    public static final String APP_CURRENT_VERSION_BUILD_DATE = "Wed Oct 25 14:39:09 CEST 2017";
    public static final String APP_PROPERTY_JOB_MANAGER_CLASS = "jobManagerClass";
    public static final String APP_PROPERTY_FILESYSTEM_ACCESS_MANAGER_CLASS = "fileSystemAccessManagerClass";
    public static final String APP_PROPERTY_EXECUTION_SERVICE_CLASS = "executionServiceClass";
    public static final String APP_PROPERTY_EXECUTION_SERVICE_USER = "executionServiceUser";
    public static final String APP_PROPERTY_EXECUTION_SERVICE_HOSTS = "executionServiceHost";
    public static final String APP_PROPERTY_EXECUTION_SERVICE_USE_COMPRESSION = "executionServiceUseCompression";
    public static final String APP_PROPERTY_EXECUTION_SERVICE_AUTH_METHOD = "executionServiceAuth";
    public static final String APP_PROPERTY_EXECUTION_SERVICE_AUTH_PWD = "executionServicePasswd";
    public static final String APP_PROPERTY_EXECUTION_SERVICE_STORE_PWD = "executionServiceStorePassword";
    public static final String APP_PROPERTY_LIBRARYDIRECTORIES = "libraryDirectories";
    public static final String APP_PROPERTY_USED_LIBRARIES = "usedLibraries";
    public static final String APP_PROPERTY_EXECUTION_SERVICE_AUTH_METHOD_PWD = "password";
    public static final String APP_PROPERTY_EXECUTION_SERVICE_AUTH_METHOD_KEYFILE = "keyfile";
    public static final String APP_PROPERTY_EXECUTION_SERVICE_AUTH_METHOD_KEYFILE_LOCATION = "executionServiceKeyfileLocation";
    public static final String APP_PROPERTY_CONFIGURATION_DIRECTORIES = "configurationDirectories";
    public static final String APP_PROPERTY_PLUGIN_DIRECTORIES = "pluginDirectories";
    public static final String APP_PROPERTY_APPLICATION_DEBUG_TAGS = "applicationDebugTags";
    public static final String APP_PROPERTIES_FILENAME = "applicationProperties.ini";
    public static final String APP_PROPERTY_APPLICATION_DEBUG_TAG_NOJOBSUBMISSION = "NOJOBSUBMISSION";
    public static final String APP_PROPERTY_NET_USEPROXY = "netUseProxy";
    public static final String APP_PROPERTY_NET_PROXY_ADDRESS = "netProxyAddress";
    public static final String APP_PROPERTY_NET_PROXY_USR = "netProxyUser";
    public static final String APP_SCRATCH_BASE_DIRECTORY = "scratchBaseDirectory";

    /////////////////////////
    // Error messages
    /////////////////////////

    public static final String ERR_MSG_ONLY_ONE_JOB_ALLOWED = "A job object is not allowed to run several times.";
    public static final String ERR_MSG_WRONG_PARAMETER_COUNT = "You did not provide proper parameters, args.length = ";
    public static final String ERR_MSG_NO_APPLICATION_PROPERTY_FILE = "Configuration does not exist. Cannot start application.";

//    public static final String APP_EXITCODE_

    /////////////////////////
    // Environment settings
    /////////////////////////

    public static final String ENV_LINESEPARATOR = System.getProperty("line.separator");

    /////////////////////////
    // Roddy tools
    /////////////////////////

    public static final String TOOLID_CREATE_LOCKFILES = "createLockFiles";
    public static final String TOOLID_STREAM_BUFFER = "streamBuffer";

    /////////////////////////
    // Other constants
    /////////////////////////

    public static final String UNKNOWN_USER = "UNKNOWN";
    public static final String UNKNOWN = "UNKNOWN";
    public static final String NO_VALUE = "<NO_VALUE>";

    public static final String RODDY_PARENT_JOBS = "RODDY_PARENT_JOBS";
    public static final String CONFIG_FILE = "CONFIG_FILE";
    public static final String PARAMETER_FILE = "PARAMETER_FILE";
    public static final String ANALYSIS_DIR = "ANALYSIS_DIR";
    public static final String PARAMETER_FILE_SUFFIX = ".parameters";
    public static final String WRAPPED_SCRIPT = "WRAPPED_SCRIPT";
    public static final String PID_CAP = "PID";
    public static final String PID = "pid";

    public static final String COMMAND_TAG_EXECUTION_CONTEXT = "ExecutionContext";
}
