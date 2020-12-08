const HQ_GRID_WIDTH  = 5;		// HQ
const HQ_GRID_HEIGHT = 3;
const HQ_DATAFEED_MAXCOL = 1;	// This is the number of grid columns reserved for data feed tiles.
const EDGE_GRID_WIDTH  = 5;		// EDGE
const EDGE_GRID_HEIGHT = 1;
const EDGE_DATAFEED_MAXCOL = 0;	// The EDGE has not data feeds.

var gridPIDs = {};
var this_grid_height;
var this_grid_width;
var this_datafeed_maxcol;
var free_tile_delay_normal;
var free_tile_delay_image;
var debugVal = 0;
var eb;

const TILE_STATUS_EMPTY     = 1;
const TILE_STATUS_INUSE     = 2;
const TILE_STATUS_IMAGE     = 3;
const TILE_STATUS_FINISHING = 4;

// These are the codes sent by the app.
// The dashboard interprets these message types and handles them accordingly.
// The demo can be modified/expanded by altering the handlers or expanding the code set. 
const CD_SERVICE_START 				= 'SERVICE_START';
const CD_SERVICE_STOP  				= 'SERVICE_STOP';
const CD_SERVICE_FAIL  				= 'SERVICE_FAIL';
const CD_EVENT_PROCESSING_START  	= 'EVENT_START';
const CD_EVENT_PROCESSING_FINISH 	= 'EVENT_FINISH';
const CD_MSG_TEXT					= "TEXT";
const CD_MSG_IMAGE               	= "IMAGE";

// EDGE-specific codes
const CD_HEARTBEAT		  			= 'HEARTBEAT';
const CD_REPL_ESTABLISHED  			= 'REPL_ESTABLISHED';
const CD_REPL_SEVERED	  			= 'REPL_SEVERED';
const CD_ASSET_AVAIL					= "ASSET_AVAIL";
const CD_DEMO_RESET_COMPLETE 		= 'DEMO_RESET_COMPLETE';
const CD_REPL_PAUSE_COMPLETE			= "REPL_PAUSE_COMPLETE";
const CD_REPL_RESUME_COMPLETE	 	= "REPL_RESUME_COMPLETE";

// EDGE-specific.
var terminatorTable;
var dotCount = 0;
var connectionStatus;
var lastHeartbeat;


// ********************************************************************
// Initialization functions.
// The dashboard web page calls the dashboardInit function at startup.
// There are two variants, one for HQ and one for EDGE.
// ********************************************************************

function dashboardInitCommon() {

	// Register handler to receive updates from cluster.
	
	// The Vert.x port is hardcoded below, but at least the hostname is not.
	// And it also works if the dashboard is accessed via IP address rather than hostname.
	// Mobile clients will probably connect via IP, since they don't have an /etc/hosts file.
	// Otherwise, a DNS would have to be setup on the Edge cluster, and the router would have
	// to be configured to provide that DNS to DHCP clients.
	
	var eventbusURL = "http://" + location.hostname + ":8081/eventbus/";
	console.log("Connecting to Vert.x at " + eventbusURL);
	eb = new vertx.EventBus(eventbusURL);
    
    eb.onopen = function() {
	    	eb.registerHandler("dashboard", function(message) {
	    		console.log( 'Received an update from vertx.');
	        	var jsonMsg = JSON.parse(message);
	        	updateDash(jsonMsg);
	    	} );
    };

	// Dynamically uilds the tile display grid
    initDisplayGrid();
    
    // Speed controls. 
    $('.slider').slider();

	// Vert.x needs this time to settle.
	setTimeout( function(){ synchWithServer(); }, 5000 );
	

};

function synchWithServer() {

	console.log("Requesting list of started services...");
	eb.send('dashboard-inbound', {directive: 'sendRunningServicesList'});
	
	console.log( "Synching up Delay speeds" );
	// These are client-side only, synchs up javascript with HTML.
	changeSpeed( 'delayCompletionText', document.getElementById("delayCompletionText").value * 1000 );
	changeSpeed( 'delayCompletionImages', document.getElementById("delayCompletionImages").value * 1000 );
	// These are server-side, synchs up client-side with server-side.
	// Just remember that on the back-end these are global settings,
	// So if you connect client 2, these defaults will override any adjustments made by client 1.
	changeSpeed( 'delayProcessing', document.getElementById("delayProcessing").value );
	changeSpeed( 'delayDataFeed', document.getElementById("delayDataFeed").value * 1000 );
	
}


