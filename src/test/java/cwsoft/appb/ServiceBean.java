package cwsoft.appb;

import javax.annotation.ManagedBean;
import javax.annotation.PostConstruct;
import javax.inject.Inject;

// Second bean: ServiceBean which depends on DatabaseConnection
@ManagedBean
public class ServiceBean {

    private final DatabaseConnection databaseConnection;

    @Inject
    public ServiceBean(DatabaseConnection databaseConnection) {
        this.databaseConnection = databaseConnection;
    }

    public void performService() {
        System.out.println("Performing service with database connection...");
        databaseConnection.connect();
    }

    @PostConstruct
    public void init() {
        System.out.println("ServiceBean initialized!");
    }
}

