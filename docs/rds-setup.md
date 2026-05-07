# RDS 생성 및 스키마 준비

## 목표

Kubernetes 배포 전에 운영 DB를 먼저 준비한다.

- MySQL RDS: `account_db`, `master_db`, `stock_db`

이번 1차 배포에서는 `ai-service`를 제외한다. 따라서 PostgreSQL RDS, pgvector, AI용 read-only MySQL 계정은 만들지 않는다.

`common`은 실행 서비스가 아니라 GitHub Packages로 배포되는 라이브러리이므로 별도 RDS 스키마를 만들지 않는다.

## RDS 인스턴스 구성

권장 시작 구성은 다음과 같다.

| 용도 | Engine | Identifier | 비고 |
| --- | --- | --- | --- |
| WMS 서비스 DB | MySQL 8.0 | `wbs-mysql` | `account/master/stock` 스키마를 한 인스턴스에 생성 |

초기 비용을 줄이려면 `db.t4g.micro`, 20GB gp3, Single-AZ로 시작한다. 운영 안정성이 더 중요해지면 Multi-AZ와 인스턴스 크기를 올린다.

보안그룹은 EKS worker node 또는 Pod가 사용하는 보안그룹에서만 접근 가능하게 연다.

- MySQL: TCP 3306

외부 공개 접속은 기본적으로 끈다.

## 생성 명령 템플릿

템플릿 파일:

- `infra/rds/create-rds.example.sh`

현재 조회된 기본 VPC 기준으로 템플릿에는 아래 값이 들어가 있다.

- VPC: `vpc-0771eef2390f29f65`
- VPC CIDR: `172.31.0.0/16`
- Subnets:
  - `subnet-0c30a4346059a82b0`
  - `subnet-05e716b5dc1a79136`
  - `subnet-03e4b914aa99deb0d`
  - `subnet-0a4d3a51b9eaf43ac`
- DB Subnet Group: `wbs-db-subnet-group`
- Security Group: `wbs-rds-sg`

필요 시 engine version, instance class, VPC/Subnet 값을 바꾼다.

RDS master password는 직접 파일에 적지 않고 `--manage-master-user-password`로 AWS Secrets Manager가 관리하게 한다.

실행 전 현재 AWS 계정/리전을 반드시 확인한다.

```bash
aws sts get-caller-identity
aws configure get region
```

실행 예:

```bash
cp infra/rds/create-rds.example.sh /tmp/create-wbs-rds.sh
chmod +x /tmp/create-wbs-rds.sh
/tmp/create-wbs-rds.sh
```

## MySQL 스키마 생성

RDS 생성 후 MySQL endpoint를 확인한다.

```bash
aws rds describe-db-instances \
  --region ap-northeast-2 \
  --db-instance-identifier wbs-mysql \
  --query 'DBInstances[0].Endpoint.Address' \
  --output text
```

`infra/rds/mysql-init.sql`에서 `CHANGE_ME_*` 비밀번호를 실제 값으로 바꾼 뒤 실행한다.

RDS master password는 생성된 Secrets Manager secret에서 확인한다. Secret ARN은 아래 명령으로 확인한다.

```bash
aws rds describe-db-instances \
  --region ap-northeast-2 \
  --db-instance-identifier wbs-mysql \
  --query 'DBInstances[0].MasterUserSecret.SecretArn' \
  --output text
```

```bash
mysql -h <MYSQL_RDS_ENDPOINT> -P 3306 -u admin -p < infra/rds/mysql-init.sql
```

생성되는 항목:

- `account_db`
- `master_db`
- `stock_db`
- `wbs_account`
- `wbs_master`
- `wbs_stock`

## Kubernetes 배포 파일 반영

RDS endpoint가 나오면 각 서비스의 Kubernetes Deployment 파일에 직접 반영한다.

- `account/k8s/depl_svc.yml`: `ACCOUNT_DB_URL`
- `master/k8s/depl_svc.yml`: `MASTER_DB_URL`
- `stock/k8s/depl_svc.yml`: `STOCK_DB_URL`

비밀번호는 파일에 적지 않고 Kubernetes Secret `wbs-secrets`에 넣는다.

## 확인

MySQL:

```sql
SHOW DATABASES;
SHOW GRANTS FOR 'wbs_account'@'%';
SHOW GRANTS FOR 'wbs_master'@'%';
SHOW GRANTS FOR 'wbs_stock'@'%';
```

서비스 배포 전에는 운영 프로필이 `ddl-auto: validate`이므로 테이블 스키마가 없으면 애플리케이션이 뜨지 않는다. 첫 운영 배포 전에 스키마 생성 전략을 별도로 확정해야 한다.
