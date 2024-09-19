package cwsoft.appb;

import com.cwsoft.flydi.FlyDI;
import com.cwsoft.flydi.BeanScannerConfig;


public class MainApp {

    public static void main(String[] args) {
        // Initialize the FlyDI
        FlyDI flyDI = new FlyDI(
                BeanScannerConfig
                        .builder()
                        .includePackage(MainApp.class.getPackage().getName())
                        .build()
        );

        // flyDI.findBeans();
        flyDI.scanForBeans();

        // flyDI.createAndInjectBeans
        // 1. anaylyse dependencies need to look at constructor and @Inject methods...
        // 2. in dependency order
        //    - create the bean using relevant constructor (if takes args then get the beans to inject) and use them in constructor
        //    - if has any inject methods then get the beans from the instantiated beans map
        //    - run any post contruct method on the bean
        //    - add the initialised bean to the instantiated beans map

        // Inject dependencies between the beans
        flyDI.injectBeans();

        // Run any @PostConstruct methods
        flyDI.runPostConstructors();

        // Fetch the ServiceBean and call a method
        ServiceBean serviceBean = flyDI.getBean(ServiceBean.class);
        if (serviceBean != null) {
            serviceBean.performService();
        }

        // Fetch all beans
        System.out.println("Beans available: " + flyDI.getAllBeans());
    }
}