// HQ-specific function to initialize the page.
function dashboardInitHQ() {

	this_grid_width  = HQ_GRID_WIDTH;
	this_grid_height = HQ_GRID_HEIGHT;
	this_datafeed_maxcol = HQ_DATAFEED_MAXCOL;

	dashboardInitCommon();
		
};


// EDGE-specific function to initialize the page.
function dashboardInitEDGE() {
	
	this_grid_width  = EDGE_GRID_WIDTH;
	this_grid_height = EDGE_GRID_HEIGHT;
	this_datafeed_maxcol = EDGE_DATAFEED_MAXCOL;

	dashboardInitCommon();
	
	// The "Terminator Table" is the green and black window
	// That displays the arrival of new assets.
	terminatorTable = document.getElementById("terminatorTable");

	// Monitor the connection to the Edge Server.
	// The demo is ruined if you enabled data replication,  
	// only to discover that the connection to the server had been dropped.
	lastHeartbeat = 0;
	connectionStatus = document.getElementById("connectionStatus");
    
};


// ***************************** 
// EDGE-specific functions
// ***************************** 


// Adds dots to the receiving data indicator ...
function checkConnectionStatus() {

	var thisTime = new Date().getTime();
	if ( thisTime - lastHeartbeat > 15000 ) {   // implements a 15 second timeout.  
      	connectionStatus.innerHTML = "<font color='red'>Not connected to local EDGE server!</font>";	
	} else {
	
	    dotCount += 1;
	    if (dotCount == 25) {
	      	dotCount = 0;
	      	connectionStatus.innerHTML = connectionStatus.getAttribute("data-display-text");	
	    } else {  
			connectionStatus.innerHTML += " .";
		}
	}
	
}


// Updates the HQ Replication Status window
function updateReplStatus(replStatus) {

	var replicationBanner = document.getElementById("replicationBanner");
	var pauseReplicationButton = document.getElementById("buttonPauseRep");
	var replicationStatus = document.getElementById("replicationStatus");
	replicationStatus.innerHTML = replStatus;

	switch (replStatus) {
	
		case "NOT CONNECTED":
			replicationBanner.className="panel panel-danger";
			pauseReplicationButton.disabled = true;
			break;
			
		case "ESTABLISHED":
			replicationBanner.className="panel panel-success";
			pauseReplicationButton.disabled = false;
			pauseReplicationButton.innerHTML = "Pause Replication";
			pauseReplicationButton.setAttribute("data-state", "1");
			break;
			
		case "PAUSED":
			replicationBanner.className="panel panel-warning";
			pauseReplicationButton.disabled = false;
			pauseReplicationButton.innerHTML = "Resume Replication";
			pauseReplicationButton.setAttribute("data-state", "2");
			break;
			
	}

}

// Pauses and Resumes the Streams replication.
function toggleReplication(button) {
	
	if (button.getAttribute("data-state") == "1") {

		// Pause Replication
		console.log("Pausing Stream Replication...");
		eb.send('dashboard-inbound', {directive: 'pauseReplication'});
		button.innerHTML = "Pausing Replication...";
		button.disabled = true;

	} else {

		// Resume Replication
		console.log("Resuming Stream Replication ...");
		eb.send('dashboard-inbound', {directive: 'resumeReplication'});
		button.innerHTML = "Resuming Replication...";
		button.disabled = true;
		
	}
	
}


// Handles "Request Asset" clicks.
function requestAsset(thisAssetID) {

	console.log("Requesting asset " + thisAssetID);
	eb.send('dashboard-inbound', {directive: 'requestAsset', assetID: thisAssetID});
	updateMetric("assetsRequested");
	
	// TO DO: Change 'Retrieve' link to 'Requested' (no link)
		
}


function resetDemo(button) {
	
	// Reset Demo
	console.log("Resetting Demo...");
	eb.send('dashboard-inbound', {directive: 'resetDemo'});
	button.innerHTML = "Resetting...";
	button.disabled = true;

}


// **********************************************************
// HQ-specific functions
// Deploys the image classifier service (versions 1 and 2)
// **********************************************************

