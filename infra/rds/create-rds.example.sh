#!/usr/bin/env bash
set -euo pipefail

# Example only. Copy this file, replace CHANGE_ME values, then run intentionally.

AWS_REGION="ap-northeast-2"
VPC_ID="vpc-0771eef2390f29f65"
VPC_CIDR="172.31.0.0/16"
DB_SUBNET_GROUP_NAME="wbs-db-subnet-group"
DB_SECURITY_GROUP_NAME="wbs-rds-sg"

MYSQL_MASTER_USERNAME="admin"

DB_SECURITY_GROUP_ID="$(
  aws ec2 describe-security-groups \
    --region "$AWS_REGION" \
    --filters "Name=group-name,Values=$DB_SECURITY_GROUP_NAME" "Name=vpc-id,Values=$VPC_ID" \
    --query 'SecurityGroups[0].GroupId' \
    --output text \
    --no-cli-pager
)"

if [ "$DB_SECURITY_GROUP_ID" = "None" ]; then
  DB_SECURITY_GROUP_ID="$(
    aws ec2 create-security-group \
      --region "$AWS_REGION" \
      --group-name "$DB_SECURITY_GROUP_NAME" \
      --description "WBS MySQL RDS access from VPC" \
      --vpc-id "$VPC_ID" \
      --query 'GroupId' \
      --output text \
      --no-cli-pager
  )"

  aws ec2 authorize-security-group-ingress \
    --region "$AWS_REGION" \
    --group-id "$DB_SECURITY_GROUP_ID" \
    --protocol tcp \
    --port 3306 \
    --cidr "$VPC_CIDR" \
    --no-cli-pager
fi

aws rds create-db-subnet-group \
  --region "$AWS_REGION" \
  --db-subnet-group-name "$DB_SUBNET_GROUP_NAME" \
  --db-subnet-group-description "WBS MySQL RDS subnet group" \
  --subnet-ids \
    subnet-0c30a4346059a82b0 \
    subnet-05e716b5dc1a79136 \
    subnet-03e4b914aa99deb0d \
    subnet-0a4d3a51b9eaf43ac \
  --no-cli-pager || true

aws rds create-db-instance \
  --region "$AWS_REGION" \
  --db-instance-identifier wbs-mysql \
  --engine mysql \
  --engine-version 8.0.42 \
  --db-instance-class db.t4g.micro \
  --allocated-storage 20 \
  --storage-type gp3 \
  --master-username "$MYSQL_MASTER_USERNAME" \
  --manage-master-user-password \
  --db-subnet-group-name "$DB_SUBNET_GROUP_NAME" \
  --vpc-security-group-ids "$DB_SECURITY_GROUP_ID" \
  --backup-retention-period 7 \
  --storage-encrypted \
  --no-publicly-accessible \
  --no-cli-pager

aws rds wait db-instance-available \
  --region "$AWS_REGION" \
  --db-instance-identifier wbs-mysql

aws rds describe-db-instances \
  --region "$AWS_REGION" \
  --db-instance-identifier wbs-mysql \
  --query 'DBInstances[0].Endpoint.Address' \
  --output text
