package com.pms.controller;

import com.pms.dao.UserDAO;
import com.pms.model.User;
import com.pms.util.Notifier;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class CashiersController {

    private static final Logger logger = LoggerFactory.getLogger(CashiersController.class);

    @FXML private TableView<User> cashiersTable;
    @FXML private TableColumn<User, String> colName;
    @FXML private TableColumn<User, String> colUsername;
    @FXML private TableColumn<User, String> colRole;
    @FXML private TableColumn<User, String> colStatus;
    @FXML private TableColumn<User, User> colActions;
    
    @FXML private TextField searchField;
    @FXML private ComboBox<String> statusFilterCombo;
    @FXML private Pagination pagination;

    private static final int ITEMS_PER_PAGE = 25;
    private final UserDAO userDAO = new UserDAO();
    private final ObservableList<User> tableData = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupTableColumns();
        
        statusFilterCombo.setItems(FXCollections.observableArrayList("All", "Active", "Inactive"));
        statusFilterCombo.getSelectionModel().select("All");
        
        pagination.setPageFactory(this::createPage);
        
        searchField.textProperty().addListener((obs, oldV, newV) -> refreshData());
        statusFilterCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> refreshData());
        
        refreshData();
    }
    
    private javafx.scene.Node createPage(int pageIndex) {
        int fromIndex = pageIndex * ITEMS_PER_PAGE;
        int toIndex = Math.min(fromIndex + ITEMS_PER_PAGE, tableData.size());
        
        if (fromIndex <= toIndex && fromIndex < tableData.size()) {
            cashiersTable.setItems(FXCollections.observableArrayList(tableData.subList(fromIndex, toIndex)));
        } else {
            cashiersTable.setItems(FXCollections.observableArrayList());
        }
        
        return new VBox(); // The pagination control just needs a node, the table is updated
    }

    private void setupTableColumns() {
        colName.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFullName()));
        colUsername.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getUsername()));
        colRole.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getRole().toUpperCase()));
        
        colStatus.setCellValueFactory(data -> {
            boolean active = data.getValue().isActive();
            return new SimpleStringProperty(active ? "Active" : "Inactive");
        });
        
        colActions.setCellValueFactory(data -> new javafx.beans.property.SimpleObjectProperty<>(data.getValue()));
        colActions.setCellFactory(tc -> new TableCell<>() {
            private final Button resetBtn = new Button("Reset Password");
            private final Button toggleBtn = new Button();
            private final HBox pane = new HBox(8, resetBtn, toggleBtn);

            {
                pane.setAlignment(Pos.CENTER);

                resetBtn.setOnAction(e -> {
                    User u = getItem();
                    if (u != null) handleResetPassword(u);
                });

                toggleBtn.setOnAction(e -> {
                    User u = getItem();
                    if (u != null) handleToggleStatus(u);
                });
            }

            @Override
            protected void updateItem(User u, boolean empty) {
                super.updateItem(u, empty);
                if (empty || u == null) {
                    setGraphic(null);
                } else {
                    if ("admin".equalsIgnoreCase(u.getRole())) {
                        setGraphic(null); 
                    } else {
                        toggleBtn.setText(u.isActive() ? "Deactivate" : "Activate");
                        setGraphic(pane);
                    }
                }
            }
        });

        cashiersTable.setItems(tableData);
    }

    private void refreshData() {
        new Thread(() -> {
            try {
                String query = searchField != null ? searchField.getText() : "";
                String status = statusFilterCombo != null ? statusFilterCombo.getValue() : "All";
                
                List<User> users = userDAO.search(query, status);
                Platform.runLater(() -> {
                    tableData.setAll(users);
                    int pageCount = (int) Math.ceil((double) users.size() / ITEMS_PER_PAGE);
                    pagination.setPageCount(pageCount == 0 ? 1 : pageCount);
                    createPage(pagination.getCurrentPageIndex());
                });
            } catch (Exception e) {
                logger.error("Failed to load users", e);
            }
        }).start();
    }

    @FXML
    public void handleAddCashier() {
        Dialog<User> dialog = new Dialog<>();
         dialog.initOwner(com.pms.util.Navigator.getStage());
        dialog.setTitle("New Cashier");
        dialog.setHeaderText("Create a Cashier Account");

        if (cashiersTable.getScene() != null && cashiersTable.getScene().getWindow() != null) {
            dialog.initOwner(cashiersTable.getScene().getWindow());
        }

        ButtonType saveBtnType = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtnType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 50, 10, 10));

        TextField nameField = new TextField();
        nameField.setPromptText("John Doe");
        TextField userField = new TextField();
        userField.setPromptText("jdoe");

        grid.add(new Label("Full Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Username:"), 0, 1);
        grid.add(userField, 1, 1);

        dialog.getDialogPane().setContent(grid);
        Platform.runLater(nameField::requestFocus);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveBtnType) {
                String name = nameField.getText().trim();
                String user = userField.getText().trim();

                if (name.isEmpty() || user.isEmpty()) {
                    Notifier.error("All fields are required.");
                    return null;
                }

                try {
                    String tempPass = userDAO.createCashier(name, user);
                    com.pms.dao.ActivityLogDAO.log("USER_CREATED", "Created user account: " + user);
                    
                    Platform.runLater(() -> {
                        Alert info = new Alert(Alert.AlertType.INFORMATION);
        info.initOwner(com.pms.util.Navigator.getStage());
                        info.setTitle("Account Created");
                        info.setHeaderText("Cashier Account Created Successfully!");
                        
                        VBox content = new VBox(10);
                        content.getChildren().add(new Label("Please share these credentials with the cashier."));
                        content.getChildren().add(new Label("They will be required to change the password on their first login."));
                        
                        TextField passField = new TextField(tempPass);
                        passField.setEditable(false);
                        passField.setStyle("-fx-font-family: monospace; -fx-font-size: 14px; -fx-font-weight: bold;");
                        
                        content.getChildren().add(new Label("Temporary Password:"));
                        content.getChildren().add(passField);
                        
                        info.getDialogPane().setContent(content);
                        com.pms.util.UIUtil.enableEnterToClick(info);
                        info.showAndWait();
                    });

                    refreshData();
                } catch (Exception e) {
                    logger.error("Failed to create cashier", e);
                    Notifier.error("Username might already exist or DB error.");
                }
            }
            return null;
        });

        com.pms.util.UIUtil.enableEnterToClick(dialog);

        dialog.showAndWait();
        Platform.runLater(cashiersTable::requestFocus);
    }

    private void handleResetPassword(User user) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.initOwner(com.pms.util.Navigator.getStage());
        confirm.setTitle("Reset Password");
        confirm.setHeaderText("Reset password for " + user.getUsername() + "?");
        confirm.setContentText("This will generate a new temporary password for this user.");
        
        com.pms.util.UIUtil.enableEnterToClick(confirm);
        
        confirm.showAndWait().ifPresent(res -> {
            if (res == ButtonType.OK) {
                try {
                    String newPass = userDAO.adminResetPassword(user.getId());
                    com.pms.dao.ActivityLogDAO.log("USER_PASSWORD_RESET", "Admin reset password for user: " + user.getUsername());
                    
                    Alert info = new Alert(Alert.AlertType.INFORMATION);
        info.initOwner(com.pms.util.Navigator.getStage());
                    info.setTitle("Password Reset");
                    info.setHeaderText("Password Reset Successful");
                    
                    VBox content = new VBox(10);
                    TextField passField = new TextField(newPass);
                    passField.setEditable(false);
                    passField.setStyle("-fx-font-family: monospace; -fx-font-size: 14px; -fx-font-weight: bold;");
                    
                    content.getChildren().add(new Label("New Temporary Password for " + user.getUsername() + ":"));
                    content.getChildren().add(passField);
                    
                    info.getDialogPane().setContent(content);
                    com.pms.util.UIUtil.enableEnterToClick(info);
                    info.showAndWait();
                    
                } catch (Exception e) {
                    logger.error("Failed to reset password", e);
                    Notifier.error("Failed to reset password.");
                }
            }
        });
        Platform.runLater(cashiersTable::requestFocus);
    }

    private void handleToggleStatus(User user) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.initOwner(com.pms.util.Navigator.getStage());
        confirm.setTitle(user.isActive() ? "Deactivate User" : "Activate User");
        confirm.setHeaderText((user.isActive() ? "Deactivate " : "Activate ") + user.getUsername() + "?");
        confirm.setContentText(user.isActive() 
            ? "This user will no longer be able to log in. Proceed?" 
            : "This user will regain access to the system. Proceed?");
        
        com.pms.util.UIUtil.enableEnterToClick(confirm);
        
        confirm.showAndWait().ifPresent(res -> {
            if (res == ButtonType.OK) {
                try {
                    if (user.isActive()) {
                        userDAO.deactivate(user.getId());
                        com.pms.dao.ActivityLogDAO.log("USER_DEACTIVATED", "Deactivated user: " + user.getUsername());
                    } else {
                        userDAO.activate(user.getId());
                        com.pms.dao.ActivityLogDAO.log("USER_ACTIVATED", "Activated user: " + user.getUsername());
                    }
                    Notifier.success("User status updated.");
                    refreshData();
                } catch (Exception e) {
                    logger.error("Failed to toggle status", e);
                    Notifier.error("Failed to update status.");
                }
            }
        });
        Platform.runLater(cashiersTable::requestFocus);
    }
}
