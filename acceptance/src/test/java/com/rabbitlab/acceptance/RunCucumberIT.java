package com.rabbitlab.acceptance;

import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

/**
 * P2-M6 — {@code src/test/resources/features} altındaki tüm senaryoları koşar. Failsafe bu sınıfı
 * {@code *IT} adından tanır; {@code ./mvnw verify} ile çalışır. Rapor: {@code target/cucumber-report.html}.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
public class RunCucumberIT {
}
