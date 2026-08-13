package com.pms.util;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.util.Duration;

/**
 * Transparent overlay pane that anchors toast notifications to the bottom-right
 * of the window. Add as the top child of the root StackPane in Main so it
 * sits above all content without blocking clicks on the background.
 *
 * Usage: Notifier.success("Saved.") — never call this class directly.
 */
public class NotificationPane extends AnchorPane {

    private final VBox container = new VBox(8);

    public NotificationPane() {
        // Don't capture mouse events in the transparent areas — only the cards do
        setPickOnBounds(false);
        setMouseTransparent(false);

        container.setAlignment(Pos.TOP_RIGHT);
        container.setPickOnBounds(false);
        container.setPadding(new Insets(16, 16, 0, 0));
        container.setMaxWidth(320);

        AnchorPane.setTopAnchor(container, 0.0);
        AnchorPane.setRightAnchor(container, 0.0);

        getChildren().add(container);
    }

    // ─── Public show ──────────────────────────────────────────────────────────

    public void show(String message, NotificationType type) {
        Platform.runLater(() -> {
            HBox card = buildCard(message, type);
            container.getChildren().add(card);
            animateIn(card);
            scheduleRemoval(card);
        });
    }

    // ─── Card builder ─────────────────────────────────────────────────────────

    private HBox buildCard(String message, NotificationType type) {
        // Icon
        Label icon = new Label(type.icon);
        icon.getStyleClass().addAll("toast-icon", "toast-icon-" + type.name().toLowerCase());
        icon.setMinWidth(20);

        // Message
        Label msg = new Label(message);
        msg.setWrapText(true);
        msg.setMaxWidth(210);
        msg.getStyleClass().add("toast-message");

        // Spacer
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Close button
        Button close = new Button("×");
        close.getStyleClass().add("toast-close");
        close.setFocusTraversable(false);

        // Card
        HBox card = new HBox(10, icon, msg, spacer, close);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(12, 14, 12, 14));
        card.setMaxWidth(300);
        card.getStyleClass().addAll("toast-card", "toast-" + type.name().toLowerCase());
        card.setPickOnBounds(true);

        close.setOnAction(e -> removeCard(card));

        return card;
    }

    // ─── Animations ───────────────────────────────────────────────────────────

    private void animateIn(HBox card) {
        card.setTranslateY(-30);
        card.setOpacity(0);

        TranslateTransition slide = new TranslateTransition(Duration.millis(200), card);
        slide.setToY(0);

        FadeTransition fade = new FadeTransition(Duration.millis(200), card);
        fade.setToValue(1.0);

        new ParallelTransition(slide, fade).play();
    }

    private void scheduleRemoval(HBox card) {
        PauseTransition pause = new PauseTransition(Duration.seconds(3));
        pause.setOnFinished(e -> removeCard(card));
        pause.play();
    }

    private void removeCard(HBox card) {
        FadeTransition fadeOut = new FadeTransition(Duration.millis(250), card);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> container.getChildren().remove(card));
        fadeOut.play();
    }
}
