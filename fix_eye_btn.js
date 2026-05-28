const fs = require('fs');

const fxmlFile = 'src/main/resources/fxml/login.fxml';
let fxml = fs.readFileSync(fxmlFile, 'utf8');

fxml = fxml.replace(
  /<Button fx:id="eyeButton"\s+text="BIO"\s+styleClass="biometric-button"/,
  '<Button fx:id="eyeButton"\n                        onAction="#handleBiometric"\n                        text="BIO"\n                        styleClass="biometric-button"'
);

fs.writeFileSync(fxmlFile, fxml);
