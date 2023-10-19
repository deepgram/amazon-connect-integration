# KVS DG Trigger

This is an AWS Lambda function that can be invoked during an Amazon Connect contact flow. Its job is to call KVS DG Integrator, passing along the relevant information from the contact flow. 

## Deploy the image
Build the image and push it to ECR:
```shell
docker build --platform linux/amd64 -t 396185571030.dkr.ecr.us-east-1.amazonaws.com/kvs-dg-trigger:latest . \
&& aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin 396185571030.dkr.ecr.us-east-1.amazonaws.com \
&& docker push 396185571030.dkr.ecr.us-east-1.amazonaws.com/kvs-dg-trigger:latest
```
Then spin up the lambda with a CloudFormation template:
```yaml
  kvsDgTrigger:
    Type: "AWS::Lambda::Function"
    Properties:
      Role: !GetAtt kvsDgTriggerRole.Arn
      Timeout: 30
      Environment:
        Variables:
          # Swap in the correct domain
          KVS_DG_INTEGRATOR_DOMAIN: !GetAtt kvsDgIntegratorLoadBalancer.DNSName
      PackageType: "Image"
      Code:
        ImageUri: 396185571030.dkr.ecr.us-east-1.amazonaws.com/kvs-dg-trigger:latest
```

## Test the image locally
```shell
docker build --platform linux/amd64 -t kvs-dg-trigger:test .
docker run -p 9000:8080 kvs-dg-trigger:test
./mock_invocation.sh
```

## Run unit tests
```shell
python3 -m unittest lambda_function_test.py
```
