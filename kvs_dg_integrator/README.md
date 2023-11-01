# KVS DG Integrator

This is an AWS Fargate task that pulls call audio from Kinesis Video Streams and sends it to Deepgram. It is called by the KVS DG Trigger Lambda function, which passes it all the info it needs for the session. Sessions are initiated by POST requests sent to its `/start-session` endpoint. 

**Create the ECR repo where the Docker image will live:**
```shell
aws ecr create-repository --repository-name kvs-dg-integrator
```

**Build the image and push it to ECR (run this in the `kvs_dg_integrator` folder):**
```shell
docker build --platform linux/amd64 -t <YOUR-ACCOUNT-NUMBER>.dkr.ecr.<YOUR-REGION>.amazonaws.com/kvs-dg-integrator:latest . \
&& aws ecr get-login-password --region <YOUR-REGION> | docker login --username AWS --password-stdin <YOUR-ACCOUNT-NUMBER>.dkr.ecr.<YOUR-REGION>.amazonaws.com \
&& docker push <YOUR-ACCOUNT-NUMBER>.dkr.ecr.<YOUR-REGION>.amazonaws.com/kvs-dg-integrator:latest
```

**Redeploy with the latest image, without rebuilding the CloudFormation stack:**
```shell
aws ecs update-service --cluster arn:aws:ecs:<YOUR-REGION>:<YOUR-ACCOUNT-NUMBER>:cluster/kvs-dg-integrator-cluster --service <SERVICE-ARN> --force-new-deployment
```