function deployV1Service(button) {
	
	if (button.getAttribute("data-state") == "1") {

		// Deploy service
		console.log("Deploying v1 service...");
		eb.send('dashboard-inbound', {directive: 'deployV1Service'});
		button.innerHTML = "Deploying Service...";
		button.disabled = true;

	} else {

		// Decommission service
		console.log("Decommissioning v1 service...");
		eb.send('dashboard-inbound', {directive: 'decommissionV1Service'});
		button.innerHTML = "Decommissioning Service...";
		button.disabled = true;
		
	}
	
}

function deployV2Service(button) {
	
	if (button.getAttribute("data-state") == "1") {

		// Deploy service
		console.log("Deploying v2 service...");
		eb.send('dashboard-inbound', {directive: 'deployV2Service'});
		button.innerHTML = "Deploying Service...";
		button.disabled = true;

	} else {

		// Decommission service
		console.log("Decommissioning v2 service...");
		eb.send('dashboard-inbound', {directive: 'decommissionV2Service'});
		button.innerHTML = "Decommissioning Service...";
		button.disabled = true;
		
	}
	
}


// ***************************************************************
// Common functions (used by both HQ and EDGE editions)
// Tile functions, message processing, metrics, speed dials, etc.
// ***************************************************************

// Every tile has an ID, which is used throughout.
function createTileID(x, y) {
	return "g:" + x + ":" + y;
}

// Dynamically build the Grid HTML.
// In the HQ edition, this grid is used to display output from all the microservices.
// In the EDGE edition, it's been re-purposed (awkwardly) to display images received through mirroring.
function initDisplayGrid() {
	
	var gridHTML = "";
	var cellID;
	for (y=0; y<this_grid_height; y++) {
		gridHTML += "<tr class='grid-row'>";
		for (x=0; x<this_grid_width; x++) { 
			cellID = createTileID(x,y);
			gridHTML += "<td id='" + cellID + "' data-tileStatus='" + TILE_STATUS_EMPTY + "' style='overflow: hidden; text-overflow: ellipsis;'></td>";
		}
		gridHTML += "</tr>";
	}
	
	newDocElement = document.createElement("tbody");
	newDocElement.innerHTML = gridHTML;
	document.getElementById("grid").appendChild(newDocElement);
	
}

// Converts a message type (INFO/WARN/FATAL) to a Bootstrap panel class.
function getPanelType(msgLevel) {
	switch (msgLevel) {
		case "INFO":
			return "panel-success";  // green
		case "ERROR": 
			return "panel-warning";  // yellow
		case "FATAL":
			return "panel-danger";   // red
		default:
			return "panel-default";  // gray
	}
}


