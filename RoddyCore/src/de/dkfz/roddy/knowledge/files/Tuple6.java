/*
 * Copyright (c) 2018 German Cancer Research Center (DKFZ).
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.knowledge.files;

/**
 * A file object tuple designed for (generic) job calls with 6 entries
 */
public class Tuple6<
        X extends FileObject,
        Y extends FileObject,
        Z extends FileObject,
        R extends FileObject,
        Q extends FileObject,
        W extends FileObject>
        extends Tuple5<X, Y, Z, R, Q> {
    private final W value5;

    public Tuple6(X value0, Y value1, Z value2, R value3, Q value4, W value5) {
        super(value0, value1, value2, value3, value4);
        this.value5 = value5;
    }
}
