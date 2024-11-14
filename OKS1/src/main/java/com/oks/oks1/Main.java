package com.oks.oks1;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import jssc.SerialPort;
import jssc.SerialPortException;
import jssc.SerialPortList;

import java.io.IOException;

public class Main extends Application {

    // Массив для хранения открытых портов
    private SerialPort[] openPorts;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(Main.class.getResource("main.fxml"));
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
        // Получаем список всех открытых портов
        if (openPorts != null) {
            for (SerialPort port : openPorts) {
                if (port.isOpened()) {
                    try {
                        port.closePort();
                        System.out.println("Закрытие порта " + port.getPortName());
                    } catch (SerialPortException e) {
                        System.out.println("Ошибка при закрытии порта " + port.getPortName());
                        e.printStackTrace();
                    }
                }
            }
        }

        // Закрываем приложение
        Platform.exit();
        System.exit(0);
    }

    // Дополнительный метод для инициализации портов
    private void initializePorts() {
        // Получаем список всех доступных COM-портов
        String[] portNames = SerialPortList.getPortNames();

        // Создаем массив для хранения открытых портов
        openPorts = new SerialPort[portNames.length];

        // Открываем каждый порт и сохраняем его в массив
        for (int i = 0; i < portNames.length; i++) {
            SerialPort serialPort = new SerialPort(portNames[i]);
            try {
                serialPort.openPort();
                serialPort.setParams(SerialPort.BAUDRATE_9600, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
                openPorts[i] = serialPort;
                System.out.println("Порт " + portNames[i] + " открыт.");
            } catch (SerialPortException e) {
                System.out.println("Не удалось открыть порт " + portNames[i]);
                e.printStackTrace();
            }
        }
    }
}