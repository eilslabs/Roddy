.. Links
.. _`GitHub project site`: https://github.com/eilslabs/Roddy
.. _`JRE v1.8.*`: https://java.com/de/download/linux_manual.jsp
.. _`JDK v1.8.*`: http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html
.. _`Groovy 2.4.*`: http://groovy-lang.org/download.html
.. _`Maven Groovy repository`: http://repo1.maven.org/maven2/org/codehaus/groovy/groovy-binary/
.. _`GroovyServ` : https://kobo.github.io/groovyserv/
.. _`SDKMan` : http://sdkman.io/
.. _`Github Releases` : https://github.com/eilslabs/Roddy/releases

.. Document

Installation guide
==================

There are several minor versions of Roddy, which can be downloaded and installed in the same directory.
Minor versions mark changes in the Roddy API. Usually Roddy plugins are only compatible to a specific minor version.
Installations for the different versions differ a bit, so we list all versions here.

Premises
--------
To install and run Roddy the following programs need to be installed on your computer or the execution hosts:

To run Roddy you need at least

- Java and Groovy
- the default plugin
- the base plugin
- zip/unzip
- bash
- the tool `lockfile` (usually in the procmail mail-processing-package (v3.22), only needed on job execution hosts)

As Roddy is Linux based, you will be able to find most of these in your OS package manager. For the JDK and Groovy, -- both required on the host on which you run Roddy -- you may want to use `SDKMan`_. The following will get you going:

.. code-block:: Bash

    curl -s "https://get.sdkman.io" | bash
    source "$HOME/.sdkman/bin/sdkman-init.sh"
    sdk install groovy 2.4.13
    sdk install java 8u151-oracle

The minimal version we tested Roddy with are Groovy 2.4.7 and Java 8u121, but lower versions might also work.

The remaining instructions deal with the installation of the Roddy environment -- basically is a set of directories into which to install the rest -- and Roddy itself.


Roddy 2.2
---------

Will not be supported in the future. Releases are only available for legacy plugins.

Roddy 2.3
---------

1. Clone the repo and select the desired tag.

2. Step two depends on your role.

  - If you intend to use Roddy and do not want to develop plugins or Roddy itself:

    - Download any `JRE v1.8.*`_ (OpenJDK and SunJDK were tested). Also download `Groovy 2.4.*`_

    - Open up the dist folder in the Roddy directory.

    - Create a folder named runtimeDevel

    - unzip / untar both archives in runtimeDevel

  - If you want to develop Roddy or Roddy plugins:

    - Download any `JDK v1.8.*`_ (OpenJDK and SunJDK were tested). Also download `Groovy 2.4.*`_

    - Open up the dist folder in the Roddy directory.

    - Create a folder named runtimeDevel

    - unzip / untar both archives in runtimeDevel

