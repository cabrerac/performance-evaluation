

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.json.JSONArray;
import org.json.JSONObject;

import com.opencsv.CSVWriter;

public class PerformanceEvaluation {
	
	private static String simrunnerFile = "";
	private static String configFile = "";
	private static String parametersFile = "";
	private static String pathToActions = "";
	private static String pathToCoordinates = "";
	private static String pathToMatrices = "";
	private static String pathToResults = "";
	private static int printErrors = 0;
	private static JSONArray performance_results = new JSONArray();

	public static void main(String[] args) {
		//String[] approaches = {"aco","bestfit", "closestfit", "ilp", "maxfit", "multiopt", "random", "sa"};
		String[] approaches = {"bestfit"};
		int[] scenarios = {2}; // the scenarios like in the maaco paper (app execution time (mu) vs requests arrival rates) 
		int[] servers = {100}; // number of servers
		int[] users = {200}; // number of users
		int rounds = 1; // rounds for repetition of experiments (I don't think we need that)
		String base = "./";
		int read = 0; // this flag helps to save the state of the experiments so we do not repeat if the execution is interrupted (I don't think we need that so you can leave experiments running)
		int iterations = 0; // parameters for the aco approach (we can fix them?)
		int ants = 0; // parameters for the aco approach (we can fix them?)
		
		simrunnerFile = base + "simrunner-placement.jar";
		configFile = base + "config/mec/mec_default.xml";
		parametersFile = base + "config/mec/mec_eval_parameters.xml";
		pathToActions = base + "config/mec/actions/actions.dat";
		pathToCoordinates = base + "config/mec/links_coordinates.json";
		pathToMatrices = base + "config/mec/trainning/";
		pathToResults = base + "results/service-placement/";
		printErrors = 0; // this flag prints the errors from the simulation if they exist
		
		for(int j = 0; j < scenarios.length; j++) {
			int scenario = scenarios[j];
			for(int x = 0; x < servers.length; x++) {
				int server = servers[x];
				for(int u = 0; u < users.length; u++) {
					int user = users[u];
					for(int i = 0; i < approaches.length; i++) {
						String approach = approaches[i];
						String fileNameStatus = base + "simonstrator-simrunner/config/mec/initial/initial_"+ approach + "_" + scenario + "_" + server + "_" + user; // file saving the state of the experiments
						HashMap<String,String> experimentStatus = readExperimentsStatus(fileNameStatus);
						int r = 1;
						if(read == 1)
							r = Integer.parseInt(experimentStatus.get("round"));
						for(int round = r; round <= rounds; round++) {
							switch(approach) {
								case "aco":
									iterations = 10;
									if(read == 1)
										iterations = Integer.parseInt(experimentStatus.get("iterations"));
									while(iterations <= 10) {
										ants = 1000;
										if(read == 1)
											ants = Integer.parseInt(experimentStatus.get("ants"));
										while(ants <= 1000) {
											int functions = 1;
											if(read == 1)
												functions = Integer.parseInt(experimentStatus.get("functions"));
											while(functions <= 5) { // Controls the number of services in the applications (i.e., 1, 3 and 5)
												JSONObject performance_result = runACOBasedApproach(approach.toUpperCase(), scenario, server, functions, user, iterations, ants, "NONE", round);
												performance_results.put(performance_result);
												experimentStatus.put("functions", "" + functions);
												experimentStatus.put("iterations", "" + iterations);
												experimentStatus.put("ants", "" + ants);
												experimentStatus.put("round", "" + r);
												writeExperimentStatus(fileNameStatus, experimentStatus);
												if(read == 1)
													read = 0;
												functions = functions + 2;
											}
											ants = ants * 10;
										}
										iterations = iterations * 10;
									}
									break;
								case "bestfit":
								case "closestfit":
								case "ilp":
								case "maxfit":
								case "multiopt":
								case "random":
								case "sa":
									int functions = 1;
									if(read == 1)
										functions = Integer.parseInt(experimentStatus.get("functions"));
									while(functions <= 5) {
										JSONObject performance_result = runBaselineApproach(approach.toUpperCase(), scenario, server, functions, user, round);
										performance_results.put(performance_result);
										experimentStatus.put("functions", "" + functions);
										experimentStatus.put("round", "" + r);
										writeExperimentStatus(fileNameStatus, experimentStatus);
										if(read == 1)
											read = 0;
										functions = functions + 2;
									}
									break;
							}
						}
					}
				}
			}
		}
		writeResults(performance_results, "./results/", "performance_results.csv");
	}

