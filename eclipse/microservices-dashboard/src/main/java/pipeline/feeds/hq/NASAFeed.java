package pipeline.feeds.hq;

import java.util.Iterator;
import java.io.File;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.JsonNode;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import com.mapr.db.MapRDB;
import com.mapr.db.Table;

import pipeline.util.AppConfig;
import pipeline.util.MessageLogger;
import pipeline.util.StreamsHelper;
import pipeline.util.hq.DBHelper;

import org.ojai.Document;


/**
 * 
 * Simulates a NASA data feed.
 * The NASA query API has already been used to generate a list of imagery assets (in JSON format).
 * This simulator iterates through that file and, for each record, it will:
 * 		1. Create a database record containing the image metadata.
 * 		2. Publish an event to MapR Streams saying that a new image from NASA is available.
 * 		3. Go to sleep for some amount of time before moving on to the next image.
 * 
 * When it reaches the end of the file, it cycles back to the beginning.
 * 
 * The metadata record doesn't actually contain the image itself, only a URL to the image on the NASA site.
 * A subscriber to this feed (i.e. the ImageDownloadService) retrieves the image, 
 * stores it in MapR-FS, and updates the DB record with its location.
 * 
 */
public class NASAFeed extends Thread {
	
	public static final String APP_CODE = "NASA_FEED";
	public static final String APP_DISPLAY_NAME = "NASA Data Feed";
	
	private String inputDataFile;
	private String imagesTableName;
	private String outputStreamName;
	private MessageLogger logger;
	private Table imagesTable;
	private KafkaProducer<String, String> outputStreamProducer;
	private int outsideCounter = 1;
	
	
	public NASAFeed () {
		
		// Validate the environment.
		
		inputDataFile    ="/mapr/" + AppConfig.getConfigValue("hq-cluster-name") + AppConfig.getConfigValue("hq-nasaDataFile-fullPath");
		imagesTableName  = AppConfig.getConfigValue("hq-tableImages-fullPath");
		outputStreamName = AppConfig.getConfigValue("hq-streamTopic-nasaFeed");

		if ( !MapRDB.tableExists(imagesTableName) ) {
			throw new IllegalStateException("Images table " + imagesTableName + " does not exist.");
		}

		if ( !StreamsHelper.streamExists(outputStreamName) ) {
			throw new IllegalStateException("Output stream " + outputStreamName + " does not exist.");
		}

		logger = new MessageLogger(APP_CODE, APP_DISPLAY_NAME);
		
	}
	

	@Override
	public void run() {
		
		String servicePID = logger.generatePID();
		logger.logServiceStart(servicePID);
		
		outputStreamProducer = StreamsHelper.getProducer();

		try {

			imagesTable = DBHelper.getTable(imagesTableName);
			
			while (true){
				
				processInputFile();
				
				logger.logMessage( servicePID, MessageLogger.MSG_LEVEL_DEBUG, MessageLogger.CD_MSG_TEXT, "Finished processing file (count = " + outsideCounter++ + "). Starting again at beginning." );
				
			}
			
		} catch (Exception e) {
			logger.logServiceFail(servicePID, "UNEXPECTED SHUTDOWN", e);
		}
		
	}
		
		
    private void processInputFile() throws Exception {
    	
	    	int insideCounter = 1;
	    	
	    	// parse through the outer layers of JSON.
	    	ObjectMapper objectMapper = new ObjectMapper();
	    	JsonNode rootNode = objectMapper.readTree( new File(inputDataFile) );
	    	JsonNode dataCollection = rootNode.path("collection");
	    	JsonNode itemList = dataCollection.path("items");
	    	
	    	// Iterate through the collection of imagery assets.
	    	Iterator<JsonNode> items = itemList.elements();
	    	while ( items.hasNext() ) {
	    		
	    		// Generate a new PID for each record,
	    		// simulating a new handler process being kicked off each time a new image is received.
	    		String pid = logger.generatePID();
	    		
				try {
					
					logger.logMsgStart(pid, null, "New asset available from NASA.");
		    			logger.logMessage( pid, MessageLogger.MSG_LEVEL_DEBUG, MessageLogger.CD_MSG_TEXT, "Processing file record #" + insideCounter + " (pass #" + outsideCounter + ")..." );
		        		
		    			JsonNode item = items.next();
		    			String imageMetaJSON = item.toString();
		    			logger.debug(imageMetaJSON);
		        		
		    			// Write image metadata to the database.
		    			// Note: Works here, but key not suitable for production scenarios due to hotspotting concerns.
		    			Document dbDocument = MapRDB.newDocument(imageMetaJSON);
		    			String docID = new String( outsideCounter + ":" + insideCounter );
		    			dbDocument.setId(docID);
		    			imagesTable.insert(dbDocument);
		    			logger.logMessage( pid, MessageLogger.MSG_LEVEL_INFO, MessageLogger.CD_MSG_TEXT, "Asset ID: " + docID);
		    			
		    			// Publish the event (new image received) to Streams.
	  			  	ObjectMapper mapper = new ObjectMapper();
	  			  	ObjectNode streamsMsg = mapper.createObjectNode();
	  			  	streamsMsg.put("description", "New image metadata received from NASA.");
	  			  	streamsMsg.put("tablename", imagesTableName);
	  			  	streamsMsg.put("assetID", docID);
	  			  	streamsMsg.put("messageCreatorID", pid);
	  			  	
		    			ProducerRecord<String, String> rec = new ProducerRecord<String, String>(outputStreamName, streamsMsg.toString() );
		    			outputStreamProducer.send(rec);
		    			
		    			logger.debug( "Published image received event to output stream:topic (" + outputStreamName + ")" );
		    			//logger.debug( "  message text: " + streamsMsg.toString() );
		    			
		    			// Sleep before fetching the next asset.
		    			// For demo purposes, to slow down the visuals.
		    			int sleepTime = AppConfig.getDelayDataFeed();
		    			logger.debug("Sleeping " + sleepTime);
		    			Thread.sleep( sleepTime );
		    			
		    			// Indicate to the dashboard that the record's been processed.
		    			logger.logMsgFinish(pid);
		    			
		    			insideCounter++;
	    			
				} catch (Exception e) {
					
					// If there's a problem with an individual record, we want to continue to the next.
	    			logger.logMessage( pid, MessageLogger.MSG_LEVEL_ERROR, MessageLogger.CD_MSG_TEXT, "Couldn't process record. Continuing with next." );
	    			logger.logMessage( pid, MessageLogger.MSG_LEVEL_ERROR, e );
	
	    			logger.logMsgFinish(pid);
	    			
				}
					
	    	}
    	
    }
    	

    public static void main(String[] args) throws Exception {
    	
	    	NASAFeed nasaFeed = new NASAFeed();
	    	nasaFeed.start();
    	
    }

}
