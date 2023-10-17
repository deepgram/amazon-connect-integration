from collections.abc import Mapping
import json
import os
import time
import boto3
import requests


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

        See `lambda_function_test.py` for an example.o work with async programming like in Rust's tokio::time::interval.tick(), an equivalent can be using asyncio.sleep() which is a coroutine that completes after a given delay.

    Here is an equivalent async
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

    integrator_lambda = os.getenv("KVS_DG_INTEGRATOR_LAMBDA")
    if integrator_lambda:
        # Running integrator as lambda for testing purposes
        return start_lambda_integrator_session(integrator_lambda, payload)

    integrator_domain = os.getenv("KVS_DG_INTEGRATOR_DOMAIN")
    if not integrator_domain:
        print("ERROR: please provide KVS_DG_INTEGRATOR_DOMAIN env variable")
        return False

    load_test_num_sessions = os.getenv("LOAD_TEST_NUM_SESSIONS")
    num_sessions = int(load_test_num_sessions) if load_test_num_sessions else 1
    if num_sessions < 1:
        print("LOAD_TEST_NUM_SESSIONS must not be less than 1")
        return False

    load_test_inverval_ms = os.getenv("LOAD_TEST_INTERVAL_MS")
    interval_ms = int(load_test_inverval_ms) if load_test_inverval_ms else 0
    if interval_ms < 0:
        print("LOAD_TEST_INTERVAL_MS must not be negative")
        return False
    interval_secs = interval_ms / 1000

    if num_sessions != 1:
        print(
            f"Running load test with {num_sessions} sessions at a {interval_ms}ms interval"
        )

    for i in range(num_sessions):
        if i != 0:
            time.sleep(interval_secs)

        if not start_fargate_integrator_session(
            integrator_domain,
            payload,
        ):
            return False

    return True


def start_lambda_integrator_session(lambda_function_name, payload):
    lambda_client = boto3.client("lambda")
    response = lambda_client.invoke(
        FunctionName=lambda_function_name,
        InvocationType="Event",
        Payload=json.dumps(payload),
    )
    if response["StatusCode"] == 202:
        return True
    else:
        print(f"ERROR: Got error response from integrator lambda: {response}")
        return False


def start_fargate_integrator_session(integrator_domain, payload):
    print(f"Hitting integrator at domain: {integrator_domain}")

    url = f"http://{integrator_domain}/start-session"
    headers = {"Content-Type": "application/json"}

    try:
        response = requests.post(url, headers=headers, data=json.dumps(payload))
        if response.status_code == 200:
            print("Successfully started integrator session.")
        else:
            print(
                f"ERROR: Integrator responded with {response.status_code}: {response.text}"
            )
            return False
    except Exception as err:
        print(f"Error sending request to integrator: {err}")
        return False

    return True
