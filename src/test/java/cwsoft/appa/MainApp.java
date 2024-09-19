package cwsoft.appa;


import com.cwsoft.flydi.FlyDI;
import com.cwsoft.flydi.BeanScannerConfig;

public class MainApp {

    public static void main(String[] args) {
        // initialise DI
        var di = new FlyDI(
                BeanScannerConfig
                        .builder()
                        .includePackage(MainApp.class.getPackage().getName())
                        .build()
        );

        di.scanForBeans();

        // Inject dependencies between the beans
        di.injectBeans();

        // Run any @PostConstruct methods
        di.runPostConstructors();

        // Fetch the ServiceBean and call a method
        ServiceBean serviceBean = di.getBean(ServiceBean.class);
        if (serviceBean != null) {
            serviceBean.performService();
        }

        // Fetch all beans
        System.out.println("Beans available: " + di.getAllBeans());
    }
}
