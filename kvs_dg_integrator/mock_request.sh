#!/bin/bash

set -euo pipefail

curl "http://localhost/start-session" -d @mock_integrator_args.json
