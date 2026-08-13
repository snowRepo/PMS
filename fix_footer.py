import glob, re

files = glob.glob('/Users/snow/PharmSys/src/main/resources/fxml/**/*.fxml', recursive=True)

for path in files:
    if "Dashboard" in path or "Main" in path:
        continue
    
    with open(path, 'r') as f:
        content = f.read()
    
    if "<BorderPane" in content:
        continue # Already border pane
        
    # We want to replace the root <VBox> with a BorderPane.
    # The VBox has children. We extract the <VBox xmlns...> ... prefHeight="500">
    # Then we extract everything inside until the <Region VBox.vgrow="ALWAYS"/> or the footer label.
    
    root_match = re.search(r'(<VBox xmlns="http://javafx.com/javafx/[^>]*>)', content)
    if not root_match: continue
    
    root_tag = root_match.group(1)
    
    # get controller
    ctrl_match = re.search(r'fx:controller="([^"]*)"', root_tag)
    controller = ctrl_match.group(1) if ctrl_match else ""
    
    # get body (everything between the root tag and the Region/Label at the end)
    # The end of the VBox is usually:
    # <Region VBox.vgrow="ALWAYS"/>
    # <Label text="© 2026 PMS - All Rights Reserved" ... />
    # </VBox>
    
    # Just grab everything inside the root VBox
    inner_content = re.sub(r'<VBox xmlns="http://javafx.com/javafx/[^>]*>', '', content, 1)
    
    # Remove the footer and region
    inner_content = re.sub(r'<Region\s+VBox\.vgrow="ALWAYS"\s*/>', '', inner_content)
    
    # Match the footer label
    footer_label_match = re.search(r'<Label\s+text="[^"]*2026 PMS - All Rights Reserved"[^>]*/>', inner_content)
    if not footer_label_match:
        # maybe it's missing entirely (like PinSetup)
        footer_label = '<Label text="© 2026 PMS - All Rights Reserved" maxWidth="Infinity" alignment="CENTER" style="-fx-padding: 24;"/>'
    else:
        footer_label = footer_label_match.group(0)
        inner_content = inner_content.replace(footer_label, '')
        
    inner_content = inner_content.replace('</VBox>', '', 1) # remove the closing root VBox
    
    new_content = f"""<?xml version="1.0" encoding="UTF-8"?>
<?import javafx.scene.control.*?>
<?import javafx.scene.layout.*?>

<BorderPane xmlns="http://javafx.com/javafx/17"
            xmlns:fx="http://javafx.com/fxml/1"
            fx:controller="{controller}"
            prefWidth="600" prefHeight="500">

    <center>
        <VBox alignment="TOP_CENTER" spacing="32" style="-fx-padding: 30 0 0 0;" BorderPane.alignment="TOP_CENTER">
{inner_content}
        </VBox>
    </center>

    <bottom>
        {footer_label}
    </bottom>
</BorderPane>
"""
    # clean up any extra xml decl
    new_content = re.sub(r'<\?xml[^>]*>\n<\?xml', '<?xml', new_content)
    # clean up extra imports
    new_content = re.sub(r'<\?import[^>]*>\n<\?import[^>]*>\n<\?import', '<?import', new_content)
    
    with open(path, 'w') as f:
        f.write(new_content)

print("Done")
