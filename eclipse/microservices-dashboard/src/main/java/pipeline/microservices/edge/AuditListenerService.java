package pipeline.microservices.edge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.util.regex.Pattern;

import pipeline.util.*;


/**
 * 
 *  This service is launched at startup, and listens for a specific
 *  event on the Audit Stream: the creation of the stream that is the 
 *  replica of the events coming from the HQ cluster.
 *  
 *  Once that replica is created, we can launch the UpstreamCommService
 *  to start listening for new events coming from HQ.
 *  (You cannot subscribe to a stream before it is created).
 *  
 */
public class AuditListenerService extends Thread {
	
	public static final String APP_CODE = "AUDIT_LISTENER";
	public static final String APP_DISPLAY_NAME = "Audit Stream Listener";
	
	private UpstreamCommService upstreamCommService;

	private String clusterName;
	private String inputStreamName;
	private String servicePID;

	private MessageLogger logger;
	private KafkaConsumer<String, String> inputStreamConsumer;
	private KafkaProducer<String, String> outputStreamProducer;
	
	public AuditListenerService() {
		
		// Validate the environment.
		
		clusterName = AppConfig.getConfigValue("edge-cluster-name");
		inputStreamName = "/var/mapr/auditstream/auditlogstream:" + clusterName;
		
		if (!StreamsHelper.streamExists(inputStreamName)) {
			throw new IllegalStateException("Input stream " + inputStreamName + " does not exist. FIX: Enable audit streaming or check cluster name.");
		}
		
		logger = new MessageLogger(APP_CODE, APP_DISPLAY_NAME);
		servicePID = logger.generatePID();
		
	}
	
	
	@Override
	public void run() {
		
		logger.logServiceStart(servicePID);
        
    		// Subscribe to the specified audit stream:topic.
		inputStreamConsumer = StreamsHelper.getConsumer(null, "start-at-end");
		Pattern pattern = Pattern.compile(inputStreamName + ".+");

		// Subscribe to the audit stream.
		// Sets the offset to the end of the stream.
		// Otherwise, you might catch some audit events from previous demo runs.
		inputStreamConsumer.subscribe(pattern, new KafkaSeekToEndListener<String, String>(inputStreamConsumer) );
		
		ObjectMapper mapper = new ObjectMapper();
	    	try {
	    		
	    		// Listen for new messages indefinitely.
	    		while (true) {
	    			
	    			ConsumerRecords<String, String> records = inputStreamConsumer.poll(1000);
		        for (ConsumerRecord<String, String> record : records) {
		        	
		        		// A new pid is generated for every message handled.
		        		String pid = logger.generatePID();
		            	
		        		logger.debug( "Caught audit record: " + record.toString() );
	
		        		// Check for the specific audit message of interest,
		        		// i.e. the establishment of replication from HQ.
		        		
		        		// Extract fields from input message.
		        		JsonNode msg = mapper.readTree(record.value());
		        		JsonNode operationNode = msg.get("operation");
		        		String operationStr = "";
		        		if (operationNode != null) {
			        		operationStr = operationNode.textValue();
		        		}
		        		
		        		// This only works if multi-master is selected during replication setup.
		        		// if ( operationStr.equals("DB_REPLICAADD") ) {

		        		if ( operationStr.equals("DB_UPSTREAMADD") ) {
		        			
		        			// Send a message to the Dashboard that replication has been established.
			        		logger.logMessage( servicePID, MessageLogger.MSG_LEVEL_INFO, MessageLogger.CD_REPL_ESTABLISHED, "  REPLICATION ESTABLISHED!!!" );
	
			        		// Now that the replica has been created, we can subscribe to the event stream.
						try {
							
							System.out.println("DASHBOARD-LISTENER: Launching UpstreamCommService.");
							upstreamCommService = new UpstreamCommService();
							upstreamCommService.start();
							System.out.println("Upstream Comm Service started...");
							
						} catch (Exception e) {
							System.out.println("DASHBOARD-LISTENER: Launch failed for UpstreamCommService.");
							e.printStackTrace();
						}
	
		        		} else {
			        		logger.debug( "Ignoring audit record - nothing of interest." );
		        		}
		        		
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
	
	
	// Shuts down the UpstreamComm Service. 
	// Called during a demo reset.
	// Allows the replication to be re-established during a subsequent demo run.
	public void reset() {

		// shut down the UpstreamCommService.
		if (upstreamCommService != null)
			upstreamCommService.decommission();
		
	}
	
	

	
    public static void main(String[] args) throws Exception {
    	
	    	AuditListenerService ids = new AuditListenerService();
	    	ids.start();
    	
    }

}
