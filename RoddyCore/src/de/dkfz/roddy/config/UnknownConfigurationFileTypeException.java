/*
 * Copyright (c) 2017 German Cancer Research Center (Deutsches Krebsforschungszentrum, DKFZ).
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/TheRoddyWMS/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.config;

/**
 * Created by heinold on 21.02.17.
 */
public class UnknownConfigurationFileTypeException extends Exception {
    public UnknownConfigurationFileTypeException(String message) {
        super(message);
    }
}
