package pipeline.util;

import java.util.Arrays;
import java.util.Scanner;
import java.util.Date;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import pipeline.microservices.hq.ImageClassifierService;
import pipeline.microservices.hq.ImageClassifierServiceV2;
import pipeline.util.edge.DemoHelper;


/*  
 *  DashboardListener is a controller class that listens for messages received 
 *  from the dashboard and performs the associated actions.
 *  
 *  Supports both dashboard variants (HQ and Edge).
 *  
 *  Controls common to both:
 *    - Request list of running services
 *    - Controlling the application speed
 *  
 *  HQ controls:
 *    - Deploying/Decommissioning the Image Classifier services.
 *  
 *  EDGE controls:
 *    - Pausing/Resuming Replication.
 *    - Demo Reset.
 *    
 */
public class DashboardListener extends Thread {
	
	public static final String APP_CODE = "DASHBOARD_LISTENER";
	public static final String APP_DISPLAY_NAME = "DASHBOARD_LISTENER";

	boolean keepRunning = true;
	private MessageLogger logger;

	// HQ
	ImageClassifierService   icsv1;
	ImageClassifierService   icsv1b;
	ImageClassifierServiceV2 icsv2;
	
	// EDGE
	private DemoHelper demoHelper;
	private KafkaProducer<String, String> outputStreamProducer;
	private String outputStreamName;

	
	public DashboardListener() {
		logger = new MessageLogger(APP_CODE, APP_DISPLAY_NAME);
	}
	
	public void setDemoHelper(DemoHelper demoHelper) {
		this.demoHelper = demoHelper;
	}


