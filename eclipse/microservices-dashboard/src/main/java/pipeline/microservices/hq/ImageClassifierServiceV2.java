package pipeline.microservices.hq;


import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import com.mapr.db.MapRDB;
import com.mapr.db.Table;
import org.ojai.Document;
import org.ojai.store.DocumentMutation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import pipeline.util.AppConfig;
import pipeline.util.MessageLogger;
import pipeline.util.StreamsHelper;
import pipeline.util.hq.DBHelper;
import pipeline.util.hq.ImageClassifierAWS;

import java.util.ArrayList;
import java.util.List;


/**
 * 
 *  Runs an image classifier against an image.
 * 
 *  Input data stream:  AppConfig(stream-image-downloaded)
 *  Output data stream: AppConfig(stream-image-classified)
 * 
 *  Input message data: AssetID (database record)
 *                      locator (image filename)
 *                     
 *  Actions: Run a classifier (AWS Rekognition) against the image.
 *           Updates the asset record in the DB with the image labels from the classifier.
 *           Publishes a new event to the Output data stream.
 *          
 *  This service is started and stopped interactively via the Dashboard.
 * 
 */
public class ImageClassifierServiceV2 extends Thread {
	
	public static final String APP_CODE = "IMG_CLASS_2";
	public static final String APP_DISPLAY_NAME = "Image Classifier 2.0 Service";

	private static boolean keepRunning;
	
