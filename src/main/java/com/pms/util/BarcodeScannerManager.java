package com.pms.util;

import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * Global utility to intercept hardware barcode scanner input.
 *
 * Scanners act as USB keyboards but type extremely fast (usually < 50 ms/char)
 * and end with a carriage return (ENTER).
 *
 * Listener stack design
 * ─────────────────────
 * Multiple screens / modals can all call pushListener() without stomping on
 * each other.  The topmost listener receives every scan.  When a screen is
 * done (navigate-away or modal close) it calls popListener() so the previous
 * screen automatically resumes receiving scans.
 *
 * Navigator calls clearAllListeners() before every full-screen navigation so
 * stale listeners from previously-loaded screens are never left behind.
 */
public class BarcodeScannerManager {

    private static final Logger logger = LoggerFactory.getLogger(BarcodeScannerManager.class);

    /**
     * Keystroke interval threshold in milliseconds.
     * Keystrokes that arrive faster than this are treated as scanner input.
     *
     * 100 ms comfortably covers USB HID scanners (<10 ms/char) AND the slower
     * Bluetooth / serial scanners (~80 ms/char), while still being far too fast
     * for any human typist (~200 ms+ average).
     */
    private static final long SCAN_THRESHOLD_MS = 100;

    /** Minimum characters required to treat a sequence as a barcode. */
    private static final int MIN_BARCODE_LENGTH = 3;

    private static BarcodeScannerManager instance;

    private final StringBuilder barcodeBuffer = new StringBuilder();
    private long lastKeyTime = 0;

    /**
     * Listener stack. The listener at the top (peek) is always the active one.
     * When a modal opens it pushes its listener; when it closes it pops,
     * restoring the parent screen's listener automatically.
     */
    private final Deque<Consumer<String>> listenerStack = new ArrayDeque<>();

    private BarcodeScannerManager() {}

    public static synchronized BarcodeScannerManager getInstance() {
        if (instance == null) {
            instance = new BarcodeScannerManager();
        }
        return instance;
    }

    // ── Listener management ──────────────────────────────────────────────────

    /**
     * Push a listener onto the stack.
     * Call this in your controller's initialize() method.
     * Always pair with {@link #popListener()} when the controller is done.
     */
    public synchronized void pushListener(Consumer<String> listener) {
        listenerStack.push(listener);
        logger.debug("Barcode listener pushed. Stack depth: {}", listenerStack.size());
    }

    /**
     * Remove the topmost listener from the stack.
     * Call this when navigating away from a screen or closing a modal.
     */
    public synchronized void popListener() {
        if (!listenerStack.isEmpty()) {
            listenerStack.pop();
            logger.debug("Barcode listener popped. Stack depth: {}", listenerStack.size());
        }
    }

    /**
     * Remove every listener from the stack.
     * Called by {@link Navigator} before every full-screen navigation to
     * guarantee no stale listeners are left from the previous screen.
     */
    public synchronized void clearAllListeners() {
        int count = listenerStack.size();
        listenerStack.clear();
        if (count > 0) {
            logger.debug("Barcode listener stack cleared ({} removed).", count);
        }
    }

    // ── Scene attachment ─────────────────────────────────────────────────────

    /**
     * Attaches key-event interceptors to the global application scene.
     * Call once from Main.java after the scene is created — never again.
     */
    public void attachToScene(Scene scene) {
        // Accumulate characters as they arrive
        scene.addEventFilter(KeyEvent.KEY_TYPED, event -> {
            long now = System.currentTimeMillis();
            long gap = now - lastKeyTime;

            if (gap > SCAN_THRESHOLD_MS) {
                // Gap too long: from a human typist, not a scanner — reset the buffer
                barcodeBuffer.setLength(0);
            }

            String ch = event.getCharacter();
            if (ch != null && !ch.equals("\r") && !ch.equals("\n") && !ch.isEmpty()) {
                barcodeBuffer.append(ch);
            }
            lastKeyTime = now;
        });

        // ENTER key finalises the scan
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() != KeyCode.ENTER) return;

            long now = System.currentTimeMillis();
            long gap = now - lastKeyTime;

            if (gap <= SCAN_THRESHOLD_MS && barcodeBuffer.length() >= MIN_BARCODE_LENGTH) {
                String finalBarcode = barcodeBuffer.toString().trim();
                barcodeBuffer.setLength(0);

                logger.debug("Hardware barcode intercepted: \"{}\"", finalBarcode);

                // Deliver to the topmost active listener
                Consumer<String> active;
                synchronized (this) {
                    active = listenerStack.isEmpty() ? null : listenerStack.peek();
                }

                if (active != null) {
                    active.accept(finalBarcode);
                } else {
                    logger.debug("Barcode scanned but no active listener registered.");
                }

                // Consume so ENTER doesn't accidentally submit any form
                event.consume();
            }
        });
    }
}
