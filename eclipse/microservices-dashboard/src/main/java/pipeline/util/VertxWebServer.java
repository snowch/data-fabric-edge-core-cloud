package pipeline.util;

import java.util.Arrays;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.PermittedOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import io.vertx.ext.web.handler.sockjs.BridgeEventType;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;


/* 
 *  Starts a Vert.x WebServer (VWS).
 *  Listens for system monitoring messages on Streams and publishes them to the Vert.x EventBus for display in the Dashboard.
 *  Receives updates from the Dashboard (via the Vert.x EventBus) and publishes them to Streams.  
 */
public class VertxWebServer extends Thread {

	public static final String APP_CODE = "VERTX";
	public static final String APP_DISPLAY_NAME = "VERT.X WEB SERVER";
	private MessageLogger logger;

	private static String outputStreamName;

	public VertxWebServer() {
		logger = new MessageLogger(APP_CODE, APP_DISPLAY_NAME);
	}

	@Override
	public void run() {
		
		logger.admin("VERTX-WEB-SERVER: Launching Vert.x...");
	
    		int httpPort = Integer.parseInt( AppConfig.getConfigValue("vertx-http-port") );
        String inputStreamName = AppConfig.getConfigValue("streamTopic-systemMonitoring");
        outputStreamName = AppConfig.getConfigValue("streamTopic-dashboardInbound");
        
        // Set up Vert.x.
        
        Vertx vertx = Vertx.vertx();
        Router router = Router.router(vertx);

        // Add security rules to allow outbound traffic to "dashboard",
        // as well as updates from dashboard on "dashboard-inbound".
    		BridgeOptions options = new BridgeOptions()
    			.addOutboundPermitted( new PermittedOptions().setAddress("dashboard") )
			.addInboundPermitted( new PermittedOptions().setAddress("dashboard-inbound") );

	    	SockJSHandler eventBusHandler = SockJSHandler.create(vertx).bridge(options, event -> {
	    		if (event.type() == BridgeEventType.SOCKET_CREATED) {
	    			logger.admin("VERTX-WEB-SERVER: A socket was created. Dashboard is connected.");
	    		}
	    		event.complete(true);
	    	});
    	
        router.route("/eventbus/*").handler(eventBusHandler);
        
        router.route().handler( StaticHandler.create().setCachingEnabled(false) );
        router.route().consumes("application/json");
        router.route().produces("application/json");

        // Watch out: switching to port 9081, this doesn't work. No response from browser. Maybe a NAT issue?
        // I do get Hello World if I browse to http://localhost:8081. (or /eventbus, or /anywhere).
        HttpServer httpServer = vertx.createHttpServer();
        //httpServer.requestHandler(req -> req.response().end("Hello World!")).listen(httpPort, ar -> {
        httpServer.requestHandler(router::accept).listen(httpPort, ar -> {
                if (ar.succeeded()) {
                logger.admin("VERTX-WEB-SERVER: Vert.x HTTP server started on port " + httpPort);
            } else {
                ar.cause().printStackTrace();
            }
        });
        
        // Register the handler to receive inbound updates from the dashboard.
        vertx.eventBus().consumer( "dashboard-inbound", message -> relayDashboardUpdatesToStreams(message.body().toString()) );
        
        // Start consuming SYSMON messages from Streams and publishing them to vert.x EventBus for display in Dashboard.
        KafkaConsumer<String, String> streamsConsumer = StreamsHelper.getConsumer();
        streamsConsumer.subscribe(Arrays.asList(inputStreamName));
        logger.admin("VERTX-WEB-SERVER: Consuming messages from Stream '" + inputStreamName + "'.");
        logger.admin("VERTX-WEB-SERVER: Publishing to EventBus for display in dashboard.");
        while (true) {
            ConsumerRecords<String, String> records = streamsConsumer.poll(200);
            for (ConsumerRecord<String, String> record : records) {
                vertx.eventBus().publish("dashboard", record.value() );
                logger.debug(record.value());
            }
        }

	}

	
    // Receives inbound messages from the dashboard and publishes them to Streams for distribution.
    public static void relayDashboardUpdatesToStreams(String msg) {
    	
    	    System.out.println("VERTX-WEB-SERVER: Received message from dashboard (" + msg + "), publishing to Streams...");
		KafkaProducer<String, String> streamsProducer = StreamsHelper.getProducer();
        ProducerRecord<String, String> rec = new ProducerRecord<String, String>(outputStreamName, 0, null, msg );
		streamsProducer.send(rec);
		streamsProducer.flush();

    }

	
    public static void main(String[] args) throws Exception {

		VertxWebServer vws = new VertxWebServer();
		vws.start();

    }

    
}
