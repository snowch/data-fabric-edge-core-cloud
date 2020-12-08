package pipeline.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;


// Handles all message logging for the pipeline application.
// Publishes system messages to a stream, to be picked up by subscribed monitoring applications.
public class MessageLogger {

	public static final int MSG_LEVEL_FATAL = 5;   // Service-level errors, shuts down the service.
	public static final int MSG_LEVEL_ERROR = 4;   // Message-level errors, processing continues with next message.
	public static final int MSG_LEVEL_INFO  = 3;   // Regular status info from running processes.
	public static final int MSG_LEVEL_ADMIN = 2;   // Written to console (not sent to Dashboard), regardless of debug setting.
	public static final int MSG_LEVEL_DEBUG = 1;   // Written to console (not sent to Dashboard) if debug set to true.  
	public static final int MSG_LEVEL_BEAT  = 0;   // Special case - heartbeats.
	
	// String values for the above levels are based on Bootstrap options for the PANEL display class. 
	private final String[] messageLevels = {"HEARTBEAT", "DEBUG", "ADMIN", "INFO", "ERROR", "FATAL"};
	
	// Significant processing events used by the Dashboard application.
	public static final String CD_SERVICE_START           = "SERVICE_START";
	public static final String CD_SERVICE_STOP            = "SERVICE_STOP";
	public static final String CD_SERVICE_FAIL            = "SERVICE_FAIL";
	public static final String CD_EVENT_PROCESSING_START  = "EVENT_START";
	public static final String CD_EVENT_PROCESSING_FINISH = "EVENT_FINISH";
	public static final String CD_REPL_ESTABLISHED        = "REPL_ESTABLISHED";
	public static final String CD_REPL_SEVERED			 = "REPL_SEVERED";
	public static final String CD_ASSET_AVAIL             = "ASSET_AVAIL";
	public static final String CD_MSG_TEXT                = "TEXT";
	public static final String CD_MSG_IMAGE               = "IMAGE";
	public static final String CD_DEMO_RESET_COMPLETE     = "DEMO_RESET_COMPLETE";
	public static final String CD_REPL_PAUSE_COMPLETE		 = "REPL_PAUSE_COMPLETE";
	public static final String CD_REPL_RESUME_COMPLETE	 = "REPL_RESUME_COMPLETE";
	public static final String CD_HEARTBEAT				 = "HEARTBEAT";
	
	// HashMap to keep track of which services have been started.
	private static Map<String, String> servicesStarted = new HashMap<String, String>();

	private static int sysPID = 1;

	private String appCode;
	private String appDisplayName;
	private String threadID;
	private KafkaProducer<String, String> sysmonStreamProducer;
	private String sysmonStreamName;
	private boolean debug = false;


	public MessageLogger(String appCode, String appDisplayName) {
		this(appCode, appDisplayName, null);
	}
		
	public MessageLogger(String appCode, String appDisplayName, String threadID) {
		  
		this.appCode = appCode;
		this.appDisplayName = appDisplayName;
		this.threadID = threadID;
		  
		// Verify the stream exists for system messages.
		sysmonStreamName = AppConfig.getConfigValue("streamTopic-systemMonitoring");
		if (!StreamsHelper.streamExists(sysmonStreamName)) {
			throw new IllegalStateException("Can't create logger - Stream " + sysmonStreamName + " does not exist.");
		}
		  
		sysmonStreamProducer = StreamsHelper.getProducer();
		
		String debugStr = AppConfig.getConfigValue("debug");
		if (debugStr.equals("true")) {
			debug = true;
		}
	      
	}
	
	
	// Generates a unique identifier.
	// Called once to identify each microservice, and called once for each message processed.
	public String generatePID() {
		String pid = "p" + appCode + sysPID++;
		return pid;
	}
    
	
	/* ****************************************************************************************
	 * 
	 *  Primary method by which microservices log general status information.
	 *  Additionally, major events (START, STOP, etc.) must be reported using the subsequent convenience functions.
	 *  
	 *  Parameters:
	 *    processID: Uniquely identifies the service or the processing of a specific message.
	 *    msgLevel:  One of the MSG_LEVEL constants indicating normal, warning, error, etc.
	 * 	  msgCode:   One of the CD_ constants, which gets interpreted by the Dashboard app. 
	 * 	  msgText:   Normally the display text, but dependent on the msgCode. When msgCode=URL, msgText is the URL.
	 *
	 * **************************************************************************************** */
	public void logMessage(String processID, int msgLevel, String msgCode, String msgText) {
		logMessageInt(processID, null, msgLevel, msgCode, msgText);
	}
	
