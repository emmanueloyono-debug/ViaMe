package com.viame.ui;

import com.viame.MainApp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.net.URL;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Desktop Application Integrity and FXML Verification Test")
public class DesktopIntegrityTest {

    private static final List<String> DESKTOP_VIEWS = List.of(
            MainApp.LOGIN_FXML,
            MainApp.REGISTER_FXML,
            MainApp.PASSENGER_DASHBOARD_FXML,
            MainApp.DRIVER_DASHBOARD_FXML,
            MainApp.ADMIN_CONSOLE_FXML
    );

    @Test
    @DisplayName("Verify that all MainApp view paths exist and are accessible on the classpath")
    public void testMainAppViewResourcesExist() {
        for (String viewPath : DESKTOP_VIEWS) {
            URL url = MainApp.class.getResource(viewPath);
            assertNotNull(url, "Resource must exist on classpath: " + viewPath);
        }
    }

    @Test
    @DisplayName("Verify that global Desktop stylesheet exists and is non-empty")
    public void testStylesheetResourceExists() throws Exception {
        URL url = MainApp.class.getResource(MainApp.STYLESHEET);
        assertNotNull(url, "Stylesheet must exist on classpath: " + MainApp.STYLESHEET);
        try (InputStream is = url.openStream()) {
            byte[] bytes = is.readAllBytes();
            assertTrue(bytes.length > 100, "Stylesheet must contain CSS rules");
        }
    }

    @Test
    @DisplayName("Verify that each FXML view is well-formed XML and binds to a valid Controller class")
    public void testFxmlXmlWellFormedAndControllerClassesExist() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();

        for (String viewPath : DESKTOP_VIEWS) {
            try (InputStream is = MainApp.class.getResourceAsStream(viewPath)) {
                assertNotNull(is, "Cannot read view stream: " + viewPath);
                Document doc = builder.parse(is);
                Element root = doc.getDocumentElement();
                assertNotNull(root, "Root element cannot be null for: " + viewPath);

                String controllerClass = root.getAttributeNS("http://javafx.com/fxml", "controller");
                if (controllerClass == null || controllerClass.isBlank()) {
                    controllerClass = root.getAttribute("fx:controller");
                }

                assertNotNull(controllerClass, "FXML root must declare fx:controller: " + viewPath);
                assertTrue(!controllerClass.isBlank(), "fx:controller must not be blank in: " + viewPath);

                // Verify that the controller class exists and can be loaded
                Class<?> clazz = Class.forName(controllerClass);
                assertNotNull(clazz, "Controller class must exist: " + controllerClass);
            }
        }
    }

    @Test
    @DisplayName("Verify that Desktop Controllers have zero dependencies on RestApiServer")
    public void testDesktopControllersDecoupledFromRestApiServer() {
        List<Class<?>> controllers = List.of(
                com.viame.controller.AuthController.class,
                com.viame.controller.RegisterController.class,
                com.viame.controller.PassengerController.class,
                com.viame.controller.DriverController.class,
                com.viame.controller.AdminReportController.class,
                com.viame.controller.LoginController.class,
                com.viame.controller.PassengerDashboardController.class,
                com.viame.controller.DriverDashboardController.class,
                com.viame.controller.AdminDashboardController.class
        );

        for (Class<?> ctrl : controllers) {
            for (var field : ctrl.getDeclaredFields()) {
                assertTrue(!field.getType().getName().contains("RestApiServer"),
                        "Controller " + ctrl.getSimpleName() + " must NOT depend on RestApiServer (Field: " + field.getName() + ")");
            }
        }
    }
}