	private static HashMap<String,String> readExperimentsStatus(String fileNameStatus) {
		HashMap<String,String> parameters = new HashMap<String,String>();
		File file = new File(fileNameStatus);
		if(file.exists()) {
			try {
				String[] lines = Files.readAllLines(new File(fileNameStatus).toPath()).toArray(new String[0]);
				for (int i = 0; i < lines.length;i++) { 
					String key = lines[i].split("=")[0];
					String value = lines[i].split("=")[1];
					parameters.put(key, value);
				}
			}catch(Exception ex) {
				ex.printStackTrace();
			}
		}
		return parameters;
	}
	
	private static void writeExperimentStatus(String initialParametersFile, Map<String, String> parameters) {
		try {
			File file = new File(initialParametersFile);
			file.getParentFile().mkdirs();
			file.createNewFile();
			FileWriter fw = new FileWriter(file,false);
			BufferedWriter out = new BufferedWriter(fw);
			parameters.forEach((key,value)->{
				try {
					out.write(key+"="+value); out.newLine();
				} catch (IOException e) {
					e.printStackTrace();
				}
			});
			out.close();
			fw.close();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static JSONObject runBaselineApproach(String approach, int scenario, int servers, int functions, int users, int round) {
		JSONObject performance_result = new JSONObject();
		performance_result.accumulate("approach", approach);
		performance_result.accumulate("scenario", scenario);
		performance_result.accumulate("servers", servers);
		performance_result.accumulate("services", functions);
		performance_result.accumulate("users", users);
		try{
			String experiment = "*** Experiment approach " + approach;
			experiment = experiment + " :: scenario " + scenario;
			experiment = experiment + " :: servers " + servers;
			experiment = experiment + " :: functions " + functions;
			experiment = experiment + " :: users " + users;
			experiment = experiment + " :: round " + round + " ***";
			System.out.println(experiment);
			String s = null;
			String e = null;
			String[] files = updateParametersFiles(approach, scenario, servers, functions, users, 0, 0, "NONE", round);
			String newParametersFile = files[0];
			String newConfigFile = files[1];
			long startingTime = System.currentTimeMillis();
			Process p=Runtime.getRuntime().exec(new String[]{"java", "-jar", simrunnerFile, newConfigFile});
			long pid = p.pid();
			AtomicLong peakMemoryBytes = new AtomicLong(0);
            // Start memory polling thread
            Thread memoryMonitor = new Thread(() -> {
                try {
                    while (p.isAlive()) {
                        Process memProbe = new ProcessBuilder("wmic", "process", "where",
                                "ProcessId=" + pid, "get", "WorkingSetSize", "/value").start();
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(memProbe.getInputStream()))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (line.trim().startsWith("WorkingSetSize=")) {
                                    long memory = Long.parseLong(line.trim().split("=")[1]);
                                    peakMemoryBytes.updateAndGet(prev -> Math.max(prev, memory));
                                }
                            }
                        }
                        Thread.sleep(200); // Measure memory consumption every 200 ms
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
            memoryMonitor.start();
			BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
			while ((s = stdInput.readLine()) != null) {
				System.out.println(s);
			}
			if(printErrors==1){
				BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));
				while ((e = stdError.readLine()) != null) {
					System.out.println(e);
				}
			}
			int exitCode = p.waitFor();
		    System.out.println("Process exited with code: " + exitCode);
			long executionTime = System.currentTimeMillis() - startingTime;
			performance_result.accumulate("execution_time", executionTime);
			performance_result.accumulate("peak_memory", peakMemoryBytes);
			removeFile(newConfigFile);
			removeFile(newParametersFile);
		}catch(Exception ex) {
			System.out.println("Problems executing approach " + approach + " ...");
			ex.printStackTrace();
		}						
		return performance_result;
	}

	private static JSONObject runACOBasedApproach(String approach, int scenario, int servers, int functions, int users, int iterations, int ants, String predictionModel, int round) {
		JSONObject performance_result = new JSONObject();
		performance_result.accumulate("approach", approach);
		performance_result.accumulate("scenario", scenario);
		performance_result.accumulate("servers", servers);
		performance_result.accumulate("services", functions);
		performance_result.accumulate("users", users);
		try{
			String experiment = "*** Experiment approach " + approach;
			experiment = experiment + " :: scenario " + scenario;
			experiment = experiment + " :: servers " + servers;
			experiment = experiment + " :: functions " + functions;
			experiment = experiment + " :: users " + users;
			experiment = experiment + " :: iterations " + iterations;
			experiment = experiment + " :: ants " + ants;
			experiment = experiment + " :: prediction " + predictionModel;
			experiment = experiment + " :: round " + round + " ***";
			System.out.println(experiment);
			String s = null;
			String e = null;
			String[] files = updateParametersFiles(approach, scenario, servers, functions, users, iterations, ants, predictionModel, round);
			String newParametersFile = files[0];
			String newConfigFile = files[1];
			long startingTime = System.currentTimeMillis();
			Process p = Runtime.getRuntime().exec(new String[]{"java", "-jar", simrunnerFile , newConfigFile});
			long pid = p.pid();
			AtomicLong peakMemoryBytes = new AtomicLong(0);
            // Start memory polling thread
            Thread memoryMonitor = new Thread(() -> {
                try {
                    while (p.isAlive()) {
                        Process memProbe = new ProcessBuilder("wmic", "process", "where",
                                "ProcessId=" + pid, "get", "WorkingSetSize", "/value").start();
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(memProbe.getInputStream()))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (line.trim().startsWith("WorkingSetSize=")) {
                                    long memory = Long.parseLong(line.trim().split("=")[1]);
                                    peakMemoryBytes.updateAndGet(prev -> Math.max(prev, memory));
                                }
                            }
                        }
                        Thread.sleep(200); // Measure memory consumption every 200 ms
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
            memoryMonitor.start();
			BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
			while ((s = stdInput.readLine()) != null) {
				System.out.println(s);
			}
			if(printErrors==1){
				BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));
				while ((e = stdError.readLine()) != null) {
					System.out.println(e);
				}
			}
			int exitCode = p.waitFor();
		    System.out.println("Process exited with code: " + exitCode);
			long executionTime = System.currentTimeMillis() - startingTime;
			performance_result.accumulate("execution_time", executionTime);
			performance_result.accumulate("peak_memory", peakMemoryBytes);
			removeFile(newConfigFile);
			removeFile(newParametersFile);
		}catch(Exception ex) {
			System.out.println("Problems executing approach " + approach + " ...");
			ex.printStackTrace();
		}
		return performance_result;
	}
	