	// Prints a stack trace to the console.
	public void logMessage(String processID, int msgLevel, Exception e) {
		System.out.println(appCode + ": Exception caught, printing stack trace.");
		e.printStackTrace();
	}
	
	public void debug(String msgText) {
		logMessageInt( null, null, MessageLogger.MSG_LEVEL_DEBUG, MessageLogger.CD_MSG_TEXT, msgText );
	}

	public void admin(String msgText) {
		logMessageInt( null, null, MessageLogger.MSG_LEVEL_ADMIN, MessageLogger.CD_MSG_TEXT, msgText );
	}
		
      
	/* ****************************************************************************************
	 * 
     *  All services must report the following events in order to be displayed in the Dashboard App:
     *    - logServiceStart: Called once, allows each service status to be displayed in the Services window.
     *    - logMsgStart:     Called once for each message, triggers the creation of a new display tile.
     *    - logMsgFinish:    Called once for each message, triggers the removal of a display tile.
	 *
	 * **************************************************************************************** */
	
	
	// MUST BE CALLED by each microservice at startup.
	// Used by the Dashboard to display lists of running services.
	public void logServiceStart(String pid) {
		logMessageInt(pid, null, MSG_LEVEL_INFO, CD_SERVICE_START, "(Service Start)");
	}


	public void logServiceStop(String pid) {
		
		logMessageInt(pid, null, MSG_LEVEL_INFO, CD_SERVICE_STOP, "(Service Stop)");   
		
		if ( !servicesStarted.containsKey(pid) ) {
			servicesStarted.remove(pid);
			debug( "(message-logger): Removed service '"+ this.appDisplayName + "' from list of started services." );
		}

	}
	
	
	// Pushes a list of started services to the sysmon message stream, 
	// where it can be picked up by clients for GUI display.
	public void sendRunningServicesList() {
		
		try {
			
			debug( "(message-logger): Received request to send list of started services." );

			for (Map.Entry<String, String> entry : servicesStarted.entrySet()) {
				// ProducerRecord(stream:topic, partition, key, value)
				ProducerRecord<String, String> rec = new ProducerRecord<String, String>(sysmonStreamName, 0, null, entry.getValue() );
				sysmonStreamProducer.send(rec);
			}
			
		} catch (Exception e) {
			System.out.println("MESSAGE-LOGGER: ERROR: Couldn't deliver list of started services.");
			e.printStackTrace();
		}
		
	}
	
	
	// To be called whenever a microservice fails for any reason.
	// Used by the Dashboard to display lists of services that are down.
	public void logServiceFail(String pid, String errorDisplayText, Exception e ) {
    		logMessageInt(pid, null, MSG_LEVEL_FATAL, CD_SERVICE_FAIL, errorDisplayText);   	  
  		logMessage( pid, MSG_LEVEL_FATAL, e);
		sysmonStreamProducer.flush();
  	}

	// MUST BE CALLED at the start of processing of an individual message.
	// Used by the Dashboard to create a new tile.
	public void logMsgStart(String pid, String ppid, String msgText) {
		logMessageInt(pid, ppid, MSG_LEVEL_INFO, CD_EVENT_PROCESSING_START, msgText);   	  
	}

	// MUST BE CALLED when processing of an individual message has finished normally.
	// Used by the Dashboard to free a tile for use by the next message processed.
	public void logMsgFinish(String pid) {
		logMessageInt(pid, null, MSG_LEVEL_INFO, CD_EVENT_PROCESSING_FINISH, "(Message Finish)");   	  
	}
      
