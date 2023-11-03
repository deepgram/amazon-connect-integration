# amazon-connect-integration
This repo enables real-time transcription of Amazon Connect calls with Deepgram, using self-hosted AWS infrastructure. For detailed documentation, see https://developers.deepgram.com/docs/deepgram-with-amazon-connect.

## Deploy the Integration

1. Create ECR repos for `kvs_dg_trigger` and `kvs_dg_integrator`:
    ```shell
    aws ecr create-repository --repository-name kvs-dg-trigger
    aws ecr create-repository --repository-name kvs-dg-integrator
    ```
2. Log in to ECR with the Docker CLI:
    ```
    aws ecr get-login-password --region <YOUR-REGION> | docker login --username AWS --password-stdin <YOUR-ACCOUNT-NUMBER>.dkr.ecr.<YOUR-REGION>.amazonaws.com 
    ```
3. In the `kvs_dg_trigger` folder, build the Docker image for the trigger Lambda:
    ```shell
    docker build --platform linux/amd64 -t <YOUR-ACCOUNT-NUMBER>.dkr.ecr.<YOUR-REGION>.amazonaws.com/kvs-dg-trigger:latest .
    ```
4. In the `kvs_dg_integrator` folder, build the Docker image for the integrator task:
    ```shell
    docker build --platform linux/amd64 -t <YOUR-ACCOUNT-NUMBER>.dkr.ecr.<YOUR-REGION>.amazonaws.com/kvs-dg-integrator:latest .
    ```
5. Push the new Docker images to ECR:
    ```shell
    docker push <YOUR-ACCOUNT-NUMBER>.dkr.ecr.<YOUR-REGION>.amazonaws.com/kvs-dg-trigger:latest
    docker push <YOUR-ACCOUNT-NUMBER>.dkr.ecr.<YOUR-REGION>.amazonaws.com/kvs-dg-integrator:latest
    ```
6. Run `cloudformation.yaml` in AWS CloudFormation to spin up all of the necessary AWS resources.

## Run the Sample Contact Flow
Also included is an Amazon Connect contact flow that demonstrates how to use the deployed integration. To run the contact flow:

1. Add the newly deployed trigger Lambda to your Amazon Connect instance ([guide](https://docs.aws.amazon.com/connect/latest/adminguide/connect-lambda-functions.html#add-lambda-function)).
2. Enable live media streaming in your Amazon Connect instance ([guide](https://docs.aws.amazon.com/connect/latest/adminguide/enable-live-media-streams.html)). Choosing **No data retention** is fine, unless you plan to load test the integration with the built-in load testing functionality, in which case you should choose a retention period at least as long as your load test duration.
3. Create a new inbound contact flow and import `sample_contact_flow.json` ([guide](https://docs.aws.amazon.com/connect/latest/adminguide/contact-flow-import-export.html#how-to-import-export-contact-flows)).
4. In the **Deepgram Configuration** block, go to **Edit Settings** and update `dg_callback` to a URL where you want to receive the transcripts. The transcripts will be sent as POST requests to the URL you provide. For testing, you can use a site like [Beeceptor](https://beeceptor.com/) to create a URL that will display the contents of the POST requests.
5. In the **Invoke Trigger Lambda** block, go to **Edit Settings** and select the trigger Lambda function from the **Function ARN** dropdown.
6. Save and publish the contact flow.
7. Assign the contact flow to a phone number ([guide](https://docs.aws.amazon.com/connect/latest/adminguide/associate-claimed-ported-phone-number-to-flow.html)).
8. Call into the phone number. After it plays the initial message, say something and watch your callback server to make sure your words are being transcribed.
