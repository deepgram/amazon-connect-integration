from collections.abc import Mapping
import json
import os
import boto3


def handler(event, context):
    print(f"Received event from Amazon Connect: {event}")

    if event["Name"] != "ContactFlowEvent":
        print(f"ERROR: Unexpected event name: {event['Name']}")
        return lambda_result(False)

    contact_data = event["Details"]["ContactData"]
    contact_attrs = contact_data.get("Attributes")

    dg_params = get_dg_params(contact_attrs)
    if dg_params is None:
        print(f"ERROR: unable to parse `dg_` contact attributes")
        return lambda_result(False)
    if len(dg_params) == 0:
        print(
            "No `dg_` contact attributes were set. You can add some to customize Deepgram."
        )

    kvs_stream_info = contact_data["MediaStreams"]["Customer"]["Audio"]

    integrator_payload = {
        "contactId": contact_data["ContactId"],
        "kvsStream": {
            "arn": kvs_stream_info["StreamARN"],
            "startFragmentNumber": kvs_stream_info["StartFragmentNumber"],
        },
        "dgParams": dg_params,
    }
    is_success = start_integrator_session(integrator_payload)

    return lambda_result(is_success)


def lambda_result(is_success):
    result_text = "success" if is_success else "fail"
    print(f"Returning lambda result: {result_text}")

    return {"lambdaResult": result_text}


def get_dg_params(contact_attrs):
    """
    Takes in the dictionary of contact attributes and returns the DG params. The DG params are the
    query parameters that will ultimately be passed to the Deepgram streaming API. They are
    represented in the contact attributes as attributes whose keys begin with `dg_`. This function
    returns them as a dictionary.

    See `lambda_function_test.py` for an example.

    If `contact_attrs` are formatted incorrectly, returns None.
    """

    if contact_attrs is None:
        return dict()
    if not isinstance(contact_attrs, Mapping):
        print("ERROR: expected contact attributes to be dictionary")
        return None

    dg_params = dict()

    for attr_key in contact_attrs:
        if not attr_key.startswith("dg_"):
            continue

        attr_value = contact_attrs[attr_key].strip()

        dg_param_key = attr_key[3:]
        dg_param_values = []

        current_dg_value_chars = []
        prev_char_was_unescaped_slash = False
        for char in attr_value:
            if prev_char_was_unescaped_slash and (char == " " or char == "\\"):
                # overwrite the \ char at the end of the value, because it was just an escape
                # for this one
                current_dg_value_chars[-1] = char

                prev_char_was_unescaped_slash = False
            else:
                if char == " ":
                    finished_value = "".join(current_dg_value_chars)
                    dg_param_values.append(finished_value)
                    current_dg_value_chars = []
                else:
                    current_dg_value_chars.append(char)

                prev_char_was_unescaped_slash = char == "\\"

        last_finished_value = "".join(current_dg_value_chars)
        dg_param_values.append(last_finished_value)

        if len(dg_param_values) == 1:
            dg_params[dg_param_key] = dg_param_values[0]
        else:
            dg_params[dg_param_key] = dg_param_values

    return dg_params


def start_integrator_session(payload):
    """
    Tells the integrator to begin sending audio from Kinesis Video Streams to Deepgram. Returns
    True if the session was started successfully, False if not.
    """
    print(f"Attempting to start integrator session with payload {payload}")

    kvs_dg_integrator = os.getenv("KVS_DG_INTEGRATOR")

    if not kvs_dg_integrator:
        print("ERROR: Please set the KVS_DG_INTEGRATOR environment variable")
        return False

    lambda_client = boto3.client("lambda")
    response = lambda_client.invoke(
        FunctionName=kvs_dg_integrator,
        InvocationType="Event",
        Payload=json.dumps(payload),
    )
    if response["StatusCode"] == 202:
        return True
    else:
        print(f"ERROR: Got error response from integrator: {response}")
        return False
