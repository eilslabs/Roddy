/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.tools;

import java.util.Objects;

/**
 * A result tuple for two results
 */
public class Tuple2<X, Y> {
    public final X x;
    public final Y y;

    public Tuple2(X x, Y y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Tuple2<?, ?> tuple2 = (Tuple2<?, ?>) o;
        return Objects.equals(x, tuple2.x) &&
                Objects.equals(y, tuple2.y);
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }
}
