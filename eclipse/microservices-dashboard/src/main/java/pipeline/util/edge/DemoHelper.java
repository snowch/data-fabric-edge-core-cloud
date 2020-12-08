package pipeline.util.edge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import pipeline.microservices.edge.*;
import pipeline.util.AppConfig;
import pipeline.util.MessageLogger;
import pipeline.util.StreamsHelper;

import java.io.File;

import org.apache.commons.io.FileUtils;


/**
 * 
 *  Helper class to perform demo resets.
 * 
 */
public class DemoHelper {
	
	public static final String APP_CODE = "DEMO_HELPER";
	public static final String APP_DISPLAY_NAME = "Demo Reset";

	private AuditListenerService auditListenerService;
	private String localStream;
	private String hqClusterPath;
	private String remoteStream;
	private MessageLogger logger;

	
	public DemoHelper() {

		localStream = AppConfig.getConfigValue("streamReplicated-fullPath");
		hqClusterPath = "/mapr/" + AppConfig.getConfigValue("hq-cluster-name");
		remoteStream = hqClusterPath + localStream;

		logger = new MessageLogger(APP_CODE, APP_DISPLAY_NAME);

	}
			

	public void linkAuditListenerService(AuditListenerService als) {
		auditListenerService = als;
	}
	
	
	// Pauses two-way stream replication between HQ and EDGE.
	public void pauseReplication() {
		
		try {
			
			logger.admin("Pausing replication ..." );
			
			boolean success = true;
			MCSRestClient mcs = new MCSRestClient();
			String responseString;
			
			logger.admin( " .. Pausing local to remote ..." );
			responseString = mcs.callAPI( "/stream/replica/pause?path=" + localStream + "&replica=" + remoteStream );
			
			if ( mcs.getReturnStatus(responseString).equals("OK") ) {
				logger.admin("  ... replication of local to remote paused successfully.");
			} else {
				success = false;
				logger.admin( "  ... UNSUCCESSFUL. Could not pause replication (local to remote)." );
				logger.admin(responseString);
			}

			logger.admin( " .. Pausing remote to local ..." );
			responseString = mcs.callAPI( "/stream/replica/pause?path=" + remoteStream + "&replica=" + localStream );
			
			if ( mcs.getReturnStatus(responseString).equals("OK") ) {
				logger.admin("  ... replication of remote to local paused successfully.");
			} else {
				success = false;
				logger.admin( "  ... UNSUCCESSFUL. Could not pause replication (remote to local)." );
				logger.admin(responseString);
			}
			
			if (success) {
				logger.admin("Pause Replication successful.");
			}
			
			logger.logMessage("-1", MessageLogger.MSG_LEVEL_INFO, MessageLogger.CD_REPL_PAUSE_COMPLETE, "Pause Replication Complete.");

		} catch (Exception e) {
			logger.logServiceFail("-1", "Pause Replication FAILED", e);
		}
		
	}
	
	
	// Resumes two-way stream replication between HQ and EDGE.
	public void resumeReplication() {
		
		try {
			
			logger.admin("Resuming replication ..." );
			
			boolean success = true;
			MCSRestClient mcs = new MCSRestClient();
			String responseString;
			
			logger.admin( " .. Resuming local to remote ..." );
			responseString = mcs.callAPI( "/stream/replica/resume?path=" + localStream + "&replica=" + remoteStream );
			
			if ( mcs.getReturnStatus(responseString).equals("OK") ) {
				logger.admin("  ... replication of local to remote resumed successfully.");
			} else {
				success = false;
				logger.admin( "  ... UNSUCCESSFUL. Could not resume replication (local to remote)." );
				logger.admin(responseString);
			}

			logger.admin( " .. Resuming remote to local ..." );
			responseString = mcs.callAPI( "/stream/replica/resume?path=" + remoteStream + "&replica=" + localStream );
			
			if ( mcs.getReturnStatus(responseString).equals("OK") ) {
				logger.admin("  ... replication of remote to local resumed successfully.");
			} else {
				success = false;
				logger.admin( "  ... UNSUCCESSFUL. Could not resume replication (remote to local)." );
				logger.admin(responseString);
			}
			
			if (success) {
				logger.admin("Resume Replication successful.");
			}
			
			logger.logMessage("-1", MessageLogger.MSG_LEVEL_INFO, MessageLogger.CD_REPL_RESUME_COMPLETE, "Resume Replication Complete.");

		} catch (Exception e) {
			logger.logServiceFail("-1", "Resume Replication FAILED", e);
		}
		
	}
	
