# Development

## Hosting Operator Catalog Archive Locally

```shell script
# 'Z' is required on SELinux machines
podman run --rm -it --name operator-catalog -p 8090:80 -v "${PWD}/operator-utils-api-service/src/test/resources":/usr/local/apache2/htdocs/:Z httpd:latest
```