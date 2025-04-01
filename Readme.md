
# How to run?

# Environment Setup

Download and Install Docker: [Docker Desktop](https://www.docker.com/products/docker-desktop/ "Docker Website")  (WSL is required)

Get OPA (Open Policy Agent): `docker pull openpolicyagent/opa`  

Run OPA: `docker run -it -p 8181:8181 openpolicyagent/opa run --server --addr :8181`  

If you run `docker ps` command you will see OPA is now running on port 8181.  

[More Information on OPA](https://www.openpolicyagent.org/docs/latest/deployments/)  

Get Keycloak: `docker pull keycloak/keycloak`  

Run Keycloak: `docker run -p 8080:8080 -e KC_BOOTSTRAP_ADMIN_USERNAME=admin -e KC_BOOTSTRAP_ADMIN_PASSWORD=admin quay.io/keycloak/keycloak:26.1.4 start-dev`  

Now again if you run `docker ps` you will see both OPA and Keycloak running on port 8181 and 8080 respectively.

[Setup Keycloak](https://www.keycloak.org/getting-started/getting-started-docker)

# Before Running Project

Create an `.env` File in the root directory of the project. and set values according to your environment.

You can find the required variables in `src/main/resources/application.properties` file.  