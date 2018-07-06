/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.knowledge.files;

/**
 * A file object tuple designed for (generic) job calls with 4 entries
 */
public class Tuple4<
        X extends FileObject,
        Y extends FileObject,
        Z extends FileObject,
        R extends FileObject>
        extends Tuple3<X, Y, Z> {
    public final R value3;

    public Tuple4(X value0, Y value1, Z value2, R value3) {
        super(value0, value1, value2);
        this.value3 = value3;
    }
}
