#!/bin/bash

set -e
set -o pipefail

REPLACE=false
STACK_NAME="$1"
DELETE_STACK_NAME=""

if [ -z "$STACK_NAME" ]; then
  echo "Usage: $0 STACK_NAME [-r|--replace STACK_NAME_TO_REPLACE]"
  exit 1
fi

shift

while (( "$#" )); do
  case "$1" in
    -r|--replace)
      REPLACE=true
      DELETE_STACK_NAME="$2"
      shift 2
      ;;
    *)
      echo "Usage: $0 STACK_NAME [-r|--replace STACK_NAME_TO_REPLACE]"
      exit 1
      ;;
  esac
done

if [ "$REPLACE" = true ] && [ -z "$DELETE_STACK_NAME" ]; then
  echo "Please specify the stack name to replace."
  exit 1
fi

TAGS='[
  { "Key": "dg:owner", "Value": "ryan.chimienti@deepgram.com" },
  { "Key": "dg:team", "Value": "appeng" },
  { "Key": "dg:service", "Value": "amazon-connect-integration" }
]'
REGION="us-east-1"
TEMPLATE_FILE="file://cloudformation.yaml"

DG_API_KEY="$AMAZON_CONNECT_DEEPGRAM_API_KEY"
if [[ -z "$DG_API_KEY" ]]
then
  echo "Please set the AMAZON_CONNECT_DEEPGRAM_API_KEY environment variable."
  exit 1
fi

# prod
# VPC_ID="vpc-46659c22"
# SUBNETS="subnet-8c1e57a7\,subnet-739f167f"

# dev
VPC_ID="vpc-077b36daf7829fbee"
SUBNETS="subnet-068e487e27efbe720\,subnet-0cd74a6d628166ca0"

if [ "$REPLACE" = true ]; then
  echo "Deleting old stack $DELETE_STACK_NAME"
  aws cloudformation delete-stack --stack-name $DELETE_STACK_NAME
  aws cloudformation wait stack-delete-complete --stack-name $DELETE_STACK_NAME
  echo "Old stack $DELETE_STACK_NAME deleted successfully!"
fi

echo "Creating new stack $STACK_NAME"
aws cloudformation create-stack \
  --stack-name "${STACK_NAME}" \
  --template-body "${TEMPLATE_FILE}" \
  --parameters ParameterKey=deepgramApiKey,ParameterValue="${DG_API_KEY}" \
    ParameterKey=vpcId,ParameterValue="${VPC_ID}" \
    ParameterKey=subnets,ParameterValue="${SUBNETS}" \
  --tags "${TAGS}" \
  --region "${REGION}" \
  --capabilities CAPABILITY_NAMED_IAM

aws cloudformation wait stack-create-complete --stack-name $STACK_NAME

echo "New stack $STACK_NAME created successfully!"
