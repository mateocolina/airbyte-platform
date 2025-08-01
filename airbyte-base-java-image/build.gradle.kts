import io.airbyte.gradle.tasks.DockerBuildxTask

plugins {
  id("io.airbyte.gradle.docker") apply false
}

tasks.register<DockerBuildxTask>("dockerJavaBaseImage") {
  inputDir = project.projectDir
  dockerfile = layout.projectDirectory.file("./Dockerfile")
  tag = "3.3.8"
  imageName = "airbyte-base-java-image"
}
