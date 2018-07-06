/*
 * Copyright (c) 2017 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.tools

import org.junit.Test

/**
 * Created by heinold on 26.04.17.
 */
class CollectionHelperMethodsTest extends GroovyTestCase {

    @Test
    void testIntersects() {
        assert CollectionHelperMethods.intersects([1, 2, 3], [3, 4, 5])
    }

    @Test
    void testIntersect() {
        assert CollectionHelperMethods.intersect([1, 2, 3], [3, 4, 5]) == [3]
    }

    @Test
    void testIntersectList() {
        def intersection = CollectionHelperMethods.intersectList([1, 2, 3], [3, 4, 5])
        assert intersection == [3]
        assert intersection instanceof List
    }
}