// Called by vert.x whenever there's an update to display on the dashboard.
function updateDash(json) {
	
	var appCode        = json.appCode;
	var appDisplayName = json.appDisplayName;
	var pid            = json.pid;
	var ppid           = json.ppid;
	var threadID       = json.threadID;
	var msgLevel       = json.msgLevel;
	var msgCode        = json.msgCode;
	var msgText        = json.msgText;
    
	// Translate some data fields into display attributes.
	var panelType       = getPanelType(msgLevel);
	var panelHeaderText = appDisplayName;
	if (threadID != null) {
		panelHeaderText += " (" + threadID + ")";
	}

	//console.log("appCode: "  + appCode);
	//console.log("appDisplayName: "  + appDisplayName);
	//console.log("pid: "      + pid);
	//console.log("ppid: "     + ppid);
	//console.log("msgLevel: " + msgLevel);
	//console.log("msgCode: "  + msgCode);
	//console.log("msgText: "  + msgText);
    

	// EDGE only.
	// Processes a heartbeat from the server, warns against lost connections.
	if (msgCode == CD_HEARTBEAT) {
	
		// Initial contact.
		if (lastHeartbeat == 0) {
      		connectionStatus.innerHTML = "Connected to MapR EDGE, waiting for live data .";	
	      	connectionStatus.setAttribute("data-display-text", connectionStatus.innerHTML);
      		setInterval( function(){ checkConnectionStatus(); }, 1000 );
		}
	
		lastHeartbeat = new Date().getTime();

	} 

    
	// EDGE only.
	// Handle demo reset operations.
	// CD_DEMO_RESET_COMPLETE indicates that back-end operations have already been performed.
	// Here, we perform GUI-related reset tasks.
	if (msgCode == CD_DEMO_RESET_COMPLETE) {
    
		// Clear the Terminator Window.
		terminatorTable.innerHTML = "";
	    
		// Reset the replication status banner to Red and NOT CONNECTED.
		updateReplStatus("NOT CONNECTED");

    		// Re-enable the Demo Reset button.
		button = document.getElementById("buttonDemoReset");
		button.innerHTML = button.getAttribute("data-state1-text");
		button.disabled = false;
    
    } 
    

	// ****************************************    
    // Replication events.
    // EDGE only.
    // Pause / Resume Replication, as well as completion of those events.
    // Streams only - Does not affect Mirroring.
	// ****************************************    
    
    if (msgCode == CD_REPL_ESTABLISHED) {

		updateReplStatus("ESTABLISHED");

    } 
    
    if (msgCode == CD_REPL_PAUSE_COMPLETE) {
    
		updateReplStatus("PAUSED");
	
    }
    
    if (msgCode == CD_REPL_RESUME_COMPLETE) {
    
		updateReplStatus("ESTABLISHED");
		
    }

	// ***********************************************
	
	
	// Handle announcements of new assets.
	// These get displayed in the "terminator window".
    if ( msgCode == CD_ASSET_AVAIL ) {
	
		var newRow = terminatorTable.insertRow(0);
		var newCell = newRow.insertCell(0);
        newRow.className = "panel-data-row";
        
        // This is a hack to extract the asset ID from the display text.
        // Really, the dashboard app should be able to handle additional data fields.
        var endIdx = msgText.indexOf(" available");
        var assetID = msgText.substr(10, endIdx - 10);
        
        newCell.innerHTML = "<a href='#' onClick='requestAsset(\"" + assetID + "\")'; return false;'>&lt;Request&gt;</a> " + msgText;
        
        updateMetric("assetsAdvertised");

    } 
    
    
    // Service-related operations.
    // Start / Stop / Fail
    if (msgCode == CD_SERVICE_START || msgCode == CD_SERVICE_STOP || msgCode == CD_SERVICE_FAIL) {
    	
		// Service status information is displayed in the DATA FEEDS AND SERVICES window.
		var tdElementID = appCode + "-status-td";
		var tdElement = document.getElementById(tdElementID);
		var trElementID = appCode + "-status-tr";
		var trElement = document.getElementById(trElementID);
		
		// Refers to the Deploy New Service buttons, i.e. the Image Classifiers (v1 & 2).
		// If we are starting or stopping these particular services, then we also have to modify their respective Deploy buttons.
		var button;
		var buttonNeedsChanging = false;
		if (appCode == "IMG_CLASS_1") {
			button = document.getElementById("buttonDeployV1");
			buttonNeedsChanging = true;
		} else if (appCode == "IMG_CLASS_2") {
			button = document.getElementById("buttonDeployV2");
			buttonNeedsChanging = true;
		}
		
		switch (msgCode) {
		
			case CD_SERVICE_START:
			
				if ( trElement == null ) {
					$("table.feedsTable").append("<tr id='" + trElementID + "'><td>" + appDisplayName + "</td><td id='" + tdElementID +"'>Started</td></tr>");
				} else {
					// tolerates the case where a previous attempt to start the service failed.
					tdElement.innerHTML = "Started";
					trElement.className = "";        // Set (or reset) row color to normal.
				}
	            	
	            	if (buttonNeedsChanging) {
	    				// Switches the button to the "decommission" state
		    			button.innerHTML = button.getAttribute("data-state2-text");
		    			button.setAttribute("data-state", "2");
		    			button.disabled = false;
	    			}
	            	
	            	break;
	            	
	        case CD_SERVICE_STOP:
	        
				trElement.remove();
								
				if (buttonNeedsChanging) {
					// Switches the button to the "deploy" state
					button.innerHTML = button.getAttribute("data-state1-text");
					button.setAttribute("data-state", "1");
					button.disabled = false;
				}
				
				break;
				
			case CD_SERVICE_FAIL:
		        	tdElement.innerHTML = msgText;
		        	trElement.className = "danger";      // Set row color to red.
			
		}
		
	}
	
	
	// A new message is being processed by some microservice.
    	// Find a free tile, create a display container, and display the initial content.
	// At the EDGE, this is used to images that arrive via mirror operations.
    if (msgCode == CD_EVENT_PROCESSING_START) {
    
		console.log("New event - fetching an empty tile.");
		var tile = getFreeTile(ppid);
	    	
	    	// Maintain system counts.
	    	// As these are maintained by the client, they may differ across multiple clients,
	    	// depending on what messages each of those clients has processed.
		if (ppid == null) {
		    	// if the parent process ID is null, then it's a new image.
			updateMetric("imagesProcessed");
		} else {
		
		    	// Otherwise, a microservice is processing a message that it's received. 
			updateMetric("messagesProcessed");
			
			// On the HQ dashboard, asset requests are _received_ from the Edge
			// and processed by the Asset Request Service. That's this.
			if (appCode == "ASSET_REQUEST") {
				updateMetric("assetsRequested");
			}
			
		}
	    	
		if (tile == null) {
			// If no tiles are available, the whole event is ignored (i.e. not displayed at all, ever).
			console.log("No tiles available. Sorry! Better luck next time.");
		} else {
	
			console.log("Using tile " + tile.id);
			
			// Map this PID to the selected tile, for future updates.
			gridPIDs[pid] = tile.id;
			
			// Post the update.
			var tableID = "TAB" + tile.id;       // create an identifier to use for the <TABLE> data container.
			var panelID = "PAN" + tile.id;       // create an identifier to use for the <Panel> data container.
				
			// Mark the tile as In Use.
			setTileStatus(tile, TILE_STATUS_INUSE);
			
			// Create a panel inside the tile to hold all status updates.
			
			var panelBodyHTML =  '<div class="panel-body custom-fixed-panel">'
				+ '<table id="' + tableID + '" class="table-responsive">'
				+ '<tr><td>' + msgText + '</td></tr>'
				+ '</table></div>';
				
			var tileHTML = '<div id="' + panelID + '" class="panel grid-panel ' + panelType + '">' 
			    + '<div class="panel-heading">' + panelHeaderText + '</div>'
			    + panelBodyHTML 
			    + '</div>';
			    
			tile.innerHTML = tileHTML;
            
        }
        
    }
    
    
    // Microservice processing updates.
    // These are updates to already placed tiles.
    if ( msgCode == CD_MSG_TEXT || msgCode == CD_MSG_IMAGE ) {
    	
        // Determine the tile to update.
        var tile = getTile(pid);
        
        // tile==null means we do nothing - there wasn't room for it on the grid.
        if (tile != null) {
        	
			// Post the update.
			 
            var tableID = "TAB" + tile.id;       // create an identifier to use for the <TABLE> data container.
            
			// Deal with state changes (i.e. warnings and errors).
			updateTileColor(tile, panelType);
            	
			// Insert a row at the top of the existing display table to hold the new info.
            var table = document.getElementById(tableID);
			var newRow = table.insertRow(0);
			var newCell = newRow.insertCell(0);

         	if (msgCode == "IMAGE") {
         		
         		newRow.className = "panel-image-row";
         		
         		console.log("Displaying the downloaded image.");
         		// "/images/" is configured in the web server configuration.
         		imgURL = "/images/" + msgText;
         		newCell.innerHTML = "<img src='" + imgURL + "' class='img-thumbnail'>";
        	    
         		setTileStatus(tile, TILE_STATUS_IMAGE);
        	    
            } else {
         		newRow.className = "panel-data-row";
         		newCell.innerHTML = msgText;
            }
             	
        }
        
    };
    
    
	// Indicates a given stage of the pipeline has finished processing the message.
	// Close out the tile.
    if (msgCode == CD_EVENT_PROCESSING_FINISH) {
    
        // Determine the tile to update.
        var tile = getTile(pid);
    
        // tile==null means we do nothing - there wasn't room for it on the grid.
        if (tile != null) {
        	
			var timeDelay = free_tile_delay_normal;
			if (getTileStatus(tile) == TILE_STATUS_IMAGE ) {
				// Let the images stay up a little longer (more visually interesting).
				timeDelay = free_tile_delay_image;
			}
			
			// Update the color to gray.
			updateTileColor(tile, "panel-default");
			
			// After some time, free up the tile for future use.
			setTimeout( function(){ releaseTile(tile, pid); }, timeDelay );
	            	
		}
    
    }
    
}


