package com.pms.util;

import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.Tooltip;
import javafx.util.Callback;

public class UIUtil {

    /**
     * Globally enables pressing ENTER to fire the currently focused button on the given Scene.
     * This overrides JavaFX's default behavior where ENTER only fires the default button.
     */
    public static void enableEnterToClick(javafx.scene.Scene scene) {
        scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ENTER) {
                javafx.scene.Node focusOwner = scene.getFocusOwner();
                if (focusOwner instanceof javafx.scene.control.ButtonBase) {
                    ((javafx.scene.control.ButtonBase) focusOwner).fire();
                    event.consume();
                }
            }
        });
    }

    /**
     * Globally enables pressing ENTER to fire the currently focused button on a Dialog/Alert.
     */
    public static void enableEnterToClick(javafx.scene.control.Dialog<?> dialog) {
        javafx.scene.control.DialogPane pane = dialog.getDialogPane();
        pane.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ENTER) {
                if (pane.getScene() != null) {
                    javafx.scene.Node focusOwner = pane.getScene().getFocusOwner();
                    if (focusOwner instanceof javafx.scene.control.ButtonBase) {
                        ((javafx.scene.control.ButtonBase) focusOwner).fire();
                        event.consume();
                    }
                }
            }
        });
    }

    /**
     * Applies a standard text-based cell factory to the given columns.
     * The factory will display the string representation of the item and attach a Tooltip
     * so that if the text is truncated in the column, the user can still hover to read the full text.
     */
    @SafeVarargs
    public static <S, T> void setTooltipCellFactory(TableColumn<S, T>... columns) {
        for (TableColumn<S, T> column : columns) {
            column.setCellFactory(new Callback<TableColumn<S, T>, TableCell<S, T>>() {
                @Override
                public TableCell<S, T> call(TableColumn<S, T> param) {
                    return new TableCell<S, T>() {
                        private final Tooltip tooltip = new Tooltip();
                        @Override
                        protected void updateItem(T item, boolean empty) {
                            super.updateItem(item, empty);
                            if (empty || item == null) {
                                setText(null);
                                setGraphic(null);
                                setTooltip(null);
                            } else {
                                String text = item.toString();
                                setText(text);
                                tooltip.setText(text);
                                setTooltip(tooltip);
                            }
                        }
                    };
                }
            });
        }
    }
}
