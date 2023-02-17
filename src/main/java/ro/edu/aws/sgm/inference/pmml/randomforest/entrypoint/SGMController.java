package ro.edu.aws.sgm.inference.pmml.randomforest.entrypoint;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.List;


import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.xml.bind.JAXBException;
import javax.xml.transform.sax.SAXSource;

import org.dmg.pmml.FieldName;
import org.dmg.pmml.MiningModel;
import org.dmg.pmml.PMML;
import org.jpmml.evaluator.FieldValue;
import org.jpmml.evaluator.MiningModelEvaluator;
import org.jpmml.evaluator.ModelEvaluator;
import org.jpmml.evaluator.ProbabilityClassificationMap;
import org.jpmml.model.ImportFilter;
import org.jpmml.model.JAXBUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import ro.edu.aws.sgm.inference.pmml.randomforest.pojo.Model;
import ro.edu.aws.sgm.inference.pmml.randomforest.exception.InsufficientMemoryException;
import ro.edu.aws.sgm.inference.pmml.randomforest.exception.ModelAlreadyPresentException;
import ro.edu.aws.sgm.inference.pmml.randomforest.exception.ModelNotFoundException;

@RestController
public class SGMController {


  private ModelEvaluator<MiningModel> modelEvaluator;
  private ConcurrentHashMap<String, PMML> concurrentHashMap;

  @Value("${java.memory.threshold.percentage}")
  private String memoryThresholdValue;

  @PostConstruct
  public void init() {
   
     // modelEvaluator = new MiningModelEvaluator(pmml);
      concurrentHashMap = new ConcurrentHashMap<String, PMML>();
   
  }

  @RequestMapping(value = "/ping", method = RequestMethod.GET)
  public String ping() {
    return "";
  }


  @RequestMapping(value = "/invocations", method = RequestMethod.POST)
  public String invoke(HttpServletRequest request) throws IOException {
    return predict(request.getReader().lines(), modelEvaluator);
  }

  @RequestMapping(value = "/models", method = RequestMethod.POST)
  public String loadModel(@RequestBody Model model) throws Exception{

    String model_name = model.getModel_name();
    String url = model.getUrl();

    System.out.println("model_name: "+ model_name);
    System.out.println("url: "+ url);

    // Throw exception when model is already present in the Map
    if(concurrentHashMap.containsKey(model_name)){
      throw new ModelAlreadyPresentException("Model Name: " + model_name + "already loaded in memory.");
    }

    // Throw exception when enough memory is not available to load the mmodel
    if(!isMemoryAvailable()){
      throw new InsufficientMemoryException("Insufficient memory. Cannot load model: " + model_name);
    }

    File pmmlFile = Paths.get(url).toFile();
    PMML pmml = createPMMLfromFile(pmmlFile.toString());

    concurrentHashMap.put(model_name, pmml);
    return  "";
  }

  @RequestMapping(value = "/models", method = RequestMethod.GET)
  public ResponseEntity<List<Model>> listModels(HttpServletRequest request) throws IOException{

    List <Model> modelList = new ArrayList<Model>();

    for (String key: concurrentHashMap.keySet()){

      Model model = new Model(key, "opt/ml/models/" +key+"model");
      modelList.add(model);
    }
    return ResponseEntity.ok(modelList);
  }

  @RequestMapping(value = "/models/{model_name}", method = RequestMethod.GET)
  public String getModel(@PathVariable String model_name){
    return "";
  }

  @RequestMapping(value = "/models/{model_name}", method = RequestMethod.DELETE)
  public String deleteModel(@PathVariable String model_name){

    if(!isModelLoaded(model_name)){
     concurrentHashMap.remove(model_name);
    }

    return "";
  }

  private static String predict(Stream<String> inputData,
      ModelEvaluator<MiningModel> modelEvaluator) {


    String returns = inputData.map(dataLine -> {
      Map<FieldName, FieldValue> arguments = readArgumentsFromLine(dataLine, modelEvaluator);
      modelEvaluator.verify();
      Map<FieldName, ?> results = modelEvaluator.evaluate(arguments);
      FieldName targetName = modelEvaluator.getTargetField();
      Object targetValue = results.get(targetName);
      ProbabilityClassificationMap nodeMap = (ProbabilityClassificationMap) targetValue;

      return  ( nodeMap != null && nodeMap.getResult() !=  null) ? nodeMap.getResult().toString() : "NA for input->"+dataLine;
    }).collect(Collectors.joining(System.lineSeparator()));

    return returns;

  }



  private static PMML createPMMLfromFile(String fileName)
      throws SAXException, JAXBException, IOException {

    try (
        InputStream pmmlFile = SGMController.class.getClassLoader().getResourceAsStream(fileName)) {
      String pmmlString = new Scanner(pmmlFile).useDelimiter("\\Z").next();

      InputStream is = new ByteArrayInputStream(pmmlString.getBytes());

      InputSource source = new InputSource(is);
      SAXSource transformedSource = ImportFilter.apply(source);

      return JAXBUtil.unmarshalPMML(transformedSource);
    }
  }


  private static Map<FieldName, FieldValue> readArgumentsFromLine(String line,
      ModelEvaluator<MiningModel> modelEvaluator) {
    Map<FieldName, FieldValue> arguments = new LinkedHashMap<FieldName, FieldValue>();
    String[] lineArgs = line.split(",");

    if (lineArgs.length != 5)
      return arguments;

    FieldValue sepalLength = modelEvaluator.prepare(new FieldName("Sepal.Length"),
        lineArgs[0].isEmpty() ? 0 : lineArgs[0]);
    FieldValue sepalWidth = modelEvaluator.prepare(new FieldName("Sepal.Width"),
        lineArgs[1].isEmpty() ? 0 : lineArgs[1]);
    FieldValue petalLength = modelEvaluator.prepare(new FieldName("Petal.Length"),
        lineArgs[2].isEmpty() ? 0 : lineArgs[2]);
    FieldValue petalWidth = modelEvaluator.prepare(new FieldName("Petal.Width"),
        lineArgs[3].isEmpty() ? 0 : lineArgs[3]);

    arguments.put(new FieldName("Sepal.Length"), sepalLength);
    arguments.put(new FieldName("Sepal.Width"), sepalWidth);
    arguments.put(new FieldName("Petal.Length"), petalLength);
    arguments.put(new FieldName("Petal.Width"), petalWidth);

    return arguments;
  }

  private boolean isModelLoaded(String model_name){

    if(!concurrentHashMap.containsKey(model_name)){
      throw new ModelNotFoundException("Model name: "+ model_name + "not loaded in memory");
    }
    return true;
  }

  private boolean isMemoryAvailable(){

    long freeMemory = Runtime.getRuntime().freeMemory();
    long maxMemory =  Runtime.getRuntime().maxMemory();

    long memoryUsage = (freeMemory/maxMemory) * 100;

      if( memoryUsage >= Integer.parseInt(memoryThresholdValue))
        return false;

    return true;

  }
}
