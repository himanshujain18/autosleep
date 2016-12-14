package org.cloudfoundry.autosleep.test;

import org.junit.runner.RunWith;  
import org.junit.runner.notification.RunNotifier;
import org.springframework.test.annotation.IfProfileValue;
import org.springframework.test.annotation.ProfileValueSourceConfiguration;
import org.springframework.test.annotation.ProfileValueUtils;
import org.springframework.test.annotation.SystemProfileValueSource;

import com.github.mkolisnyk.cucumber.runner.ExtendedCucumber;
import com.github.mkolisnyk.cucumber.runner.ExtendedCucumberOptions;

import cucumber.api.CucumberOptions;

import org.cloudfoundry.autosleep.test.RunTests.SpringProfileCucumber;

@RunWith(SpringProfileCucumber.class)
@ProfileValueSourceConfiguration(SystemProfileValueSource.class)
@IfProfileValue(name = "acceptance-test", value = "true")
@ExtendedCucumberOptions(jsonReport = "target/Organization-REST-APIs-Report.json",
        detailedReport = true,
        overviewReport = true,
        outputFolder = "target",
        reportPrefix = "Organization-REST-APIs-Report")
@CucumberOptions(plugin = {"pretty:target/Organization-REST-APIs-pretty.txt", "html:target/cucumber",
        "json:target/Organization-REST-APIs-Report.json", "usage:target/Organization-REST-APIs-usage.json",
        "junit:target/Organization-REST-APIs-results.xml"},
        features = {"src/test/java/org/cloudfoundry/autosleep/feature/OrgEnroll.feature"})
public class RunTests {
    
    public static class SpringProfileCucumber extends ExtendedCucumber {
        public SpringProfileCucumber(Class clazz) throws Exception {
            super(clazz);
        }
        
        public void run(RunNotifier notifier) {   
            if (!ProfileValueUtils.isTestEnabledInThisEnvironment(getTestClass().getJavaClass())) {
                notifier.fireTestIgnored(getDescription());
                return;
            }
            super.run(notifier);
        }
    }
    
}