# KVS DG Integrator

This is a long-running task that pulls audio from Kinesis Video Streams 
and sends it to Deepgram. It is triggered by the KVS DG Trigger Lambda 
function, which passes it all the info it needs for the session.

## Deploy to Fargate

Build the image and push it to ECR:

```shell
docker build --platform linux/amd64 -t 396185571030.dkr.ecr.us-east-1.amazonaws.com/kvs-dg-integrator:latest . \
&& aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin 396185571030.dkr.ecr.us-east-1.amazonaws.com \
&& docker push 396185571030.dkr.ecr.us-east-1.amazonaws.com/kvs-dg-integrator:latest
```

Then spin up the Fargate task and all its associated resources
using `cloudformation.yaml`.

If you don't need to recreate the whole stack, you can also just redeploy the
Fargate task:

```shell
aws ecs update-service --cluster arn:aws:ecs:us-east-1:396185571030:cluster/kvs-dg-integrator-cluster --service <service-arn> --force-new-deployment
```
