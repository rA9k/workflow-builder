
# How to run?

# Environment Setup

Download and Install Docker: [Docker Desktop](https://www.docker.com/products/docker-desktop/ "Docker Website")  (WSL is required)

Get OPA (Open Policy Agent): `docker pull openpolicyagent/opa`  

Run OPA: `docker run -it -p 8181:8181 openpolicyagent/opa run --server --addr :8181`  

If you run `docker ps` command you will see OPA is now running on port 8181.  

[More Information on OPA](https://www.openpolicyagent.org/docs/latest/deployments/)  

Get Keycloak: `docker pull keycloak/keycloak`  

Run Keycloak: `docker run -p 8080:8080 -e KC_BOOTSTRAP_ADMIN_USERNAME=admin -e KC_BOOTSTRAP_ADMIN_PASSWORD=admin quay.io/keycloak/keycloak:26.1.4 start-dev`  

Now again if you run `docker ps` you will see both Keycloak and OPA are running on port 8080 and 8181 respectively.  

[Setup Keycloak](https://www.keycloak.org/getting-started/getting-started-docker)

After Creating a Client in Keycloak, enable Client Authentication under Capability Config. Add http://localhost:8081/* in the Valid redirect URIs field and http://localhost:8081 in the Web origins field under Access settings, then under Logout settings disable Front channel logout and add http://localhost:8081/logout/back-channel/keycloak in the Backchannel logout URL (Change Ports according to your setup).  

You can find the Credentials tab on the top which will get you Client Secret, that you need to set in the `.env` file.  

Now go to Client Scopes from left side navigation menu find and open roles, click on Mappers tab then select realm roles from the list scroll down and enable Add to ID token toggle. Make sure Multivalued and Add to access token are also enabled and Claim JSON Type is set to String. this will add the user's roles to the ID token.  

Finally go to Clients tab from left side navigation menu, open your client and click Client Scopes tab that is on the top, find roles and make sure its Assigned type set to Default and not Optional.  

# Creating the .env File

Create an `.env` File in the root directory of the project. and set values according to your environment.

KEYCLOAK_CLIENT=KEYCLOAK_CLIENT_ID  
KEYCLOAK_CLIENT_SECRET=KEYCLOAK_CLIENT_SECRET  
KEYCLOAK_REALM=KEYCLOAK_REALM_NAME  

KEYCLOAK_BASE_URL=http://localhost:8080  
OPA_URL=http://localhost:8181  

POSTGRESQL_HOST=jdbc:postgresql://localhost:5432/DB_NAME  
POSTGRESQL_USER=PGADMIN_USERNAME  
POSTGRESQL_PASSWORD=PGADMIN_PASSWORD  

These variables are used in `src/main/resources/application.properties` and `src/main/java/com/example/workflow/config/SecurityConfig.java` file.  