3. Optionally unpack one or more of the release zips in *dist/bin/* directory.

Please see `Versioning`_ for information about how to mix different versions of Roddy in the same directory.

Roddy 3.0
---------

For Roddy version 3.0 zips are deployed to Github releases (continuous deployment via Travis). The thus installed Roddy will contain all Java library dependencies except the JDK and Groovy, which are both needed during the start-up, before the actual Roddy is started.

1. Release ZIPs for Roddy and the Roddy environment are available via `Github Releases`_. Download the latest release of the RoddyEnv ZIP and unpack it and change into the Roddy environment directory (e.g. "Roddy"). This "environment" is basically a specific directory structure and a start-up script that allow to install multiple Roddy versions in parallel.
2. After that you can install arbitrary releases of the Roddy ZIP into `dist/bin/$major.$minor.$patch` directories.
3. Finally the default and base plugin repositories need to be cloned into the `dist/plugins/` directory.

.. code-block:: Bash

   pushd dist/plugins
   git clone https://github.com/eilslabs/Roddy-Default-Plugin.git DefaultPlugin
   git clone https://github.com/eilslabs/Roddy-Base-Plugin PluginBase
   popd

Versioning
----------

The Roddy environment with the top-level "roddy.sh" allow you to co-install multiple Roddy versions. Simply install the different versions of Roddy,
e.g. from the release zips, into directories in "dist/bin" following the naming scheme "dist/bin/$major.$minor.$patch". The desired version can than
be selected during Roddy invocations using the "--useRoddyVersion" parameter.

Additionally, Roddy is capable of handling multiple versions of the same workflow plugin. Therefore, if you install specific plugins, such as the `ACEseq plugin <https://github.com/eilslabs/ACEseqWorkflow>`_, you will need specific versions of e.g. the default and base plugins. The way to progress here is to first check in the plugin of interest in the "buildinfo.txt", which plugins and their versions are needed, and then progress in this way from plugin to plugin recursively.

The installation of specific plugin version needs to be done in directories named after the scheme `$pluginName_$major.$minor.$patch[-$revision]` (the revision is optional). Usually you can get specific versions -- official releases of plugins -- in the Github Releases of the plugin. Alternatively you clone the repository into an appropriately named directory and then check out the tag with the version of interest.

On the long run, this manual plugin installation mechanism may get automatized.


[Optional] Setup GroovyServ
---------------------------

Roddy uses Groovy, however, Groovy is a bit slow to start. So Roddy 3.0+ supports `GroovyServ`_, which can be used by you to speed things up.
GroovyServ tremendously decreases the startup time of Groovy applications and Roddy will try to download and set it up automatically. If that fails or
if you want to set it up by yourself, do the following in your Roddy directory:

.. code-block:: Bash

    mkdir -p dist/runtime
    cd dist/runtime

    # Download the GroovyServ binary zip archive from the `GroovyServ`_ download site,
    # unzip it and delete the archive afterwards.

    unzip groovyserv*.zip
    rm groovyserv*.zip

    # Last step, put Groovy and the Java binary folders to your PATH environment variable. This
    # is e.g. set in your ~/.bashrc file.

Now that's it. If you want to disable GroovyServ, you also do this.

.. code-block:: Bash

    mkdir -p dist/runtime
    cd dist/runtime
    touch gservforbidden

If you create the file, Roddy will not use GroovyServ.

.. Note::

    This setup was tested using GroovyServ 1.1.0!

Test your installation
----------------------

Head over to the Roddy directory and do

.. code-block:: Bash

  ./roddy.sh

If everything is properly done, Roddy will print its help screen.

Quick build instructions
------------------------

If you want to build Roddy yourself, clone the repository. The repository already contains the Roddy environment. Change into this directory and use Gradle to build the Roddy JAR. In summary:

.. code-block:: bash

    git clone https://github.com/eilslabs/Roddy.git
    cd Roddy
    git checkout develop
    pushd dist/plugins
    git clone https://github.com/eilslabs/Roddy-Default-Plugin.git DefaultPlugin
    git clone https://github.com/eilslabs/Roddy-Base-Plugin PluginBase
    popd
    ./gradlew build

The example will build the Roddy from the `develop` branch. If you use this branch, the dependencies BatchEuphoria and RoddyToolLib will automatically be pulled from Github with their development snapshots. On the master branch we fix the version numbers of these two dependencies. Note that the two basic plugins are required for some of the integration tests.

Full developer build instructions
---------------------------------

If you want to work with a full Roddy installation and its dependencies, we suggest you create a dedicated directory to install everything. Roddy and its dependencies [BatchEuphoria](https://github.com/TheRoddyWMS/BatchEuphoria) and [RoddyToolLib](https://github.com/TheRoddyWMS/RoddyToolLib) use the Gradle build system. Specifically, it uses the [composite build feature](https://docs.gradle.org/current/userguide/composite_builds.html) of Gradle. Let's get your own clones of the BatchEuphoria and RoddyToolLib Git repos and reference them with the `--includeBuild` parameter:

.. code-block:: bash

    mkdir RoddyProject
    cd RoddyProject
    git clone https://github.com/TheRoddyWMS/RoddyToolLib.git
    git clone https://github.com/TheRoddyWMS/BatchEuphoria.git
    git clone https://github.com/TheRoddyWMS/Roddy.git

    mkdir -p Roddy/dist/plugins
    pushd Roddy/dist/plugins
    git clone https://github.com/TheRoddyWMS/Roddy-Default-Plugin.git DefaultPlugin
    git clone https://github.com/TheRoddyWMS/Roddy-Base-Plugin PluginBase
    popd

    cd Roddy
    ./gradlew build --include-build ../RoddyToolLib/ --include-build ../BatchEuphoria/


Via the `--include-build` options you make sure to use the local "development" installations of the libraries.

Gradle and proxies
^^^^^^^^^^^^^^^^^^

If you are behind a proxy you should first configure the proxy for Gradle. Create `$HOME/.gradle/gradle.properties` with the appropriate settings. You can use the following template:

.. code-block:: groovy

    systemProp.http.proxyHost=
    systemProp.http.proxyPort=
    systemProp.https.proxyHost=
    systemProp.https.proxyPort=


IntelliJ
--------

1. Download and activate the Gradle-plugin of IntelliJ, if you have not done so already.
2. Open a new project. The project should be an "Empty Project".
3. Clone the `RoddyToolLib`, `BatchEuphoria` and `Roddy` into your new empty project. Also the `DefaultPlugin` and `PluginBase` plugins are required for some of the integration tests and should be present for most useful things you can do with Roddy.

.. code-block:: bash

    cd $yourProjectDirectory
    git clone https://github.com/TheRoddyWMS/RoddyToolLib
    git clone https://github.com/TheRoddyWMS/BatchEuphoria
    git clone https://github.com/TheRoddyWMS/Roddy
    mkdir -p Roddy/dist/plugins
    pushd Roddy/dist/plugins
    git clone https://github.com/TheRoddyWMS/Roddy-Default-Plugin.git DefaultPlugin
    git clone https://github.com/TheRoddyWMS/Roddy-Base-Plugin PluginBase
    popd

4. Import the five source repositories via "File" -> "Project Structure" -> "+" (Module pane). For import select the `build.gradle` from the specific repository.
5. Open the Gradle tasks window by clicking on the Gradle symbol on the task bar. If there is no Gradle symbol in the tool bars of IntelliJ, select "View" -> "Tool Windows" -> "Gradle".
6. Configure the composite Gradle builds by right-clicking on the gradle project.
7. Now if you go to the Gradle toolbar and select the `build` target of Roddy, RoddyToolLib, BatchEuphoria and Roddy itself will be build with Gradle.

Setting up plugins in the project
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

After these initial steps you can add your Roddy plugins to you project. We usually clone the plugin repositories into a dedicated `plugins_R3.0/` directory just beneath the root project directory (the now not so empty project that you initially created). This directory is then used for the `usePluginVersion` command-line option or in the `applicationProperties.ini`. The only exception are the `DefaultPlugin` and `PluginBase` that need to be in the `Roddy/dist/plugins` directory.

In IntelliJ then add the repository to your project as a module, ideally by directly importing the `.iml` file from the repository. Make sure that the plugin modules depends on the PluginBase, Roddy_main and maybe RoddyToolLib_main modules.

Running Roddy from within IntelliJ
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

For running Roddy with parameters from IntelliJ you an "Application" configuration with `-enableassertions -Xms4m -Xmx50m` as VM options, the path to your `Roddy/` repository as working dir and `de.dkfz.roddy.Roddy` as Main class. When debugging plugin code you should use the plugin's repository root for "Use class path of module".
