package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.sparrow.AppController;
import javafx.application.Platform;
import javafx.beans.NamedArg;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;

public class TextAreaDialog extends Dialog<String> {
    private final TextArea textArea;
    private final String defaultValue;

    public TextAreaDialog() {
        this("");
    }

    public TextAreaDialog(@NamedArg("defaultValue") String defaultValue) {
        final DialogPane dialogPane = getDialogPane();

        HBox hbox = new HBox();
        this.textArea = new TextArea(defaultValue);
        this.textArea.setMaxWidth(Double.MAX_VALUE);
        this.textArea.setWrapText(true);
        hbox.getChildren().add(textArea);

        this.defaultValue = defaultValue;

        dialogPane.setContent(hbox);
        dialogPane.getStylesheets().add(AppController.class.getResource("general.css").toExternalForm());
        AppController.setStageIcon(dialogPane.getScene().getWindow());

        dialogPane.getStyleClass().add("text-input-dialog");
        dialogPane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Platform.runLater(textArea::requestFocus);

        setResultConverter((dialogButton) -> {
            ButtonBar.ButtonData data = dialogButton == null ? null : dialogButton.getButtonData();
            return data == ButtonBar.ButtonData.OK_DONE ? textArea.getText() : null;
        });
    }

    public final TextArea getEditor() {
        return textArea;
    }

    public final String getDefaultValue() {
        return defaultValue;
    }
}
