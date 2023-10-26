#!/bin/bash

set -euo pipefail

curl "http://localhost:5555/start-session" -d @mock_integrator_args.json
