package cwsoft.appb;

import javax.annotation.ManagedBean;
import javax.annotation.PostConstruct;

// First bean: DatabaseConnection
@ManagedBean
public class DatabaseConnection {

    public void connect() {
        System.out.println("Connected to database!");
    }

    @PostConstruct
    public void init() {
        System.out.println("DatabaseConnection initialized!");
    }
}
