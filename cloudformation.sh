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

VPC_ID="vpc-077b36daf7829fbee"
SUBNETS="subnet-068e487e27efbe720\,subnet-0cd74a6d628166ca0"
KVS_DG_TRIGGER_IMAGE="396185571030.dkr.ecr.us-east-1.amazonaws.com/kvs-dg-trigger:latest"
KVS_DG_INTEGRATOR_IMAGE="396185571030.dkr.ecr.us-east-1.amazonaws.com/kvs-dg-integrator:latest"
KVS_DG_INTEGRATOR_DESIRED_TASK_COUNT="1"
KVS_DG_INTEGRATOR_TASK_CPU="256"
KVS_DG_INTEGRATOR_TASK_MEMORY="512"
LOAD_TEST_IS_ENABLED="no"
LOAD_TEST_NUM_SESSIONS="20"
LOAD_TEST_INTERVAL_MS="15000"

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
    ParameterKey=kvsDgTriggerImage,ParameterValue="${KVS_DG_TRIGGER_IMAGE}" \
    ParameterKey=kvsDgIntegratorImage,ParameterValue="${KVS_DG_INTEGRATOR_IMAGE}" \
    ParameterKey=kvsDgIntegratorDesiredTaskCount,ParameterValue="${KVS_DG_INTEGRATOR_DESIRED_TASK_COUNT}" \
    ParameterKey=kvsDgIntegratorTaskCpu,ParameterValue="${KVS_DG_INTEGRATOR_TASK_CPU}" \
    ParameterKey=kvsDgIntegratorTaskMemory,ParameterValue="${KVS_DG_INTEGRATOR_TASK_MEMORY}" \
    ParameterKey=loadTestIsEnabled,ParameterValue="${LOAD_TEST_IS_ENABLED}" \
    ParameterKey=loadTestNumSessions,ParameterValue="${LOAD_TEST_NUM_SESSIONS}" \
    ParameterKey=loadTestIntervalMs,ParameterValue="${LOAD_TEST_INTERVAL_MS}" \
  --tags "${TAGS}" \
  --region "${REGION}" \
  --capabilities CAPABILITY_NAMED_IAM

aws cloudformation wait stack-create-complete --stack-name $STACK_NAME

echo "New stack $STACK_NAME created successfully!"
