package ro.edu.aws.sgm.inference.pmml.randomforest.entrypoint;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.bind.JAXBException;
import javax.xml.transform.sax.SAXSource;

import org.dmg.pmml.PMML;
import org.jpmml.model.ImportFilter;
import org.jpmml.model.JAXBUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import ro.edu.aws.sgm.inference.pmml.randomforest.exception.InsufficientMemoryException;
import ro.edu.aws.sgm.inference.pmml.randomforest.exception.ModelAlreadyPresentException;
import ro.edu.aws.sgm.inference.pmml.randomforest.exception.ModelNotFoundException;
import ro.edu.aws.sgm.inference.pmml.randomforest.handler.InferenceHandlerInf;
import ro.edu.aws.sgm.inference.pmml.randomforest.pojo.Features;
import ro.edu.aws.sgm.inference.pmml.randomforest.pojo.InputData;
import ro.edu.aws.sgm.inference.pmml.randomforest.pojo.Model;

@RestController
public class SGMController {


  //private ModelEvaluator<MiningModel> modelEvaluator;
  private ConcurrentHashMap<String, PMML> concurrentHashMap;

  @Autowired
  @Qualifier("jpmmlInferenceImpl")
  private InferenceHandlerInf jpmmlInferenceHandlerImpl;

  @Value("${java.memory.threshold.percentage}")
  private String memoryThresholdValue;

  @PostConstruct
  public void init() {
   
      concurrentHashMap = new ConcurrentHashMap<String, PMML>();
   
  }

  @RequestMapping(value = "/ping", method = RequestMethod.GET)
  public String ping() {
    return "";
  }


  @PostMapping(value = "/models/{model_name}/invoke")
  public String invoke(HttpServletRequest request, 
    @RequestHeader(value = "X-Amzn-SageMaker-Target-Model") String targetModel,
    @PathVariable String model_name, @RequestBody InputData inputData) throws IOException {
    
    if(isModelLoaded(model_name)){

      List <Features> data = inputData.getFeatureList();
      jpmmlInferenceHandlerImpl.predict(data, concurrentHashMap.get(model_name));
    }
 
    return "";
    
  }


  @PostMapping(value = "/models")
  public ResponseEntity <?>loadModel(@RequestBody Model model) throws Exception{

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
    PMML pmml = createPMMLfromFile(pmmlFile);

    concurrentHashMap.put(model_name, pmml);
    return  new ResponseEntity<>("Model: "+ model_name + " loaded in memory", HttpStatus.OK);
  }


  @GetMapping("/models")
  public ResponseEntity<List<Model>> listModels(HttpServletRequest request) throws IOException{

    List <Model> modelList = new ArrayList<Model>();

    for (String key: concurrentHashMap.keySet()){

      Model model = new Model(key, "opt/ml/models/" +key+"/model");
      modelList.add(model);
    }
    return ResponseEntity.ok(modelList);
  }


  @GetMapping("/models/{model_name}")
  public String getModel(@PathVariable String model_name){
    return "";
  }


  @DeleteMapping("/models/{model_name}")
  public ResponseEntity <?> deleteModel(@PathVariable String model_name){

    if(isModelLoaded(model_name)){
     concurrentHashMap.remove(model_name);
    }

    return new ResponseEntity<String>("Model: " + model_name + " removed from the memory.", HttpStatus.OK);
  }


  private static PMML createPMMLfromFile(File pmmlFile)
      throws SAXException, IOException, JAXBException{


     // File pmmlFile = new File(SGMController.class.getResource(fileName).getPath());  
      String pmmlString = new Scanner(pmmlFile).useDelimiter("\\Z").next();

      InputStream is = new ByteArrayInputStream(pmmlString.getBytes());

      InputSource source = new InputSource(is);
      SAXSource transformedSource = ImportFilter.apply(source);

      return JAXBUtil.unmarshalPMML(transformedSource);

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
