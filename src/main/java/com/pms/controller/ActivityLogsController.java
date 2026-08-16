package com.pms.controller;

import com.pms.dao.ActivityLogDAO;
import com.pms.model.ActivityLog;
import com.pms.util.DateTimeUtil;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Pagination;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;

public class ActivityLogsController {

    private static final Logger logger = LoggerFactory.getLogger(ActivityLogsController.class);

    @FXML private TextField searchField;
    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker endDatePicker;
    
    @FXML private TableView<ActivityLog> logsTable;
    @FXML private TableColumn<ActivityLog, String> colTimestamp;
    @FXML private TableColumn<ActivityLog, String> colUser;
    @FXML private TableColumn<ActivityLog, String> colAction;
    @FXML private TableColumn<ActivityLog, String> colDetails;
    
    @FXML private Pagination pagination;

    private static final int ITEMS_PER_PAGE = 50;
    private final ActivityLogDAO logDAO = new ActivityLogDAO();
    private final ObservableList<ActivityLog> tableData = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupTableColumns();
        
        pagination.setPageFactory(this::createPage);
        
        // Listeners for filters
        searchField.textProperty().addListener((obs, oldV, newV) -> triggerRefresh());
        startDatePicker.valueProperty().addListener((obs, oldV, newV) -> triggerRefresh());
        endDatePicker.valueProperty().addListener((obs, oldV, newV) -> triggerRefresh());
        
        refreshData();
    }

    private void setupTableColumns() {
        colTimestamp.setCellValueFactory(data -> new SimpleStringProperty(DateTimeUtil.formatForDisplay(data.getValue().getCreatedAt())));
        colUser.setCellValueFactory(data -> {
            String username = data.getValue().getUsername();
            return new SimpleStringProperty(username != null ? username : "System");
        });
        colAction.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getAction()));
        colDetails.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getDescription()));
        
        // Add tooltips
        com.pms.util.UIUtil.setTooltipCellFactory(colTimestamp, colUser, colAction, colDetails);

        logsTable.setItems(tableData);
    }

    private void triggerRefresh() {
        pagination.setCurrentPageIndex(0);
        refreshData();
    }

    private javafx.scene.Node createPage(int pageIndex) {
        loadDataForPage(pageIndex);
        return new javafx.scene.layout.StackPane();
    }

    private String getSearchText() {
        return searchField.getText() != null ? searchField.getText().trim() : "";
    }

    private String getStartDate() {
        return startDatePicker.getValue() != null ? startDatePicker.getValue().toString() : null;
    }

    private String getEndDate() {
        return endDatePicker.getValue() != null ? endDatePicker.getValue().toString() : null;
    }

    private void refreshData() {
        new Thread(() -> {
            try {
                int totalItems = logDAO.countFiltered(getSearchText(), getStartDate(), getEndDate());
                int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / ITEMS_PER_PAGE));
                
                Platform.runLater(() -> {
                    pagination.setPageCount(totalPages);
                    loadDataForPage(pagination.getCurrentPageIndex());
                });
            } catch (SQLException e) {
                logger.error("Failed to count activity logs", e);
            }
        }).start();
    }

    private void loadDataForPage(int pageIndex) {
        new Thread(() -> {
            try {
                int offset = pageIndex * ITEMS_PER_PAGE;
                List<ActivityLog> items = logDAO.findFilteredPaginated(ITEMS_PER_PAGE, offset, getSearchText(), getStartDate(), getEndDate());
                Platform.runLater(() -> tableData.setAll(items));
            } catch (SQLException e) {
                logger.error("Failed to fetch activity logs page", e);
            }
        }).start();
    }
}
