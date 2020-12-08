import pipeline.util.*;
import pipeline.util.hq.*;
import pipeline.util.edge.*;

import pipeline.feeds.hq.*;
import pipeline.microservices.hq.*;
import pipeline.microservices.edge.*;

import java.io.File;


/*  
 *  Main class to run the Microservices Pipeline Demo Application.
 *  This application allows multiple microservices to be assembled into a data processing pipeline.
 *  New microservices can be easily deployed and upgraded.
 *  
 *  The first instantiation of this app should be run as an "hq" edition, i.e.:
 *  		MicroservicesDashboard hq
 *  
 *  The hq edition can be run standalone to demonstrate microservice architectures on MapR.
 *  Additionally, a second instantiation of the app can be run on a separate MapR cluster, 
 *  and two-way data transfer can be demonstrated between the two.  Run that version
 *  using the "edge" option:
 *  		MicroservicesDashboard edge
 *  
 */
public class MicroservicesDashboard {

	public static void main(String[] args) {
		
		// Note: all the setup steps performed here could also
		//       be shifted to the bash startup script.
		
		String usage = "Usage: MicroservicesDashboard [hq|edge]";
		
		if (args.length != 1) {
			System.err.println(usage);
            System.exit(-1);
        }
		
		String edition = args[0];
		if ( !edition.equals("hq") && !edition.equals("edge") ) {
			System.err.println(usage);
            System.exit(-1);
		}
		
        System.out.println("Starting MicroserviceDashboard, " + edition + " edition...");

		AppConfig.setEdition(edition);
		String appURL = AppConfig.getConfigValue(edition + "-dashboard-url");
		
		System.out.println("Checking NFS status...");
		String clusterName = AppConfig.getConfigValue(edition + "-cluster-name");
		try {
			File testFile = new File ("/mapr/" + clusterName + "/tmp/nfsTestFile.test");
			testFile.createNewFile();
			testFile.delete();
		} catch (Exception e) {
			System.out.println("NFS looks to be down. Start NFS and relaunch demo. Exiting.");
			System.exit(-1);
		}
		
		
	    	// These objects are rebuilt each time the demo is run,
		// allowing the demo to be restarted in a clean state.
		System.out.println("Creating required system objects...");
	    	
		// Start with an empty (local) data stream.
		String pipelineStream = AppConfig.getConfigValue("streamLocal-fullPath");
		try {
			StreamsHelper.deleteStream(pipelineStream);
			StreamsHelper.createStream(pipelineStream);
		} catch (Exception e) {
			System.out.println("Could not create pipeline stream (" + pipelineStream + ").");
			e.printStackTrace();
			System.exit(1);
		}
		
	    	// HQ Specific
	    	if ( edition.equals("hq") ) {
	    		
	    		// Start with an empty images table.
	    		String imagesTable = AppConfig.getConfigValue("hq-tableImages-fullPath");
	    		try {
	    			DBHelper.deleteTable(imagesTable);
	    			DBHelper.createTable(imagesTable);
	    		} catch (Exception e) {
	    			System.out.println("Could not create images table (" + imagesTable + ").");
	    			e.printStackTrace();
	    			System.exit(1);
	    		}
	    		
	    		// Start with an empty outbound edge notification stream.
	    		String replicatedStream = AppConfig.getConfigValue("streamReplicated-fullPath");
	    		try {
	    			StreamsHelper.deleteStream(replicatedStream);
	    			StreamsHelper.createStream(replicatedStream);
	    		} catch (Exception e) {
	    			System.out.println("Could not create replicated pipeline stream (" + replicatedStream + ").");
	    			e.printStackTrace();
	    			System.exit(1);
	    		}

	    		// Start with an empty outbound cloud notification stream.
	    		String cloudStream = AppConfig.getConfigValue("hq-streamCloud-fullPath");
	    		try {
	    			StreamsHelper.deleteStream(cloudStream);
	    			StreamsHelper.createStream(cloudStream);
	    		} catch (Exception e) {
	    			System.out.println("Could not create cloud stream (" + cloudStream + ").");
	    			e.printStackTrace();
	    			System.exit(1);
	    		}

	    	}
	    	
	    	// Edge Specific
	    	if ( edition.equals("edge") ) {
	    		
	    		// Start without any replicated stream.
	    		// As part of the demo, data replication is configured through MCS.
	    		// This only ensures there aren't any leftover artifacts from previous demo runs.
	    		String replicatedStream = AppConfig.getConfigValue("streamReplicated-fullPath");
	    		try {
	    			StreamsHelper.deleteStream(replicatedStream);
	    		} catch (Exception e) {
	    			System.out.println("Could not delete replicated pipeline stream (" + replicatedStream + ").");
	    			System.exit(1);
	    		}

	    		// Remove the mirror volume. 
			try {
				
				MCSRestClient mcs = new MCSRestClient();
				String responseString;
				System.out.println("Removing mirror volume (files-missionX)...");
				responseString = mcs.callAPI("/volume/remove?name=files-missionX&force=1");
				
				if ( mcs.getReturnStatus(responseString).equals("OK") ) {
					System.out.println("Mmirror volume (files-missionX) removed successfully.");
				} else {
					System.out.println( "ERROR: Could not deleted volume (files-missionX). It may not have existed." );
					System.out.print(responseString);
				}
			} catch (Exception e) {
				System.out.println("ERROR: Unexpected error trying to delete the files-missionX mirror volume.");
				e.printStackTrace();
				System.exit(1);
			}

	    	}

	    	// Launch vert.x, which provides bi-directional communication between the backend and the web browser.
		System.out.println("Launching Vert.x on a separate thread...");
		VertxWebServer vws = new VertxWebServer();
		vws.start();
		
		/* This was here so the dashboard would catch the service startup info coming from Vert.x.
		 * That's been redone. The dashboard now requests a list of started services.
		// Open a web browser at this point, so that subsequent microservices are displayed in the Dashboard.
		System.out.println("Please launch your web browser and connect to the Dashboard.");
		System.out.println("You will see an acknowledgement pop up in this window,");
		System.out.println("and you will then be prompted to continue.");
	    Scanner in = new Scanner(System.in);
		String line = in.nextLine();
		in.close();
		System.out.println("Continuing....");
		*/
		
		// Listen for inbound events from the dashboard.
		System.out.println("Launching dashboard listener...");
		DashboardListener dl = new DashboardListener();
		dl.start();
		System.out.println("Dashboard Listener started...");

		
		System.out.println("**********************************************************************************");
		System.out.println("* Dashboard can now be accessed at " + appURL);
		System.out.println("**********************************************************************************");
		

		// Wait a bit for the user to notice.
		try {
			Thread.sleep(5000);
		} catch (Exception e) {
			// do nothing
		}
		
		if ( edition.equals("hq") ) {
			
			// Launch HQ microservices
			System.out.println("Launching HQ microservices...");

			// Launch the NASA Data Feed service.
			System.out.println("Launching NASA Data Feed...");
			NASAFeed nasaFeed = new NASAFeed();
			nasaFeed.start();
			System.out.println("NASA Data Feed started...");
			
			// Launch the Image Download Service.
			System.out.println("Launching Image Download Service...");
			ImageDownloadService ids = new ImageDownloadService();
			ids.start();
			System.out.println("Image Download Service started...");

			// Launch the Broadcast Service.
			System.out.println("Launching Asset Broadcast Service...");
			AssetBroadcastService abs = new AssetBroadcastService();
			abs.start();
			System.out.println("Asset Broadcast Service started...");

			// Launch the Asset Request Service.
			System.out.println("Launching Asset Request Service...");
			AssetRequestService ars = new AssetRequestService();
			ars.start();
			System.out.println("Asset Request Service started...");

			// Image Classifier Services (v1 and v2) are launched via the Dashboard.
			
		} else {
			
			// Launch EDGE microservices
			System.out.println("Launching EDGE microservices...");

			// Launch the Image Display service.
			System.out.println("Launching Image Display Service...");
			ImageDisplayService ids = new ImageDisplayService();
			ids.start();
			System.out.println("Image Display Service started...");

			// Launch the Audit Listener service.
			System.out.println("Launching Audit Listener...");
			AuditListenerService als = new AuditListenerService();
			als.start();
			System.out.println("Audit Listener Service started...");
			
			// Required by the DashboardListener to perform a demo reset. 
			DemoHelper demoHelper = new DemoHelper();
			demoHelper.linkAuditListenerService(als);
			dl.setDemoHelper(demoHelper);
			
		}
		
	}
	
}
