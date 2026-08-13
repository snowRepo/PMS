import os, glob, re

def revert_file(path):
    with open(path, 'r') as f:
        content = f.read()

    # 1. Strip all styleClass attributes entirely
    content = re.sub(r'\s*styleClass="[^"]*"', '', content)
    
    # 2. Revert the wrapper StackPane/BorderPane/Card to a simple VBox, if it exists
    if "<BorderPane" in content:
        # Extract the inner body
        body_match = re.search(r'<!-- Body -->\s*<VBox[^>]*>(.*?)</VBox>\s*(?:</VBox>|<!-- Back)', content, re.DOTALL)
        if body_match:
            body = body_match.group(1)
        else:
            # try finding setup-body
            body_match = re.search(r'<VBox[^>]*spacing="1[248]"[^>]*>(.*?)</VBox>\s*(?:</VBox>|<!--)', content, re.DOTALL)
            if body_match:
                body = body_match.group(1)
            else:
                body = "<!-- FORM BODY -->"

        # Extract title and subtitle
        title = "Title"
        subtitle = "Subtitle"
        t_match = re.search(r'<Label[^>]*text="([^"]*)"[^>]*setup-title', content)
        if t_match: title = t_match.group(1)
        s_match = re.search(r'<Label[^>]*text="([^"]*)"[^>]*setup-subtitle', content)
        if s_match: subtitle = s_match.group(1)
        
        # Get controller
        c_match = re.search(r'fx:controller="([^"]*)"', content)
        controller = c_match.group(1) if c_match else ""

        new_content = f"""<?xml version="1.0" encoding="UTF-8"?>
<?import javafx.scene.control.*?>
<?import javafx.scene.layout.*?>

<VBox xmlns="http://javafx.com/javafx/17"
      xmlns:fx="http://javafx.com/fxml/1"
      fx:controller="{controller}"
      alignment="TOP_CENTER"
      spacing="32"
      style="-fx-padding: 80 0 0 0;"
      prefWidth="600" prefHeight="500">

    <VBox alignment="CENTER" spacing="8">
        <Label text="{title}"/>
        <Label text="{subtitle}"/>
    </VBox>

    <VBox spacing="12" maxWidth="400">
{body}
    </VBox>
</VBox>
"""
        with open(path, 'w') as f:
            f.write(new_content)

files = glob.glob('/Users/snow/PharmSys/src/main/resources/fxml/**/*.fxml', recursive=True)
for f in files:
    revert_file(f)
