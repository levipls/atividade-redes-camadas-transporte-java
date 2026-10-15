package br.edu.redes.simulador;

import br.edu.redes.simulador.ui.MainView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class App extends Application {
    @Override public void start(Stage stage) {
        Scene scene = new Scene(new MainView(stage), 1600, 1020);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        stage.setMinWidth(1200); stage.setMinHeight(780);
        stage.setScene(scene); stage.show();
    }
    public static void main(String[] args) { launch(args); }
}
