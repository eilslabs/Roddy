/*
 * Copyright (c) 2018 German Cancer Research Center (DKFZ).
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.knowledge.files;

/**
 * A file object tuple designed for (generic) job calls with 8 entries
 */
public class Tuple8<
        X extends FileObject,
        Y extends FileObject,
        Z extends FileObject,
        R extends FileObject,
        Q extends FileObject,
        W extends FileObject,
        S extends FileObject,
        T extends FileObject>
        extends Tuple7<X, Y, Z, R, Q, W, S> {
    private final T value7;

    public Tuple8(X value0, Y value1, Z value2, R value3, Q value4, W value5, S value6, T value7) {
        super(value0, value1, value2, value3, value4, value5, value6);
        this.value7 = value7;
    }
}