// Returns an available tile from the Grid.
// Current implementation: 
// If there's no ppid, then it's a data feed and it belongs in column 0.
// Otherwise, it's a processing stage and it belongs in the first available tile (top to bottom, left to right).
// Return null if no tiles are available.
function getFreeTile(ppid) {

const HQ_DATAFEED_MAXCOL = 1;	// This is the number of grid columns reserved for data feed tiles.
const EDGE_DATAFEED_MAXCOL = 0;	// The EDGE has not data feeds.

	var minX;
	var maxX;
	var x = 0;
	var y = 0;
	var found = 0;
	
	if (ppid == null) {				<!-- data feeds are displayed in leftmost columns -->
		minX = 0;
		maxX = this_datafeed_maxcol;
	} else {							<!-- microservices are displayed in rightmost columns -->
		minX = this_datafeed_maxcol;
		maxX = this_grid_width;
	}
	
	x = minX;
	while (!found && x < maxX) {
		y = getTopFreeTile(x);
		if (y != -1) {
			found = 1;
		} else {
			x++;
		}
	}
		
	if (!found) {
		return null;
	} else {
		var tileID = createTileID(x, y);
		return document.getElementById(tileID);
	}

}


// Returns the first available tile in a given column.
// Returns -1 if no tiles are available.
function getTopFreeTile(col) {
	var y = 0;
	var found = 0;
	var tileID;
	while (!found && y < this_grid_height) {
		tileID = createTileID(col,y);
		var tile = document.getElementById( tileID ); 
		if (getTileStatus(tile) == TILE_STATUS_EMPTY ) {
			console.log("getTopFreeTile: found " + tileID + " to be free.");
			found = 1;
		} else {
			//console.log("getTopFreeTile: found " + tileID + " to NOT be free.");
			y = y + 1;
		}
	}
	if (!found) {
		y = -1;
	}
	return y;
}


