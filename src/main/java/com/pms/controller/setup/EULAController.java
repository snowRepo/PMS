package com.pms.controller.setup;

import com.pms.util.Navigator;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;

public class EULAController {

    @FXML private Label eulaTextLabel;
    @FXML private CheckBox agreeCheckBox;
    @FXML private Button continueBtn;

    private static final String EULA_TEXT = 
        "END USER LICENSE AGREEMENT (EULA)\n\n" +
        "1. ACCEPTANCE OF TERMS\n" +
        "By accessing and using the Product Management System (the \"Software\"), you agree to be bound by the terms and conditions set forth in this Agreement. If you do not agree to all terms and conditions, do not use the Software.\n\n" +
        "2. LICENSE GRANT\n" +
        "The Software owner (\"Owner\") grants you a non-exclusive, non-transferable, revocable license to use the Software solely for your internal business operations. All rights, title, and interest in and to the Software, including any intellectual property rights, remain exclusively with the Owner.\n\n" +
        "3. DATA STORAGE AND THIRD-PARTY CLOUD PROVIDERS\n" +
        "The Software allows you to synchronize your local data with a third-party cloud database provider (e.g., Supabase, PostgreSQL, MySQL) of your choosing.\n" +
        "a) You are solely responsible for creating, managing, securing, and maintaining your own account and database with your chosen third-party cloud provider.\n" +
        "b) The Owner does not host, store, or have access to your data. All data synchronization occurs directly between your local machine and your configured cloud provider.\n" +
        "c) The Owner assumes absolutely NO LIABILITY for data loss, data breaches, unauthorized access, or any damages arising from your use of third-party cloud providers or your failure to secure your database credentials.\n\n" +
        "4. RESTRICTIONS\n" +
        "You may not: (i) reverse engineer, decompile, or disassemble the Software; (ii) rent, lease, lend, sell, or sublicense the Software; (iii) use the Software for any unlawful purpose; or (iv) remove any proprietary notices or labels on the Software.\n\n" +
        "5. DISCLAIMER OF WARRANTIES\n" +
        "THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE OWNER BE LIABLE FOR ANY CLAIM, DAMAGES, OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF, OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.\n\n" +
        "6. TERMINATION\n" +
        "The Owner reserves the right to terminate this license at any time if you fail to comply with any term or condition of this Agreement. Upon termination, you must cease all use of the Software and destroy all copies in your possession.";

    @FXML
    public void initialize() {
        Navigator.setTitle("Agreement");
        eulaTextLabel.setText(EULA_TEXT);

        // Bind continue button disable state to checkbox selected state
        continueBtn.disableProperty().bind(agreeCheckBox.selectedProperty().not());
    }

    @FXML
    private void handleContinue() {
        if (agreeCheckBox.isSelected()) {
            Navigator.navigateTo("/fxml/setup/DbSetup.fxml");
        }
    }

    @FXML
    private void handleDecline() {
        Platform.exit();
        System.exit(0);
    }
}
