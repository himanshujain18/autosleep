#Acceptance test for Organization Enrollment Rest Apis
How to run the acceptance tests.

Acceptance tests for organization enrollment Rest Apis are written using [cucumber](https://cucumber.io/).

## Preconditions

### What you need on your computer to run the test
clone the acceptance test code to the local directory {ACCEPTANCE_TEST_DIRECTORY}

### What you need in your cloudfoundry environment
- Autosleep application deployed as an application in cloudfoundry.

## Run the tests 

1. Fill the information in the  `{ACCEPTANCE_TEST_DIRECTORY}/src/main/resources/config.properties` file
2. Run the tests by running the following command from {ACCEPTANCE_TEST_DIRECTORY}:

gradle test or
gradlew build

## Run the tests directly from the project's root directory

1. Fill the information in the
`autosleep/acceptance/acceptance-test/src/main/resources/config.properties` file
2. Run the tests by running the following command from root directory:

gradlew build -Dacceptance-test=true or
gradlew check -Dacceptance-test=true

## Sample test report

The detailed test report is a html report named `Organization-REST-APIs-test-results` generated in the directory {ACCEPTANCE_TEST_DIRECTORY}/target/