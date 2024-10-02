package com.oks.oks1;

import com.fazecast.jSerialComm.SerialPort;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class Main extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(Main.class
                .getResource("main.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 800, 450);

        stage.setOnCloseRequest(event -> {
            System.out.println("Приложение закрывается...");
            handleClosingApp();
        });

        stage.setTitle("Передача данных через COM-порты");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }

    private void handleClosingApp() {
        for(SerialPort port: SerialPort.getCommPorts()) {
            port.closePort();
        }
        Platform.exit();
        System.exit(0);
    }
}