	// Resets the Demo:
	// Removes the Mirror Volume, Stream replicas, and more.
	public void resetDemo() {
		
		String pid = logger.generatePID();
		try {
			
			logger.admin("Resetting Demo...");
	        	logger.logMsgStart(pid, "NoParentPID", "Resetting Demo");

			boolean success = true;
			MCSRestClient mcs = new MCSRestClient();
			String responseString;
			
			// 1) Remove the Mirror Volume.
			logger.admin( "1) Removing mirror volume (files-missionX)..." );
			responseString = mcs.callAPI( "/volume/list?filter=%5Bvolumename%3D%3Dfiles-missionX%5D" );
			logger.debug(responseString);
			try {
				ObjectMapper objectMapper = new ObjectMapper();
				JsonNode rootNode = objectMapper.readTree( responseString );
				int volumeCount = rootNode.get("total").intValue();
			    if ( volumeCount == 0 ) {
					logger.logMessage(pid, MessageLogger.MSG_LEVEL_INFO, MessageLogger.CD_MSG_TEXT, "Mirror did not exist.");
			    } else {
			    	
					responseString = mcs.callAPI("/volume/remove?name=files-missionX&force=1");
					logger.debug(responseString);
					if ( mcs.getReturnStatus(responseString).equals("OK") ) {
						logger.logMessage(pid, MessageLogger.MSG_LEVEL_INFO, MessageLogger.CD_MSG_TEXT, "Mirror removed.");
					} else {
						throw new Exception("Response from MCS for volume remove was NOT OK.");
					}
			    }
			} catch (Exception e ) {
				success = false;
				logger.logMsgFail(pid, "FAILED! Could not delete mirror volume.", e);
			}

			// 1.5) Remove the files from the Mirror source (/<hq-cluster>/apps/pipeline/data/files-missionX)
			logger.admin( "1.5) Deleting files from source volume (files-missionX) at HQ cluster..." );
			String mirrorSourceLocation = "/mapr/" + AppConfig.getConfigValue("hq-cluster-name") + AppConfig.getConfigValue("missionX-imagesDir-fullPath");
			FileUtils.cleanDirectory( new File(mirrorSourceLocation) );
			logger.logMessage( pid, MessageLogger.MSG_LEVEL_INFO, MessageLogger.CD_MSG_TEXT, "Deleted files from files-missionX at HQ cluster." );

			// 2) Resets the ImageDisplayService. Empties the hash table.  
			logger.admin( "2) Resetting the ImageDisplayService hash table...");
			ImageDisplayService.reset();
			logger.logMessage( pid, MessageLogger.MSG_LEVEL_INFO, MessageLogger.CD_MSG_TEXT, "ImageDisplayService hash table reset.");
			
			// 3) Reset the AuditListenerService (which decommissions the UpstreamCommService).
			logger.admin( "3) Resetting the AuditListenerService...");
			try {
				auditListenerService.reset();
				logger.logMessage( pid, MessageLogger.MSG_LEVEL_INFO, MessageLogger.CD_MSG_TEXT, "AuditListener reset.");
			} catch (Exception e) {
				success = false;
				logger.admin( "  ... UNSUCCESSFUL. AuditListener reset did not complete successfully." );
				logger.logMessage("DemoHelper", MessageLogger.MSG_LEVEL_ERROR, e);
			}
			
			// 4) Remove /hq/replicatedStream as an Upstream Source and Downstream Replica of /edge/replicated Stream.
			logger.admin( "4) Deleting local replicatedStream...");
			StreamsHelper.deleteStream(localStream);
			logger.logMessage( pid, MessageLogger.MSG_LEVEL_INFO, MessageLogger.CD_MSG_TEXT, "Local stream deleted.");
			
			// 5) Remove /edge/replicatedStream as a Downstream Replica of /hq/replicated Stream.
			
			logger.admin( "5) Removing downstream replicas for " + remoteStream + "...");
			responseString = mcs.callAPI( "/stream/replica/list?path=" + remoteStream );
			logger.debug(responseString);
			try {
				
			    ObjectMapper objectMapper = new ObjectMapper();
			    JsonNode rootNode = objectMapper.readTree( responseString );
			    int replicaCount = rootNode.get("total").intValue();
			    
			    if ( replicaCount == 0 ) {
			    		logger.logMessage( pid, MessageLogger.MSG_LEVEL_INFO, MessageLogger.CD_MSG_TEXT, "Downstream replica for HQ stream did not exist.");
			    	} else if ( replicaCount == 1 ) {
			    	
			    		logger.admin( "Removing downstream replica (edge) of HQ replicatedStream ..." );
					responseString = mcs.callAPI( "/stream/replica/remove?path=" + remoteStream + "&replica=" + localStream );
					logger.debug(responseString);
					
					if ( mcs.getReturnStatus(responseString).equals("OK") ) {
						logger.logMessage( pid, MessageLogger.MSG_LEVEL_INFO, MessageLogger.CD_MSG_TEXT, "Downstream replica of HQ replicatedStream removed.");
					} else {
						throw new Exception("Response from MCS for removing downstream replica was NOT OK.");
					}
						
			    } else {
					throw new Exception("Response from MCS had unexpected number of replicas.");
			    }
			    
			} catch (Exception e) {
				success = false;
				logger.logMsgFail(pid, "Could not remove downstream replica of " + remoteStream + ".", e );
			}

			// 6) Remove /edge/replicatedStream as an Upstream Source of /hq/replicated Stream.
			
			logger.admin( "6) Removing upstream Sources for " + remoteStream + "...");
			responseString = mcs.callAPI( "/stream/upstream/list?path=" + remoteStream );
			logger.debug(responseString);
			
			try {
				
			    ObjectMapper objectMapper = new ObjectMapper();
			    JsonNode rootNode = objectMapper.readTree( responseString );
			    int upstreamCount = rootNode.get("total").intValue();

			    if ( upstreamCount == 0 ) {
				    	logger.logMessage( pid, MessageLogger.MSG_LEVEL_INFO, MessageLogger.CD_MSG_TEXT, "Upstream source for HQ stream did not exist.");
			    	} else if ( upstreamCount == 1 ) {
			    	
			    		logger.admin( "Removing upstream source (edge) of HQ replicatedStream ..." );
					responseString = mcs.callAPI( "/stream/upstream/remove?path=" + remoteStream + "&upstream=" + localStream );
					logger.debug(responseString);
					
					if ( mcs.getReturnStatus(responseString).equals("OK") ) {
						logger.logMessage( pid, MessageLogger.MSG_LEVEL_INFO, MessageLogger.CD_MSG_TEXT, "Upstream source of HQ replicatedStream removed.");
					} else {
						throw new Exception("Response from MCS for removing upstream source was NOT OK.");
					}
						
			    } else {
					throw new Exception("Response from MCS had unexpected number of upstream sources.");
			    }
			    
			} catch (Exception e) {
				success = false;
				logger.logMsgFail(pid, "Could not remove upstream source of " + remoteStream + ".", e );
			}
			
			// Send an event to the Dashboard that replication has been severed.
			logger.logMessage( "NoServicePID", MessageLogger.MSG_LEVEL_INFO, MessageLogger.CD_REPL_SEVERED, "REPLICATION SEVERED." );
			
			// Send a COMPLETE message back to the dashboard.
			if (success) {
				logger.logMessage( pid, MessageLogger.MSG_LEVEL_INFO, MessageLogger.CD_MSG_TEXT, "RESET COMPLETE<br>with no warnings." );
				logger.admin("DEMO RESET COMPLETED SUCCESSFULLY.");
			} else {
				logger.logMessage( pid, MessageLogger.MSG_LEVEL_ERROR, MessageLogger.CD_MSG_TEXT, "RESET COMPLETE<br>with errors." );
			}
			
			// Triggers a dashboard event.
			logger.logMessage( pid, MessageLogger.MSG_LEVEL_INFO, MessageLogger.CD_DEMO_RESET_COMPLETE, "RESET COMPLETE" );

			// Indicate to the dashboard that the record's been processed.
	        logger.logMsgFinish(pid);

		} catch (Exception e) {
			logger.logMsgFail(pid, "FAILED!<br>UNEXPECTED ERROR", e);
			logger.logMessage( pid, MessageLogger.MSG_LEVEL_INFO, MessageLogger.CD_DEMO_RESET_COMPLETE, "RESET COMPLETE" );
		}
    	
	}
	
	
    public static void main(String[] args) throws Exception {
    	
    		DemoHelper demoHelper = new DemoHelper();
	    	demoHelper.resetDemo();
    	
    }

}
