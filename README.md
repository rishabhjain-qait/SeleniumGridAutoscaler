### Horizontal Pod Scaler for Selenium Grid Kubernetes

This is a small spring boot application which can be used to auto scale the selenium browser pods running inside a kubernetes cluster.

It can give an elasticity to the K8s based selenium grid where the browser nodes can be scaled up/down on demand basis.

To make sure this application does not end up sucking the entire compute power of your cluster, we have a lower and upper cap beyound which the scale will never go.



#### Configurable Properties
```
selenium_grid_host=<Selenium Grid HOST>
k8s_host=<Kubernetes HOST>
chromeDeploymentName=<CHROME DEPLOYMENT NAME>
firefoxDeploymentName=<FIREFOX DEPLOYMENT NAME>
namespace=<NAMESPACE>
gridUrl=http://${selenium_grid_host}
k8s_api_url=https://${k8s_host}/apis/apps/v1/namespaces/default/deployments/${deploymentName}/scale

# This property will control the maximum scale to which the browser pods can be scaled up.
ch_max_scale_limit=2
ff_max_scale_limit=2

# This property will decide the minimum number of browser pods you want to run all the time. 
Recommended value is 1.
ch_min_scale_limit=1
ff_min_scale_limit=1

k8s_token=<Kubernetes Service Account Token>
grid_scale_check_frequency_in_sec=10
grid_daily_cleanup_cron=0 10 10 * * ?
server_port=8088
```

#### Build Commands
```
mvn install -DskipTests=true

docker build -t selenium-grid-k8s-autoscaler:1.0 .
```

if using docker desktop for running k8s use kubernetes.docker.internal:6443 as the host name 
if using rancher desktop for running k8s use kubernetes.default.svc:443 as the host name

### k8s api endpoint for autoscaling logic

#### to get pods list - pod ips and name mapping 
```
curl -k -X GET \
  -H "Authorization: Bearer eyJhbGciOiJSUzI1NiIsImtpZCI6IklOR1I0STV2aHU4enRmdkZZSW5xUmRhdHJhWGpYaGItWnVJZTg4VXo1NG8ifQ.eyJpc3MiOiJrdWJlcm5ldGVzL3NlcnZpY2VhY2NvdW50Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9uYW1lc3BhY2UiOiJkZWZhdWx0Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9zZWNyZXQubmFtZSI6ImF1dG8tc2NhbGUtcm9ib3Qtc2EiLCJrdWJlcm5ldGVzLmlvL3NlcnZpY2VhY2NvdW50L3NlcnZpY2UtYWNjb3VudC5uYW1lIjoiYXV0by1zY2FsZS1yb2JvdC1zYSIsImt1YmVybmV0ZXMuaW8vc2VydmljZWFjY291bnQvc2VydmljZS1hY2NvdW50LnVpZCI6IjNmNWIxYTk4LTRhNDYtNDIzNS05OWE2LWNjYjU5NzBmODhiMiIsInN1YiI6InN5c3RlbTpzZXJ2aWNlYWNjb3VudDpkZWZhdWx0OmF1dG8tc2NhbGUtcm9ib3Qtc2EifQ.G3xbgq7BTfyf1mogsQpmp0Amcywv5DdHm_FfyqFpB-kzkJq3lDsZHpVquHXi041ruAnf5mY0CuyFqo6SI-RqBCF5jT9d2K-vpCOtEXHqtJyev-E8dbyqYLiAoYNYmD5cnhgvkoF70cWzZiKW2u9ryPm28ELSZvEBME1vfs7RvgfLxm4REGGh8jb1NAHUNHbEoCm2nfGke0_78Vi54nNbggmSMqgTLfOYP6OByHTgrqyQXRu56y6Ir2qRvgflfhQmHqR7kOi7wH7XYkjroDmDfLmA2hNmO91-LiSavxucdr6Vj3rn1fZFGd0tZ5gDU__fpkuNSwhGNo0ByGeq0dNAlA" \
  https://kubernetes.docker.internal:6443/api/v1/namespaces/default/pods | jq .
```


#### to update cost of pod
```
curl -k -X PATCH \
  -H "Authorization: Bearer eyJhbGciOiJSUzI1NiIsImtpZCI6IklOR1I0STV2aHU4enRmdkZZSW5xUmRhdHJhWGpYaGItWnVJZTg4VXo1NG8ifQ.eyJpc3MiOiJrdWJlcm5ldGVzL3NlcnZpY2VhY2NvdW50Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9uYW1lc3BhY2UiOiJkZWZhdWx0Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9zZWNyZXQubmFtZSI6ImF1dG8tc2NhbGUtcm9ib3Qtc2EiLCJrdWJlcm5ldGVzLmlvL3NlcnZpY2VhY2NvdW50L3NlcnZpY2UtYWNjb3VudC5uYW1lIjoiYXV0by1zY2FsZS1yb2JvdC1zYSIsImt1YmVybmV0ZXMuaW8vc2VydmljZWFjY291bnQvc2VydmljZS1hY2NvdW50LnVpZCI6IjNmNWIxYTk4LTRhNDYtNDIzNS05OWE2LWNjYjU5NzBmODhiMiIsInN1YiI6InN5c3RlbTpzZXJ2aWNlYWNjb3VudDpkZWZhdWx0OmF1dG8tc2NhbGUtcm9ib3Qtc2EifQ.G3xbgq7BTfyf1mogsQpmp0Amcywv5DdHm_FfyqFpB-kzkJq3lDsZHpVquHXi041ruAnf5mY0CuyFqo6SI-RqBCF5jT9d2K-vpCOtEXHqtJyev-E8dbyqYLiAoYNYmD5cnhgvkoF70cWzZiKW2u9ryPm28ELSZvEBME1vfs7RvgfLxm4REGGh8jb1NAHUNHbEoCm2nfGke0_78Vi54nNbggmSMqgTLfOYP6OByHTgrqyQXRu56y6Ir2qRvgflfhQmHqR7kOi7wH7XYkjroDmDfLmA2hNmO91-LiSavxucdr6Vj3rn1fZFGd0tZ5gDU__fpkuNSwhGNo0ByGeq0dNAlA" \
  -H "Accept: application/json" \
  -H "Content-Type: application/strategic-merge-patch+json" \
  -d '{ "metadata": { "annotations": { "controller.kubernetes.io/pod-deletion-cost": "-1" } } }' \
  https://kubernetes.docker.internal:6443/api/v1/namespaces/default/pods/selenium-node-chrome-8457f569c8-nhgct

```


