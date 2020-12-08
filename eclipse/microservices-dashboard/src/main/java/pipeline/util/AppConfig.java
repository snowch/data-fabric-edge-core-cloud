package pipeline.util;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.util.Properties;
import org.apache.commons.lang.StringUtils;


/* ******************************************************************************************
 *  Loads application configuration values from a properties file.
 *  System delay parameters can be modified based on feedback received from the Dashboard.
 * ****************************************************************************************** */
public class AppConfig {

	private static AppConfig appConfig;
	private static Properties properties;
	private static String demoEdition;
	private static int delayDataFeedMS;
	private static int delayProcessingMS;
	private static int delayErrorMS;
	
	public static void setEdition(String thisDemoEdition) {
		demoEdition = thisDemoEdition;
	}
	
	// instantiated by first call to getPropertyValue.
	private AppConfig() {
		
		String filename = "config.properties";
		String filenameFull = "./config/" + filename;
		properties = new Properties();
		
		// Use if loading properties file from the file system.
		
		// Look for the config file in the file system.
		FileInputStream inputFile = null;
		try {
			inputFile = new FileInputStream(filenameFull);
			System.out.println("AppConfig: Loading config from " + filenameFull);
			properties.load(inputFile);
		} catch (FileNotFoundException e) {

			System.out.println("AppConfig: Could not find " + filenameFull + " - checking in jar files...");
			
			// Look for the config file in the class path (i.e. jar file).
			InputStream inputCP = null;

			try {
				inputCP = AppConfig.class.getClassLoader().getResourceAsStream(filename);
		    		if (inputCP == null) {
		    			throw new IllegalStateException("AppConfig: Unable to find config.properties file.");
		    		}
				System.out.println("AppConfig: config.properties found");
		    		properties.load(inputCP);
			} catch (IOException e2) {
				e2.printStackTrace();
			} finally {
				if (inputCP != null) {
					try {
						inputCP.close();
					} catch (IOException e3) {
						e3.printStackTrace();
					}
				}
			}
			
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (inputFile != null) {
				try {
					inputFile.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		// Sets the initial (artificial) processing delays.
		// These are only in effect until the client connects.
		delayDataFeedMS = Integer.parseInt( getConfigValue("default-delay-datafeed-ms") );
		delayProcessingMS = Integer.parseInt( getConfigValue("default-delay-processing-ms") );
		delayErrorMS = Integer.parseInt( getConfigValue("default-delay-error-ms") );
			
		System.out.println("AppConfig initialized");
		  
	}


	// Returns the requested property value.
	public static String getConfigValue(String propertyName) {
		
		if (properties == null) {
			appConfig = new AppConfig();
		}
		  
		String propertyValue = properties.getProperty(propertyName);
		  
		if ( StringUtils.isEmpty(propertyValue) ) {
			throw new IllegalStateException("AppConfig: Required property ('" + propertyName + "') not set in config file (config.properties).");
		} else {
			return propertyValue;
		}
			
	}
	 
	
	/* ****************************************************************************************
	 * 
     *  Speed controls.
     *  Without introducing artificial delays, pipelines will be processed too quickly to see.
     *  The Dashboard app provides multiple speed dials to control the display and processing of information.
     *    - delay-datafeed-ms:   A pause introduced into the data feeds (after each new object received).
     *    - delay-processing-ms: A pause introduced after each message processing update displayed in the Dashboard.
     *    - delay-error-ms:      An extra delay for any errors that pop up, so you can see them before they disappear.
     *                           (A better approach here might be to leave them up until they are manually acknowledged).
     *    
     *  Default values are provided in the config.properties file.
     *  Speed dials are surfaced through the dashboard, which communicates with the backend via the 
     *  Vert.x EventBus.  Messages are transferred from the EventBus to MapR Streams.  
     *  The DashboardListener class listens for inbound messages from the dashboard and calls the 
     *  setSystemDelay method appropriately.
     *  
	 * **************************************************************************************** */
	
	public static void setSystemDelay(String controllerName, int delayMS) {
		System.out.println("Setting " + controllerName + " to " + delayMS + " ms.");
		if ( controllerName.equals("delayDataFeed") ) {
			delayDataFeedMS = delayMS;
		} else if ( controllerName.equals("delayProcessing") ) {   
			delayProcessingMS = delayMS;
		} else if ( controllerName.equals("delayError") ) {
			delayErrorMS = delayMS;
		} else {
			throw new IllegalStateException(controllerName + " is not a recognized delay type.");
		}
	}

	public static int getDelayDataFeed() {
		return delayDataFeedMS;
	}
	
	public static int getDelayProcessing() {
		return delayProcessingMS;
	}
	
	public static int getDelayError() {
		return delayErrorMS;
	}
	

    // Command line test.	
	public static void main(String[] args) {

		if (args.length != 1) {
			System.err.println("Usage: AppConfig(<Test Parameter Name>)");
            System.exit(-1);
        }
        
        System.out.println(getConfigValue(args[0]));

	}
	
}
