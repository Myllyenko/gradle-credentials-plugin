package nu.studer.gradle.credentials

import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Requires
import spock.lang.Unroll

@Unroll
class CredentialsFuncTest extends BaseFuncTest {

  @Rule
  TemporaryFolder tempFolder

  void setup() {
    new File(testKitDir, 'gradle.encrypted.properties').delete()
  }

  void "cannot add credentials with null key"() {
    given:
    buildFile()

    when:
    def result = runAndFailWithArguments('addCredentials', '--value', 'someValue', '-i')

    then:
    result.task(':addCredentials').outcome == TaskOutcome.FAILED
    result.output.contains('Credentials key must not be null')
  }

  void "cannot add credentials with null value"() {
    given:
    buildFile()

    when:
    def result = runAndFailWithArguments('addCredentials', '--key', 'someKey', '-i')

    then:
    result.task(':addCredentials').outcome == TaskOutcome.FAILED
    result.output.contains('Credentials value must not be null')
  }

  void "cannot access credentials added in same build execution"() {
    given:
    buildFile()

    when:
    def result = runWithArguments('addCredentials', '--key', 'someKey', '--value', 'someValue', 'printValue', '-i')

    then:
    result.task(':addCredentials').outcome == TaskOutcome.SUCCESS
    result.output.contains('value: null')
  }

  void "can access credentials added in previous build execution"() {
    given:
    buildFile()

    when:
    runWithArguments('addCredentials', '--key', 'someKey', '--value', 'someValue', '-i')
    def result = runWithArguments('printValue', '-i')

    then:
    result.task(':printValue').outcome == TaskOutcome.SUCCESS
    result.output.contains('value: someValue')
  }

  void "can access credentials with dollar character in value"() {
    given:
    buildFile()

    when:
    runWithArguments('addCredentials', '--key', 'someKey', '--value', 'before$after', '-i')
    def result = runWithArguments('printValue', '-i')

    then:
    result.task(':printValue').outcome == TaskOutcome.SUCCESS
    result.output.contains('value: before$after')
  }

  void "can access credentials added with custom passphrase"() {
    given:
    buildFile()

    when:
    runWithArguments('addCredentials', '--key', 'someKey', '--value', 'someValue', '-PcredentialsPassphrase=xyz', '-i')
    def result = runWithArguments('printValue', '-PcredentialsPassphrase=xyz', '-i')

    then:
    result.task(':printValue').outcome == TaskOutcome.SUCCESS
    result.output.contains('value: someValue')
  }

  void "cannot access credentials used with different passphrase from when added with custom passphrase"() {
    given:
    buildFile()

    when:
    runWithArguments('addCredentials', '--key', 'someKey', '--value', 'someValue', '-PcredentialsPassphrase=xyz', '-i')
    def result = runWithArguments('printValue', '-PcredentialsPassphrase=abz', '-i')

    then:
    result.task(':printValue').outcome == TaskOutcome.SUCCESS
    result.output.contains('value: null')
  }

  void "cannot access credentials used with different passphrase from when added with default passphrase"() {
    given:
    buildFile()

    when:
    runWithArguments('addCredentials', '--key', 'someKey', '--value', 'someValue', '-i')
    def result = runWithArguments('printValue', '-PcredentialsPassphrase=abz', '-i')

    then:
    result.task(':printValue').outcome == TaskOutcome.SUCCESS
    result.output.contains('value: null')
  }

  void "can configure custom location of password file"() {
    given:
    buildFile()
    def location = tempFolder.newFolder()

    when:
    runWithArguments('addCredentials', '--key', 'someKey', '--value', 'someValue', '-PcredentialsLocation=' + location.canonicalPath, '-i')
    def result = runWithArguments('printValue', '-PcredentialsLocation=' + location.canonicalPath, '-i')

    then:
    result.task(':printValue').outcome == TaskOutcome.SUCCESS
    result.output.contains('value: someValue')

    when:
    result = runWithArguments('printValue', '-i')

    then:
    result.task(':printValue').outcome == TaskOutcome.SUCCESS
    result.output.contains('value: null')

  }

  void "can apply plugin in conjunction with the maven publish plugins"() {
    given:
    buildFile << """
plugins {
    id 'nu.studer.credentials'
    id 'maven-publish'
}

publishing {
    publications {
        something(MavenPublication) { artifact file('build.gradle') }
    }
}

task printValue {
  doLast {
    String val = credentials.someKey
    println "value: \$val"
  }
}
"""

    when:
    runWithArguments('addCredentials', '--key', 'someKey', '--value', 'someValue', '-i')
    def result = runWithArguments('printValue', '-i')

    then:
    result.task(':printValue').outcome == TaskOutcome.SUCCESS
    result.output.contains('value: someValue')
  }

  @Requires( { sys["testContext.gradleVersion"]?.startsWith("5") })
  void "can apply plugin in settings.gradle"() {
    given:
    buildFile << """
plugins {
    id 'nu.studer.credentials'
}
"""

    expect:
    runWithArguments('addCredentials', '--key', 'someKey', '--value', 'someValue', '-i')

    when:
    def pluginClasspath = getClass().classLoader.getResource("plugin-classpath.txt").readLines().
            collect { it.replace('\\', '\\\\') }.collect { "'$it'" }.join(", ")

    settingsFile << """
buildscript {
    dependencies {
        classpath files($pluginClasspath)
    }
}

apply plugin: 'nu.studer.credentials'

assert credentials.someKey == 'someValue'
"""

    println settingsFile.text

    then:
    runWithArguments()
  }

  private File buildFile() {
    buildFile << """
plugins {
    id 'nu.studer.credentials'
}

task printValue {
  doLast {
    String val = credentials.someKey
    println "value: \$val"
  }
}
"""
  }

}
