# KVS DG Integrator

This is a long-running task that pulls audio from Kinesis Video Streams 
and sends it to Deepgram. It is triggered by the KVS DG Trigger Lambda 
function, which passes it all the info it needs for the session.

For testing, it can be deployed to a Lambda function. In production, it 
should be deployed to Fargate or EC2 so that it can run for an unlimited 
amount of time.

## Deploy to Lambda

Build the image and push it to ECR:
```shell
docker build -f Dockerfile.lambda --platform linux/amd64 -t 764576996850.dkr.ecr.us-east-1.amazonaws.com/kvs-dg-integrator-lambda:latest .
docker push 764576996850.dkr.ecr.us-east-1.amazonaws.com/kvs-dg-integrator-lambda:latest
```
Then spin up the Lambda with a CloudFormation template:
```yaml
kvsDgIntegratorLambda:
Type: "AWS::Lambda::Function"
Properties:
  Role: arn:aws:iam::764576996850:role/kvsDgIntegratorLambdaRole
  MemorySize: 512
  Timeout: 900
  Environment:
    Variables:
      APP_REGION: !Ref "AWS::Region"
      DEEPGRAM_API_KEY: "<API KEY GOES HERE>" # Swap this in
  PackageType: "Image"
  Code:
    ImageUri: 764576996850.dkr.ecr.us-east-1.amazonaws.com/kvs-dg-integrator-lambda:latest
```
