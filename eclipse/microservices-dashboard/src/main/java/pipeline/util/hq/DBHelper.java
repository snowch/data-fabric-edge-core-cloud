package pipeline.util.hq;

import com.mapr.db.MapRDB;
import com.mapr.db.Table;
import java.io.IOException;


public class DBHelper {

      // Helper class for database operations.
	
	  private DBHelper() {
	  }

	  public static void deleteTable(String tableName) throws IOException {
		  
		  System.out.println("Deleting table " + tableName + "...");
		  
		  if ( MapRDB.tableExists(tableName) ) {
			  MapRDB.deleteTable(tableName);
			  System.out.println("Table deleted.");
		  } else {
			  System.out.println("Deletion skipped... Table does not exist.");
		  }

	  }
	  
	  public static void createTable(String tableName) throws IOException {
		  
		  System.out.println("Creating table " + tableName + "...");
		  MapRDB.createTable(tableName);
		  System.out.println("Table created.");
		  
	  }
	  
	  public static Table getTable(String tableName) throws IOException {
		  if ( ! MapRDB.tableExists(tableName) ) {
			  return null;
		  } else {
			  return MapRDB.getTable(tableName);
		  }
	  }

	  
	  // Command line test.	
	  public static void main(String[] args) throws IOException {

		  String usage = "Usage: DBHelper [delete | create | get] <tablePath>";
		  
		  if (args.length < 2) {
			  System.out.println(usage);
		      System.exit(1);
		  }
		  
		  String tableName = args[1];
		  switch (args[0]) {
		  	
		  	case "delete":
		  		DBHelper.deleteTable(tableName);
		  		break;
		  		
		  	case "create":
		  		DBHelper.createTable(tableName);
		  		break;
		  		
		  	case "get":
		  		Table t = DBHelper.getTable(tableName);
		  		if (t == null) {
		  			System.out.println("Table does not exist.");
		  		} else {
			  		System.out.println("Table retrieved.");
		  		}
		  		break;
		  		
		  	default:
		  		System.out.println(usage);
		  
		  }
		    
	  }
	
}
