Developers guide
----------------

Code guidelines
~~~~~~~~~~~~~~~
Roddy has no specific development or code style.
Here, we try to collect topics and settings, where we think that they might be important.

Code Format
^^^^^^^^^^^
We are mainly using IntelliJ IDEA and use the default settings for code formatting.

Collections as return types
^^^^^^^^^^^^^^^^^^^^^^^^^^^

By default, we do not return a copy (neither shallow, nor deep) of the Collection object. Be careful, not to modify the collection, if you do not change the contents of the object.

Keep it clean and simple
^^^^^^^^^^^^^^^^^^^^^^^^

We do not really enforce rules, but we try to keep things simple and readable.

- If a code block is not readable, try to make a method out of it.

- Reduce size and complexity of methods.

- Your code should be self explanatory. If it is not, try to make it that way.

We know, that we have a lot of issues in our codebase, but we listen to every improvement suggestion and constantly try to improve things.

Development model
~~~~~~~~~~~~~~~~~

For development we follow the standard git flow with feature branches
getting merged into the develop branch and merge into master branch upon
release. Currently we are discussing if we remove the development branch.
Roddys versioning system makes it easy to go back to previous versions.

Settings for Groovy classes
~~~~~~~~~~~~~~~~~~~~~~~~~~~

We will not accept Groovy classes without the @CompileStatic annotation. If you are in the rare situation that you need dynamic dispatch on more than
the object (this) itself, you can mark the affected methods with @CompileDynamic.

Roddy versioning scheme
~~~~~~~~~~~~~~~~~~~~~~~

.. Note::

    NOTE: The versioning scheme is under revision and may change in the future.
    We consider using a versioning plugin for Gradle which calculates the
    proper version from the commit messages and tags. E.g.

    .. code-block:: Bash

       ./gradlew printVersion


Roddy version numbers consist of three entries: $major.$minor.$build.
The build number is also sometimes called patch number. Release numbers
are added to the repository.

The $major entry is used to mark huge changes in the Roddy core
functions. Backward compatibility is most likely not granted and Roddy
will not execute plugins built with different $major versions.

The $minor entry marks smaller changes which might affect your plugin.
Backward compatibility might be affected and Roddy will warn you when a
plugin was built with another $minor version. Only decrease this value,
when you increase the $major version. Likewise, you should only decrease
the build number, if you increase either the $major or $minor version.

The combination of $major.$minor can somehow be seen as the API level
of Roddy. For a “full API level” the plugin versions of “PluginBase” and
“DefaultPlugin” need to be considered as well.

Basically the same versioning convention applies to the plugins, with
some extension. If we have to maintain old plugin version with bugfixes or
feature backports for specific projects in production, the we usually
release version numbers with an additional "-$revision” suffix.

Importantly, if Roddy sees multiple plugin directories for the same plugin
only differing in the revision number, Roddy may automatically upgrade
to the version with the largest revision number! So be sure only to use
revisions for semantically equivalent plugin versions (e.g. minor bugfixes).
Every change that affects the output of you plugin in a way that, e.g., the
results are not comparable with previous versions anymore, should receive
at least a build-number increase.

Below, you’ll find, how things are (or are supposed to be) handled in
git.

How to get started
~~~~~~~~~~~~~~~~~~

Have you already checked out the :doc:`../installationGuide`?
If not, please do so and do not forget to use the developer
settings instead of the user settings.

The first thing you'll need is a working Java 8+ installation and a Groovy installation (e.g. 2.4.9+).

Repository Structure
^^^^^^^^^^^^^^^^^^^^

::

    /
    roddy.sh                                          Top-level script
    ./RoddyCore/                                      The core project
        buildversion.txt                              Current buildversion
        Java/Groovy sources
    docs/                                             Documentation
    dist/
        bin/
            develop/
            $major.$minor.$build/
        plugins/
            DefaultPlugin
            PluginBase
        plugins_R$major.$minor/                       Plugin directory for specific Roddy versions
        runtimeDevel/
            groovy-$major.$minor.$build
            jdk, jre, jdk_$major.$minor._$revision

The runtimeDevel/ directory is only required for Roddy up to version 2.3.

Compiling Roddy
~~~~~~~~~~~~~~~

The preferred way to build Roddy is via Gradle. Please run

::

    ./gradlew build

This will download all necessary dependencies into the dist/bin/develop/lib directory and create the Roddy.jar in dist/bin/develop.

If you want to develop Roddy and additionally want to work on the RoddyToolLib or BatchEuphoria you can clone these libraries into neighbouring
directories and execute gradle with composite build parameters

::

    ./gradlew build --include-build ../RoddyToolLib/ --include-build ../BatchEuphoria/

Note that if you are using a proxy, additional configuration is necessary for gradle. Please add the folling lines with the appropriate values for
your environment to the file "~/.gradle/gradle.properties":

::

    systemProp.http.proxyHost=
    systemProp.http.proxyPort=
    systemProp.https.proxyHost=
    systemProp.https.proxyPort=

Hosts are specified without the "http[s]://" prefix.


Packing Roddy
~~~~~~~~~~~~~

The packaging of Roddy is done using the Gradle distribution plugin. There is two packaging targets

::

    ./gradlew roddyDistZip roddyEnvironmentDistZip

The distribution zips are put in the "gradleBuild/distribution" directory.

The "roddyEnvironmentDistZip" target will produce a zip with the top-level directory containing the roddy.sh and the essential "dist/bin"
subdirectories.

The content of the "roddyDistZip" produces a release zip that is supposed to be extracted into a directory called "dist/bin/$major.$minor.$build".

Building the documentation
~~~~~~~~~~~~~~~~~~~~~~~~~~

The Sphinx-based documentation is located in the "docs/" directory and build with

::

    ./gradlew sphinx

The output is then produced in "gradleBuild/site" for inspection in the browser.

Further important notes
~~~~~~~~~~~~~~~~~~~~~~~

The "roddyDistZip" target will produce a zip with the content of the "dist/bin/develop" directory. For deployment you should unzip it in that
directory and copy its content into an appropriately named "dist/bin/" subdirectory, e.g. "develop" for testing purposes or the version number,
such as 3.1.0.
