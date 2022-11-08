# Spring Boot - Tapestry integration

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/ch.baurs/spring-boot-tapestry-integration/badge.svg?subject=Maven%20Central)](https://maven-badges.herokuapp.com/maven-central/ch.baurs/spring-boot-tapestry-integration/)
[![License](https://img.shields.io/github/license/sniffertine/spring-boot-tapestry-integration.svg?color=blue&label=License)]()

A simple integration module for Tapestry 5.7.x into Spring Boot

Originally copied from <https://github.com/code8/tapestry-boot> and refactored.

To use `spring-boot-tapestry-integration`:
1. create a `@SpringBootApplication` class
2. configure the Tapestry application through the Spring Boot environment (application.yml & friends)

All configuration properties are exposed to the Tapestry application:

```yaml
tapestry:
  app-package: my.app
  execution-mode: dev
  # enables class reloading during development
  production-mode: false
  # modules to load if execution-mode is `dev`
  dev-modules: my.app.services.DevModule
```

This configuration expects the main module under `my.app.services.AppModule`. Here's how the
application/filter name can be customized:

```yaml
spring:
  tapestry:
    filter-name: 'example' # => my.app.services.ExampleModule
```

## Usage

1. Add the dependency (use the newest version from Maven Central)

            <dependency>
                <groupId>ch.baurs</groupId>
                <artifactId>spring-boot-tapestry-integration</artifactId>
                <version>0.9.3</version>
            </dependency>

2. Define spring.tapestry.integration.appmodule in your application.properties

## Features
 - bootstraps tapestry framework inside embedded servlet container managed by spring-boot
 - configure tapestry using spring environment (e.g. application.properties)
 - provides injection of spring services in tapestry
 - provides injection of tapestry services in spring

## Example 
see DemoApplicationTest

