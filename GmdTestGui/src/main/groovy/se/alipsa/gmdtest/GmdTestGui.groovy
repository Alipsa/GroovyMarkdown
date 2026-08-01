package se.alipsa.gmdtest

import javafx.application.Application
import javafx.application.Platform
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.TextArea
import javafx.scene.control.TextField
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.stage.Stage

import se.alipsa.gmd.core.*

import java.awt.Desktop

class GmdTestGui extends Application {

  TextField tf = new TextField()
  TextArea ta

  private void updateStatus(String message) {
    if (Platform.isFxApplicationThread()) {
      tf.setText(message)
    } else {
      Platform.runLater { tf.setText(message) }
    }
  }

  static void main(String[] args) {
    launch(GmdTestGui.class, args)
  }

  /**
   * The main entry point for all JavaFX applications.
   * The start method is called after the init method has returned,
   * and after the system is ready for the application to begin running.
   *
   * <p>
   * NOTE: This method is called on the JavaFX Application Thread.
   * </p>
   *
   * @param primaryStage the primary stage for this application, onto which
   * the application scene can be set.
   * Applications may create other stages, if needed, but they will not be
   * primary stages.
   * @throws java.lang.Exception if something goes wrong
   */
  @Override
  void start(Stage primaryStage) throws Exception {
    BorderPane root = new BorderPane()
    ta = new TextArea('''
    # Test
    
    ```{groovy}
    def a = 3
    for (i in 1..a) {
      out.println("Hello ${i}")  
    }
    ```
    
    - first 
    - second
    '''.stripIndent())
    root.setCenter(ta)
    HBox infoBox = new HBox()
    root.setTop(infoBox)

    tf.setDisable(true)
    HBox.setHgrow(tf, Priority.ALWAYS)
    infoBox.getChildren().add(tf)

    HBox actionBox = new HBox()
    root.setBottom(actionBox)
    File file = new File("target/test.pdf")
    TextField toFile = new TextField(file.getAbsolutePath())
    HBox.setHgrow(toFile, Priority.ALWAYS)
    Button openPdfButton = new Button("Open PDF")
    openPdfButton.setOnAction {
      String text = ta.getText()
      Thread.start {
        try {
          updateStatus("Creating pdf file...")
          Gmd gmd = new Gmd()
          gmd.gmdToPdf(text, file)
          updateStatus("Opening pdf file...")
          Desktop.desktop.open(file)
        } catch (Exception e) {
          updateStatus(e.getMessage() + "; " + e.getCause())
        }
      }
    }
    Button exportButton = new Button("Export to pdf")

    exportButton.setOnAction {
      final String filePath = toFile.getText()
      updateStatus("Exporting to pdf file...")
      final String text = ta.getText()
      Thread.start {
        try {
          File f = new File(filePath)
          Gmd gmd = new Gmd()
          gmd.gmdToPdf(text, f)
          updateStatus("Exported file to " + filePath + ", size = " + new File(filePath).length() + " bytes")
        } catch (Exception e) {
          updateStatus(e.getMessage() + "; " + e.getCause())
        }
      }
    }
    actionBox.getChildren().addAll(toFile, openPdfButton, exportButton)
    primaryStage.setTitle("GmdTestGui")
    primaryStage.setScene(new Scene(root, 650, 400))
    primaryStage.show()
  }
}
