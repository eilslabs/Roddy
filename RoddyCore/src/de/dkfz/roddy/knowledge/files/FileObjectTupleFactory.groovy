/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.knowledge.files
/**
 * Created by michael on 18.06.14.
 */
@groovy.transform.CompileStatic
public class FileObjectTupleFactory {

    public static final int MIN_SIZE = 2;

    public static final int MAX_SIZE = 10;

    public static <X extends FileObject, Y extends FileObject> AbstractFileObjectTuple createTuple(FileObject... fileObjects) {
        return createTuple(fileObjects.toList());
    }

    public static <X extends FileObject, Y extends FileObject> AbstractFileObjectTuple createTuple(List<FileObject> fileObjects) {
        if (!isValidSize(fileObjects.size()))
            throw new RuntimeException("Wrong count of tuple entries, it is not ${MIN_SIZE} <= ${fileObjects.size()} <= ${MAX_SIZE}");

        if (fileObjects.size() == 2) {
            return  new Tuple2<> (fileObjects[0], fileObjects[1]);
        } else if (fileObjects.size() == 3) {
            return  new Tuple3<> (fileObjects[0], fileObjects[1], fileObjects[2]);
        } else if (fileObjects.size() == 4) {
            return  new Tuple4<> (fileObjects[0], fileObjects[1], fileObjects[2], fileObjects[3]);
        } else if (fileObjects.size() == 5) {
            return  new Tuple5<> (fileObjects[0], fileObjects[1], fileObjects[2], fileObjects[3], fileObjects[4]);
        } else if (fileObjects.size() == 6) {
            return  new Tuple6<> (fileObjects[0], fileObjects[1], fileObjects[2], fileObjects[3], fileObjects[4], fileObjects[5]);
        } else if (fileObjects.size() == 7) {
            return  new Tuple7<> (fileObjects[0], fileObjects[1], fileObjects[2], fileObjects[3], fileObjects[4], fileObjects[5], fileObjects[6]);
        } else if (fileObjects.size() == 8) {
            return  new Tuple8<> (fileObjects[0], fileObjects[1], fileObjects[2], fileObjects[3], fileObjects[4], fileObjects[5], fileObjects[6], fileObjects[7]);
        } else if (fileObjects.size() == 9) {
            return  new Tuple9<> (fileObjects[0], fileObjects[1], fileObjects[2], fileObjects[3], fileObjects[4], fileObjects[5], fileObjects[6], fileObjects[7], fileObjects[8]);
        } else if (fileObjects.size() == 10) {
            return new Tuple10<> (fileObjects[0], fileObjects[1], fileObjects[2], fileObjects[3], fileObjects[4], fileObjects[5], fileObjects[6], fileObjects[7], fileObjects[8], fileObjects[9]);
        } else if (fileObjects.size() == 13) {
            return new Tuple13<> (fileObjects[0], fileObjects[1], fileObjects[2], fileObjects[3], fileObjects[4], fileObjects[5], fileObjects[6], fileObjects[7], fileObjects[8], fileObjects[9], fileObjects[10], fileObjects[11], fileObjects[12]
            );
        }
    }

    public static final boolean isValidSize(int size) {
        return size == 13 || size >= MIN_SIZE && size <= MAX_SIZE;
    }
}
