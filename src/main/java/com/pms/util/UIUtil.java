package com.pms.util;

import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.Tooltip;
import javafx.util.Callback;

public class UIUtil {

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
