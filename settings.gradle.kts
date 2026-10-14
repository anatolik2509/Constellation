rootProject.name = "Constellation"

// Sources live under modules/, but project paths stay :core / :webui
// so the existing commands (`./gradlew :webui:run`) keep working.
include(":core", ":webui")
project(":core").projectDir = file("modules/core")
project(":webui").projectDir = file("modules/webui")
