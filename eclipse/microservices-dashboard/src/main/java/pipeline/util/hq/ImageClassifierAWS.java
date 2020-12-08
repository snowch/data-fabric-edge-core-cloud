package pipeline.util.hq;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;
import com.amazonaws.services.rekognition.AmazonRekognition;
import com.amazonaws.services.rekognition.AmazonRekognitionClientBuilder;
//import com.amazonaws.auth.AWSCredentials;
//import com.amazonaws.auth.AWSStaticCredentialsProvider;
//import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.rekognition.model.AmazonRekognitionException;
import com.amazonaws.services.rekognition.model.DetectLabelsRequest;
import com.amazonaws.services.rekognition.model.DetectLabelsResult;
import com.amazonaws.services.rekognition.model.Image;
import com.amazonaws.services.rekognition.model.Label;
import com.amazonaws.util.IOUtils;

import pipeline.util.AppConfig;

public class ImageClassifierAWS {
	
	/* 
	 * Useful info:
	 * Using AWS SDK: http://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/basics.html
	 * Creating Service Clients: http://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/creating-clients.html
	 * Default Region Provider Chain: http://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/java-dg-region-selection.html 
	 * Example: https://github.com/dwarcher/amazon-rekognition-example
	 * 
	 */
	
	private AmazonRekognition rekognitionClient;
	
	public ImageClassifierAWS() {
		
		/* 
		I'm using Java system properties here, instead of the credential profiles file.
    	To use a credential profiles file:
    	Create a file named "credentials" at ~/.aws/ (C:\Users\USER_NAME.aws\ for Windows users) 
    	and save the following lines in the file:
    		[default]
    		aws_access_key_id = <your access key id>
    		aws_secret_access_key = <your secret key>
    	
    	AWSCredentials credentials;
        try {
            credentials = new ProfileCredentialsProvider("rekognitionuser").getCredentials();
        } catch (Exception e) {
            throw new AmazonClientException("Cannot load the credentials from the credential profiles file. "
                    + "Please make sure that your credentials file is at the correct "
                    + "location (/Usersuserid.aws/credentials), and is in a valid format.", e);
        }
        */
		
        rekognitionClient = AmazonRekognitionClientBuilder
          		.standard()
          		.withRegion(Regions.US_EAST_1)
        		.build();
//		.withCredentials(new AWSStaticCredentialsProvider(credentials))

        
	}

	// Verify that the required Java system properties have been set.
	public static void validateEnv() {
		
		// Set the Java system properties through the command line (-Daws.accessKeyId="<keyID>" -Daws.secretKey="keyValue")
		// or config.properties file.
		if ( (System.getProperty("aws.accessKeyId") == null) || (System.getProperty("aws.secretKey") == null) ) {
			System.setProperty( "aws.accessKeyId", AppConfig.getConfigValue("aws.accessKeyId") );
			System.setProperty( "aws.secretKey", AppConfig.getConfigValue("aws.secretKey") );
		}
		
	}
	
	
	public String classifyImage(String photo, int maxLabels) throws Exception {
		
		System.out.println("ImageClassifierAWS.classifyImage: Classifiying " + photo + "...");
		
		String returnData = "";
		ByteBuffer imageBytes;
        try (InputStream inputStream = new FileInputStream(new File(photo))) {
            imageBytes = ByteBuffer.wrap(IOUtils.toByteArray(inputStream));
        }
		
        DetectLabelsRequest request = new DetectLabelsRequest()
                .withImage(new Image().withBytes(imageBytes))
                .withMaxLabels(maxLabels)
                .withMinConfidence(77F);

        try {

            DetectLabelsResult result = rekognitionClient.detectLabels(request);
            List <Label> labels = result.getLabels();

            System.out.println("Detected labels for " + photo);
            for (Label label: labels) {
               System.out.println(label.getName() + ": " + label.getConfidence().toString());
               returnData += label.getName() + ": " + label.getConfidence().toString() + " ";
            }
            

        } catch (AmazonRekognitionException e) {
            e.printStackTrace();
        }
        
        return returnData;
        
	}
	
	
	public static void main(String[] args) throws Exception {
    	
    	String usage = "Usage: ImageClassifierAWS <image filename>";
    	
        if (args.length != 1) {
            System.err.println(usage);
            System.exit(-1);
        }
        
		String filename = args[0];
		
		validateEnv();

		ImageClassifierAWS imgClassifier = new ImageClassifierAWS();

		imgClassifier.classifyImage(filename, 10);

    }
}
