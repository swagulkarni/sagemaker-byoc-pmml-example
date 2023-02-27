FROM openjdk:19
LABEL maintainer "AWS"
LABEL com.amazonaws.sagemaker.capabilities.multi-models=true
RUN mkdir /work
ADD target/sgm-java-example-0.0.1-SNAPSHOT.jar /work/app.jar
ADD start_java.py /work/start_java.py
RUN sh -c 'touch /work/app.jar'
RUN microdnf install python36
EXPOSE 8080
WORKDIR /work
ENTRYPOINT ["python3", "start_java.py"]

