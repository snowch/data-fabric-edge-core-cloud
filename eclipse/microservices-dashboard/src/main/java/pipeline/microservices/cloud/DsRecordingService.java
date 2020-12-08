package pipeline.microservices.cloud;

import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import pipeline.util.AppConfig;
import pipeline.util.StreamsHelper;

/**
 * 
 *  Monitors all requests for assets and write those requests to a file,
 *  which becomes a data set that data scientists can use to build recommendation engines. 
 * 
 */
public class DsRecordingService {

    static class Formatter {
		public static String format(String inputString, int maxLength) {
			String formattedStr;
			if (inputString == null) {
				formattedStr = "";
			} else {
				if (inputString.length() <= maxLength) {
					formattedStr = inputString;
				} else {
					formattedStr = inputString.substring( 0, maxLength-1 );
				}
			}
			return formattedStr;
		}
    }

    public static void main(String[] args) throws Exception {

		ObjectMapper mapper = new ObjectMapper();
		String separator = System.getProperty("line.separator");
		File dataRequestsFileTXT;
		File dataRequestsFileJSON;
		
		// Validate the environment.
		
	    	// These objects are rebuilt each time the demo is run,
		// allowing the demo to be restarted in a clean state.
		System.out.println("Creating required system objects...");
	    	
		// Start with an empty (local) data stream.
		String cloudStream = AppConfig.getConfigValue("cloud-stream-fullPath");
		try {
			StreamsHelper.deleteStream(cloudStream);
			StreamsHelper.createStream(cloudStream);
		} catch (Exception e) {
			System.out.println("Could not create stream (" + cloudStream + ").");
			e.printStackTrace();
			System.exit(1);
		}

		String inputStreamName = AppConfig.getConfigValue("cloud-stream-topic");

		// Files to log all data requests (for use by data science teams to create recommendation engines).
		// Delete files from previous demo runs.
		// Stores requests as both text files and json files. 
		// In demonstration, use text file to show real-time data being consolidated for data science team.
		// Json file might be useful to demonstrate with Drill? (just providing options!).
		String dataRequestsFilename  = "/mapr/" + AppConfig.getConfigValue("cloud-cluster-name") + AppConfig.getConfigValue("cloud-requestedDataFile-fullPath");
		try {

			dataRequestsFileTXT = new File(dataRequestsFilename + ".txt");
			dataRequestsFileTXT.delete();
			dataRequestsFileTXT.createNewFile();
			
			dataRequestsFileJSON = new File(dataRequestsFilename + ".json");
			dataRequestsFileJSON.delete();
			dataRequestsFileJSON.createNewFile();
			
			System.out.println("Created data files (" + dataRequestsFilename + ").");

		} catch (Exception e) {
			throw new IllegalStateException("Could not create data science file (" + dataRequestsFilename + ")." );
		}
		
		// Subscribe to the specified input stream:topic.
		KafkaConsumer<String, String> inputStreamConsumer = StreamsHelper.getConsumer();
		List<String> topics = new ArrayList<>();
        topics.add(inputStreamName);
        inputStreamConsumer.subscribe(topics);

        try { 
        	
	    		// Listen for new messages indefinitely.
        		System.out.println("Listening for messages.");
        		System.out.println("This window only indicates service is still running.");
        		System.out.println("For demo, launch a new terminal and run 'tail -f " + dataRequestsFilename + ".txt'");
        		System.out.println("Messages must be replicated from HQ site in order to see anything.");
			while (true) {
				
				ConsumerRecords<String, String> records = inputStreamConsumer.poll(1000);
	            for (ConsumerRecord<String, String> record : records) {
	
		            	// Extract fields from input message.
		            	JsonNode msgRoot = mapper.readTree(record.value());
		            
					// Log the request to a file for the data science team.
	                try {
	
						// Open file in append mode.
						FileWriter fileWriter = new FileWriter(dataRequestsFileTXT, true);
						PrintWriter linePrinter = new PrintWriter(fileWriter);

						System.out.println("");;
						System.out.println("Message Received");

						// Pull some fields and output to .txt file.
						JsonNode msgData = msgRoot.path("data").get(0);
						String nasa_id = Formatter.format( msgData.get("nasa_id").textValue(), 10);
						String center = Formatter.format( msgData.get("center").textValue(), 7 );
						String mediaType = Formatter.format( msgData.get("media_type").textValue(), 10 );
						String searchTerm = Formatter.format( msgData.get("search_term").textValue(), 10 );
						String title = Formatter.format( msgData.get("title").textValue(), 25 );
						String description = Formatter.format( msgData.get("description").textValue(), 30 );
						
						linePrinter.printf("%-10s | %-7s | %-10s | %-10s | %-25s | %-30s", nasa_id, center, mediaType, searchTerm, title, description + separator);
						linePrinter.close();
						fileWriter.close();

						// Output full JSON document to .json file.
						fileWriter = new FileWriter(dataRequestsFileJSON, true);
						fileWriter.write( msgRoot.toString() + separator );
						fileWriter.close();
	
					} catch (Exception e) {
						System.out.println("Could not record request to data science file. Skipping.");
						e.printStackTrace();
					}
			            			
	            }
	            
	            System.out.print(". ");
	            
			}
		
	    	} catch (Exception e) {
	    		
			System.out.println("Fatal error processing messages. Shutting down.");
			e.printStackTrace();
			
	    	} finally {
	     		
	 		try {
		        inputStreamConsumer.close();
	 		} catch (Exception e) {}
	 		
	    	}
	
    }
	
}