	private MessageLogger logger;
	private String servicePID;
	private KafkaConsumer<String, String> inputStreamConsumer;
	private KafkaProducer<String, String> outputStreamProducer;
	private String inputStreamName;
	private String outputStreamName;
	
	
	public ImageClassifierServiceV2() {
		
		// Validate the environment.
		
		inputStreamName = AppConfig.getConfigValue("hq-streamTopic-imageDownloaded");
		outputStreamName = AppConfig.getConfigValue("hq-streamTopic-imageClassified");
		
		if (!StreamsHelper.streamExists(inputStreamName)) {
			throw new IllegalStateException("Input stream " + inputStreamName + " does not exist.");
		}
		if (!StreamsHelper.streamExists(outputStreamName)) {
			throw new IllegalStateException("Input stream " + outputStreamName + " does not exist.");
		}
		
		// Validate the AWS keys.
		ImageClassifierAWS.validateEnv();

		logger = new MessageLogger(APP_CODE, APP_DISPLAY_NAME);

	}
	
	
	// Stops the thread in an orderly fashion, sending a notification to the Dashboard.
	public void decommission() {
		logger.debug("Decommission signal received. Shutting down...");
		logger.logServiceStop(servicePID);
		keepRunning = false;
	}
	
	
	@Override
	public void run() {

		servicePID = logger.generatePID();
		logger.logServiceStart(servicePID);

		// Subscribe to the specified input stream:topic.
		inputStreamConsumer = StreamsHelper.getConsumer();
		List<String> topics = new ArrayList<>();
        topics.add(inputStreamName);
        inputStreamConsumer.subscribe(topics);
        
        // Setup the output stream:topic.
		outputStreamProducer = StreamsHelper.getProducer();

		ObjectMapper mapper = new ObjectMapper();
		
		keepRunning = true;
		try {
			
			// Listen for new messages indefinitely.
			while (keepRunning) {

				ConsumerRecords<String, String> records = inputStreamConsumer.poll(1000);
	            for (ConsumerRecord<String, String> record : records) {
	            	
            		// Even though this is checked in the while loop, we might have just pulled 
            		// a large number of messages.  Checking on every record ensures that 
            		// we stop as soon as we receive the STOP signal.  
	            	// Kafka 0.10 introduces max.poll.records, which is another option.
	            	if (keepRunning) {
	            		
		            	try {    // Errors caught here will be written to stdout.  
	            		
			            	// A new pid is generated for every message handled.
			            	String pid = logger.generatePID();
	
		            		// Extract minimal amount of data from the message.
		            		JsonNode msg = mapper.readTree(record.value());
		  			  	String parentPID = msg.get("messageCreatorID").textValue();

		  			  	try {      // Any errors caught here can be surfaced in the dashboard.
		  			  		
		  			  		// Extract remaining fields from input message.
			                String assetID = msg.get("assetID").textValue();
			                String tablename = msg.get("tablename").textValue();
			                String filenameFQN = msg.get("filename").textValue();
	
				            	logger.logMsgStart(pid, parentPID, "Running image classifier.");
	
			                String filename = filenameFQN.substring( filenameFQN.lastIndexOf('/') + 1 );
		            		
			                logger.logMessage( pid, MessageLogger.MSG_LEVEL_INFO, MessageLogger.CD_MSG_TEXT, "Asset ID: " + assetID );
			                logger.logMessage( pid, MessageLogger.MSG_LEVEL_INFO, MessageLogger.CD_MSG_IMAGE, filename );
				                	                
				            	// Fetch the asset record from MapR-DB
			                Table imagesTable = DBHelper.getTable(tablename);
				            	Document assetDoc  = imagesTable.findById(assetID);
				            	if (assetDoc == null) {
				            		
				            		logger.logMsgFail( pid, "Could not retrieve DB record for Asset ID " + assetID, null );
				            		
				            	} else {
			
				            		logger.logMessage( pid, MessageLogger.MSG_LEVEL_DEBUG, MessageLogger.CD_MSG_TEXT, "Retrieved asset record from database." );
				            		
				            		// Run the Image Classifier.
				            		ImageClassifierAWS imageClassifier = new ImageClassifierAWS();
				            		String labels = imageClassifier.classifyImage(filenameFQN, 5);
				            		
				            		if (labels == "") {
					            		logger.logMessage( pid, MessageLogger.MSG_LEVEL_INFO, MessageLogger.CD_MSG_TEXT, "(Unable to categorize)");
				            		} else {
					            		logger.logMessage( pid, MessageLogger.MSG_LEVEL_INFO, MessageLogger.CD_MSG_TEXT, "Categorized image:<br>" + labels);
				            		}
				            		
				            		// Update the DB record with the file location.
				            		DocumentMutation mutation = MapRDB.newMutation().set("imageClassificationsV2", labels);
				            		imagesTable.update(assetID, mutation);
				            		imagesTable.flush();
				            		logger.logMessage( pid, MessageLogger.MSG_LEVEL_INFO, MessageLogger.CD_MSG_TEXT, "Updated image metadata with v2 category labels.");
				            		logger.logMessage( pid, MessageLogger.MSG_LEVEL_DEBUG, MessageLogger.CD_MSG_TEXT, imagesTable.findById(assetID).toString() );
			
				        			// Publish the IMAGE_CLASSIFIED event to Streams.
			      			    ObjectNode streamsMsg = mapper.createObjectNode();
				        			streamsMsg.put( "description", "Image classified." );
				        			streamsMsg.put( "assetID", assetID );
				        			streamsMsg.put( "labels", labels );
				        			
				        			ProducerRecord<String, String> rec = new ProducerRecord<String, String>(outputStreamName, streamsMsg.toString() );
				        			outputStreamProducer.send(rec);
				        			
				        			logger.logMessage( pid, MessageLogger.MSG_LEVEL_DEBUG, MessageLogger.CD_MSG_TEXT, "Published image classified event to output stream:topic (" + outputStreamName + ")" );
					            
					    			// Indicate to the dashboard that the record's been processed.
					            	logger.logMsgFinish(pid);
	
				            	}
			  			  		
			            	} catch (Exception e) {
			            		logger.logMsgFail( pid, "Problem with record.", e );
			            	} 
	
		            	} catch (Exception e) {
		            		
		            		// Can't display in the dashboard because the minimal info couldn't be extracted.
		            		logger.debug("Problem with record - couldn't parse the JSON.");
		            		e.printStackTrace();
		            	
		            	} 
	            	}
            }
            
		}
		logger.debug("Shutting down normally, upon request.");
			
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
			logger.debug("Input/Output streams closed.");
	 		
		}
		
	}    	
    	
	
	// Enables service to be run from the command line.
    public static void main(String[] args) throws Exception {
    	
		ImageClassifierServiceV2 ics2 = new ImageClassifierServiceV2();
		ics2.start();
    	
    }

}
