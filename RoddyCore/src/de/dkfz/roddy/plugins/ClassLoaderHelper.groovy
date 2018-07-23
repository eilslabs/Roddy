/*
 * Copyright (c) 2017 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.plugins

import de.dkfz.roddy.Roddy
import de.dkfz.roddy.execution.io.LocalExecutionHelper
import de.dkfz.roddy.knowledge.files.BaseFile
import de.dkfz.roddy.knowledge.files.FileObject
import de.dkfz.roddy.tools.LoggerWrapper
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode

import java.lang.reflect.Method

/**
 * Useful to load classes and synthetic classes.
 *
 * Created by heinold on 09.05.17.
 */
@CompileStatic
class ClassLoaderHelper {

    final static LoggerWrapper logger = LoggerWrapper.getLogger(ClassLoaderHelper.class)

    Map<PluginInfo, List<String>> classListCacheByPlugin = [:];

    Package[] _cachedRoddyPackages = null;

    Package[] getRoddyPackages() {
        if (!_cachedRoddyPackages) {
            def gcl = LibrariesFactory.instance.getGroovyClassLoader()
            Method getPackages //= gcl.class.getDeclaredMethod("getPackages")
            for (Class clc = gcl.class; clc != null; clc = clc.superclass) {
                def foundMethod = clc.getDeclaredMethods().find { Method m -> m.name == "getPackages" }
                if (!foundMethod)
                    continue;
                getPackages = foundMethod;
                break;
            }
            if (!getPackages) throw new RuntimeException("The classloader hierarchy does not contain a valid classloader with the getPackages method")

            // Make method accessible
            getPackages.setAccessible(true)
            Package[] packageList = getPackages.invoke(gcl) as Package[]
            // Invoke it and get list of all gcl loaded packages
            Package[] roddyPackages = packageList.findAll { Package p -> p.name.startsWith(Roddy.package.name) }
            // Find all Roddy packages
            _cachedRoddyPackages = roddyPackages
            return roddyPackages
        }
        return _cachedRoddyPackages
    }

    Class searchForClass(String name) {
        def groovyClassLoader = LibrariesFactory.instance.getGroovyClassLoader()
        if (name.contains(".")) {
            return groovyClassLoader.loadClass(name);
        }

        // Search synthetic classes first.
        if (LibrariesFactory.instance.synthetic.map.containsKey(name))
            return LibrariesFactory.instance.synthetic.map[name];

        // Search core classes second. Find packages of Roddy first. Search for the class in every! package.
        // This is some bad reflection, but I won't get the package information without it!
        Class foundCoreClass = null;
        for (Package p in getRoddyPackages()) {
            String className = "${p.name}.${name}"
            try {
                foundCoreClass = LibrariesFactory.instance.loadClass(className)
            } catch (ClassNotFoundException ex) {
                // Silently ignored. The class may or may not be in core. If the class can not be found, it is fine here.
            }
            if (foundCoreClass) break
            // Ignore if it is empty, we will fall back to the plugin strategy afterwards! Or search in the next package
        }

        // We found it in core, so return it.
        if (foundCoreClass)
            return foundCoreClass

        // This part of the code depends on the existence of jar on the system!
        // Java normally ships jar with it and we check jar on startup. So this should pose no problems.
        List<String> listOfClasses = []
        synchronized (LibrariesFactory.instance.loadedPlugins) {
            LibrariesFactory.instance.loadedPlugins.each {
                PluginInfo plugin ->
                    if (!classListCacheByPlugin.containsKey(plugin)) {
                        String text = LocalExecutionHelper.execute("jar tvf ${LibrariesFactory.instance.loadedJarsByPlugin[plugin]}")
                        classListCacheByPlugin[plugin] = text.readLines();
                    }
                    for (String line in classListCacheByPlugin[plugin]) {
                        if (!line.endsWith(".class")) continue;
                        String cls = line.split("[ ]")[-1][0..-7];
                        if (cls.endsWith("/" + name)) {
                            cls = cls.replace("/", ".");
                            cls = cls.replace("\\", ".");
                            synchronized (listOfClasses) {
                                listOfClasses << cls;
                            }
                        }
                    }
            }
        }

        if (listOfClasses.size() > 1) {
            logger.severe("Too many available classes, please specify fully, choosing one of the following: ")
            listOfClasses.each { logger.severe("  " + it) }
            return null;
        }
        if (listOfClasses.size() == 1) {
            return groovyClassLoader.loadClass(listOfClasses[0]);
        }
        logger.postSometimesInfo("No class found for ${name}")

        return null;
    }



    public Class loadRealOrSyntheticClass(String classOfFileObject, String baseClassOfFileObject) {
        Class<BaseFile> _cls = searchForClass(classOfFileObject);
        if (_cls == null) {
            _cls = generateSyntheticFileClassWithParentClass(classOfFileObject, baseClassOfFileObject, LibrariesFactory.getGroovyClassLoader())
            LibrariesFactory.getInstance().getSynthetic().addClass(_cls);
            logger.postSometimesInfo("Class ${classOfFileObject} could not be found, created synthetic class ${_cls.name}.");
        }
        return _cls
    }

    public Class loadRealOrSyntheticClass(String classOfFileObject, Class<FileObject> constructorClass) {
        return loadRealOrSyntheticClass(classOfFileObject, constructorClass.name);
    }

    @groovy.transform.CompileStatic(TypeCheckingMode.SKIP)
    public static Class generateSyntheticFileClassWithParentClass(String syntheticClassName, String constructorClassName, GroovyClassLoader classLoader = null) {
        String syntheticFileClass =
                """
                package ${SyntheticPluginInfo.SYNTHETIC_PACKAGE}

                public class ${syntheticClassName} extends de.dkfz.roddy.knowledge.files.BaseFile {

                    public ${syntheticClassName}(de.dkfz.roddy.knowledge.files.BaseFile.ConstructionHelperForBaseFiles helper) {
                        super(helper);
                    }
                }
            """
        GroovyClassLoader groovyClassLoader = classLoader ?: new GroovyClassLoader();
        Class _classID = (Class<BaseFile>) groovyClassLoader.parseClass(syntheticFileClass);
        return _classID
    }
}
