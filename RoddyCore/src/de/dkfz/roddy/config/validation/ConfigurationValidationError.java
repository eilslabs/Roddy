/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.config.validation;

import de.dkfz.roddy.config.Configuration;
import de.dkfz.roddy.config.ConfigurationValue;
import de.dkfz.roddy.config.EnumerationValue;

/**
 * A basic class for the storage of configuration validation errors.
 * TODO Unify ValidationError and ConfigurationValidationError
 * TODO Rethink validation error system
 */
public abstract class ConfigurationValidationError {

    public abstract String getErrorMessage();


    public static class ConfigurationValueCombinationMismatch extends ConfigurationValidationError {

        public static final String ERROR_STRING = "Could not validate the configuration value combination [%s] in configuration [%s]";

        private Configuration configuration;
        private final String validationRuleGroupID;

        public ConfigurationValueCombinationMismatch(Configuration configuration, String validationRuleGroupID) {
            this.configuration = configuration;
            this.validationRuleGroupID = validationRuleGroupID;
        }

        @Override
        public String getErrorMessage() {
            return String.format(ERROR_STRING, configuration.getID(), validationRuleGroupID);
        }
    }

    public static class DataTypeValidationError extends ConfigurationValidationError {
        public static final String ERROR_STRING = "Could not validate data type [%s] for configuration value [%s] with value [%s]";

        private final ConfigurationValue configurationValue;

        private final EnumerationValue dataType;

        private final String value;

        private final Exception exception;

        public DataTypeValidationError(ConfigurationValue configurationValue, Exception exception) {
            this.configurationValue = configurationValue;
            dataType = configurationValue.getEnumerationValueType();
            value = configurationValue.getValue();
            this.exception = exception;
        }

        @Override
        public String getErrorMessage() {
            return String.format(ERROR_STRING, dataType.getId(), configurationValue.getID(), value);
        }
    }
}
