package pipeline.util.edge;

import java.net.URL;
import java.io.*;
import javax.net.ssl.HttpsURLConnection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import pipeline.util.AppConfig;

public class MCSRestClient {
	
	String mcsHostname;
	String mcsBaseURL;
	String mcsUserPassEncoded;

    public MCSRestClient() throws Exception {
    	
    		this.mcsHostname = AppConfig.getConfigValue("edge-MCS-hostname");
    		this.mcsBaseURL = String.format("https://%s:8443/rest", this.mcsHostname);
    		
    		String mcsUserPass = AppConfig.getConfigValue("edge-MCS-userpass");
    		this.mcsUserPassEncoded = "Basic " + javax.xml.bind.DatatypeConverter.printBase64Binary( mcsUserPass.getBytes() );

    }
    
    
    public String callAPI(String restAPI) throws Exception {
    	
		String httpsURL = this.mcsBaseURL + restAPI;
		
		URL myUrl = new URL(httpsURL);
        HttpsURLConnection conn = (HttpsURLConnection)myUrl.openConnection();
        conn.setRequestProperty ("Authorization", this.mcsUserPassEncoded); 
        conn.setRequestMethod("POST");
        
        InputStream is = conn.getInputStream();
        InputStreamReader isr = new InputStreamReader(is);
        BufferedReader br = new BufferedReader(isr);
        StringBuilder response = new StringBuilder();

        String inputLine;
        while ((inputLine = br.readLine()) != null) {
        		response.append(inputLine+"\n");
        }

        br.close();
        
        return response.toString();
        
    }

    // Parses the JSON return string and extracts the return status code.
    public String getReturnStatus(String responseString) throws Exception {
    	
    		String returnStatus;
    		
    		try {
    	        ObjectMapper objectMapper = new ObjectMapper();
    	        JsonNode rootNode = objectMapper.readTree( responseString );
    	        returnStatus = rootNode.get("status").textValue();
    		} catch (Exception e) {
    			returnStatus = "UNKNOWN";
    		}

    		return returnStatus;
    	
    }

    
    public static void main(String[] args) throws Exception {
		
		String testRestAPI = "/license/list";
		
		MCSRestClient restClient = new MCSRestClient();
		String responseString = restClient.callAPI(testRestAPI);
		
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode = objectMapper.readTree( responseString );
        
        System.out.println("*** REST API call returned following JSON:");
        System.out.println( rootNode.toString() );
        
        String returnStatus = rootNode.get("status").textValue();
        System.out.println("REST API Call return status code:" + returnStatus);

        System.out.println();
		
    }

}
