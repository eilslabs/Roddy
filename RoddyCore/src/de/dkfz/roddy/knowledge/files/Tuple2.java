/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.knowledge.files;

/**
 * A file object tuple designed for (generic) job calls with 2 entries
 */
public class Tuple2<
        X extends FileObject,
        Y extends FileObject>
        extends AbstractFileObjectTuple {
    public final X value0;
    public final Y value1;

    public Tuple2(X value0, Y value1) {
        super(value0.getExecutionContext());
        this.value0 = value0;
        this.value1 = value1;
    }
}
