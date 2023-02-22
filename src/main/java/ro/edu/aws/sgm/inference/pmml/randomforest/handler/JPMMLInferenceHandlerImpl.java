package ro.edu.aws.sgm.inference.pmml.randomforest.handler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.dmg.pmml.FieldName;
import org.dmg.pmml.MiningModel;
import org.dmg.pmml.PMML;
import org.jpmml.evaluator.FieldValue;
import org.jpmml.evaluator.MiningModelEvaluator;
import org.jpmml.evaluator.ModelEvaluator;
import org.jpmml.evaluator.ProbabilityClassificationMap;
import org.springframework.stereotype.Service;

import ro.edu.aws.sgm.inference.pmml.randomforest.pojo.Features;

@Service("jpmmlInferenceImpl")
public class JPMMLInferenceHandlerImpl implements InferenceHandlerInf {

    public String predict(List <Features> data, Object model){

        PMML pmmlFile = (PMML) model;
        List <Features> featuresList = data;

        StringBuilder sb = new StringBuilder();

        for( Features feature: featuresList){

            List <String> featureString = feature.getFeatures();
            String features = String.join(",", featureString).concat("\n");
            sb.append(features);
         }

        ModelEvaluator<MiningModel> modelEvaluator = new MiningModelEvaluator(pmmlFile);

        return predict(sb.toString().lines(), modelEvaluator);
    
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
}
