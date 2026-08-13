package com.pms.util;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import com.pms.model.Sale;
import com.pms.model.SaleItem;

import java.io.File;
import java.io.FileOutputStream;

public class ReceiptPdfGenerator {

    /**
     * Generates a perfectly formatted monospace text receipt for the given sale.
     * This matches the exact format used in the POS screen.
     */
    public static String generateReceiptText(Sale sale) {
        StringBuilder sb = new StringBuilder();
        sb.append("====================================\n");
        sb.append("          SALES RECEIPT\n");
        sb.append("====================================\n");
        
        String id = sale.getId();
        if (id != null && id.length() >= 8) {
            sb.append("Receipt No: ").append(id.substring(0,8).toUpperCase()).append("\n");
        }
        sb.append("Date: ").append(sale.getSaleDate()).append("\n");
        if (sale.getCustomerName() != null && !sale.getCustomerName().isEmpty()) {
            sb.append("Customer: ").append(sale.getCustomerName()).append("\n");
        }
        sb.append("------------------------------------\n");
        sb.append(String.format("%-20s %-5s %s\n", "Item", "Qty", "Total"));
        sb.append("------------------------------------\n");
        
        if (sale.getItems() != null) {
            for (SaleItem item : sale.getItems()) {
                String name = item.getProductName();
                if (name == null) name = "Unknown";
                
                java.util.List<String> wrapped = wrapText(name, 18);
                if (wrapped.isEmpty()) {
                    sb.append(String.format("%-20s %-5d %s\n", "", item.getQty(), CurrencyUtil.format(item.getSubtotal())));
                } else {
                    sb.append(String.format("%-20s %-5d %s\n", wrapped.get(0), item.getQty(), CurrencyUtil.format(item.getSubtotal())));
                    for (int i = 1; i < wrapped.size(); i++) {
                        sb.append(String.format("%-20s\n", wrapped.get(i)));
                    }
                }
            }
        }
        
        sb.append("------------------------------------\n");
        sb.append(String.format("%-26s %s\n", "Subtotal:", CurrencyUtil.format(sale.getTotalAmount())));
        if (sale.getDiscount() > 0) {
            sb.append(String.format("%-26s %s\n", "Cart Discount:", "-" + CurrencyUtil.format(sale.getDiscount())));
        }
        sb.append(String.format("%-26s %s\n", "NET TOTAL:", CurrencyUtil.format(sale.getNetTotal())));
        sb.append("------------------------------------\n");
        sb.append(String.format("%-26s %s\n", "Amount Tendered:", CurrencyUtil.format(sale.getAmountPaid())));
        sb.append(String.format("%-26s %s\n", "Change:", CurrencyUtil.format(sale.getChangeAmount())));
        
        if (sale.getPaymentMethod() != null) {
            sb.append("Payment: ").append(sale.getPaymentMethod());
            if (sale.getPaymentRef() != null && !sale.getPaymentRef().isEmpty()) {
                sb.append(" (Ref: ").append(sale.getPaymentRef()).append(")");
            }
            sb.append("\n");
        }
        
        sb.append("====================================\n");
        sb.append("       THANK YOU FOR SHOPPING!\n");
        sb.append("====================================\n");
        return sb.toString();
    }

    private static java.util.List<String> wrapText(String text, int maxLineLength) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        if (text == null || text.isEmpty()) return lines;
        
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();
        
        for (String word : words) {
            if (currentLine.length() + word.length() + (currentLine.length() > 0 ? 1 : 0) <= maxLineLength) {
                if (currentLine.length() > 0) currentLine.append(" ");
                currentLine.append(word);
            } else {
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder();
                }
                while (word.length() > maxLineLength) {
                    lines.add(word.substring(0, maxLineLength));
                    word = word.substring(maxLineLength);
                }
                currentLine.append(word);
            }
        }
        if (currentLine.length() > 0) lines.add(currentLine.toString());
        return lines;
    }


    /**
     * Creates a PDF file containing the formatted text receipt.
     */
    public static void generateReceiptPdf(Sale sale, File destination) throws Exception {
        String receiptText = generateReceiptText(sale);

        Document document = new Document();
        PdfWriter.getInstance(document, new FileOutputStream(destination));
        document.open();

        // Use Courier (monospace) font so alignment holds exactly like in the UI
        Font font = FontFactory.getFont(FontFactory.COURIER, 12);
        
        Paragraph paragraph = new Paragraph(receiptText, font);
        document.add(paragraph);
        
        document.close();
    }
}
