package pipeline.util;

import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;

import java.util.Collection;


/**
 * 
 * Used by Kafka consumers that want to start consuming at the end of the Stream.
 * Currently used by the AuditStreamListener.
 *
 */
public class KafkaSeekToEndListener<K, V> implements ConsumerRebalanceListener {

	private Consumer<K, V> consumer;
	
	public KafkaSeekToEndListener(Consumer<K, V> consumer) {
        this.consumer = consumer;
    }

    @Override
    public void onPartitionsRevoked(Collection<TopicPartition> partitions) {

    }

    @Override
    public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
    		for (TopicPartition partition: partitions)
    			consumer.seekToEnd(partition);
    }
    
}
