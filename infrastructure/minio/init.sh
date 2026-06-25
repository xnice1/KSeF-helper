#!/bin/sh
set -eu

case "$S3_BUCKET" in
  ""|*[!A-Za-z0-9._-]*)
    echo "Invalid S3 bucket name." >&2
    exit 1
    ;;
esac

mc alias set staging http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD"
mc mb --ignore-existing "staging/$S3_BUCKET"
mc version enable "staging/$S3_BUCKET"

cat > /tmp/ksef-helper-policy.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:GetBucketLocation",
        "s3:ListBucket",
        "s3:ListBucketVersions"
      ],
      "Resource": ["arn:aws:s3:::$S3_BUCKET"]
    },
    {
      "Effect": "Allow",
      "Action": [
        "s3:DeleteObject",
        "s3:DeleteObjectVersion",
        "s3:GetObject",
        "s3:GetObjectVersion",
        "s3:PutObject"
      ],
      "Resource": ["arn:aws:s3:::$S3_BUCKET/*"]
    }
  ]
}
EOF

mc admin user add staging "$S3_ACCESS_KEY" "$S3_SECRET_KEY"
mc admin policy create staging ksef-helper-app /tmp/ksef-helper-policy.json
mc admin policy attach staging ksef-helper-app --user "$S3_ACCESS_KEY"
