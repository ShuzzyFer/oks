package com.oks.oks1;

import com.fazecast.jSerialComm.SerialPort;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.sun.javafx.application.PlatformImpl.exit;

public class Controller {
    @FXML
    private TextArea inputTextArea;
    @FXML
    private TextArea outputTextArea;
    @FXML
    private ComboBox<String> inputPortComboBox;
    @FXML
    private ComboBox<String> outputPortComboBox;
    @FXML
    private Label transmittedCharactersLabel;
    @FXML
    private Button clearInput;
    @FXML
    private Button clearOutput;

    private SerialPort inputPort;
    private SerialPort outputPort;

    // Хранение предыдущего текста для сравнения
    private String previousText = "";
    private int totalSentCharacters = 0;

    @FXML
    private void initialize() {
        // Инициализация COM-портов
        for (int i = 0; i < SerialPort.getCommPorts().length; i += 2) {
            inputPortComboBox.getItems().add(Arrays.stream(SerialPort.getCommPorts())
                    .collect(Collectors.toList()).get(i).getSystemPortName());
        }
        inputPortComboBox.getItems().add(null);
        outputPortComboBox.getItems().add(null);

        inputPortComboBox.setOnAction(event -> openInputPort());
        outputPortComboBox.setOnAction(event -> openOutputPort());
        clearInput.setOnAction(event -> {inputTextArea.clear();});
        clearOutput.setOnAction(event -> {outputTextArea.clear();});

        // Обработка изменений в TextArea для отправки данных
        inputTextArea.textProperty()
                .addListener((observable, oldValue, newValue) -> handleTextChange(oldValue, newValue));
        inputTextArea.setText("   ");
        inputTextArea.setEditable(false);
        outputTextArea.setEditable(false);
        inputTextArea.addEventFilter(KeyEvent.KEY_TYPED, event -> {

            String input = event.getCharacter();

            // Разрешаем только символы '1', '0' и Enter
            if (!input.equals("1") && !input.equals("0") && !input.equals("\r")) {
                event.consume(); // Блокируем ввод
            }

            // Дополнительно проверяем, чтобы вводился только один символ за раз
            if (input.length() > 1) {
                event.consume(); // Блокируем, если введено больше одного символа
            }

            int caretPosition = inputTextArea.getCaretPosition();
            int textLength = inputTextArea.getText().length();

            // Если каретка не в конце текста, блокируем ввод
            if (caretPosition != textLength) {
                event.consume(); // Блокируем событие
            }
        });

        // Запуск процесса получения данных в фоновом режиме
        startReceivingData();
        updateAvailablePorts();
    }

    private void openInputPort() {
        if (inputPort != null && inputPort.isOpen()) {
            inputPort.closePort();
        }
        String portName = inputPortComboBox.getValue();

        if (portName != null) {
            inputPort = SerialPort.getCommPort(portName);
            inputPort.setComPortParameters(9600, 8, 1, SerialPort.NO_PARITY);
            if (inputPort.openPort()) {
                System.out.println("Input port " + portName + " opened successfully.");
            } else {
                if (openPortWithRetries(inputPort, 3, 50)) {
                    System.out.println("Success to open port after 5 attempts.");
                }
                else {
                    portName = inputPortComboBox.getValue();
                    String finalPortName = portName;
                    inputPortComboBox.setValue(null);
                    //Platform.runLater(() -> inputPortComboBox.getItems().remove(finalPortName));
                    //System.out.println("Failed to open input port " + portName + ".");
                    //Platform.runLater(() -> inputPortComboBox.getItems().add(finalPortName));
                }
            }
        }
    }

    private void openOutputPort() {
        if (outputPort != null && outputPort.isOpen()) {
            outputPort.closePort();
        }

        String portName = outputPortComboBox.getValue();
        String port1 = modifyLastNumber(inputPortComboBox.getValue(), true);
        System.out.println(port1);
        System.out.println(portName);
        if(Objects.equals(portName, port1) && portName!=null) {
            outputPortComboBox.setValue(null);
            showErrorMessage("Приложение не может принимать данные из этого COM-порта", "Этот порт можно выбрать только в другом приложении");
            SerialPort.getCommPort(portName).closePort();
            return;
        }
        if (portName != null) {
            outputPort = SerialPort.getCommPort(portName);
            outputPort.setComPortParameters(9600, 8, 1, SerialPort.NO_PARITY);
            if (outputPort.openPort()) {
                System.out.println("Output port " + portName + " opened successfully.");
            } else {
                if (openPortWithRetries(outputPort, 5, 50)) {
                    System.out.println("Success to open port after 5 attempts.");
                }
                else {
                    Platform.runLater(() -> outputPortComboBox.getItems().remove(outputPort.getSystemPortName()));
                    System.out.println("Failed to open output port " + portName + ".");
                }
            }
        }
    }

    private boolean changed = false;

    private void handleTextChange(String oldValue, String newValue) {

        inputTextArea.setOnMouseClicked(event -> {
            if (!inputTextArea.isEditable()) {
                showErrorMessage("Ошибка ввода", "Сначала выберите порты ввода и вывода.");
            }
        });
        if (newValue.length() > oldValue.length()) {
            if(changed == false) {
                changed=true;
                inputTextArea.clear();
            }
            // Символы добавлены
            String newChars = newValue.substring(oldValue.length()); // Получаем только добавленные символы
            sendData(newChars);
        }
        // Обновляем предыдущий текст
        previousText = newValue;
    }

