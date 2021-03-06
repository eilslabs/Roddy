/*
 * Copyright (c) 2016 German Cancer Research Center (Deutsches Krebsforschungszentrum, DKFZ).
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/TheRoddyWMS/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.core;

/**
 * The base class for roddy libraries
 */
public class LibraryEntry {
    private String name;
    private String description;
    private ClassLoader classLoader;


    public LibraryEntry(String name, String description, ClassLoader classLoader) {
        this.name = name;
        this.description = description;
        this.classLoader = classLoader;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }
}
