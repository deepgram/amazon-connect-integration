from collections.abc import Mapping
import json
import os
import time
import requests
import logging

INTEGRATION_TAG = "dg_amazonconnect"
LOG_LEVELS = {
    "debug": logging.DEBUG,
    "info": logging.INFO,
    "warning": logging.WARNING,
    "error": logging.ERROR,
}

logger = logging.getLogger()
log_level = LOG_LEVELS[os.getenv("LOG_LEVEL", "info")]
logger.setLevel(log_level)


def handler(event, context):
    logger.info(f"Received event from Amazon Connect: {event}")

    if event["Name"] != "ContactFlowEvent":
        logger.error(f"Unexpected event name: {event['Name']}")
        return lambda_result(False)

    contact_data = event["Details"]["ContactData"]
    contact_attrs = contact_data.get("Attributes")
    contact_id = contact_data["ContactId"]

    dg_params = get_dg_params(contact_attrs, contact_id)
    if dg_params is None:
        logger.error(f"unable to parse `dg_` contact attributes")
        return lambda_result(False)

    kvs_stream_info = contact_data["MediaStreams"]["Customer"]["Audio"]

    integrator_payload = {
        "contactId": contact_id,
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
    logger.info(f"Returning lambda result: {result_text}")

    return {"lambdaResult": result_text}


def get_dg_params(contact_attrs, contact_id):
    """
    Takes in the dictionary of contact attributes and the contact id and returns the DG params. The
    DG params are the query parameters that will ultimately be passed to the Deepgram streaming API.
    They are represented in the contact attributes as attributes whose keys begin with `dg_`. This
    function returns them as a dictionary. See `lambda_function_test.py` for an example.
    """

    if contact_attrs is None:
        contact_attrs = dict()
    if not isinstance(contact_attrs, Mapping):
        logger.error("Expected contact attributes to be dictionary")
        return None

    dg_params = contact_attrs_to_dg_params(contact_attrs)
    if len(dg_params) == 0:
        logger.info(
            "No `dg_` contact attributes were set. You can add some to customize Deepgram."
        )

    if "callback" in dg_params and isinstance(dg_params["callback"], list):
        logger.error("More than one callback provided")
        return None

    inject_contact_id_into_callback(dg_params, contact_id)

    add_integration_tag(dg_params)

    return dg_params


def contact_attrs_to_dg_params(contact_attrs):
    if contact_attrs is None:
        return dict()

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


def inject_contact_id_into_callback(dg_params, contact_id):
    if "callback" in dg_params:
        unsubstituted_callback = dg_params["callback"]

        assert isinstance(unsubstituted_callback, str)

        dg_params["callback"] = unsubstituted_callback.replace(
            "{contact-id}", contact_id
        )


def add_integration_tag(dg_params):
    tags = []
    if "tag" in dg_params:
        preexisting_tags = dg_params["tag"]
        if isinstance(preexisting_tags, str):
            tags.append(preexisting_tags)
        elif isinstance(preexisting_tags, list):
            tags.extend(preexisting_tags)
        else:
            raise Exception(f"Tags list has unexpected type: {type(preexisting_tags)}")

    if INTEGRATION_TAG not in tags:
        tags.append(INTEGRATION_TAG)

    if len(tags) == 1:
        dg_params["tag"] = tags[0]
    else:
        dg_params["tag"] = tags


def start_integrator_session(payload):
    """
    Tells the integrator to begin sending audio from Kinesis Video Streams to Deepgram. Returns
    True if the session was started successfully, False if not.
    """
    logger.info(f"Attempting to start integrator session with payload {payload}")

    integrator_domain = os.getenv("KVS_DG_INTEGRATOR_DOMAIN")
    if not integrator_domain:
        logger.error("Please provide KVS_DG_INTEGRATOR_DOMAIN env variable")
        return False
    logger.info(f"KVS_INTEGRATOR_DOMAIN = {integrator_domain}")

    load_test_num_sessions = os.getenv("LOAD_TEST_NUM_SESSIONS")
    num_sessions = int(load_test_num_sessions) if load_test_num_sessions else 1
    if num_sessions < 1:
        logger.error("LOAD_TEST_NUM_SESSIONS must not be less than 1")
        return False

    load_test_inverval_ms = os.getenv("LOAD_TEST_INTERVAL_MS")
    interval_ms = int(load_test_inverval_ms) if load_test_inverval_ms else 0
    if interval_ms < 0:
        logger.error("LOAD_TEST_INTERVAL_MS must not be negative")
        return False
    interval_secs = interval_ms / 1000

    if num_sessions != 1:
        logger.info(
            f"Running load test with {num_sessions} sessions at a {interval_ms}ms interval"
        )

    for i in range(num_sessions):
        if i != 0:
            time.sleep(interval_secs)

        if not make_integrator_request(
            integrator_domain,
            payload,
        ):
            return False

    return True


def make_integrator_request(integrator_domain, payload):
    url = f"http://{integrator_domain}/start-session"
    headers = {"Content-Type": "application/json"}

    try:
        response = requests.post(url, headers=headers, data=json.dumps(payload))
        if response.status_code == 200:
            logger.info("Successfully started integrator session.")
        else:
            logger.error(
                f"Integrator responded with {response.status_code}: {response.text}"
            )
            return False
    except Exception as err:
        logger.error(f"Error sending request to integrator: {err}")
        return False

    return True
