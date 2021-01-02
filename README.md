# API idempotence

The service provides these API methods:

* Create order for a user (the single modification method)
* List user orders
* Get fingerprint of user orders

To create an order the caller must provide a 'fingerprint', which needs to be fetched by a prior call to either of the
methods: 'get fingerprint' or 'list user orders'.

If the there are no prior orders, the caller may skip specifying the fingerprint.

Application detects the fingerprint for the 'no prior orders' case and handles the 'create order' request
the same way as if no fingerprint was specified.

# Deployment

Deploying:

```shell
git clone https://github.com/audintsev/otus-architect-api-idempotence.git
cd otus-architect-api-idempotence

kubectl create ns udintsev
helm install -n udintsev hw18 ./chart
```

Undeploying:

```shell
helm uninstall -n udintsev hw18
for pvc in $(kubectl get pvc -n udintsev -o jsonpath='{.items[*].metadata.name}'); do kubectl delete -n udintsev pvc $pvc; done
kubectl delete ns udintsev
```

# Invoking the postman collection

Run from the root of the cloned repo:
```shell
newman run postman_collection.json 
```


# Building and pushing

```shell
./gradlew bootBuildImage --imageName=udintsev/hw18:latest
docker push udintsev/hw18:latest
```

Deploying and running tests on my dev env:
```shell
helm install hw18 ./chart --set "ingress.host=arch.labs"
newman run --env-var "baseUrl=http://arch.labs" postman_collection.json
```
