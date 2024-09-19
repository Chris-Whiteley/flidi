package cwsoft.appa;

import javax.annotation.ManagedBean;
import javax.annotation.PostConstruct;
import javax.inject.Inject;

// Second bean: ServiceBean which depends on DatabaseConnection
@ManagedBean
public class ServiceBean {

    private  DatabaseConnection databaseConnection;

    @Inject
    public void setDatabaseConnection(DatabaseConnection databaseConnection) {
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

