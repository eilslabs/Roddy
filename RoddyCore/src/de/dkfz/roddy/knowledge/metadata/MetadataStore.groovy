/*
 * Copyright (c) 2018 German Cancer Research Center (DKFZ).
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */
package de.dkfz.roddy.knowledge.metadata

import de.dkfz.roddy.execution.io.BaseMetadataTable

interface MetadataStore<T extends BaseMetadataTable> {

    abstract T load()

}
