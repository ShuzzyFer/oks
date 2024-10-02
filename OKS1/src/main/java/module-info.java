module com.oks.oks1 {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.fazecast.jSerialComm;


    opens com.oks.oks1 to javafx.fxml;
    exports com.oks.oks1;
}