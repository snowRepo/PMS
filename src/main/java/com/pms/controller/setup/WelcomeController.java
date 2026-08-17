package com.pms.controller.setup;

import com.pms.util.Navigator;
import javafx.fxml.FXML;

public class WelcomeController {

    @FXML
    public void initialize() {
        Navigator.setTitle("Welcome");
    }

    @FXML
    private void handleContinue() {
        Navigator.navigateTo("/fxml/setup/EULA.fxml");
    }
}
