# WBS Deployment Architecture Diagram

```text
                                      +-----------------------------+
                                      |       GitHub Repository      |
                                      |  source / workflow / common  |
                                      +--------------+--------------+
                                                     |
                                                     | push main / manual run
                                                     v
                                      +-----------------------------+
                                      |        GitHub Actions        |
                                      | deploy-with-msa-k8s.yml      |
                                      | changed module deploy        |
                                      +-------+---------------+-----+
                                              |               |
                          publish common.jar  |               | docker build / push
                                              v               v
                              +--------------------+   +-----------------------------+
                              |  GitHub Packages   |   |           AWS ECR           |
                              |  common library    |   |  wbs/* service images      |
                              +--------------------+   +--------------+--------------+
                                                                  |
                                                                  | image pull
                                                                  v
+------------------+       +--------------------+       +-----------------------------+
|      User        |       | Frontend Hosting   |       |          AWS EKS            |
| Browser          +------>+ www.wbs.asia       |       | wbs-cluster / wbs-ns        |
+------------------+       | Route53            |       |                             |
                           | CloudFront         |       |  +-----------------------+  |
                           | S3 React build     |       |  | Ingress Controller    |  |
                           +---------+----------+       |  | nginx                 |  |
                                     |                  |  +-----------+-----------+  |
                                     | API request       |              |
                                     | server.wbs.asia   |              v
                                     v                  |  +-----------------------+  |
                           +--------------------+       |  | Ingress               |  |
                           | Backend DNS        |       |  | wbs-ingress           |  |
                           | Route53            |       |  +-----------+-----------+  |
                           +---------+----------+       |              |
                                     |                  |              v
                                     v                  |  +-----------------------+  |
                           +--------------------+       |  | API Gateway           |  |
                           | AWS Load Balancer  +------>+  | apigateway-service    |  |
                           | 80 / 443           |       |  | JWT / CORS / Routing  |  |
                           +--------------------+       |  +---+---+---+---+---+---+  |
                                                        |      |   |   |   |   |      |
                                                        |      |   |   |   |   |      |
                                                        |      v   v   v   v   v      |
                                                        | +------+ +------+ +------+  |
                                                        | |account| |master| |stock |  |
                                                        | +---+--+ +---+--+ +---+--+  |
                                                        |     |        |        |      |
                                                        |     |        |        |      |
                                                        | +---v--------v--------v---+  |
                                                        | |        RDS MySQL        |  |
                                                        | | account/master/stock DB |  |
                                                        | +-------------------------+  |
                                                        |                             |
                                                        | +------+ +--------------+   |
                                                        | |search| | ai-service   |   |
                                                        | +---+--+ | RAG / query  |   |
                                                        |     |    +------+-------+   |
                                                        |     |           |           |
                                                        |     |           v           |
                                                        |     |    +--------------+   |
                                                        |     |    | RDS PostgreSQL|   |
                                                        |     |    | ai_db/vector  |   |
                                                        |     |    +--------------+   |
                                                        |     |                       |
                                                        |     v                       |
                                                        | +-------------------------+ |
                                                        | | Elasticsearch + EBS PVC | |
                                                        | | search read model       | |
                                                        | +-------------------------+ |
                                                        |                             |
                                                        | +----------+ +------------+ |
                                                        | | Redis    | | Kafka      | |
                                                        | | cache    | | events     | |
                                                        | +----------+ +------------+ |
                                                        |                             |
                                                        | +-------------------------+ |
                                                        | | K8s Secret              | |
                                                        | | wbs-secrets             | |
                                                        | | DB/JWT/OpenAI/AWS/Mail  | |
                                                        | +-------------------------+ |
                                                        +-----------------------------+
```

## Connection Summary

```text
Frontend -> server.wbs.asia -> Load Balancer -> Ingress -> API Gateway

API Gateway -> account-service
API Gateway -> master-service
API Gateway -> stock-service
API Gateway -> search-service
API Gateway -> ai-service

account/master/stock -> RDS MySQL
ai-service -> RDS PostgreSQL
ai-service -> RDS MySQL stock_db readonly

stock/search -> Elasticsearch
services -> Redis
services -> Kafka

GitHub Actions -> GitHub Packages
GitHub Actions -> AWS ECR
GitHub Actions -> EKS kubectl apply / rollout
```
