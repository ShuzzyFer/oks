package com.oks.oks1;

import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import jssc.SerialPort;
import jssc.SerialPortException;
import jssc.SerialPortList;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

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
    private Label collisions;
    @FXML
    private Button clearInput;
    @FXML
    private Button clearOutput;
    @FXML
    private TextFlow packetStructureTextFlow;

    private SerialPort inputPort;
    private SerialPort outputPort;

    private String previousText = "";

    private boolean input = false;
    private boolean output = false;
    private String prevoiusOutputPort;

    private int stuffedBits = 0;
    private int totalSentCharacters = 0;

    private int visualDelay = 20;

    private final BlockingQueue<String> packetQueue = new LinkedBlockingQueue<>();
    private boolean isSending = false; // Флаг, указывающий на статус отправки

    private StringBuilder accumulatedData = new StringBuilder(); // Накопитель данных

    @FXML
    private void initialize() {
        updateAvailablePorts();

        inputPortComboBox.setOnAction(event -> {
            try {
                openInputPort();
            } catch (SerialPortException e) {
                throw new RuntimeException(e);
            }
        });
        outputPortComboBox.setOnAction(event -> {
            try {
                openOutputPort();
            } catch (SerialPortException e) {
                throw new RuntimeException(e);
            }
        });
        clearInput.setOnAction(event -> inputTextArea.clear());
        clearOutput.setOnAction(event -> outputTextArea.clear());

        inputTextArea.textProperty()
                .addListener((observable, oldValue, newValue) -> handleTextChange(oldValue, newValue));

        inputTextArea.setEditable(false);
        outputTextArea.setEditable(false);

        // Перехватываем нажатие клавиши Backspace через фильтр событий
        inputTextArea.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.BACK_SPACE) {
                event.consume(); // Полностью блокируем обработку Backspace
            }
        });

        inputTextArea.addEventFilter(KeyEvent.KEY_TYPED, event -> {
            String input = event.getCharacter();
            if (!input.equals("1") && !input.equals("0") && !input.equals("\r")) {
                event.consume();
            }
            if (input.length() > 1) {
                event.consume();
            }
            if (inputTextArea.getCaretPosition() != inputTextArea.getText().length()) {
                event.consume();
            }
        });

        Thread senderThread = new Thread(this::processQueue);
        senderThread.setDaemon(true); // Сделаем поток демоном, чтобы он завершался вместе с программой
        senderThread.start();

        startReceivingData();
    }

    private void processQueue() {
        while (true) {
            try {
                // Берем пакет из очереди (этот метод блокируется, если очередь пуста)
                String packet = packetQueue.take();
                String portNumber = convertToBinaryString(getLastNumber(inputPortComboBox.getValue()));
                String zeros = "0000";
                String newData = packet;

                String fcs = calculateHammingBits(replaceNewlineWithZero(newData));

                // Объединяем все части в одну строку без флага
                String combinedData = zeros + portNumber + packet + fcs;

                // Применяем бит-стаффинг к объединенным данным
                String stuffedData = applyBitStuffing(combinedData);

                String finalPacket = "10010010" + stuffedData;


                for(int i=0; i<SerialPortList.getPortNames().length; i++) {
                    List<String> ls = Arrays.asList(SerialPortList.getPortNames());
                    System.out.println(ls.get(i));
                    System.out.println(inputPortComboBox.getValue());
                    if(Objects.equals(ls.get(i), inputPortComboBox.getValue())) {
                        if(i%2==0) {
                            sendData(finalPacket);
                        }
                    }
                } // Отправка пакета
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void openInputPort() throws SerialPortException {
        closePort(inputPort);
        accumulatedData.setLength(0);
        packetQueue.clear();
        String portName = inputPortComboBox.getValue();

        if (Objects.equals(portName, "-")) {
            inputTextArea.setEditable(false);
            inputPort.closePort();

            return;
        }

        if (Objects.equals(modifyLastNumber(outputPortComboBox.getValue(), false), portName)) {
            showErrorMessage("Неправильный выбор портов", "Вы не можете выбрать этот порт, выберите другой");
            inputPortComboBox.setValue("-");
            return;
        }
        if (portName != null) {
            inputPort = new SerialPort(portName);
            try {
                if (!inputPort.isOpened()) {
                    if (inputPort.openPort()) {
                        input = true;
                        inputTextArea.setEditable(true);
                        inputPort.setParams(
                                SerialPort.BAUDRATE_9600,
                                SerialPort.DATABITS_8,
                                SerialPort.STOPBITS_1,
                                SerialPort.PARITY_NONE
                        );
                        System.out.println("Input port " + portName + " opened successfully.");
                    } else {
                        inputPortComboBox.setValue("-");
                        System.out.println("Failed to open input port " + portName);
                    }
                } else {
                    System.out.println("Input port " + portName + " is already open.");
                }
            } catch (SerialPortException e) {
                System.err.println("Error opening input port: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.out.println("Port name is null. Please select a valid port.");
        }
        if (input != true) {
            inputPortComboBox.setValue("-");
            showErrorMessage("Неправильный выбор портов", "Вы не можете выбрать этот порт, выберите другой");
        } else {
            input = false;
        }
    }

    private void openOutputPort() throws SerialPortException {
        closePort(outputPort);
        String portName = outputPortComboBox.getValue();

        if (portName == "-") {
            outputPort.closePort();
            return;
        }

        if (modifyLastNumber(inputPortComboBox.getValue(), true) == portName) {
            showErrorMessage("Неправильный выбор портов", "Вы не можете выбрать этот порт, выберите другой");
            outputPortComboBox.setValue("-");
            return;
        }

        String correspondingInputPort = modifyLastNumber(inputPortComboBox.getValue(), true);
        if (Objects.equals(portName, correspondingInputPort) && portName != null) {
            outputPortComboBox.setValue("-");
            showErrorMessage("Ошибка", "Порт нельзя использовать одновременно для ввода и вывода.");
            return;
        }

        if (portName != null) {
            outputPort = new SerialPort(portName);
            prevoiusOutputPort = portName;
            try {
                if (outputPort.openPort()) {
                    output = true;
                    outputPort.setParams(SerialPort.BAUDRATE_9600, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
                    System.out.println("Output port " + portName + " opened successfully.");
                } else {
                    outputPortComboBox.setValue("-");
                    handleFailedPortOpening(outputPort);
                }
            } catch (SerialPortException e) {
                e.printStackTrace();
            }
            if (output != true) {
                outputPortComboBox.setValue("-");
                showErrorMessage("Неправильный выбор портов", "Вы не можете выбрать этот порт, выберите другой");
            } else {
                output = false;
            }
        }
    }

    boolean skip=false;
    private void handleTextChange(String oldValue, String newValue) {
        inputTextArea.setOnMouseClicked(event -> {
            if (!inputTextArea.isEditable()) {
                showErrorMessage("Ошибка", "Сначала выберите порты.");
            }
        });

        if (newValue.length() > oldValue.length()) {
            String newChars = newValue.substring(oldValue.length());

            // Добавляем символы в накопитель данных
            for (char ch : newChars.toCharArray()) {
//                if (skip == false && accumulatedData.length() == 0 && ch == '\n') {
//                    skip = true;
//                    return;
//                }

                accumulatedData.append(ch);
                skip = false;

                // Проверяем длину накопленных данных
                if (accumulatedData.length() == 18) {
                    sendPacket(accumulatedData.toString());
                    accumulatedData.setLength(0); // Очищаем накопитель после отправки
                }
            }
        }

        previousText = newValue;
    }

    public static String replaceNewlineWithZero(String input) {
        return input.replace('\n', '0');
    }

    private void sendPacket(String data) {
        // Формируем пакет: номер порта отправителя, 4 нуля, введённые данные и завершающий 0
        packetQueue.offer(data);
        String portNumber = convertToBinaryString(getLastNumber(inputPortComboBox.getValue()));
        String zeros = "0000";
        String newData = data;

        String fcs = calculateHammingBits(replaceNewlineWithZero(newData));

        // Объединяем все части в одну строку без флага
        String combinedData = zeros + portNumber + data + fcs;

        // Применяем бит-стаффинг к объединенным данным
        String stuffedData = applyBitStuffing(combinedData);

        packetStructureTextFlow.setStyle("-fx-font-size: 14px;");
        packetStructureTextFlow.getChildren().clear();
        packetStructureTextFlow.getChildren().add(new Text("10010010"));
        boolean check = false;
        // Формируем и выводим данные с выделением битов-стаффинга
        for (int i = 0; i < stuffedData.length()-6; i++) {

            char ch = stuffedData.charAt(i);
            if(ch=='\n') {
                String str;
                str = "\\n";
                Text textNode = new Text(str);
                packetStructureTextFlow.getChildren().add(textNode);
            } else {
                Text textNode = new Text(String.valueOf(ch));
                packetStructureTextFlow.getChildren().add(textNode);
            }

            // Проверяем на наличие бит-стаффинга
            if (i >= 6 && stuffedData.substring(i - 6, i + 1).equals("1001001")) {
                // Выделяем бит-стаффинга цветом
                Text highlightedBit = new Text("1");
                highlightedBit.setFill(Color.RED); // Устанавливаем цвет для выделения
                packetStructureTextFlow.getChildren().add(highlightedBit); // Добавляем выделенный бит
                i++; // Пропускаем следующий символ, так как это уже обработано
            }
        }

        for (int i = stuffedData.length()-6; i<stuffedData.length();i++) {
            if (i >= 6 && stuffedData.substring(i - 6, i + 1).equals("1001001")) {
                check=true;
            }
        }
        if(check==true) {
            packetStructureTextFlow.getChildren().add(new Text(" "));
        }

        for (int i = stuffedData.length()-6; i<stuffedData.length();i++) {
            if(i == stuffedData.length()-5){
                packetStructureTextFlow.getChildren().add(new Text(" "));
            }
            char ch = stuffedData.charAt(i);
            if(ch=='\n') {
                String str;
                str = "\\n";
                Text textNode = new Text(str);
                packetStructureTextFlow.getChildren().add(textNode);
            } else {
                Text textNode = new Text(String.valueOf(ch));
                packetStructureTextFlow.getChildren().add(textNode);
            }
            // Проверяем на наличие бит-стаффинга
            if (i >= 6 && stuffedData.substring(i - 6, i + 1).equals("1001001")) {
                // Выделяем бит-стаффинга цветом
                Text highlightedBit = new Text("1");
                highlightedBit.setFill(Color.RED); // Устанавливаем цвет для выделения
                packetStructureTextFlow.getChildren().add(highlightedBit); // Добавляем выделенный бит
                i++; // Пропускаем следующий символ, так как это уже обработано
            }
        }
    }

    private String applyBitStuffing(String data) {
        stuffedBits=0;
        StringBuilder stuffedData = new StringBuilder();
        StringBuilder origData = new StringBuilder(data);
        for (int i = 0; i < origData.length(); i++) {
            stuffedData.append(origData.charAt(i));
            // Проверяем последние 7 символов, чтобы найти "1001001"
            if (i >= 6 && origData.substring(i - 6, i + 1).equals("1001001")) {
                stuffedData.append('1'); // Вставляем '1' после "1001001"
                origData.insert(i+1, "1");
                stuffedBits++;
                i++;
            }
        }
        return stuffedData.toString();
    }

    private String convertToBinaryString(int number) {
        // Преобразуем число в двоичное представление и форматируем до 4 символов
        return String.format("%4s", Integer.toBinaryString(number)).replace(' ', '0');
    }

    private void sendData(String data) {
        if (inputPort != null && inputPort.isOpened() && !data.isEmpty()) {
            Random random = new Random();
            StringBuilder sendStatus = new StringBuilder(); // Для накопления статуса отправки

            try {
                isSending = true;
                for (char c : data.toCharArray()) {
                    boolean sent = false;
                    int attemptCount = 0; // Счётчик попыток для текущего символа
                    StringBuilder symbolStatus = new StringBuilder(); // Для записи статуса текущего символа

                    while (!sent && attemptCount < 10) {
                        int channelBusyChance = random.nextInt(100);
                        if (channelBusyChance < 30) {
                            int chance = random.nextInt(100);
                            inputPort.writeBytes(new byte[]{(byte) c});
                            if (chance > 30) {
                                symbolStatus.append("."); // Добавляем точку после успешной отправки
                                sent = true; // Символ успешно отправлен
                                totalSentCharacters += 1;

                                // Добавляем статус текущего символа в общий статус
                                sendStatus.append(symbolStatus).append(" "); // Добавляем пробел между символами
                            } else {
                                inputPort.writeBytes(new byte[]{'\u0003'});
                                symbolStatus.insert(0, "!"); // Добавляем "!" в начале для каждой коллизии

                                // Рассчитываем задержку согласно формуле 0 ≤ r ≤ 2^k, где k = min(attemptCount, 10)
                                int delay = visualDelay * random.nextInt((int) Math.pow(2, Math.min(attemptCount, 10)) + 1);
                                Thread.sleep(delay);
                                attemptCount++; // Увеличиваем счётчик попыток
                            }

                            // Обновляем интерфейс строкой состояния отправки после каждой отправки
                            Platform.runLater(() -> collisions.setText(sendStatus.toString().trim()));

                            if (!sent) {
                                Thread.yield(); // Освобождаем процессор для других потоков
                            }
                        }
                    }
                }

                // Отправляем символ конца кадра, например, '\u0004' (EOT) после отправки всех данных
                inputPort.writeBytes(new byte[]{'\u0004'});
                totalSentCharacters-=(4+4+8+5+stuffedBits/2);
                Platform.runLater(() -> transmittedCharactersLabel.setText("Символов передано: " + totalSentCharacters));

            } catch (SerialPortException | InterruptedException e) {
                e.printStackTrace();
            } finally {
                isSending = false;
            }
        } else {
            System.out.println("Input port is not open or data is empty.");
        }
    }


    private void startReceivingData() {
        new Thread(() -> {
            StringBuilder receivedDataBuffer = new StringBuilder();
            long lastReceivedTime = System.currentTimeMillis(); // Время последнего получения данных

            while (true) {
                if (outputPort != null && outputPort.isOpened()) {
                    try {
                        byte[] buffer = outputPort.readBytes();
                        if (buffer != null && buffer.length > 0) {
                            for (byte b : buffer) {
                                char c = (char) b;
                                lastReceivedTime = System.currentTimeMillis(); // Обновляем время последнего получения данных

                                if (c == '\u0004') { // Проверяем символ конца кадра
                                    processReceivedPacket(receivedDataBuffer.toString());
                                    receivedDataBuffer.setLength(0); // Очищаем буфер после обработки
                                } else if (c == '\u0003') { // Символ коллизии
                                    if (receivedDataBuffer.length() > 0) {
                                        receivedDataBuffer.deleteCharAt(receivedDataBuffer.length() - 1); // Удаляем последний символ
                                    }
                                } else {
                                    receivedDataBuffer.append(c); // Накапливаем данные
                                }
                            }
                        }
                    } catch (SerialPortException e) {
                        e.printStackTrace();
                    }
                }

                // Проверяем, прошло ли больше 4.5 секунд с последнего получения данных
                if (System.currentTimeMillis() - lastReceivedTime > visualDelay * 1100L) {
                    receivedDataBuffer.setLength(0); // Очищаем буфер, если данные не поступали более 4.5 секунд
                }

                try {
                    Thread.sleep(1); // Минимальная задержка для основного цикла
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }





    private void processReceivedPacket(String packet) {
        // Удаляем вставленные единицы из данных
        String cleanedData = removeBitStuffing(packet.substring(8, packet.length())); // Пропускаем флаг, номер порта и 4 нуля, завершающий 0

        // Создаем объект Random для генерации случайных чисел
        Random random = new Random();
        String errorData;

        // Генерируем случайное число от 0 до 99
        int randomValue = random.nextInt(100); // Генерирует значение от 0 до 99

        // Вводим ошибку в 30% случаев
        if (randomValue < 30) { // Если значение меньше 30, значит, вводим ошибку
            errorData = introduceSingleError(cleanedData.substring(8, cleanedData.length()-5));
        } else {
            errorData = cleanedData.substring(8, cleanedData.length()-5); // Если нет ошибки, просто передаем данные
        }

        String correctedData = correctHammingErrors(errorData, cleanedData.substring(cleanedData.length()-5));

        // Добавляем данные в текстовое поле
        Platform.runLater(() -> {
            outputTextArea.appendText(correctedData); // Добавляем данные
            //outputTextArea.appendText("\n"); // Добавляем новую строку
        });
    }

    private String removeBitStuffing(String data) {
        StringBuilder cleanedData = new StringBuilder();
        for (int i = 0; i < data.length(); i++) {
            cleanedData.append(data.charAt(i));
            // Проверяем последние 7 символов на наличие "1001001"
            if (i >= 6 && data.substring(i - 6, i + 1).equals("1001001")) {
                // Если следующий символ '1', пропускаем его
                if (i + 1 < data.length() && data.charAt(i + 1) == '1') {
                    i++; // Пропускаем следующий символ
                }
            }
        }
        return cleanedData.toString();
    }

    private void updateAvailablePorts() {
        List<String> availablePorts = Arrays.asList(SerialPortList.getPortNames());
        Platform.runLater(() -> {
            for (int i = 0; i < availablePorts.size(); i++) {
                //if (i % 2 == 0 && getLastNumber(availablePorts.get(i))<=15) {
                inputPortComboBox.getItems().add(availablePorts.get(i));
                // } else if(getLastNumber(availablePorts.get(i))<=16)
                outputPortComboBox.getItems().add(availablePorts.get(i));
            }
            inputPortComboBox.getItems().add("-");
            outputPortComboBox.getItems().add("-");
        });
    }

    private void closePort(SerialPort port) {
        if (port != null && port.isOpened()) {
            try {
                port.closePort();
            } catch (SerialPortException e) {
                e.printStackTrace();
            }
        }
    }

    private String modifyLastNumber(String str, boolean increment) {
        if (str != null && str.length() > 3) {
            String portNumber = str.substring(3);
            int num = Integer.parseInt(portNumber);
            if (increment) {
                num++;
            } else {
                num--;
            }
            return str.substring(0, 3) + num;
        }
        return str;
    }

    public Integer getLastNumber(String input) {
        // Удаляем пробелы в начале и конце строки
        input = input.trim();

        // Находим все числа в строке
        String[] parts = input.split("\\D+"); // Разделяем по нечисловым символам

        // Ищем последнее число
        for (int i = parts.length - 1; i >= 0; i--) {
            if (!parts[i].isEmpty()) {
                return Integer.parseInt(parts[i]); // Преобразуем строку в число и возвращаем
            }
        }

        return null; // Возвращаем null, если чисел нет
    }

    private void handleFailedPortOpening(SerialPort port) throws SerialPortException {
        if (port.isOpened()) {
            System.out.println("Failed to open port " + port.getPortName());
            port.closePort();
        }
    }


    static String calculateHammingBits(String data) {
        int dataLength = data.length();
        StringBuilder controlBits = new StringBuilder();
        int r = 1;
        while (Math.pow(2, r) < (dataLength + r + 1)) {
            r++;
        }

        for (int i = 0; i < r; i++) {
            char parity = '0';

            for (int j = 1; j <= dataLength; j++) {
                if (((j >> i) & 1) == 1) {
                    parity = xorChars(parity, data.charAt(j - 1));
                }
            }
            controlBits.append(parity);
        }
        return controlBits.toString();
    }

    // Метод для операции XOR между символами '1' и '0'
    static char xorChars(char a, char b) {
        return (a == b) ? '0' : '1';
    }

    static String correctHammingErrors(String data, String providedControlBits) {
        // Сохраняем позиции символов \n
        List<Integer> newlinePositions = new ArrayList<>();
        StringBuilder modifiedData = new StringBuilder();

        // Заменяем \n на 0 и сохраняем позиции \n
        for (int i = 0; i < data.length(); i++) {
            if (data.charAt(i) == '\n') {
                newlinePositions.add(i);
                modifiedData.append('0');
            } else {
                modifiedData.append(data.charAt(i));
            }
        }

        // Вычисляем контрольные биты для модифицированных данных
        int errorPosition = 0;
        String dataWithoutNewlines = modifiedData.toString();

        int r = 1;
        while (Math.pow(2, r) < (dataWithoutNewlines.length() + r + 1)) {
            r++;
        }

        for (int i = 0; i < r; i++) {
            char parity = providedControlBits.charAt(i);

            for (int j = 1; j <= dataWithoutNewlines.length(); j++) {
                if (((j >> i) & 1) == 1) {
                    parity = xorChars(parity, dataWithoutNewlines.charAt(j - 1));
                }
            }

            if (parity != '0') {
                errorPosition += (int) Math.pow(2, i);
            }
        }

        // Если была обнаружена ошибка, исправляем её
        if (errorPosition > 0) {
            // Индекс символа в строке data (0-based)
            int index = errorPosition - 1;

            // Проверяем, находится ли индекс в пределах данных
            if (index < dataWithoutNewlines.length()) {
                // Исправляем ошибку, меняем символ
                char correctedBit = (dataWithoutNewlines.charAt(index) == '0') ? '1' : '0';
                modifiedData.setCharAt(index, correctedBit);
            } else {
                System.out.println("Ошибка: Позиция ошибки выходит за пределы данных.");
            }
        }

        // Восстанавливаем символы \n на исходные позиции
        for (int pos : newlinePositions) {
            modifiedData.setCharAt(pos, '\n');
        }

        // Возвращаем исправленные данные с восстановленными \n
        return modifiedData.toString();
    }

    static String introduceSingleError(String data) {
        // Проверяем, что длина строки соответствует 18 символам
        if (data.length() != 18) {
            throw new IllegalArgumentException("Длина строки должна быть равна 18.");
        }

        Random random = new Random();
        while(true) {
            int index = random.nextInt(18);

            StringBuilder corruptedData = new StringBuilder(data);

            char currentBit = data.charAt(index);
            if(currentBit!='\n') {
                char erroneousBit = (currentBit == '0') ? '1' : '0';
                corruptedData.setCharAt(index, erroneousBit);

                return corruptedData.toString();
            }

        }

    }

    private void showErrorMessage(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}