#!/usr/bin/env bash
set -euo pipefail

awslocal s3 mb "s3://${S3_BUCKET}" || true

awslocal s3api put-bucket-cors \
  --bucket "${S3_BUCKET}" \
  --cors-configuration "{
    \"CORSRules\": [
      {
        \"AllowedOrigins\": [\"${FRONTEND_ORIGIN}\", \"${FRONTEND_ORIGIN_ALT}\"],
        \"AllowedMethods\": [\"PUT\", \"GET\", \"HEAD\"],
        \"AllowedHeaders\": [\"*\"],
        \"ExposeHeaders\": [\"ETag\"],
        \"MaxAgeSeconds\": 3000
      }
    ]
  }"

echo "LocalStack init: ${S3_BUCKET} ready (with CORS)."
