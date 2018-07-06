/*
 * Copyright (c) 2018 German Cancer Research Center (DKFZ).
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.knowledge.files;

/**
 * A file object tuple designed for (generic) job calls with 10 entries
 */
public class Tuple10<
        X extends FileObject,
        Y extends FileObject,
        Z extends FileObject,
        R extends FileObject,
        Q extends FileObject,
        W extends FileObject,
        S extends FileObject,
        T extends FileObject,
        U extends FileObject,
        V extends FileObject>
        extends Tuple9<X, Y, Z, R, Q, W, S, T, U> {
    private final V value9;

    public Tuple10(X value0, Y value1, Z value2, R value3, Q value4, W value5, S value6, T value7, U value8, V value9) {
        super(value0, value1, value2, value3, value4, value5, value6, value7, value8);
        this.value9 = value9;
    }
}

