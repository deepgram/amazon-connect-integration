# KVS DG Trigger

This is an AWS Lambda function that can be invoked during an Amazon Connect contact flow. Its job
is to call the `/start-session` endpoint of DG KVS Integrator, passing along the relevant 
information from the contact flow. 

## Build the image
```
docker build --platform linux/amd64 -t trigger-lambda:test .
```

## Test the image locally
```
docker run -p 9000:8080 trigger-lambda:test
./mock_invocation.sh
```

## Run unit tests
```
python3 -m unittest lambda_function_test.py
```
