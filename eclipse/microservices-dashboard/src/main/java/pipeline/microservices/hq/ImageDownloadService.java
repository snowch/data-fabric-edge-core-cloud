package pipeline.microservices.hq;

import org.apache.commons.io.FileUtils;
import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.net.URL;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import com.mapr.db.MapRDB;
import com.mapr.db.Table;

import pipeline.util.AppConfig;
import pipeline.util.MessageLogger;
import pipeline.util.StreamsHelper;
import pipeline.util.hq.DBHelper;

import org.ojai.Document;
import org.ojai.store.DocumentMutation;


/**
 * 
 *  In the prior stage, a new imagery asset from NASA has been received,
 *  and a metadata record for the asset has been created in MapR-DB.
 *  
 *  This microservice performs Stage 1 of the processing pipeline as follows:
 * 		1. Fetches the asset metadata from the database.
 * 		2. Obtain the image URL and retrieve the actual image from NASA.
 *      3. Store the image in MapR-FS.
 *      4. Update the database record with the location of the retrieved image file.
 *      5. Publish an event that the stage has completed.
 * 
 */
public class ImageDownloadService extends Thread {
	
	public static final String APP_CODE = "IMG_DOWNLOAD";
	public static final String APP_DISPLAY_NAME = "Image Download Service";
	
