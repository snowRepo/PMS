package com.pms.controller;

import com.pms.dao.CategoryDAO;
import com.pms.model.Category;
import com.pms.util.Notifier;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class CategoriesController {

    private static final Logger logger = LoggerFactory.getLogger(CategoriesController.class);

    @FXML private TextField searchField;
    @FXML private TableView<Category> categoryTable;
    @FXML private TableColumn<Category, String> colName;
    @FXML private TableColumn<Category, String> colDescription;
    @FXML private TableColumn<Category, Category> colActions;
    @FXML private Pagination pagination;

    private static final int ITEMS_PER_PAGE = 25;
    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final ObservableList<Category> tableData = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupTableColumns();
        setupSearch();
        pagination.setPageFactory(this::createPage);
        refreshData();
    }

    private void setupTableColumns() {
        colName.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));
        colDescription.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getDescription()));

        colActions.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue()));
        colActions.setCellFactory(tc -> new TableCell<>() {
            private final Button editBtn = new Button("Edit");
            private final Button delBtn = new Button("Delete");
            private final HBox pane = new HBox(8, editBtn, delBtn);

            {
                // Native buttons, perfectly centered
                pane.setAlignment(Pos.CENTER);
                
                editBtn.setOnAction(e -> {
                    Category c = getItem();
                    if (c != null) openCategoryDialog(c);
                });
                
                delBtn.setOnAction(e -> {
                    Category c = getItem();
                    if (c != null) handleDelete(c);
                });
            }

            @Override
            protected void updateItem(Category c, boolean empty) {
                super.updateItem(c, empty);
                if (empty || c == null) {
                    setGraphic(null);
                } else {
                    setGraphic(pane);
                }
            }
        });

        categoryTable.setItems(tableData);
        
        categoryTable.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && categoryTable.getSelectionModel().getSelectedItem() != null) {
                openCategoryDialog(categoryTable.getSelectionModel().getSelectedItem());
            }
        });
    }

    private void setupSearch() {
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            pagination.setCurrentPageIndex(0);
            refreshData();
        });
    }

    private javafx.scene.Node createPage(int pageIndex) {
        loadDataForPage(pageIndex);
        return new javafx.scene.layout.StackPane();
    }

    private void refreshData() {
        new Thread(() -> {
            try {
                String search = searchField.getText().trim();
                int totalItems = categoryDAO.countAll(search);
                int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / ITEMS_PER_PAGE));
                
                Platform.runLater(() -> {
                    pagination.setPageCount(totalPages);
                    loadDataForPage(pagination.getCurrentPageIndex());
                });
            } catch (SQLException e) {
                logger.error("Failed to count categories", e);
                Platform.runLater(() -> Notifier.error("Failed to load categories data."));
            }
        }).start();
    }

    private void loadDataForPage(int pageIndex) {
        new Thread(() -> {
            try {
                String search = searchField.getText().trim();
                int offset = pageIndex * ITEMS_PER_PAGE;
                List<Category> items = categoryDAO.findPaginated(ITEMS_PER_PAGE, offset, search);
                
                Platform.runLater(() -> tableData.setAll(items));
            } catch (SQLException e) {
                logger.error("Failed to fetch paginated categories", e);
                Platform.runLater(() -> Notifier.error("Failed to fetch categories page."));
            }
        }).start();
    }

    @FXML
    private void handleAddCategory() {
        openCategoryDialog(null);
    }

    private void openCategoryDialog(Category existingCategory) {
        Dialog<Category> dialog = new Dialog<>();
         dialog.initOwner(com.pms.util.Navigator.getStage());
        dialog.initOwner(categoryTable.getScene().getWindow());
        dialog.setTitle(existingCategory == null ? "Add Category" : "Edit Category");
        dialog.setHeaderText(null);

        // Set the button types.
        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField nameField = new TextField();
        nameField.setPromptText("Category Name");
        if (existingCategory != null) nameField.setText(existingCategory.getName());

        TextField descField = new TextField();
        descField.setPromptText("Description (Optional)");
        if (existingCategory != null) descField.setText(existingCategory.getDescription());

        grid.add(new Label("Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Description:"), 0, 1);
        grid.add(descField, 1, 1);

        dialog.getDialogPane().setContent(grid);

        // Request focus on the name field by default.
        Platform.runLater(nameField::requestFocus);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                if (nameField.getText().trim().isEmpty()) {
                    Notifier.error("Category name cannot be empty.");
                    return null;
                }
                Category c = existingCategory != null ? existingCategory : new Category();
                c.setName(nameField.getText().trim());
                c.setDescription(descField.getText().trim());
                return c;
            }
            return null;
        });

        Optional<Category> result = dialog.showAndWait();

        result.ifPresent(category -> {
            try {
                if (category.getId() == null) {
                    categoryDAO.create(category);
                    com.pms.dao.ActivityLogDAO.log("CATEGORY_CREATED", "Category created: " + category.getName());
                    Notifier.success("Category created.");
                } else {
                    categoryDAO.update(category);
                    com.pms.dao.ActivityLogDAO.log("CATEGORY_UPDATED", "Category updated: " + category.getName());
                    Notifier.success("Category updated.");
                }
                refreshData();
            } catch (Exception e) {
                logger.error("Failed to save category", e);
                Notifier.error("Failed to save category: " + e.getMessage());
            }
        });
        Platform.runLater(categoryTable::requestFocus);
    }

    private void handleDelete(Category c) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(com.pms.util.Navigator.getStage());
        alert.initOwner(categoryTable.getScene().getWindow());
        alert.setTitle("Delete Category");
        alert.setHeaderText("Delete '" + c.getName() + "'?");
        alert.setContentText("Are you sure you want to permanently delete this category?");
        
        alert.showAndWait().ifPresent(res -> {
            if (res == ButtonType.OK) {
                try {
                    categoryDAO.delete(c.getId(), c.getName());
                    com.pms.dao.ActivityLogDAO.log("CATEGORY_DELETED", "Category deleted: " + c.getName());
                    Notifier.success("Category deleted.");
                    refreshData();
                } catch (IllegalStateException ex) {
                    Notifier.error(ex.getMessage());
                } catch (Exception e) {
                    logger.error("Failed to delete category", e);
                    Notifier.error("Failed to delete category: " + e.getMessage());
                }
            }
        });
        Platform.runLater(categoryTable::requestFocus);
    }
}
