/**
 * 
 */
package com.tarun.dynamodb;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;

import com.amazonaws.util.json.JSONException;
import com.amazonaws.util.json.JSONObject;

/**
 * The Class DynamoDBMain.
 *
 * @author taruntyagi
 */
public class DynamoDBMain {
	public static void main(String[] args) {
		String configFile = "config.json";
		URL path = DynamoDBMain.class.getClassLoader().getResource(configFile);
		try {
			JSONObject config = new JSONObject(new String(Files.readAllBytes(Paths.get(path.toURI()))));
			DynamoDBWorker ddbWorker = new DynamoDBWorker(config);
			ddbWorker.scan();
		} catch (JSONException ex) {
			System.out.println(configFile + " is not a valid JSON config file(" + ex.getMessage() + ").");
		} catch (IOException ex) {
			System.out.println(configFile + " can not be read(" + ex.getMessage() + ").");
		} catch (URISyntaxException ex) {
			System.out.println(configFile + " URI syntax exception (" + ex.getMessage() + ").");
		}

	}
}
