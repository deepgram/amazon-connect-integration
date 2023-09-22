#!/bin/bash

STACK_NAME="$1"
if [ -z "$STACK_NAME" ]; then
  echo "Usage: $0 STACK_NAME"
  exit 1
fi

TAGS='[
  { "Key": "dg:owner", "Value": "ryan.chimienti@deepgram.com" },
  { "Key": "dg:team", "Value": "appeng" },
  { "Key": "dg:service", "Value": "amazon-connect-integration" }
]'
REGION="us-east-1"
TEMPLATE_FILE="file://cloudformation.yaml"
CLOUDFORMATION_ROLE="arn:aws:iam::764576996850:role/OnPremDeploymentCFRole"

DG_API_KEY="$AMAZON_CONNECT_DEEPGRAM_API_KEY"
if [[ -z "$DG_API_KEY" ]]
then
  echo "Please set the AMAZON_CONNECT_DEEPGRAM_API_KEY environment variable."
  exit 1
fi

VPC_ID="vpc-46659c22"
SUBNETS="subnet-8c1e57a7\,subnet-739f167f"

aws cloudformation create-stack \
  --stack-name "${STACK_NAME}" \
  --template-body "${TEMPLATE_FILE}" \
  --parameters ParameterKey=deepgramApiKey,ParameterValue="${DG_API_KEY}" \
    ParameterKey=vpcId,ParameterValue="${VPC_ID}" \
    ParameterKey=subnets,ParameterValue="${SUBNETS}" \
  --tags "${TAGS}" \
  --region "${REGION}" \
  --role-arn "${CLOUDFORMATION_ROLE}"

echo "Stack creation initiated for ${STACK_NAME}"
