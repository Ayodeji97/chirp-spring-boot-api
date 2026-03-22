pluginManagement {
    includeBuild("build-logic")
}

rootProject.name = "chirp"

// Include the submodules in the project
include("app")

include("user")
include("chat")
include("notification")
include("common")