/*
 * Copyright (c) 2018 German Cancer Research Center (DKFZ).
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.knowledge.files;

/**
 * A file object tuple designed for (generic) job calls with 9 entries
 */
public class Tuple9<
        X extends FileObject,
        Y extends FileObject,
        Z extends FileObject,
        R extends FileObject,
        Q extends FileObject,
        W extends FileObject,
        S extends FileObject,
        T extends FileObject,
        U extends FileObject>
        extends Tuple8<X, Y, Z, R, Q, W, S, T> {
    private final U value8;

    public Tuple9(X value0, Y value1, Z value2, R value3, Q value4, W value5, S value6, T value7, U value8) {
        super(value0, value1, value2, value3, value4, value5, value6, value7);
        this.value8 = value8;
    }
}

