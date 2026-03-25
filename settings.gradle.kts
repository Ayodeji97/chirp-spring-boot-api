pluginManagement {
    includeBuild("build-logic")
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "chirp"

// Include the submodules in the project
include("app")

include("user")
include("chat")
include("notification")
include("common")