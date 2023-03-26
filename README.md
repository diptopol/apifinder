# API Finder: Accurate Extraction of Method Binding Information from Call Sites without Building the Code

API Finder can accurately extract method binding information from method references (i.e., call sites) in partial programs. Our approach requires only the Java version of the project, the dependent external library artifacts, and the method invocation as inputs in order to generate precise method-binding information. We also provide support for extracting the Java version of the project, as well as dependent external library artifacts for the Gradle and Maven build systems.


## Suported Build System

- Maven
- Gradle

## Prerequisite Installation

- MySQL

## Usage Guide
- Clone [API Finder](https://github.com/diptopol/apifinder.git) locally.
- Run the migration [scripts](https://github.com/diptopol/apifinder/tree/master/dbScripts) for the local Mysql installation.
- Update the [config.properties](https://github.com/diptopol/apifinder/blob/master/src/main/resources/config.properties).
- Perform the tests from [JFreeChartV153TypeInferenceV2APITest.java](https://github.com/diptopol/apifinder/blob/master/src/test/java/ca/concordia/apifinder/JFreeChartV153TypeInferenceV2APITest.java).
- If all the tests pass, build the project using Maven command.
```sh
mvn clean install -DskipTests
```
- Copy the file "apifiner-[Version]-jar-with-dependencies" from "\target" directory.
- Include the archive as a dependency of your project.


## Update process of 'config.properties'
- Update the github oauth token `github.oauth.token`.
- Update the mysql host url `datasource.jdbc.url`.
- Update the java installation directory [e.g., `java.6.jar.directory`].
- For locally cloned projects, show the path `corpus.path`.
- Update Maven [`maven.home`] and Gradle [`gradle.home`] installation directory.