	@Override
	public void run() {
		
		Date lastTime = new Date();
		Date currentTime;
		
		try {
			  
			// Subscribe to updates from the dashboard.
			KafkaConsumer<String, String> inputStreamConsumer = StreamsHelper.getConsumer();
			inputStreamConsumer.subscribe( Arrays.asList( AppConfig.getConfigValue("streamTopic-dashboardInbound") ) );
			
			// Used to send data back from the EDGE to HQ .
			// e.g. requests for Assets.
			outputStreamName = AppConfig.getConfigValue("streamTopic-assetRequest");
			outputStreamProducer = StreamsHelper.getProducer();

			//Start processing messages
			while (keepRunning) {

				ConsumerRecords<String, String> records = inputStreamConsumer.poll(100);
				for (ConsumerRecord<String, String> record : records) {
					
					logger.admin("Received an update from the dashboard.");
					logger.debug( "Message -> " + record.value() );
					
					ObjectMapper mapper = new ObjectMapper();
					JsonNode msg = mapper.readTree(record.value());
					String directive = msg.get("directive").textValue();
					
					switch (directive) {
					
					// HQ Services
					
						case("deployV1Service"):
							// Deploys an Image Classifier,
							// demonstrating the ease in which microservice architectures allows you
							// to introduce new capabilities into your applications and data pipelines.
							// 2 instances are deployed to illustrate parallelism.
							try {
								
								logger.admin("Launching v1 ImageClassifierService.");
								
								icsv1 = new ImageClassifierService("t1");
								icsv1.start();
								
								// Capability is here to run multiple consumers as multiple threads.
								// StreamsHelper.createStream sets defaultPartitions to 1.
								// Multiple threads led to confusion as messages can appear out of sequence.  
								// icsv1b = new ImageClassifierService("t2");
								// icsv1b.start();
								
							} catch (Exception e) {
								System.out.println("DASHBOARD-LISTENER: Launch failed for v1 ImageClassifierService.");
								e.printStackTrace();
							}
							break;
					
						case("deployV2Service"):
							// Deploys a slightly modified version of the image classifier,
							// demonstrating the ease with which system capabilities can be upgraded.
							try {
								logger.admin("Launching v2 ImageClassifierService.");
								icsv2 = new ImageClassifierServiceV2();
								icsv2.start();
							} catch (Exception e) {
								System.out.println("DASHBOARD-LISTENER: Launch failed for v2 ImageClassifierService.");
								e.printStackTrace();
							}
							break;
							
						case("decommissionV1Service"):
							// Sends a STOP signal to the classifier service.
							try {
								logger.admin("Decomissioning v1 ImageClassifierService.");
								icsv1.decommission();
							} catch (Exception e) {
								System.out.println("DASHBOARD-LISTENER: Decommission failed for v1 ImageClassifierService.");
								e.printStackTrace();
							}
							break;
						
						case("decommissionV2Service"):
							// Sends a STOP signal to the V2 classifier service.
							try {
								logger.admin("Decomissioning v2 ImageClassifierService.");
								icsv2.decommission();
							} catch (Exception e) {
								System.out.println("DASHBOARD-LISTENER: Decommission failed for v2 ImageClassifierService.");
								e.printStackTrace();
							}
							break;
							
					// EDGE Services
							
						case("resetDemo"):
							// Resets the Demo.
							// Undoes all the replication steps such that they can be performed again.
							demoHelper.resetDemo();
							break;
						
						case("pauseReplication"):
							System.out.println("DASHBOARD-LISTENER: Received request to Pause Stream Replication...");
							demoHelper.pauseReplication();
							break;
						
						case("resumeReplication"):
							System.out.println("DASHBOARD-LISTENER: Received request to Resume Stream Replication...");
							demoHelper.resumeReplication();
							break;

						case("requestAsset"):
							// Send a message back to the HQ cluster, requesting the specified asset.
							try {
								
								System.out.print("DASHBOARD-LISTENER: Received request for assset.");
								String assetID = msg.get("assetID").textValue();
								System.out.println(" ID extracted: " + assetID);
								
								ObjectMapper mapper2 = new ObjectMapper();
								ObjectNode outboundStreamsMsg = mapper2.createObjectNode();
								outboundStreamsMsg.put("description", "Asset requested by Edge-Cluster.");
								outboundStreamsMsg.put("assetID", assetID);
								
				        			ProducerRecord<String, String> rec = new ProducerRecord<String, String>(outputStreamName, outboundStreamsMsg.toString() );
				        			outputStreamProducer.send(rec);
				        			outputStreamProducer.flush();
				        			System.out.println( "Sent request to HQ for new asset using stream:topic (" + outputStreamName + ")" );

							} catch (Exception e) {
								System.out.println("DASHBOARD-LISTENER: UNEXPECTED ERROR requestign assset from HQ.");
								e.printStackTrace();
							}
							break;

					// Common Services
							
						case("sendRunningServicesList"):
							logger.admin("Sending list of running services.");
							logger.sendRunningServicesList();
							break;
						
						default:
							try {
								// Modifies the speed settings.
								logger.admin("Updating new delay value.");
								AppConfig.setSystemDelay( msg.get("directive").textValue(), Integer.parseInt(msg.get("newSpeed").textValue()) );
							} catch (Exception e) {
								System.out.println("DASHBOARD-LISTENER: Failed to update speed setting.");
								e.printStackTrace();
							}

					}

				}
				
				// Send a heartbeat to the clients
				currentTime = new Date();
				if ( currentTime.getTime() - lastTime.getTime() > 1000 ) {
					logger.heartbeat();
				}
				
			}
		} catch (Exception e) {
			System.out.println("UNEXPECTED ERROR inside DashboardListener.");
			e.printStackTrace();
		}
	}
	

	// Provides the ability to run from the command line.
	// Normally, a DashboardListener is launched by the PipelineApp.
	public static void main(String[] args) throws Exception {
		
		DashboardListener dl = new DashboardListener();
		dl.start();
		System.out.println("DashboardListener launched. Type 'exit' to quit.");
		
	    Scanner in = new Scanner(System.in);
        String line = "";
        while (!line.equals("exit")) {
            line = in.next();
        }
        in.close();
        dl.keepRunning = false;
        System.out.println("Stopping DashboardListener...");
        dl.join();

	}

}
