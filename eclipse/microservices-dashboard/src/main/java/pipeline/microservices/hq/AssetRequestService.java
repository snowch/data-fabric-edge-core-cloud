package pipeline.microservices.hq;

import java.util.ArrayList;
import java.util.List;
import java.io.File;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapr.db.MapRDB;
import com.mapr.db.Table;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.ojai.Document;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.commons.io.FileUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import pipeline.util.AppConfig;
import pipeline.util.MessageLogger;
import pipeline.util.StreamsHelper;
import pipeline.util.hq.DBHelper;

/**
 * 
 *  Receives requests for assets from the Edge Cluster.
 * 
 */
public class AssetRequestService extends Thread {
	
	public static final String APP_CODE = "ASSET_REQUEST";
	public static final String APP_DISPLAY_NAME = "Asset Request Service";
	
	private MessageLogger logger;
	private KafkaConsumer<String, String> inputStreamConsumer;
	private KafkaProducer<String, String> outputStreamProducer;
	private String imagesTableName;
	private String inputStreamName;
	private String outputStreamName;
	
	
	public AssetRequestService() {
		
		// Validate the environment.
		
		imagesTableName  = AppConfig.getConfigValue("hq-tableImages-fullPath");
		inputStreamName = AppConfig.getConfigValue("streamTopic-assetRequest");
		outputStreamName = AppConfig.getConfigValue("hq-streamTopic-toCloud");
		
		if ( !MapRDB.tableExists(imagesTableName) ) {
			throw new IllegalStateException("Images table " + imagesTableName + " does not exist.");
		}

		// Maybe useful if I want to publish a thumbnail.
		// imagesDirectory = AppConfig.getConfigValue("mapr-root") + AppConfig.getConfigValue("images-directory");

		if (!StreamsHelper.streamExists(inputStreamName)) {
			throw new IllegalStateException("Input stream " + inputStreamName + " does not exist.");
		}
		
		logger = new MessageLogger(APP_CODE, APP_DISPLAY_NAME);
		
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
	                //String tableName = msg.get("tablename").textValue();
	                // Another hack. Need a parent ID here, but it's not being sent from the Edge dashboard.
	                String parentPID = "bogus-value";
	                //String parentPID = msg.get("messageCreatorID").textValue();
	                
	                logger.logMsgStart(pid, parentPID, "Servicing asset request.");
	                logger.logMessage( pid, MessageLogger.MSG_LEVEL_INFO, MessageLogger.CD_MSG_TEXT, "Asset ID: " + assetID );

	                // Fetch the asset record from MapR-DB.
	                Table imagesTable = DBHelper.getTable(imagesTableName);
		            	Document assetDoc  = imagesTable.findById(assetID);
		            	if (assetDoc == null) {
		            		logger.logMsgFail( pid, "Could not retrieve DB record for Asset ID " + assetID, null );
		            	} else {
	
		            		logger.logMessage( pid, MessageLogger.MSG_LEVEL_DEBUG, MessageLogger.CD_MSG_TEXT, "Retrieved asset record from DB." );
		            		
			            	String imageDownloadLocation;
			            	try {
			            		
			            		// Parse through the NASA metadata for fields of interest ("media type" and "href")
			            		imageDownloadLocation  = assetDoc.getString("imageDownloadLocation");
			            		if (imageDownloadLocation == null) {
				            		logger.logMsgFail( pid, "Cannot send file - File location missing from DB record.", null );
			            		} else {
			            			
			            			// Copy the file into the files-missionX volume, which is mirrored to the Edge.
			            			String imageMirrorLocation = "/mapr/" + AppConfig.getConfigValue("hq-cluster-name") + AppConfig.getConfigValue("missionX-imagesDir-fullPath");
			            			String fileName = imageDownloadLocation.substring( imageDownloadLocation.lastIndexOf("/") + 1);
			            			logger.logMessage( pid, MessageLogger.MSG_LEVEL_INFO, MessageLogger.CD_MSG_TEXT, "Requested file: " +  fileName );
			            			File sourceFile = new File(imageDownloadLocation);
			            			File destFile = new File(imageMirrorLocation + "/" + fileName);
			            			FileUtils.copyFile(sourceFile, destFile);
			            			logger.logMessage( pid, MessageLogger.MSG_LEVEL_INFO, MessageLogger.CD_MSG_TEXT, "Sent for outbound (missionX)" );
			            			
			            		}

			            	} catch (Exception e) {
	
			            		logger.logMsgFail( pid, "Unexpected error servicing request.", e );
				                
			            	}
			            	
			            	try {

			        			// Publish the request to the cloud.
			        			ProducerRecord<String, String> rec = new ProducerRecord<String, String>(outputStreamName, assetDoc.toString() );
			        			outputStreamProducer.send(rec);

			            	} catch (Exception e) {
			            		logger.logMessage( pid, MessageLogger.MSG_LEVEL_INFO, MessageLogger.CD_MSG_TEXT, "Unable to forward request to the cloud.");
			            	}
			            	
		            	} 
		            	
		    			// Indicate to the dashboard that the record's been processed.
		            	logger.logMsgFinish(pid);
	
	            }
	            
			}
			
	    	} catch (Exception e) {
	    		
	    		logger.logServiceFail( servicePID, "UNEXPECTED SHUTDOWN", e );
	            
	    	} finally {
	     		
	 		try {
		        inputStreamConsumer.close();
	 		} catch (Exception e) {}
	 		
	 		try {
		        outputStreamProducer.close();
	 		} catch (Exception e) {}
	     		
	    	}
    	
	}
	
	
    public static void main(String[] args) throws Exception {
    	
	    	AssetRequestService abs = new AssetRequestService();
	    	abs.start();
    	
    }
    
    
}