#### to get scale
```
curl -k -X GET \
  -H "Authorization: Bearer eyJhbGciOiJSUzI1NiIsImtpZCI6IklOR1I0STV2aHU4enRmdkZZSW5xUmRhdHJhWGpYaGItWnVJZTg4VXo1NG8ifQ.eyJpc3MiOiJrdWJlcm5ldGVzL3NlcnZpY2VhY2NvdW50Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9uYW1lc3BhY2UiOiJkZWZhdWx0Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9zZWNyZXQubmFtZSI6ImF1dG8tc2NhbGUtcm9ib3Qtc2EiLCJrdWJlcm5ldGVzLmlvL3NlcnZpY2VhY2NvdW50L3NlcnZpY2UtYWNjb3VudC5uYW1lIjoiYXV0by1zY2FsZS1yb2JvdC1zYSIsImt1YmVybmV0ZXMuaW8vc2VydmljZWFjY291bnQvc2VydmljZS1hY2NvdW50LnVpZCI6IjNmNWIxYTk4LTRhNDYtNDIzNS05OWE2LWNjYjU5NzBmODhiMiIsInN1YiI6InN5c3RlbTpzZXJ2aWNlYWNjb3VudDpkZWZhdWx0OmF1dG8tc2NhbGUtcm9ib3Qtc2EifQ.G3xbgq7BTfyf1mogsQpmp0Amcywv5DdHm_FfyqFpB-kzkJq3lDsZHpVquHXi041ruAnf5mY0CuyFqo6SI-RqBCF5jT9d2K-vpCOtEXHqtJyev-E8dbyqYLiAoYNYmD5cnhgvkoF70cWzZiKW2u9ryPm28ELSZvEBME1vfs7RvgfLxm4REGGh8jb1NAHUNHbEoCm2nfGke0_78Vi54nNbggmSMqgTLfOYP6OByHTgrqyQXRu56y6Ir2qRvgflfhQmHqR7kOi7wH7XYkjroDmDfLmA2hNmO91-LiSavxucdr6Vj3rn1fZFGd0tZ5gDU__fpkuNSwhGNo0ByGeq0dNAlA" \
  https://kubernetes.docker.internal:6443/apis/apps/v1/namespaces/default/deployments/selenium-node-chrome/scale | jq .  

```

#### to update scale
```
curl -k -X PATCH -H "Authorization: Bearer eyJhbGciOiJSUzI1NiIsImtpZCI6IklOR1I0STV2aHU4enRmdkZZSW5xUmRhdHJhWGpYaGItWnVJZTg4VXo1NG8ifQ.eyJpc3MiOiJrdWJlcm5ldGVzL3NlcnZpY2VhY2NvdW50Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9uYW1lc3BhY2UiOiJkZWZhdWx0Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9zZWNyZXQubmFtZSI6ImF1dG8tc2NhbGUtcm9ib3Qtc2EiLCJrdWJlcm5ldGVzLmlvL3NlcnZpY2VhY2NvdW50L3NlcnZpY2UtYWNjb3VudC5uYW1lIjoiYXV0by1zY2FsZS1yb2JvdC1zYSIsImt1YmVybmV0ZXMuaW8vc2VydmljZWFjY291bnQvc2VydmljZS1hY2NvdW50LnVpZCI6ImQ3NDdlZjZkLWMwOWQtNGY4YS05NWU5LTY1MGUyNzQ3NzAwZSIsInN1YiI6InN5c3RlbTpzZXJ2aWNlYWNjb3VudDpkZWZhdWx0OmF1dG8tc2NhbGUtcm9ib3Qtc2EifQ.km9nvwBhncw5JNOxG8GKnRg7YEOi_hC81YWBv6y00tban4CnVUoV-7zALr9SNBeAA1z10emhvaKFsUbIjP8BA78R9P0s9CNDSZyU1bSZvzvJPNkFGMkp5iyIz5kSmbTzV4u2WDP18Yt2DnmvG2z_TbM6ewUR8t_Rz0pgpjs7Wja2PGzb7K-nW_AY8UoOa99DA4aV0c_3Pymo1HsoRVDXcrnJifuEvgqGYqRIHAYdB7Bg3qXRplAG3PVd5VWMM103Uttgp7AnH7lS-GJyOVFktRBPL-fKgRvBTYkVz9BFmOeLM33wQ_l63-P1hOX_FhhQTKZ83O69xpv36X58Yzr8Jw" -H "Accept: application/json" -H "Content-Type: application/strategic-merge-patch+json" -d '{ "spec": { "replicas": 1 } }' https://kubernetes.docker.internal:6443/apis/apps/v1/namespaces/default/deployments/selenium-node-chrome/scale
```