	// Must be called whenever the processing of an individual message fails.
	// This calls logMsgFinish at the end of it, so call logMsgFinish OR logMsgFail, but not both.
	public void logMsgFail(String processID, String msgText, Exception e ) {
		logMessageInt( processID, null, MSG_LEVEL_ERROR, CD_MSG_TEXT, msgText );
		if (e != null) {
	  		logMessage( processID, MSG_LEVEL_ERROR, e);
		}
		logMsgFinish(processID);
	}
	
	
	// Internal workhorse.  
	// All message logging code is implemented here.  
	// Public functions call this with the appropriate settings.
	private void logMessageInt(String processID, String parentID, int msgLevel, String msgCode, String msgText) {
		
		String msgLevelText = null;
		try {
			msgLevelText = messageLevels[msgLevel];
		} catch (Exception e) {
			msgLevelText = "UNKNOWN";
			//throw new IllegalArgumentException("Illegal message level: " + msgLevel);
		}
		
		// Output to console.
		if ( (msgLevel > MSG_LEVEL_DEBUG) || (debug && msgLevel == MSG_LEVEL_DEBUG) ) {
			DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
			Date date = new Date();
			System.out.println(dateFormat.format(date) + " " + appCode + ": " + msgLevelText + ": " + msgText);
		}
		  
		if (msgLevel >= MSG_LEVEL_INFO) {
			  
			// Publish the event to Streams.
			
			ObjectMapper mapper = new ObjectMapper();
			ObjectNode sysmonMsg = mapper.createObjectNode();
			sysmonMsg.put("appCode", this.appCode);
			sysmonMsg.put("appDisplayName", this.appDisplayName);
			sysmonMsg.put("pid", processID);
			sysmonMsg.put("ppid", parentID);
			sysmonMsg.put("threadID",  this.threadID);
			sysmonMsg.put("msgLevel", msgLevelText);
			sysmonMsg.put("msgCode", msgCode);
			sysmonMsg.put("msgText", msgText);
			  
			// ProducerRecord(stream:topic, partition, key, value)
			ProducerRecord<String, String> rec = new ProducerRecord<String, String>(sysmonStreamName, 0, null, sysmonMsg.toString() );
			sysmonStreamProducer.send(rec);
			debug( "(message-logger): " + sysmonMsg.toString() );
			
			// Maintain a list of running services.
			// If the dashboard isn't already connected when the demo starts up,
			// it won't receive indications that the services have started.
			// The initial workaround was to start the app, wait for the dashboard to connect, and then continue.
			// That got tiresome, and subsequently connected dashboards (e.g. a mobile client) would look incomplete.
			// Now, dashboards just request a list of running services.
			if ( (msgCode == CD_SERVICE_START) && !servicesStarted.containsKey(processID) ) {
				servicesStarted.put( processID,  sysmonMsg.toString() );
				debug( "(message-logger): Added service '"+ this.appDisplayName + "' to list of started services." );
			}
			
	  		  
			// Every time we write a message that gets displayed on the Dashboard, we take a pause.
			// This is for demo purposes, so people can watch the activity.
			try {
				
				int sleepTime;
				if (msgLevel >= MSG_LEVEL_ERROR) {
					sleepTime = AppConfig.getDelayError();
				} else {
					sleepTime = AppConfig.getDelayProcessing();
				}
				debug("(message-logger): Sleeping for " + sleepTime + " milliseconds.");
				Thread.sleep(sleepTime);
				
			} catch (Exception e) {
				System.out.println("MESSAGE-LOGGER: ERROR: Couldn't sleep");
				e.printStackTrace();
			}
	  			
		}
		  
	}
	
	
	// Sends a heartbeat signal to the clients.
	// In demo situations, it's good to know that you lost the connection. 
	public void heartbeat() {
		
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode sysmonMsg = mapper.createObjectNode();
		// If these aren't present, the client Javascript will throw errors.
		sysmonMsg.put("appCode", "NA");
		sysmonMsg.put("appDisplayName", "NA");
		sysmonMsg.put("pid", "NA");
		sysmonMsg.put("ppid", "NA");
		sysmonMsg.put("threadID",  "NA");
		sysmonMsg.put("msgLevel", MSG_LEVEL_BEAT);
		sysmonMsg.put("msgCode", CD_HEARTBEAT);
		sysmonMsg.put("msgText", "NA");
		  
		ProducerRecord<String, String> rec = new ProducerRecord<String, String>(sysmonStreamName, 0, null, sysmonMsg.toString() );
		sysmonStreamProducer.send(rec);

	}

	  
    // Command line test.	
	public static void main(String[] args) {

		if (args.length != 5) {
			System.err.println("Usage: MessageLogger <processID> <parentProcessID> <msgLevel> <msgCode> <msgText>");
            System.err.println("Use msgLevel > 0 to see message sent to system monitoring stream.");
            System.exit(-1);
        }
        
        MessageLogger ml = new MessageLogger("TESTUNIT", "Test Unit");
        ml.logMessageInt( args[0], args[1], Integer.parseInt(args[2]), args[3], args[4]);

	}

}
