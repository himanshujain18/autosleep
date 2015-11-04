*** Settings ***
Resource            Keywords.robot
Documentation       Check that all pre-conditions are met for the test
Force Tags          Pre-conditions



*** Test Cases ***

1) Check service broker is published
    [Documentation]     Check if autosleep service broker appears in market place
    ${result} =    Run Process    cf  marketplace
    Should Contain    ${result.stdout}    autosleep

2) Check test app is deployed
    [Documentation]     Check that ${TESTED_APP_NAME} appears in deployed apps
    ${result} =    Run Process    cf  apps
    Should Contain    ${result.stdout}    ${TESTED_APP_NAME}


