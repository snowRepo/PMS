package com.pms.util;

import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * Global utility to intercept hardware barcode scanner input.
 * Scanners act as USB keyboards but type extremely fast (usually < 50ms per character)
 * and end with a carriage return (ENTER).
 */
public class BarcodeScannerManager {

    private static final Logger logger = LoggerFactory.getLogger(BarcodeScannerManager.class);
    
    // Threshold in milliseconds. Keystrokes faster than this are considered scanner input.
    private static final long SCAN_THRESHOLD_MS = 60;
    
    private static BarcodeScannerManager instance;
    
    private final StringBuilder barcodeBuffer = new StringBuilder();
    private long lastKeyTime = 0;
    
    private Consumer<String> currentListener;

    private BarcodeScannerManager() {}

    public static synchronized BarcodeScannerManager getInstance() {
        if (instance == null) {
            instance = new BarcodeScannerManager();
        }
        return instance;
    }

    /**
     * Set the active listener that will receive the barcode when a scan finishes.
     */
    public void setListener(Consumer<String> listener) {
        this.currentListener = listener;
    }

    /**
     * Attaches the key interceptors to the global application scene.
     */
    public void attachToScene(Scene scene) {
        // Collect characters as they are typed
        scene.addEventFilter(KeyEvent.KEY_TYPED, event -> {
            long now = System.currentTimeMillis();
            if (now - lastKeyTime > SCAN_THRESHOLD_MS) {
                barcodeBuffer.setLength(0); // Reset if too slow (likely a human typing)
            }
            
            String text = event.getCharacter();
            // Ignore enter/newline characters in the buffer
            if (text != null && !text.equals("\r") && !text.equals("\n") && !text.isEmpty()) {
                barcodeBuffer.append(text);
            }
            lastKeyTime = now;
        });

        // Detect ENTER key to finalize the scan
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER) {
                long now = System.currentTimeMillis();
                
                // If it was fast enough AND we have enough characters to be a barcode
                if (now - lastKeyTime <= SCAN_THRESHOLD_MS && barcodeBuffer.length() >= 3) {
                    String finalBarcode = barcodeBuffer.toString();
                    barcodeBuffer.setLength(0); // clear buffer
                    
                    logger.debug("Hardware barcode intercepted: {}", finalBarcode);
                    
                    if (currentListener != null) {
                        currentListener.accept(finalBarcode);
                    }
                    
                    // Consume the event so the Enter key doesn't submit whatever form the user is on
                    event.consume();
                }
            }
        });
    }
}