// Retrieves the tile currently being used to monitor the given pid.
function getTile(pid) {
	
	var tile;
	
	if( typeof(gridPIDs[pid]) === "undefined" ){
		console.log("Tile not found for specified PID, so it's not being tracked (grid must have been full).");
	} else {
		
		tileID = gridPIDs[pid];
		console.log("PID (" + pid + ") is being monitored in tile " + tileID + ".");
		tile = document.getElementById(tileID);
		
	}
	return tile;
	
}


//Marks a tile as empty and available for future use.
function releaseTile(tile, pid) {
	
	// Remove any content from the display.
	tile.innerHTML = "";

	// Remove the pid-Tile mapping.
	delete gridPIDs[pid];
	
	// After some time, let another PID use the tile.
	// A 5-second delay is introduced to give viewers time to notice.
	setTimeout( function(){ setTileStatus(tile, TILE_STATUS_EMPTY); }, 5000 );
	
}


//The tile colors are based on Bootstrap panels.
function updateTileColor(tile, panelType) {
    var panelID = "PAN" + tile.id;  // create an identifier to use for the <Panel> data container.
	// Deal with status changes.
	document.getElementById(panelID).className = "panel grid-panel " + panelType;
}


function setTileStatus(tile, status) {
	console.log("Setting status of tile " + tile.id + " to " + status);
    tile.setAttribute("data-tileStatus", status);
}


function getTileStatus(tile) {
	return tile.getAttribute("data-tileStatus");
}


// Update system metrics
function updateMetric(metricElementID) {
	var docElement = document.getElementById(metricElementID);
	if (docElement) {   // this check is important, because edge/hq versions have different metrics.
		var metricVal = parseInt( docElement.innerHTML ) + 1;
		docElement.innerHTML= metricVal;
	}
}


// Controls the speed at which the data processing operates.
function changeSpeed(speedDial, value) {

	if ( (speedDial == "delayProcessing") || (speedDial == "delayDataFeed") ) {
		
		// These system delays are at the backend, so send a notification via Vert.x.
		var strValue = String(value);
		eb.send('dashboard-inbound', {directive: speedDial, newSpeed: strValue});
		
	// These delays are client-side via javascript.
	} else if (speedDial == "delayCompletionText") { 
		free_tile_delay_normal = value;
	} else if (speedDial == "delayCompletionImages") {
		free_tile_delay_image = value;
	}
	
}

// Sets a banner logo to indicate the deployment environment.
function setBanner(deploymentEnv) {

	switch (deploymentEnv) {
		
		case "none":
			document.getElementById("deployment-env").src = "";
			break;
			
		case "aws":
			document.getElementById("deployment-env").src = "./images/aws.png";
			break;
			
		case "azure":
			document.getElementById("deployment-env").src = "./images/azure.png";
			break;
			
	}

}


function debugButtonClick(value) {

	console.log( "Debug button clicked - executing test code." );
	
};