    private void sendData(String data) {
        if (inputPort != null && inputPort.isOpen() && !data.isEmpty()) {
            byte[] dataToSend = data.getBytes(java.nio.charset.StandardCharsets.UTF_8); // Преобразование строки в байты UTF-8
            inputPort.writeBytes(dataToSend, dataToSend.length);
            totalSentCharacters += data.length(); // Обновляем счетчик отправленных символов
            transmittedCharactersLabel.setText("Символов передано: " + totalSentCharacters);
        } else {
            System.out.println("Input port is not open or data is empty.");
        }
    }

    private void startReceivingData() {
        new Thread(() -> {
            while (true) {
                if (outputPort != null && outputPort.isOpen()) {
                    byte[] readBuffer = new byte[1024];
                    int numRead = outputPort.readBytes(readBuffer, readBuffer.length);
                    if (numRead > 0) {
                        String receivedData = new String(readBuffer, 0, numRead, java.nio.charset.StandardCharsets.UTF_8);
                        System.out.println("Data received on output port: " + receivedData);
                        Platform.runLater(() -> outputTextArea.appendText(receivedData));
                    }
                }
                try {
                    Thread.sleep(100); // Интервал проверки в 100 мс
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private boolean openPortWithRetries(SerialPort port, int retries, long delay) {
        for (int i = 0; i < retries; i++) {
            if (port.openPort()) {
                System.out.println("Port opened successfully on attempt " + (i + 1));
                return true;
            } else {
                System.out.println("Failed to open port on attempt " + (i + 1) + ", retrying...");
                try {
                    Thread.sleep(delay);  // Задержка между попытками
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        if(getLastNumber(port.getSystemPortName())%2==0) {
            outputPortComboBox.setValue(null);
        }
        if(getLastNumber(port.getSystemPortName())%2==1) {
            inputPortComboBox.setValue(null);
        }
        showErrorMessage("COM-порт уже открыт", "Выбранный вами COM-порт уже открыт другим приложением. Выберите другой COM-порт.");
        return false;
    }

    public static String modifyLastNumber(String str, boolean increase) {
        if (str == null || str.isEmpty()) {
            return str; // Или бросьте исключение, если строка не должна быть пустой
        }

        int lastIndex = -1;
        int start = -1;
        for (int i = str.length() - 1; i >= 0; i--) {
            if (Character.isDigit(str.charAt(i))) {
                if (start == -1) {
                    start = i; // Начало числа
                }
                lastIndex = i; // Конец числа
            } else if (start != -1) {
                break; // Заканчиваем поиск после последней цифры
            }
        }

        if (lastIndex == -1) {
            return str; // Нет чисел в строке
        }

        // Извлекаем последнее число
        String numberStr = str.substring(start, lastIndex + 1);
        int number = Integer.parseInt(numberStr);

        // Изменяем число
        if (increase) {
            number += 1;
        } else {
            number -= 1;
        }

        // Создаем новую строку с измененным числом
        return str.substring(0, start) + number + str.substring(lastIndex + 1);
    }

    public static Integer getLastNumber(String str) {
        if (str == null || str.isEmpty()) {
            return null;  // Возвращаем null, если строка пустая или равна null
        }

        int lastIndex = -1;
        int start = -1;

        // Поиск последнего числа в строке
        for (int i = str.length() - 1; i >= 0; i--) {
            if (Character.isDigit(str.charAt(i))) {
                if (start == -1) {
                    start = i; // Начало последнего числа
                }
                lastIndex = i; // Конец числа
            } else if (start != -1) {
                break; // Заканчиваем поиск, если встретили нечисловой символ после последней цифры
            }
        }

        if (lastIndex == -1) {
            return null;  // Чисел в строке нет
        }

        // Извлекаем и возвращаем последнее число как Integer
        String numberStr = str.substring(lastIndex, start + 1);
        return Integer.parseInt(numberStr);
    }

    private void updateAvailablePorts() {
        new Thread(() -> {
            while(true) {
                List<SerialPort> ports = List.of(SerialPort.getCommPorts());
                for (int i = 0; i < ports.size(); i++) {
                    if (getLastNumber(ports.get(i).getSystemPortName()) % 2 == 1) {
                        String port = modifyLastNumber(ports.get(i).getSystemPortName(), true);
                        SerialPort portNext = SerialPort.getCommPort(port);
                        if (!ports.get(i).openPort()) {
                            if (!outputPortComboBox.getItems().contains(port)) {
                                Platform.runLater(() -> outputPortComboBox.getItems().add(port));
                            }

                            if(!portNext.openPort()) {
                                Platform.runLater(() -> inputTextArea.setEditable(true));
                                portNext.closePort();
                            }
                            else {
                                Platform.runLater(() -> inputTextArea.setEditable(false));
                                portNext.closePort();
                            }
                            if(inputPortComboBox.getValue() == null) {
                                Platform.runLater(() -> inputTextArea.setEditable(false));
                            }
                        } else {
                            Platform.runLater(() -> outputPortComboBox.getItems().remove(port));
                            ports.get(i).closePort();
                        }
                    }
                }
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
    }).start();
    }

    private void showErrorMessage(String title, String message) {
        // Создаем и отображаем маленькое окно с сообщением об ошибке
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}