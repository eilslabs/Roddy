/*
 * Copyright (c) 2018 German Cancer Research Center (DKFZ).
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */
package de.dkfz.roddy.knowledge.metadata

import de.dkfz.roddy.core.DataSet

class BaseMetadataAccessor {

    Metadata metadata

    BaseMetadataAccessor(Metadata metadata) {
        this.metadata = metadata
    }

    List<DataSet> getDataSets() {
        metadata.lis
    }

}
