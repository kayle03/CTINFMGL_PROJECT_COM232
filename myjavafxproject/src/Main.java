import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {
    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/LoginPage.fxml"));
        Parent root = loader.load(); // Load the root element
        primaryStage.setScene(new Scene(root));
        primaryStage.setTitle("Fake Discord");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}