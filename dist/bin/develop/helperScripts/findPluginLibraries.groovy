import java.nio.file.AccessDeniedException

/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */


List<String> parsePluginDirectoriesParameter(String roddyDirectory, String parameterValue) {
	// Note: The plugins in the dist/plugins directory will be found first!
	List<String> pluginLines = ["${roddyDirectory}/dist/plugins"] // Add the Roddy directory
	pluginLines.addAll(parameterValue.split("[=]")[1].split("[,:]")) // Add lines from ini file
	return pluginLines.unique().findAll { it != "" } // Filter the list to get rid of doublettes.
}

List<String> extractDependenciesFromBuildinfoFile(String pluginDir) {
	File pluginPath = new File(pluginDir)
	if (!pluginPath.canExecute())
		throw new FileNotFoundException("Cannot access directory ${pluginPath.absolutePath}")
	File buildInfo = new File(pluginPath, "buildinfo.txt")
	if (!buildInfo.canRead())
		throw new AccessDeniedException("Cannot access '${buildInfo.absolutePath}'")
	return (buildInfo).
			readLines().
			findAll {
				it.startsWith("dependson")
			}.collect { it.split("[=]")[1] }
}

/** Given a plugin name with version, if the version is not 'develop' then the plugin JAR may be called $id_$version.jar or $id.jar. If both are
 *  present, then use the versioned JAR for the versioned directory, but warn there are both JARs.
 *  If the searched plugin version is 'develop', the jar must be called $id.jar.
 *  This allows to compile against plugin directories renamed to versioned directories, but still containing older jars compiled while they were
 *  'develop'.
 */
List<File> findPossibleJarsForDependency(List<String> pluginDirectories, String pluginNameAndVersion) {
	String pluginDirname = pluginNameAndVersion.replace(":", "_")
	def (id, version) = pluginNameAndVersion.split("[:]")
	pluginDirname = pluginDirname.replace("_develop", "")

	return pluginDirectories.collect { String pluginsDir ->
		def searchPath = new File(pluginsDir, pluginDirname)
		def jar = new File(searchPath, "${id}.jar")
		def versionedJar = new File(searchPath, "${id}_${version}.jar")
		if (version != "develop") {
			if (jar.canRead() && versionedJar.canRead())  {
				System.err.println("WARNING: Directory '${searchPath.absolutePath}' contains two the readable JARs '${jar.name}' and '${versionedJar.name}'. Using versioned JAR.")
				[versionedJar]
			} else if (jar.canRead()) {
				[jar]
			} else if (versionedJar.canRead()) {
				[versionedJar]
			} else {
				[]
			}
		} else
			[jar]
	}.flatten()
}

List<File> findPossibleJarsForDependencies(List<String> pluginDirectories, List<String> pluginsAndVersions) {
	return pluginsAndVersions.collect { findPossibleJarsForDependency(pluginDirectories, it) }.flatten()
}

List<File> collectAllJars(List<String> pluginSearchDirectories, String pluginDirectory) {
	List<String> dependencies = extractDependenciesFromBuildinfoFile(pluginDirectory)
	List<File> possibleJars = findPossibleJarsForDependencies(pluginSearchDirectories, dependencies)

	def jars = possibleJars.findAll() { it.exists()  }

	// Now search recursively for the dependencies of the newly found JARs.
	return jars.collect {
		it.parent.toString()
	}.collect {
		jars + collectAllJars(pluginSearchDirectories, it)
	}.flatten().unique()
}


// This script is used to find the JARs on which a query plugin depends. Multiple plugin directories, configured in, e.g., the
// applicationProperties.ini, are being searched. The dependency information that is used is from the buildinfo.txt files.
//
// The script needs several parameters:
// - The plugin line from the configuration file starting with "pluginDirectories="
// - The roddy binary directory
// - The working directory for the query plugin

if (args.size() != 3) {
	System.err.println('Given a list of pluginDirectories (search paths), a roddyDirectory, and a queryPluginDirectory to find all plugin jars in the plugins dependencies.')
	System.err.println('Usage: findPluginLibraries pluginDirectories=pluginDirectoryA,pluginDirectoryB roddyDirectory/ queryPluginDirectory/')
	System.exit(1)
}
def pluginDirectoryParameter = args[0]
def roddyDirectory = args[1]
def queryPluginDirectory = args[2]

if (!pluginDirectoryParameter.startsWith("pluginDirectories=")) {
	System.err.println("First parameter should start with 'pluginDirectories='")
	System.exit(1)
}

List<String> pluginDirectories = parsePluginDirectoriesParameter(roddyDirectory, pluginDirectoryParameter)
List<File> jars = collectAllJars(pluginDirectories, queryPluginDirectory).reverse()
System.out.println(jars.join(":"))


