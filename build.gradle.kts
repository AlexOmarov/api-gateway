plugins {
    alias(libs.plugins.kotlin) // Sonarqube may break if this plugin will be in subprojects with version
    alias(libs.plugins.sonarqube)
}

project.allprojects.forEach {
    it.repositories {
        mavenCentral()
    }
}

sonar {
    val exclusions = project.properties["test_exclusions"].toString()
    val dir = project(":api-gateway-app").layout.buildDirectory.get().asFile

    properties {
        property("sonar.kotlin.detekt.reportPaths", "$dir/reports/detekt/detekt.xml")
        property("sonar.qualitygate.wait", "true")
        property("sonar.core.codeCoveragePlugin", "jacoco")
        property("sonar.coverage.jacoco.xmlReportPaths", "$dir/reports/kover/report.xml")
        property("sonar.cpd.exclusions", exclusions)
        property("sonar.jacoco.excludes", exclusions)
        property("sonar.coverage.exclusions", exclusions)
        property("sonar.junit.reportPaths", "$dir/test-results/test/")
    }
}
