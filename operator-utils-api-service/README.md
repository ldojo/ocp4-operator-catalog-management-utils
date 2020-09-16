# Openshift 4.x Operator Catalog Utilities API Service

This project contains a Spring Boot API service which provides some useful endpoints for managing tar.gz Operator package manifest archives, as described in [Openshift 4.x Operator Catalog Management project](https://github.com/ldojo/ocp4-operator-catalog-management). 

To build/launch the api service with Podman or Docker:

```
podman build -t <image> .
podman run --rm -it -p 8080:8080 <image>
```

Launched on port 8080, you can go to http://host:8080/swagger-ui.html#/operator-catalog-util-apis and try out the apis via the Swagger interface

The apis provide useful utilities for mirroring, listing images referenced in the catalog, searching for Operator Details based on image, etc.., by processing the Operator package manifest tar.gz archives as described in the [Openshift 4.x Operator Catalog Management project](https://github.com/ldojo/ocp4-operator-catalog-management/blob/master/README.md#day-1). 

Here are some examples using curl. Note that you can try these same apis right in the Swagger UI in your Browser by going to http://host:8080/swagger-ui.html#/operator-catalog-util-apis

#### List registries in package manifest tar.gz

```
curl -X POST "http://localhost:8080/listRegistriesInCatalogManifests" -H "accept: */*" -H "Content-Type: multipart/form-data" -F "file=@operator-catalog-dev-1.0.0.tar.gz;type=application/gzip"
[
  "docker.io",
  "quay.io",
  "registry.access.redhat.com",
  "registry.connect.redhat.com",
  "registry.redhat.io"
]
```

Getting a unique list of registries that images in the package manifest Yaml reference can be useful for you to make sure that you know which regiestries you might need Podman/Docker credentials for before running any mirror commands

#### List images in package manifest tar.gz

```
curl -X POST "http://localhost:8080/listAllImagesInCatalogManifests" -H "accept: */*" -H "Content-Type: multipart/form-data" -F "file=@operator-catalog-dev-1.0.3.tar.gz;type=application/gzip"
```

Returns a unique list of image references across all of the CRD/CSV Yaml files in the tar.gz archive. Useful for knowing which images you might need to mirror for your environment

#### Create ImageContentSourcePolicy for mirroring images in package manifest tar.gz
```
curl -X POST "http://localhost:8080/createImageContentSourcePolicy?mirrorUrl=my.registry.com&name=my-registry-mirror" -H "accept: */*" -H "Content-Type: multipart/form-data" -F "file=@operator-catalog-dev-1.0.3.tar.gz;type=application/gzip"

apiVersion: operator.openshift.io/v1alpha1
kind: ImageContentSourcePolicy
metadata: {name: my-registry-mirror}
spec:
  repositoryDigestMirrors:
  - mirrors: [my.registry.com/couchbase/operator]
    source: docker.io/couchbase/operator
  - mirrors: [my.registry.com/amq7/amq-streams-cluster-operator]
    source: registry.access.redhat.com/amq7/amq-streams-cluster-operator
```

This command is analogous to ImageContentSourcePolicy produced by the "oc adm catalog mirror" command, as [described in the OCP 4 docs](https://docs.openshift.com/container-platform/4.3/operators/olm-restricted-networks.html#olm-restricted-networks-operatorhub_olm-restricted-networks)

#### Create input to "oc image mirror"
```
curl -X POST "http://localhost:8080/mirrorImagesInCatalogManifests?mirrorUrl=my.registry.com" -H "accept: */*" -H "Content-Type: multipart/form-data" -F "file=@operator-catalog-dev-1.0.3.tar.gz;type=application/gzip"

docker.io/couchbase/operator:1.0.0=my.registry.com/couchbase/operator:1.0.0
docker.io/couchbase/operator:1.1.0=my.registry.com/couchbase/operator:1.1.0
...
```
This command is analogous to the mappint.txt file produced by the "oc adm catalog mirror command, as [described in the OCP 4 docs](https://docs.openshift.com/container-platform/4.3/operators/olm-restricted-networks.html#olm-restricted-networks-operatorhub_olm-restricted-networks)

NOTE: at the time of this writing, there are some [know issues with this "oc adm catalog mirror" and mapping.txt](https://bugzilla.redhat.com/show_bug.cgi?id=1800674). It may be safer to use the skopeo command, and run something like `skopeo copy --all <src> <dst>` instead

#### Apply mirror directly to the tar.gz Yaml files
If you create a simple JSON mirrors map file with a list of registry mirrors you want to apply
```
cat mirrors.json
{
  "docker.io": "my.registry.com",
  "quay.io": "my.registry.com"
}
```

run this api, passing the mirrors json map file, and a tar.gz package manifest file.
```
curl -X POST "http://localhost:8080/applyImageMirrors" -H "accept: */*" -H "Content-Type: multipart/form-data" -F "json-mirrors-file=@mirrors.json;type=application/json" -F "package-manifest-file=@operator-catalog-dev-1.0.3.tar.gz;type=application/gzip" > operator-catalog-dev-mirrored-1.0.3.tar.gz 
```
The result `operator-catalog-dev-mirrored-1.0.3.tar.gz` file will have the same content as the input `operator-catalog-dev-1.0.3.tar.gz`, but with the mirrors applied to all of the image references in the Yaml. 