	private static String[] updateParametersFiles(String approach, int scenario, int servers, int functions, int users, int iterations, int ants, String predictionModel, int round) {
		System.out.println("Setting paremeters...");
		String [] files = {"", ""}; 
		String newConfigFile = "";
		String newParametersFile = "";
		int numMecSystems = 5;
		int serversPerSystem = servers / numMecSystems;
		int operaUsers = (int) (users * 0.4);
		int ambulanceUsers = (int) (users * 0.1);
		int genericUsers = (int) (users * 0.5);
		String muServiceTime = "";
		String lambdaAmbulance = "";
		String lambdaGeneric = "";
		String lambdaOpera = "";
		
		if(scenario == 1) {
			muServiceTime = "5m";
			lambdaAmbulance = "30m";
			lambdaGeneric = "10m";
			lambdaOpera = "40m";
		}
		
		if(scenario == 2) {
			muServiceTime = "5s";
			lambdaAmbulance = "30m";
			lambdaGeneric = "10m";
			lambdaOpera = "40m";
		}
		
		try{
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db = dbf.newDocumentBuilder(); 
			org.w3c.dom.Document documentParameters =db.parse(parametersFile);
			for (int i = 0; i <	  documentParameters.getElementsByTagName("Variable").getLength(); i++) {
				if(documentParameters.getElementsByTagName("Variable").item(i).getAttributes().getNamedItem("name").getNodeValue().equals("seed")){
					int seed = (int) (Math.random() * 10000);
					if(seed == 0)
						seed = 961;
					documentParameters.getElementsByTagName("Variable").item(i).getAttributes().getNamedItem("value").setNodeValue("" + seed);
				}
				if(documentParameters.getElementsByTagName("Variable").item(i).getAttributes().getNamedItem("name").getNodeValue().equals("actions"))
					documentParameters.getElementsByTagName("Variable").item(i).getAttributes().getNamedItem("value").setNodeValue(pathToActions);
				if(documentParameters.getElementsByTagName("Variable").item(i).getAttributes().getNamedItem("name").getNodeValue().equals("NUM_EDGE_SERVERS"))
					documentParameters.getElementsByTagName("Variable").item(i).getAttributes().getNamedItem("value").setNodeValue("" + servers);
				if(documentParameters.getElementsByTagName("Variable").item(i).getAttributes().getNamedItem("name").getNodeValue().equals("NUM_MEC_SYSTEMS"))
					documentParameters.getElementsByTagName("Variable").item(i).getAttributes().getNamedItem("value").setNodeValue("" + numMecSystems);
				if(documentParameters.getElementsByTagName("Variable").item(i).getAttributes().getNamedItem("name").getNodeValue().equals("EDGE_SERVERS_PER_SYSTEM"))
					documentParameters.getElementsByTagName("Variable").item(i).getAttributes().getNamedItem("value").setNodeValue("" + serversPerSystem);
				if(documentParameters.getElementsByTagName("Variable").item(i).getAttributes().getNamedItem("name").getNodeValue().equals("NUM_GENERIC_USERS"))
					documentParameters.getElementsByTagName("Variable").item(i).getAttributes().getNamedItem("value").setNodeValue("" + genericUsers);
				if(documentParameters.getElementsByTagName("Variable").item(i).getAttributes().getNamedItem("name").getNodeValue().equals("NUM_OPERA_USERS"))
					documentParameters.getElementsByTagName("Variable").item(i).getAttributes().getNamedItem("value").setNodeValue("" + operaUsers);
				if(documentParameters.getElementsByTagName("Variable").item(i).getAttributes().getNamedItem("name").getNodeValue().equals("NUM_AMBULANCE_USERS"))
					documentParameters.getElementsByTagName("Variable").item(i).getAttributes().getNamedItem("value").setNodeValue("" + ambulanceUsers);
				if(documentParameters.getElementsByTagName("Variable").item(i).getAttributes().getNamedItem("name").getNodeValue().equals("LINKS_COORDINATES_FILE"))
					documentParameters.getElementsByTagName("Variable").item(i).getAttributes().getNamedItem("value").setNodeValue(pathToCoordinates);
				if(documentParameters.getElementsByTagName("Variable").item(i).getAttributes().getNamedItem("name").getNodeValue().equals("RESULTS_PATH"))
					documentParameters.getElementsByTagName("Variable").item(i).getAttributes().getNamedItem("value").setNodeValue(pathToResults);
				if(documentParameters.getElementsByTagName("Variable").item(i).getAttributes().getNamedItem("name").getNodeValue().equals("MATRICES_FOLDER"))
					documentParameters.getElementsByTagName("Variable").item(i).getAttributes().getNamedItem("value").setNodeValue(pathToMatrices);
				if(documentParameters.getElementsByTagName("Variable").item(i).getAttributes().getNamedItem("name").getNodeValue().equals("SCENARIO"))
					documentParameters.getElementsByTagName("Variable").item(i).getAttributes().getNamedItem("value").setNodeValue(""+scenario);
				if(documentParameters.getElementsByTagName("Variable").item(i).getAttributes().getNamedItem("name").getNodeValue().equals("MU_SERVICE_TIME"))
					documentParameters.getElementsByTagName("Variable").item(i).getAttributes().getNamedItem("value").setNodeValue(muServiceTime);
				if(documentParameters.getElementsByTagName("Variable").item(i).getAttributes().getNamedItem("name").getNodeValue().equals("APPROACH"))
					documentParameters.getElementsByTagName("Variable").item(i).getAttributes().getNamedItem("value").setNodeValue(approach);
				if(documentParameters.getElementsByTagName("Variable").item(i).getAttributes().getNamedItem("name").getNodeValue().equals("FUNCTIONS"))
					documentParameters.getElementsByTagName("Variable").item(i).getAttributes().getNamedItem("value").setNodeValue(""+functions);				
				if(documentParameters.getElementsByTagName("Variable").item(i).getAttributes().getNamedItem("name").getNodeValue().equals("LAMBDA_GENERIC_ARRIVAL"))
					documentParameters.getElementsByTagName("Variable").item(i).getAttributes().getNamedItem("value").setNodeValue(lambdaGeneric);
				if(documentParameters.getElementsByTagName("Variable").item(i).getAttributes().getNamedItem("name").getNodeValue().equals("LAMBDA_AMBULANCE_ARRIVAL"))
					documentParameters.getElementsByTagName("Variable").item(i).getAttributes().getNamedItem("value").setNodeValue(lambdaAmbulance);
				if(documentParameters.getElementsByTagName("Variable").item(i).getAttributes().getNamedItem("name").getNodeValue().equals("LAMBDA_OPERA_ARRIVAL"))
					documentParameters.getElementsByTagName("Variable").item(i).getAttributes().getNamedItem("value").setNodeValue(lambdaOpera);
				if(documentParameters.getElementsByTagName("Variable").item(i).getAttributes().getNamedItem("name").getNodeValue().equals("PREDICTION_ALGORITHM"))
					documentParameters.getElementsByTagName("Variable").item(i).getAttributes().getNamedItem("value").setNodeValue(predictionModel);
				if(documentParameters.getElementsByTagName("Variable").item(i).getAttributes().getNamedItem("name").getNodeValue().equals("ANTS_NUMBER"))
					documentParameters.getElementsByTagName("Variable").item(i).getAttributes().getNamedItem("value").setNodeValue(""+ants);
				if(documentParameters.getElementsByTagName("Variable").item(i).getAttributes().getNamedItem("name").getNodeValue().equals("ITERATIONS"))
					documentParameters.getElementsByTagName("Variable").item(i).getAttributes().getNamedItem("value").setNodeValue(""+iterations);
				if(documentParameters.getElementsByTagName("Variable").item(i).getAttributes().getNamedItem("name").getNodeValue().equals("ROUND"))
					documentParameters.getElementsByTagName("Variable").item(i).getAttributes().getNamedItem("value").setNodeValue(""+round);
			}
			TransformerFactory transformerFactoryTime = TransformerFactory.newInstance(); 
			Transformer transformerTime = transformerFactoryTime.newTransformer(); 
			DOMSource sourceTime = new DOMSource(documentParameters); 
			newParametersFile = "";
			if(iterations > 0)
				newParametersFile = parametersFile.replace(".xml", "_"+approach+"_"+scenario+"_"+servers+"_"+functions+"_"+users+"_"+predictionModel+"_"+iterations+"_"+ants+"_"+round+".xml");
			else
				newParametersFile = parametersFile.replace(".xml", "_"+approach+"_"+scenario+"_"+servers+"_"+functions+"_"+users+"_"+predictionModel+"_"+round+".xml");
			StreamResult resultTime = new StreamResult(new File(newParametersFile));
			transformerTime.transform(sourceTime, resultTime);
			
			dbf = DocumentBuilderFactory.newInstance();
			db = dbf.newDocumentBuilder(); 
			documentParameters =db.parse(configFile);
			for (int i = 0; i <	  documentParameters.getElementsByTagName("xi:include").getLength(); i++) {
				if(documentParameters.getElementsByTagName("xi:include").item(i).getAttributes().getNamedItem("name").getNodeValue().equals("eval_parameters"))
					documentParameters.getElementsByTagName("xi:include").item(i).getAttributes().getNamedItem("href").setNodeValue(newParametersFile.replace("./config/mec/", ""));
			}
			transformerFactoryTime = TransformerFactory.newInstance(); 
			transformerTime = transformerFactoryTime.newTransformer(); 
			sourceTime = new DOMSource(documentParameters);
			newConfigFile = "";
			if(iterations > 0)
				newConfigFile = configFile.replace(".xml", "_"+approach+"_"+scenario+"_"+servers+"_"+functions+"_"+users+"_"+predictionModel+"_"+iterations+"_"+ants+"_"+round+".xml");
			else
				newConfigFile = configFile.replace(".xml", "_"+approach+"_"+scenario+"_"+servers+"_"+functions+"_"+users+"_"+predictionModel+"_"+round+".xml");
			resultTime = new StreamResult(new File(newConfigFile));
			transformerTime.transform(sourceTime, resultTime);
		}catch(Exception ex) {
			ex.printStackTrace();
		}
		
		files[0] = newParametersFile;
		files[1] = newConfigFile;
		
		return files;
	}
	
