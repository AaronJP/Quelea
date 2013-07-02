/*
 * This file is part of Quelea, free projection software for churches.
 * 
 * 
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.quelea.windows.help;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.quelea.services.languages.LabelGrabber;
import org.quelea.services.utils.LoggerUtils;
import org.quelea.services.utils.QueleaProperties;

/**
 * Quelea's about Dialog, displaying general features about the program and the
 * debug log location (so we can point any users here who may be looking for
 * it.)
 * <p/>
 * @author Michael
 */
public class AboutDialog extends Stage {

    /**
     * Create a new about dialog.
     */
    public AboutDialog() {
        initModality(Modality.APPLICATION_MODAL);
        initStyle(StageStyle.UTILITY);
        setResizable(false);
        setTitle(LabelGrabber.INSTANCE.getLabel("help.about.title"));

        BorderPane newLayout = new BorderPane();
        ImageView logo = new ImageView(new Image("file:icons/full logo.png"));
        BorderPane.setAlignment(logo, Pos.CENTER);
        newLayout.setTop(logo);

        VBox subLayout = new VBox();
        Text headingText = new Text(LabelGrabber.INSTANCE.getLabel("help.about.version") + " " + QueleaProperties.VERSION.getFullVersionString());
        headingText.setFont(Font.font("Arial", FontWeight.BOLD, FontPosture.REGULAR, 20));
        subLayout.getChildren().add(headingText);
        subLayout.getChildren().add(new Text(" "));
        subLayout.getChildren().add(new Text(LabelGrabber.INSTANCE.getLabel("help.about.line1")));
        subLayout.getChildren().add(new Text(LabelGrabber.INSTANCE.getLabel("help.about.line2")));
        subLayout.getChildren().add(new Text(" "));
        subLayout.getChildren().add(new Text("Java: " + System.getProperty("java.version")));
        subLayout.getChildren().add(new Text(LabelGrabber.INSTANCE.getLabel("debug.location") + ": " + LoggerUtils.getHandlerFileLocation()));
        Button closeButton = new Button(LabelGrabber.INSTANCE.getLabel("help.about.close"));
        closeButton.setOnAction(new EventHandler<ActionEvent>() {

            @Override
            public void handle(ActionEvent t) {
                hide();
            }
        });
        newLayout.setCenter(subLayout);
        BorderPane.setMargin(subLayout, new Insets(10));
        BorderPane.setAlignment(closeButton, Pos.CENTER);
        BorderPane.setMargin(closeButton, new Insets(10));
        newLayout.setBottom(closeButton);

        setScene(new Scene(newLayout));
    }
}
