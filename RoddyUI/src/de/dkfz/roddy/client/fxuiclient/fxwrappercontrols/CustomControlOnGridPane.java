/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.client.fxuiclient.fxwrappercontrols;

import javafx.scene.layout.GridPane;

/**
 */
public abstract class CustomControlOnGridPane extends GridPane {

    protected CustomControlOnGridPane() {
        CustomControlHelper.loadFXML(this);
//        String className = getClass().getSimpleName();
//        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource(className + ".fxml"));
//        fxmlLoader.setRoot(this);
//        fxmlLoader.setController(this);
////
//        try {
//            fxmlLoader.load();
//        } catch (Exception ex) {
//            throw new RuntimeException(ex);
//        }
    }
}