	private static void writeResults(JSONArray results, String path, String name) {
		File file = new File(path);
		try {
			file.mkdirs();
			FileWriter outputfile = new FileWriter(file + "/" + name);
			CSVWriter writer = new CSVWriter(outputfile);
			List<String[]> data = new ArrayList<String[]>();
			data.add(new String[] { "approach", "scenario", "servers", "services", "users", "execution_time", "peak_memory"});
			for(int i = 0; i < results.length(); i++) {
				JSONObject result = (JSONObject) results.get(i);
				data.add(new String[] { 
						"" + result.get("approach"),							
						"" + result.get("scenario"),
						"" + result.get("servers"),
						"" + result.get("services"),
						"" + result.get("users"),
						"" + result.get("execution_time"),					
						"" + result.get("peak_memory")
						}
				);
			}
			writer.writeAll(data);
			writer.close();
			System.out.println("File written: " + name);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}

	
	private static void removeFile(String fileName) {
		File file = new File(fileName);
		file.delete();
	}

	public static String getConfigFile() {
		return configFile;
	}

	public static void setConfigFile(String configFile) {
		PerformanceEvaluation.configFile = configFile;
	}

	public static String getParametersFile() {
		return parametersFile;
	}

	public static void setParametersFile(String parametersFile) {
		PerformanceEvaluation.parametersFile = parametersFile;
	}

	public static String getPathToResults() {
		return pathToResults;
	}

	public static void setPathToResults(String pathToResults) {
		PerformanceEvaluation.pathToResults = pathToResults;
	}

	public static String getPathToActions() {
		return pathToActions;
	}

	public static void setPathToActions(String pathToActions) {
		PerformanceEvaluation.pathToActions = pathToActions;
	}

	public static String getPathToCoordinates() {
		return pathToCoordinates;
	}

	public static void setPathToCoordinates(String pathToCoordinates) {
		PerformanceEvaluation.pathToCoordinates = pathToCoordinates;
	}

	public static String getPathToMatrices() {
		return pathToMatrices;
	}

	public static void setPathToMatrices(String pathToMatrices) {
		PerformanceEvaluation.pathToMatrices = pathToMatrices;
	}

}
