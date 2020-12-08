package pipeline.microservices.hq;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import com.mapr.db.Table;

import pipeline.util.AppConfig;
import pipeline.util.MessageLogger;
import pipeline.util.StreamsHelper;
import pipeline.util.hq.DBHelper;

import org.ojai.Document;


/**
 * 
 *  Publishes messages to an outbound data Stream,
 *  for subsequent replication to an Edge cluster.
 * 
 */
public class AssetBroadcastService extends Thread {
	
	public static final String APP_CODE = "ASSET_BROADCAST";
	public static final String APP_DISPLAY_NAME = "Asset Broadcast Service";
	
	private MessageLogger logger;
	private KafkaConsumer<String, String> inputStreamConsumer;
	private KafkaProducer<String, String> outputStreamProducer;
	private String inputStreamName;
	private String outputStreamName;
	
	
	public AssetBroadcastService() {
		
		// Validate the environment.
		
		inputStreamName = AppConfig.getConfigValue("hq-streamTopic-imageDownloaded");
		outputStreamName = AppConfig.getConfigValue("streamTopic-assetBroadcast");

		if (!StreamsHelper.streamExists(inputStreamName)) {
			throw new IllegalStateException("Input stream " + inputStreamName + " does not exist.");
		}
		if (!StreamsHelper.streamExists(outputStreamName)) {
			throw new IllegalStateException("Output stream " + outputStreamName + " does not exist.");
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
	                String tableName = msg.get("tablename").textValue();	                
	                String parentPID = msg.get("messageCreatorID").textValue();
	                
	                logger.logMsgStart(pid, parentPID, "Broadcasting new asset.");

	                logger.logMessage( pid, MessageLogger.MSG_LEVEL_INFO, MessageLogger.CD_MSG_TEXT, "Asset ID: " + assetID );
	                logger.logMessage( pid, MessageLogger.MSG_LEVEL_DEBUG, MessageLogger.CD_MSG_TEXT, "  Event Data: " + msg );
	                	                
	                // Fetch the asset record from MapR-DB
	                Table imagesTable = DBHelper.getTable(tableName);
	                Document assetDoc  = imagesTable.findById(assetID);
	                	if (assetDoc == null) {
	    	                logger.logMessage( pid, MessageLogger.MSG_LEVEL_ERROR, MessageLogger.CD_MSG_TEXT, "Could not retrieve DB record for Asset ID " + assetID );
	    	            	} else {

	            		logger.logMessage( pid, MessageLogger.MSG_LEVEL_DEBUG, MessageLogger.CD_MSG_TEXT, "Retrieved asset record from DB." );
	            		
		            	String assetTitle;
		            	boolean broadcastAsset;
		            	try {
		            		
		            		// Parse through the NASA metadata for fields of interest
		            		List<Object> dataList = assetDoc.getList("data");
			            	Document element0 = (Document) dataList.get(0);
			            	assetTitle = element0.getString("title");
			            	
			            	logger.logMessage( pid, MessageLogger.MSG_LEVEL_INFO, MessageLogger.CD_MSG_TEXT, "Extracted asset title (" + assetTitle + ")" );
			            	//logger.logMessage( pid, MessageLogger.MSG_LEVEL_INFO, MessageLogger.CD_MSG_TEXT, "Extracted asset URL." );
			                
			            broadcastAsset = true;

		            	} catch (Exception e) {

		            		assetTitle = "unknown";
		            		broadcastAsset = false;
		            		logger.logMsgFail( pid, "Asset title unavailable", null );
			                
		            	}
		            	
		            	if (broadcastAsset) {
		            		
		            		// Broadcast the asset to all subscribers.
		      			ObjectNode streamsMsg = mapper.createObjectNode();
		        			streamsMsg.put("description", "New asset available from NASA.");
		        			streamsMsg.put("tablename", tableName);
			  			streamsMsg.put("assetID", assetID);
		        			streamsMsg.put("title", assetTitle);
			  			streamsMsg.put("messageCreatorID", pid);
		        			
		        			ProducerRecord<String, String> rec = new ProducerRecord<String, String>(outputStreamName, streamsMsg.toString() );
		        			outputStreamProducer.send(rec);
		        			logger.logMessage( pid, MessageLogger.MSG_LEVEL_DEBUG, MessageLogger.CD_MSG_TEXT, "Broadcast new asset to output stream:topic (" + outputStreamName + ")" );

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
    	
	    	AssetBroadcastService abs = new AssetBroadcastService();
	    	abs.start();
    	
    }

}
