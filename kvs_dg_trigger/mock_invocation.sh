#!/bin/bash

set -euo pipefail

curl "http://localhost:9000/2015-03-31/functions/function/invocations" -d @mock_invocation.json
