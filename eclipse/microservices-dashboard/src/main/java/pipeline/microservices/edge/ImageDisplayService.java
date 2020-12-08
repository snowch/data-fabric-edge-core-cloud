package pipeline.microservices.edge;

import java.io.File;
import pipeline.util.AppConfig;
import pipeline.util.MessageLogger;
import java.util.HashMap;
import java.util.Map;


/**
 * 
 *  Detects the receipt of new images from the HQ cluster and
 *  sends a message to the Dashboard for the image to be displayed.
 *  
 *  This service is monitoring a specific directory (i.e. the mirror volume)
 *  for the appearance of new files.
 * 
 */
public class ImageDisplayService extends Thread {
	
	public static final String APP_CODE = "IMAGE_DISPLAY";
	public static final String APP_DISPLAY_NAME = "Image Display Service";
	
	// Using a HashMap to keep track of which files have already been processed.
	// Quick and dirty - only useful in a demo scenario.
	private static Map<String, Integer> filesProcessed = new HashMap<String, Integer>();

	private MessageLogger logger;
	private String imagesDirectory;

	public ImageDisplayService() {
		
		logger = new MessageLogger(APP_CODE, APP_DISPLAY_NAME);
		
		// Fetch the directory for the mirror volume.
		// This is the volume that will contain the images mirrored from HQ.
		imagesDirectory = "/mapr/" + AppConfig.getConfigValue("edge-cluster-name") + AppConfig.getConfigValue("missionX-imagesDir-fullPath");

	}
	
	
	@Override
	public void run() {
		
		String servicePID = logger.generatePID();
		logger.logServiceStart(servicePID);
        
		try {
    		
	    		// Look for new image files indefinitely.
	    		while (true) {
	    			
	    			File dir = new File(imagesDirectory);
	    			File[] directoryListing = dir.listFiles();
	    			if (directoryListing != null) {
	    				
	    				// If there are any files in the images directory, 
	    				// loop through them looking for any new files.
	    				for (File child : directoryListing) {
	    					
	    					String filename = child.getName();
	    					if ( !filesProcessed.containsKey(filename) ) {
	    						
		    		        		// A new pid is generated for every file handled.
		    		        		String pid = logger.generatePID();
	    		            	
		    		        		logger.logMsgStart(pid, "0", "Image Retrieved");
		    		        		logger.logMessage( pid, MessageLogger.MSG_LEVEL_INFO, MessageLogger.CD_MSG_IMAGE, filename );
		    		        		
		    		        		// Indicate to the dashboard that the record's been processed.
		    		        		logger.logMsgFinish(pid);
	
		    	    				filesProcessed.put(filename, 1);
	
	    					}
	    					
	    				}
	    				
	    			}
	    			
	    			// Poll directory every 5 seconds for new files.
	    			System.out.println("ImageDisplayService sleeping for 5 seconds.");
	    			Thread.sleep(5000);
	    			
	        }
	            
	    	} catch (Exception e) {
	    		
	    		logger.logServiceFail( servicePID, "UNEXPECTED SHUTDOWN", e );
	            
	    	} finally {
     		
     		/*
     		try {
    	        inputStreamConsumer.close();
     		} catch (Exception e) {}
     		
     		try {
    	        outputStreamProducer.close();
     		} catch (Exception e) {}
     		*/
     		
     	}
    	
	}
	
	// Clears the Hashmap. 
	// Called during a demo reset.
	// Allows the (set of image files to be processed again.
	public static void reset() {
		
		filesProcessed.clear();
		
	}
	
	
    public static void main(String[] args) throws Exception {
    	
	    	ImageDisplayService ids = new ImageDisplayService();
	    	ids.start();
    	
    }

}
