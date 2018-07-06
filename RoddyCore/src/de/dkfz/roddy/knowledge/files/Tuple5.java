/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.knowledge.files;

/**
 * A file object tuple designed for (generic) job calls with 5 entries
 */
public class Tuple5<
        X extends FileObject,
        Y extends FileObject,
        Z extends FileObject,
        R extends FileObject,
        Q extends FileObject>
        extends Tuple4<X, Y, Z, R> {
    public final Q value4;

    public Tuple5(X value0, Y value1, Z value2, R value3, Q value4) {
        super(value0, value1, value2, value3);
        this.value4 = value4;
    }
}
