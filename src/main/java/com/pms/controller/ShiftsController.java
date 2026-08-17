package com.pms.controller;

import com.pms.dao.ShiftDAO;
import com.pms.model.Shift;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ShiftsController {

    private static final Logger logger = LoggerFactory.getLogger(ShiftsController.class);

    @FXML private TableView<Shift> shiftsTable;
    @FXML private TableColumn<Shift, String> colCashier;
    @FXML private TableColumn<Shift, String> colStart;
    @FXML private TableColumn<Shift, String> colEnd;
    @FXML private TableColumn<Shift, String> colStartingCash;
    @FXML private TableColumn<Shift, String> colExpectedCash;
    @FXML private TableColumn<Shift, String> colDeclaredCash;
    @FXML private TableColumn<Shift, String> colDiscrepancy;
    @FXML private TableColumn<Shift, String> colStatus;
    @FXML private TableColumn<Shift, Shift> colActions;
    
    @FXML private javafx.scene.control.Pagination pagination;

    private static final int ITEMS_PER_PAGE = 25;
    private final ShiftDAO shiftDAO = new ShiftDAO();
    private final ObservableList<Shift> tableData = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupTableColumns();
        pagination.setPageFactory(this::createPage);
        refreshData();
    }
    
    private javafx.scene.Node createPage(int pageIndex) {
        int fromIndex = pageIndex * ITEMS_PER_PAGE;
        int toIndex = Math.min(fromIndex + ITEMS_PER_PAGE, tableData.size());
        
        if (fromIndex <= toIndex && fromIndex < tableData.size()) {
            shiftsTable.setItems(FXCollections.observableArrayList(tableData.subList(fromIndex, toIndex)));
        } else {
            shiftsTable.setItems(FXCollections.observableArrayList());
        }
        
        return new javafx.scene.layout.VBox();
    }

    private void setupTableColumns() {
        colCashier.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCashierName()));
        colStart.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getStartTime()));
        
        colEnd.setCellValueFactory(data -> {
            String end = data.getValue().getEndTime();
            return new SimpleStringProperty(end == null ? "Ongoing" : end);
        });

        colStartingCash.setCellValueFactory(data -> new SimpleStringProperty(
            com.pms.util.CurrencyUtil.format(data.getValue().getStartingCash())
        ));
        
        colExpectedCash.setCellValueFactory(data -> {
            if ("ACTIVE".equals(data.getValue().getStatus())) return new SimpleStringProperty("-");
            return new SimpleStringProperty(com.pms.util.CurrencyUtil.format(data.getValue().getExpectedEndingCash()));
        });
        
        colDeclaredCash.setCellValueFactory(data -> {
            if ("ACTIVE".equals(data.getValue().getStatus())) return new SimpleStringProperty("-");
            return new SimpleStringProperty(com.pms.util.CurrencyUtil.format(data.getValue().getDeclaredEndingCash()));
        });
        
        colDiscrepancy.setCellValueFactory(data -> {
            if ("ACTIVE".equals(data.getValue().getStatus())) return new SimpleStringProperty("-");
            return new SimpleStringProperty(com.pms.util.CurrencyUtil.format(data.getValue().getDiscrepancy()));
        });
        
        colDiscrepancy.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (!item.equals("-") && !item.equals("$0.00") && !item.equals("$-0.00")) {
                        setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });
        
        colStatus.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getStatus()));
        
        colStatus.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    Shift s = (Shift) getTableRow().getItem();
                    
                    String badgeText = item; // default "ACTIVE" or "CLOSED"
                    String badgeStyle = "-fx-padding: 4 8; -fx-background-radius: 12; -fx-font-size: 11px; -fx-font-weight: bold; ";
                    
                    if ("ACTIVE".equals(item)) {
                        badgeStyle += "-fx-background-color: #dbeafe; -fx-text-fill: #1d4ed8;";
                    } else if (s != null && "CLOSED".equals(item)) {
                        if (Math.abs(s.getDiscrepancy()) > 0.01) {
                            if (s.isDiscrepancyResolved()) {
                                badgeText = "RESOLVED";
                                badgeStyle += "-fx-background-color: #d1fae5; -fx-text-fill: #065f46;";
                            } else {
                                badgeText = "UNRESOLVED";
                                badgeStyle += "-fx-background-color: #fee2e2; -fx-text-fill: #991b1b;";
                            }
                        } else {
                            badgeStyle += "-fx-background-color: #f3f4f6; -fx-text-fill: #374151;";
                        }
                    }
                    
                    Label badge = new Label(badgeText);
                    badge.setStyle(badgeStyle);
                    setGraphic(badge);
                }
            }
        });

        colActions.setCellValueFactory(data -> new javafx.beans.property.SimpleObjectProperty<>(data.getValue()));
        colActions.setCellFactory(tc -> new TableCell<>() {
            private final javafx.scene.control.Button viewBtn = new javafx.scene.control.Button("View");

            {
                viewBtn.setOnAction(e -> {
                    Shift s = getItem();
                    if (s != null) handleViewShift(s);
                });
            }

            @Override
            protected void updateItem(Shift s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) {
                    setGraphic(null);
                } else {
                    setGraphic(viewBtn);
                }
            }
        });

        shiftsTable.setItems(tableData);
    }

    private void refreshData() {
        new Thread(() -> {
            try {
                List<Shift> shifts = shiftDAO.getAllShifts();
                Platform.runLater(() -> {
                    tableData.setAll(shifts);
                    int pageCount = (int) Math.ceil((double) shifts.size() / ITEMS_PER_PAGE);
                    pagination.setPageCount(pageCount == 0 ? 1 : pageCount);
                    createPage(pagination.getCurrentPageIndex());
                });
            } catch (Exception e) {
                logger.error("Failed to load shifts", e);
            }
        }).start();
    }
    
    private void handleViewShift(Shift s) {
        javafx.scene.control.Dialog<Void> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Shift Details");
        dialog.setHeaderText("Shift by " + s.getCashierName() + " (" + s.getStatus() + ")");
        
        if (shiftsTable.getScene() != null && shiftsTable.getScene().getWindow() != null) {
            dialog.initOwner(shiftsTable.getScene().getWindow());
        }
        
        dialog.getDialogPane().getButtonTypes().add(javafx.scene.control.ButtonType.CLOSE);
        
        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(16);
        content.setStyle("-fx-padding: 10;");
        content.setPrefWidth(400);
        
        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(15);
        grid.setVgap(10);
        
        grid.add(new Label("Start Time:"), 0, 0);
        grid.add(new Label(s.getStartTime()), 1, 0);
        grid.add(new Label("End Time:"), 0, 1);
        grid.add(new Label(s.getEndTime() == null ? "Ongoing" : s.getEndTime()), 1, 1);
        
        grid.add(new Label("Starting Cash:"), 0, 2);
        grid.add(new Label(com.pms.util.CurrencyUtil.format(s.getStartingCash())), 1, 2);
        grid.add(new Label("Cash Sales:"), 0, 3);
        grid.add(new Label(com.pms.util.CurrencyUtil.format(s.getCashSales())), 1, 3);
        
        if ("CLOSED".equals(s.getStatus())) {
            grid.add(new Label("Expected End Cash:"), 0, 4);
            grid.add(new Label(com.pms.util.CurrencyUtil.format(s.getExpectedEndingCash())), 1, 4);
            grid.add(new Label("Declared End Cash:"), 0, 5);
            grid.add(new Label(com.pms.util.CurrencyUtil.format(s.getDeclaredEndingCash())), 1, 5);
            
            Label discLbl = new Label("Discrepancy:");
            Label discVal = new Label(com.pms.util.CurrencyUtil.format(s.getDiscrepancy()));
            if (Math.abs(s.getDiscrepancy()) > 0.01) {
                discVal.setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold;");
            }
            grid.add(discLbl, 0, 6);
            grid.add(discVal, 1, 6);
        }
        
        content.getChildren().add(grid);
        
        if (s.getNotes() != null && !s.getNotes().isBlank()) {
            javafx.scene.control.TextArea notesArea = new javafx.scene.control.TextArea(s.getNotes());
            notesArea.setEditable(false);
            notesArea.setWrapText(true);
            notesArea.setPrefRowCount(4);
            content.getChildren().addAll(new Label("Notes:"), notesArea);
        }
        
        if ("CLOSED".equals(s.getStatus()) && Math.abs(s.getDiscrepancy()) > 0.01 && !s.isDiscrepancyResolved()) {
            content.getChildren().add(new javafx.scene.control.Separator());
            javafx.scene.control.TextField resolutionField = new javafx.scene.control.TextField();
            resolutionField.setPromptText("Resolution notes (e.g., Cash deposited to bank)");
            javafx.scene.control.Button resolveBtn = new javafx.scene.control.Button("Resolve Discrepancy");
            resolveBtn.setStyle("-fx-background-color: #039ED3; -fx-text-fill: white; -fx-font-weight: bold;");
            
            resolveBtn.setOnAction(e -> {
                try {
                    shiftDAO.resolveDiscrepancy(s.getId(), resolutionField.getText().trim());
                    com.pms.dao.ActivityLogDAO.log("DISCREPANCY_RESOLVED", "Resolved shift discrepancy for " + s.getCashierName());
                    com.pms.util.Notifier.success("Discrepancy marked as resolved.");
                    dialog.close();
                    refreshData();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    com.pms.util.Notifier.error("Failed to resolve discrepancy.");
                }
            });
            
            content.getChildren().addAll(new Label("Resolve Discrepancy:"), resolutionField, resolveBtn);
        }
        
        dialog.getDialogPane().setContent(content);
        com.pms.util.UIUtil.enableEnterToClick(dialog);
        dialog.showAndWait();
    }
}
