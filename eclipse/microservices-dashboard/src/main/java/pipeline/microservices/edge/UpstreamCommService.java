package pipeline.microservices.edge;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import pipeline.util.AppConfig;
import pipeline.util.MessageLogger;
import pipeline.util.StreamsHelper;


/**
 * 
 *  Listens for messages from the HQ cluster indicating that a new asset is available,
 *  forwarding that message to the Edge Dashboard for display to the user.
 *  
 */
public class UpstreamCommService extends Thread {
	
	public static final String APP_CODE = "UPSTREAM_COMM";
	public static final String APP_DISPLAY_NAME = "Upstream Comm Service";
	
	private static boolean keepRunning;
	
	private String servicePID;

	private MessageLogger logger;
	private KafkaConsumer<String, String> inputStreamConsumer;
	private String inputStreamName;
	private String outputStreamName;
	
	
	public UpstreamCommService() {
		
		// Validate the environment.
		
		inputStreamName = AppConfig.getConfigValue("streamTopic-assetBroadcast");

		logger = new MessageLogger(APP_CODE, APP_DISPLAY_NAME);
		
	}
	
	
	// Stops the thread in an orderly fashion, sending a notification to the Dashboard.
	public void decommission() {
		logger.admin("Decommission signal received. Shutting down...");
		logger.logServiceStop(servicePID);
		keepRunning = false;
	}
	
	
	@Override
	public void run() {
		
		servicePID = logger.generatePID();
		logger.logServiceStart(servicePID);

		try {
			
			// The stream will only exist if replication has been established, 
			// and it should exist if this service is being run.
			// If not, something went wrong.
			if (!StreamsHelper.streamExists(inputStreamName)) {
				throw new IllegalStateException("Input stream " + inputStreamName + " does not exist. Replication setup must have failed.");
			}
	
	    		// Subscribe to the specified input stream:topic.
			inputStreamConsumer = StreamsHelper.getConsumer();
			List<String> topics = new ArrayList<>();
	        topics.add(inputStreamName);
	        inputStreamConsumer.subscribe(topics);
	        
			ObjectMapper mapper = new ObjectMapper();
	
	    		// Listen for new messages indefinitely (until the shutdown signal is sent).
			keepRunning = true;
	    		while (keepRunning) {
	    			
	    			ConsumerRecords<String, String> records = inputStreamConsumer.poll(100);
	    			for (ConsumerRecord<String, String> record : records) {
	    				
	    				// A new pid is generated for every message handled.
	    				// Only because this piggybacks on the tile framework used for the microservices,
	    				// in which a sequence of messages has to be routed to a single tile.
	    				// It's really only used here as a placeholder, because all new assets
	    				// are announced in the same Edge dashboard window.
	    				// A single pid could probably be generated in the constructor.
		        		String pid = logger.generatePID();
		            	
		        		// Extract fields from input message.
		        		JsonNode msg = mapper.readTree(record.value());
		        		String assetID = msg.get("assetID").textValue();
		        		String tableName = msg.get("tablename").textValue();	                
		        		String parentPID = msg.get("messageCreatorID").textValue();
		        		String assetDescription = msg.get("description").textValue();
		        		String assetTitle = msg.get("title").textValue();
	
		        		// The tiles require Start and Finish indicators.
		        		// We don't need that here, since it runs continually.
		        		// We just need to send a message to the Dashboard.
		        		// logger.logMsgStart(pid, parentPID, "Processing new asset: " + assetID);
		        		logger.logMessage( pid, MessageLogger.MSG_LEVEL_INFO, MessageLogger.CD_ASSET_AVAIL, "New asset " + assetID + " available from HQ: " + assetTitle );
		                	                	            	
		        		// Indicate to the dashboard that the record's been processed.
		        		// logger.logMsgFinish(pid);
	
		       }
		            
			}
				
	    	} catch (Exception e) {
	    		logger.logServiceFail( servicePID, "UNEXPECTED SHUTDOWN", e );
	    	} 
     	finally {
     		try {
     			inputStreamConsumer.close();
     		} catch (Exception e) {}
	    	}
    	
	}
	
	
    public static void main(String[] args) throws Exception {
    	
	    	UpstreamCommService ids = new UpstreamCommService();
	    	ids.start();
    	
    }

}
