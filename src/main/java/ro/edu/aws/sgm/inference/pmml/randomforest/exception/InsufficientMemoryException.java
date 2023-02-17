package ro.edu.aws.sgm.inference.pmml.randomforest.exception;

public class InsufficientMemoryException extends RuntimeException {

    public InsufficientMemoryException(String exception){
        super(exception);
    }
}
