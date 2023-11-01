# KVS DG Trigger

This is an AWS Lambda function that can be invoked during an Amazon Connect contact flow. Its job is to call KVS DG Integrator, passing along the relevant information from the contact flow.

**Create the ECR repo where the Docker image will live:**
```shell
aws ecr create-repository --repository-name kvs-dg-trigger
```

**Build the image and push it to ECR (run this in the `kvs_dg_trigger` folder):**
```shell
docker build --platform linux/amd64 -t <YOUR-ACCOUNT-NUMBER>.dkr.ecr.<YOUR-REGION>.amazonaws.com/kvs-dg-trigger:latest . \
&& aws ecr get-login-password --region <YOUR-REGION> | docker login --username AWS --password-stdin <YOUR-ACCOUNT-NUMBER>.dkr.ecr.<YOUR-REGION>.amazonaws.com \
&& docker push <YOUR-ACCOUNT-NUMBER>.dkr.ecr.<YOUR-REGION>.amazonaws.com/kvs-dg-trigger:latest
```

**Run unit tests:**
```shell
python3 -m unittest lambda_function_test.py
```
