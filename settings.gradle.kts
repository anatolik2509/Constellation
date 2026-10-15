rootProject.name = "Constellation"

// Sources live under modules/, but project paths stay :core / :webui / :gradle-plugin
// so the existing commands (`./gradlew :webui:run`) keep working.
include(":core", ":webui", ":gradle-plugin")
project(":core").projectDir = file("modules/core")
project(":webui").projectDir = file("modules/webui")
project(":gradle-plugin").projectDir = file("modules/gradle-plugin")
