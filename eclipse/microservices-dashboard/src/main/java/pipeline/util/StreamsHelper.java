package pipeline.util;

import java.util.Properties;
import java.io.IOException;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.hadoop.conf.Configuration;
import com.mapr.streams.Admin;
import com.mapr.streams.Streams;
import com.mapr.streams.StreamDescriptor;

import com.mapr.db.MapRDB;


// Helper class for Streams operations.
public class StreamsHelper {
	

	// Tests for the existence of the specified stream.  
	public static boolean streamExists(String streamName) {

		String streamNameNoTopic;
		int streamTopicDelimiter = streamName.indexOf(":");
		
		if (streamTopicDelimiter == -1) {
			streamNameNoTopic = streamName;
		} else {
			streamNameNoTopic = streamName.substring(0, streamTopicDelimiter );
		}

		return MapRDB.tableExists(streamNameNoTopic);

	}
    


	public static void deleteStream(String streamName) throws IllegalArgumentException, IOException{
		
		System.out.println("Deleting stream " + streamName + "...");
		
		if ( streamExists(streamName) ) {

			Configuration conf = new Configuration();
			Admin streamAdmin = Streams.newAdmin(conf);
			streamAdmin.deleteStream(streamName);
			streamAdmin.close();
			System.out.println("Stream deleted.");

		} else {
			System.out.println("Deletion skipped... Stream does not exist.");
		}
		
	}
	
	
	public static void createStream(String streamName) throws IllegalArgumentException, IOException{

		System.out.println("Creating stream " + streamName + "...");
		
		StreamDescriptor desc = Streams.newStreamDescriptor();
		// Original pipeline demo had two partitions to demonstrate parallelized stream processing.
		// But it does raise some confusing questions.
		// Keeping things simple here - 1 partition.
		desc.setDefaultPartitions(1);
		//desc.setDefaultPartitions(2);
		desc.setCompressionAlgo("lz4");
		desc.setAutoCreateTopics(true);
		desc.setProducePerms("p");
		desc.setConsumePerms("p");
		desc.setTopicPerms("p");

		Configuration conf = new Configuration();
		Admin streamAdmin = Streams.newAdmin(conf);
		streamAdmin.createStream(streamName, desc);
		streamAdmin.close();
		
		System.out.println("Stream created.");
		
	}
	
	
	public static KafkaProducer<String, String> getProducer() {
		
		// Set the config props and return a new Streams producer handle.
		Properties props = new Properties();
		props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
		props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
		    
		KafkaProducer<String, String> producer = new KafkaProducer<String, String>(props);
		return producer;

	}
	  
	public static KafkaConsumer<String, String> getConsumer() {
		return getConsumer(null, null);
	}
	
	public static KafkaConsumer<String, String> getConsumer(String consumerGroup, String startAtEnd) {

		// Set the config props and return a new Streams producer handle.
		Properties props = new Properties();
		props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
		props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
		
		// Unless specified, configure consumers to start at the beginning of the message stream.
		if (startAtEnd == null) {
			props.put("auto.offset.reset", "earliest");
		// The following code does NOT ensure that the consumer will start at the end.
		// Consumers of the audit stream were picking up earlier events.
		// Solution was to introduce the KafkaSeekToEndListener class.
		} else {
			props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
		}
			        
		if (consumerGroup != null) {
			props.put("group.id", consumerGroup);
		}
		
		// Additional configuration parameters.
		// props.put("enable.auto.commit", "true");
		// props.put("auto.commit.interval.ms", "1000");
		// props.put("session.timeout.ms", "30000");

		KafkaConsumer<String, String> consumer = new KafkaConsumer<String, String>(props);
		return consumer;

	}
	
	
	public static void main(String[] args) throws IOException {

		String usage = "Usage: StreamsHelper [deleteStream | createStream] </streampath/streamname>";
		
		if ( args.length != 2 ) {
			System.out.println(usage);
			System.exit(1);
		}

		if ( args[0].equals("deleteStream") ) {
			
			StreamsHelper.deleteStream(args[1]);
			
		} else if ( args[0].equals("createStream") ) {
			
			StreamsHelper.createStream(args[1]);;
			
		} else {
			System.out.println(usage);
			System.exit(1);		    	
		}
		    
	}

	
}
