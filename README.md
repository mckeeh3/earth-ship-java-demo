# EarthShip

A Kalix Java demo of a web app with a full earth map UI used to submit numerous order that are prepared for shipping. Submitted orders are automatically allocated available stock, or if not stock is available, the orders are placed into a back ordered state. As new stock is added to the system, back ordered order items are automatically allocated.

The full earth map UI is used to create bulk random order generators. The order generators are created at a specific location on the map. Generators are defined by location, radius, number of orders to be created within the circular map location, and the order generation rate.

To create an order generator, zoom into any location on the map, then hit the 'g' key. A circular area appears on the map. Move the mouse right or left to increase or decrease the size of the circle. Click the left mouse button to set the circle size. Next, move the mouse right or left to incerease or decrease the number of orders to be created. Click the left mouse button to select the number. Next move the mouse to select the order generation rate. Click again, and the generator is created.

To understand the Kalix concepts that are the basis for this example, see [Designing services](https://docs.kalix.io/services/development-process.html) in the documentation.



This project contains the framework to create a Kalix application by adding Kalix components. To understand more about these components, see [Developing services](https://docs.kalix.io/services/). Spring-SDK is an experimental feature and so far there is no [official](https://docs.kalix.io/) documentation. Examples can be found [here](https://github.com/lightbend/kalix-jvm-sdk/tree/main/samples) in the folders with "spring" in their name.



Use Maven to build your project:

```shell
mvn compile
```



To run the example locally, you must run the Kalix proxy. The included `docker-compose` file contains the configuration required to run the proxy for a locally running application.
It also contains the configuration to start a local Google Pub/Sub emulator that the Kalix proxy will connect to.
To start the proxy, run the following command from this directory:

```shell
docker-compose up
```

To start the application locally, the `exec-maven-plugin` is used. Use the following command:

```shell
mvn spring-boot:run
```

With both the proxy and your application running, once you have defined endpoints they should be available at `http://localhost:9000`. 



To deploy your service, install the `kalix` CLI as documented in
[Setting up a local development environment](https://docs.kalix.io/setting-up/)
and configure a Docker Registry to upload your docker image to.

You will need to update the `dockerImage` property in the `pom.xml` and refer to
[Configuring registries](https://docs.kalix.io/projects/container-registries.html)
for more information on how to make your docker image available to Kalix.

Finally, you can use the [Kalix Console](https://console.kalix.io)
to create a project and then deploy your service into the project either by using `mvn deploy` which
will also conveniently package and publish your docker image prior to deployment, or by first packaging and
publishing the docker image through `mvn clean package docker:push -DskipTests` and then deploying the image
through the `kalix` CLI.
