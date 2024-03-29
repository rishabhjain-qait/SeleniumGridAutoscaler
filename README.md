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

# This property will decide the minimum number of browser pods you want to run all the time. Recommended value is 1.
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