	private MessageLogger logger;
	private String imagesDirectory;
	private KafkaConsumer<String, String> inputStreamConsumer;
	private KafkaProducer<String, String> outputStreamProducer;
	private String inputStreamName;
	private String outputStreamName;
	private Boolean useInternet = true;
	
	
	public ImageDownloadService() {
		
		logger = new MessageLogger(APP_CODE, APP_DISPLAY_NAME);
		
		// Validate the environment.
		
		inputStreamName = AppConfig.getConfigValue("hq-streamTopic-nasaFeed");
		outputStreamName = AppConfig.getConfigValue("hq-streamTopic-imageDownloaded");
		imagesDirectory = "/mapr/" + AppConfig.getConfigValue("hq-cluster-name") + AppConfig.getConfigValue("hq-imagesDir-fullPath");

		if (!StreamsHelper.streamExists(inputStreamName)) {
			throw new IllegalStateException("Input stream " + inputStreamName + " does not exist.");
		}
		if (!StreamsHelper.streamExists(outputStreamName)) {
			throw new IllegalStateException("Input stream " + outputStreamName + " does not exist.");
		}
		
		// This was added to support disconnected demo environments.
		// The actual images have already been downloaded during previous runs,
		// so now we can just use those.  Default is still to use the internet.
		// Internet is also required if you're using the ImageClassifierService.
		String useInternetStr = AppConfig.getConfigValue("hq-use-internet");
		if ( !useInternetStr.equals("true") ) {
			useInternet = false;
			logger.admin("***use-internet set to FALSE. USING LOCAL IMAGERY FILES INSTEAD.");
		}
		
	}
	
	
	@Override
	public void run() {
		
		String servicePID = logger.generatePID();
		logger.logServiceStart(servicePID);
        
    		// Subscribe to the specified input stream:topic.
		inputStreamConsumer = StreamsHelper.getConsumer();
		List<String> topics = new ArrayList<>();
        topics.add(inputStreamName);
        inputStreamConsumer.subscribe(topics);
        
        // Setup the output stream:topic.
        outputStreamProducer = StreamsHelper.getProducer();

		ObjectMapper mapper = new ObjectMapper();

		try {
			
	    		// Listen for new messages indefinitely.
			while (true) {
				
				ConsumerRecords<String, String> records = inputStreamConsumer.poll(1000);
	            for (ConsumerRecord<String, String> record : records) {
	            	
		            	// A new pid is generated for every message handled.
		            	String pid = logger.generatePID();
		            	
		            	// Extract fields from input message.
		            	JsonNode msg = mapper.readTree(record.value());
	                String assetID = msg.get("assetID").textValue();
	                String tableName = msg.get("tablename").textValue();	                
	                String parentPID = msg.get("messageCreatorID").textValue();
	
		            	logger.logMsgStart(pid, parentPID, "Processing new asset.");
	
	                logger.logMessage( pid, MessageLogger.MSG_LEVEL_INFO, MessageLogger.CD_MSG_TEXT, "Asset ID: " + assetID );
	                logger.logMessage( pid, MessageLogger.MSG_LEVEL_DEBUG, MessageLogger.CD_MSG_TEXT, "  Event Data: " + msg );
		                	                
	                // Fetch the asset record from MapR-DB
	                Table imagesTable = DBHelper.getTable(tableName);
		            	Document assetDoc  = imagesTable.findById(assetID);
		            	if (assetDoc == null) {
		            		logger.logMessage( pid, MessageLogger.MSG_LEVEL_ERROR, MessageLogger.CD_MSG_TEXT, "Could not retrieve DB record for Asset ID " + assetID );
		            	} else {
	
		            		logger.logMessage( pid, MessageLogger.MSG_LEVEL_DEBUG, MessageLogger.CD_MSG_TEXT, "Retrieved asset record from DB." );
		            		
			            	String mediaType;
			            	String imageURL;
			            	boolean fetchImage;
			            	try {
			            		
			            		// Parse through the NASA metadata for fields of interest ("media type" and "href")
			            		
				            	List<Object> dataList = assetDoc.getList("data");
				            	Document element0 = (Document) dataList.get(0);
				            	mediaType = element0.getString("media_type");
				            	List<Object> linkList = assetDoc.getList("links");
				            	element0 = (Document) linkList.get(0);
				            	imageURL = element0.getString("href");
	
				            	//logger.logMessage( logger.MSG_LEVEL_DEBUG, "Got data list: " + dataList.toString() );
				            	//logger.logMessage( logger.MSG_LEVEL_DEBUG, "Got element 0: " + element0.toString() );
				            	//logger.logMessage( logger.MSG_LEVEL_DEBUG, "Got link list: " + linkList.toString() );
				            	//logger.logMessage( logger.MSG_LEVEL_DEBUG, "Got element 0: " + element0.toString() );
	
				            	logger.logMessage( pid, MessageLogger.MSG_LEVEL_INFO, MessageLogger.CD_MSG_TEXT, "Extracted media type (" + mediaType + ")" );
				            	logger.logMessage( pid, MessageLogger.MSG_LEVEL_INFO, MessageLogger.CD_MSG_TEXT, "Extracted asset URL." );
				                
			                fetchImage = true;
	
			            	} catch (Exception e) {
	
			            		mediaType = "unknown";
			            		imageURL = "Not Available";
			            		fetchImage = false;
			            		logger.logMsgFail( pid, "Media type unknown or asset URL unavailable", null );
				                
			            	}
			            	
			            	if (fetchImage) {
			            		
			            		// Download the image file to MapR-FS.
			            		String filename = imageURL.substring( imageURL.lastIndexOf('/') + 1 );
			            		String fileLocation = imagesDirectory + filename;
			            		if (useInternet) {
				            		FileUtils.copyURLToFile( new URL(imageURL), new File(fileLocation) );
				            		logger.logMessage( pid, MessageLogger.MSG_LEVEL_DEBUG, MessageLogger.CD_MSG_TEXT, "Downloaded media file from " + imageURL + " to " + fileLocation );
			            		} else {
			            			// This was added so we can run the demo in environment with no internet.
			            			// The imagery data was downloaded previously and is now packaged up with the demo app.
			            			// "use-internet" has to be set to false in the config.properties file.  
				            		logger.logMessage( pid, MessageLogger.MSG_LEVEL_DEBUG, MessageLogger.CD_MSG_TEXT, "Skipping download of media file from " + imageURL + " to " + fileLocation + " - use local copy instead.");
			            		}
	
			            		// Update the DB record with the file location.
			            		DocumentMutation mutation = MapRDB.newMutation().set("imageDownloadLocation", fileLocation);
			            		imagesTable.update(assetID, mutation);
			            		imagesTable.flush();
			            		logger.logMessage( pid, MessageLogger.MSG_LEVEL_DEBUG, MessageLogger.CD_MSG_TEXT, "Updated database record with file location.");
			            		logger.logMessage( pid, MessageLogger.MSG_LEVEL_DEBUG, MessageLogger.CD_MSG_TEXT, imagesTable.findById(assetID).toString() );
			            		
			            		logger.logMessage( pid, MessageLogger.MSG_LEVEL_INFO, MessageLogger.CD_MSG_TEXT, "Downloaded new media file.");
			            		logger.logMessage( pid, MessageLogger.MSG_LEVEL_INFO, MessageLogger.CD_MSG_IMAGE, filename );
	
			        			// Publish the IMAGE_DOWNLOADED event to Streams.
		      			    ObjectNode streamsMsg = mapper.createObjectNode();
			        			streamsMsg.put("description", "New image file downloaded from NASA.");
			        			streamsMsg.put("tablename", tableName);
			  			  	streamsMsg.put("assetID", assetID);
			        			streamsMsg.put("filename", fileLocation);
			  			  	streamsMsg.put("messageCreatorID", pid);
			        			
			        			ProducerRecord<String, String> rec = new ProducerRecord<String, String>(outputStreamName, streamsMsg.toString() );
			        			outputStreamProducer.send(rec);
			        			logger.logMessage( pid, MessageLogger.MSG_LEVEL_DEBUG, MessageLogger.CD_MSG_TEXT, "Published image retrieved event to output stream:topic (" + outputStreamName + ")" );
	
			            	}
			            	
		            	}
		            	
		    			// Indicate to the dashboard that the record's been processed.
		            	logger.logMsgFinish(pid);

	            }
	            
			}
			
    	} catch (Exception e) {
    		
    		logger.logServiceFail( servicePID, "UNEXPECTED SHUTDOWN", e );
            
    	} 
     	finally {
     		
     		try {
     			inputStreamConsumer.close();
     		} catch (Exception e) {}
     		
     		try {
     			outputStreamProducer.close();
     		} catch (Exception e) {}
     		
     	}
    	
	}
	
	
    public static void main(String[] args) throws Exception {
    	
	    	ImageDownloadService ids = new ImageDownloadService();
	    	ids.start();
    	
    }

}
