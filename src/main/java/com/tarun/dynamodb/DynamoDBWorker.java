/**
 * 
 */
package com.tarun.dynamodb;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.util.json.JSONArray;
import com.amazonaws.util.json.JSONException;
import com.amazonaws.util.json.JSONObject;

/**
 * The Class DynamoDBWorker.
 *
 * @author taruntyagi
 */
public class DynamoDBWorker {

	JSONObject config;

	AmazonDynamoDBClient client;

	Logger LOGGER = Logger.getLogger(DynamoDBWorker.class.getName());

	/**
	 * Instantiates a new dynamo DB worker.
	 *
	 * @param config the config
	 */
	public DynamoDBWorker(JSONObject config) {
		if (validateConfig(config)) {
			try {
				this.config = config;
				BasicAWSCredentials awsCreds = new BasicAWSCredentials((String) config.get("accessKeyId"),
						(String) config.get("secretAccessKey"));
				client = new AmazonDynamoDBClient(awsCreds);

				Region r = Region.getRegion(Regions.fromName((String) config.get("region")));
				client.setRegion(r);

			} catch (JSONException ex) {
				LOGGER.log(Level.SEVERE, ex.getMessage());
			}
		}
	}

	public void query() {
		
	}

	public void scan() throws JSONException {

		// DynamoDB dynamoDB = new DynamoDB(client);
		ScanRequest scanRequest = new ScanRequest().withTableName((String) config.get("tableName"));
		if (config.has("filterExpression")) {
			scanRequest.withFilterExpression((String) config.get("filterExpression"));
		}
		if (config.has("expressionAttributeValues")) {
			JSONArray evals = (JSONArray) config.get("expressionAttributeValues");
			Map<String, AttributeValue> expressionAttributeValues = new HashMap<String, AttributeValue>();
			for (int i = 0; i < evals.length(); i++) {
				JSONObject val = (JSONObject) evals.get(i);
				String type = val.getString("type");
				AttributeValue av = new AttributeValue();
				switch (type) {
				case "N":
					av.withN(val.getString("value"));
					break;
				case "S":
					av.withS(val.getString("value"));
					break;
				default:
					// handle all non numeric as String
					av.withS(val.getString("value"));
				}
				expressionAttributeValues.put(val.getString("name"), av);
			}
			scanRequest.withExpressionAttributeValues(expressionAttributeValues);
		}
		if (config.has("expressionAttributeNames")) {
			JSONArray evals = (JSONArray) config.get("expressionAttributeNames");
			Map<String, String> expressionAttributeNames = new HashMap<String, String>();
			for (int i = 0; i < evals.length(); i++) {
				JSONObject val = (JSONObject) evals.get(i);
				expressionAttributeNames.put(val.getString("name"), val.getString("value"));
			}
			scanRequest.setLimit(100000);
			scanRequest.withExpressionAttributeNames(expressionAttributeNames);
		}

		ScanResult result = client.scan(scanRequest);
		// A map to hold all unique columns that were found, and their index
		HashMap<String, Integer> columnMap = new HashMap<String, Integer>();
		// A map to hold all records
		List<HashMap<Integer, String>> recordList = new ArrayList<HashMap<Integer, String>>();
		if (result != null) {
			LOGGER.log(Level.INFO, "Results count : [" + result.getItems().size() + "]");
		}
		for (Map<String, AttributeValue> item : result.getItems()) {
			HashMap<Integer, String> record = new HashMap<Integer, String>();
			handleMap("", item, columnMap, record);
			recordList.add(record);
		}

		CSVFormat csvFileFormat = CSVFormat.DEFAULT.withRecordSeparator("\n");
		if (config.has("delimiter")) {
			csvFileFormat = csvFileFormat.withDelimiter(((String) config.get("delimiter")).charAt(0));

		}
		if (config.has("quotechar")) {
			csvFileFormat = csvFileFormat.withQuote(((String) config.get("quotechar")).charAt(0));
		}
		if (config.has("nullstring")) {
			csvFileFormat = csvFileFormat.withNullString((String) config.get("nullstring"));
		}

		FileWriter fileWriter;
		try {
			String fileName = (String) config.get("tableName") + ".csv";
			if (config.has("outputfile")) {
				fileName = (String) config.get("outputfile");
			}
			fileWriter = new FileWriter(fileName);
			CSVPrinter csvFilePrinter = new CSVPrinter(fileWriter, csvFileFormat);
			// Create a map of columns indexed by their column nr
			List<Integer> cols = new ArrayList<Integer>(columnMap.values());
			Collections.sort(cols);
			Map<Integer, String> invertedColumnMap = new HashMap<Integer, String>();
			for (String key : columnMap.keySet()) {
				invertedColumnMap.put(columnMap.get(key), key);
			}
			List<String> colList = new ArrayList<String>();
			for (Integer i : cols) {
				colList.add(invertedColumnMap.get(i));
			}
			// Write the headers
			if (!config.has("headers") || (config.has("headers") && config.getString("headers").equals("true"))) {
				csvFilePrinter.printRecord(colList);
			}
			// Write the records
			for (HashMap<Integer, String> record : recordList) {

				List<String> recList = new ArrayList<String>();
				for (Integer i : cols) {
					if (record.containsKey(i)) {
						if (record.get(i) != null & !record.get(i).isEmpty()) {
							recList.add(record.get(i));
						} else {
							recList.add(null);
						}
					} else {
						recList.add(null);
					}
				}
				csvFilePrinter.printRecord(recList);
			}
			csvFilePrinter.flush();
			csvFilePrinter.close();
		} catch (IOException ex) {
			Logger.getLogger(DynamoDBWorker.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

	/**
	 * Handle map.
	 *
	 * @param path      the path
	 * @param item      the item
	 * @param columnMap the column map
	 * @param record    the record
	 */
	private void handleMap(String path, Map<String, AttributeValue> item, HashMap<String, Integer> columnMap,
			HashMap<Integer, String> record) {
		for (String key : item.keySet()) {
			String keyName = key;
			if (!path.isEmpty()) {
				keyName = path + "." + key;
			}

			String type = "";
			if (item.get(key).getS() != null) {
				type = "S";
			}
			if (item.get(key).getN() != null) {
				type = "N";
			}
			if (item.get(key).getM() != null) {
				type = "M";
			}
			if (item.get(key).getL() != null) {
				type = "L";
			}
			if (item.get(key).getBOOL() != null) {
				type = "B";
			}
			// Add as column if it's not a list or map
			if (!type.equals("M") & !type.equalsIgnoreCase("L") & !columnMap.containsKey(keyName)) {
				// Add newly discovered column
				columnMap.put(keyName, columnMap.size());
			}

			switch (type) {
			case "S":
				record.put(columnMap.get(keyName), item.get(key).getS());
				break;
			case "N":
				record.put(columnMap.get(keyName), item.get(key).getN());
				break;
			case "B":
				record.put(columnMap.get(keyName), item.get(key).getBOOL().toString());
				break;
			case "M":
				Map<String, AttributeValue> a = (Map<String, AttributeValue>) item.get(key).getM();
				handleMap(keyName, a, columnMap, record);
				break;
			case "L":
				Map<String, AttributeValue> ml = new HashMap<String, AttributeValue>();
				List<AttributeValue> l = item.get(key).getL();
				for (AttributeValue v : l) {
					ml.put("" + ml.size(), v);
				}
				handleMap(keyName, ml, columnMap, record);
				break;
			default:
				System.out.println(keyName + " : \t" + item.get(key));
			}
		}
	}

	/**
	 * Validate config.
	 *
	 * @param config the config
	 * @return the boolean
	 */
	private Boolean validateConfig(JSONObject config) {
		Boolean valid = true;
		if (!config.has("accessKeyId")) {
			System.out.println("config parameter 'accessKeyId' is missing.");
			valid = false;
		}
		if (!config.has("secretAccessKey")) {
			System.out.println("config parameter 'secretAccessKey' is missing.");
			valid = false;
		}
		if (!config.has("region")) {
			System.out.println("config parameter 'region' is missing.");
			valid = false;
		}
		if (!config.has("tableName")) {
			System.out.println("config parameter 'tableName' is missing.");
			valid = false;
		}
		return valid;
	}

}
