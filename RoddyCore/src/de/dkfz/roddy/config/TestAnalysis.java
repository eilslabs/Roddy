/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.config;

import de.dkfz.roddy.core.Analysis;
import de.dkfz.roddy.core.Project;
import de.dkfz.roddy.core.Workflow;

/**
 * Created by michael on 28.10.14.
 */
public class TestAnalysis extends Analysis {
    public TestAnalysis(AnalysisConfiguration configuration) {
        super("TestAnalysis", null, null, configuration);
    }
}
