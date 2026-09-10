rootProject.name = "payment"

val sharedCommon = file("../shared-common")
if (sharedCommon.exists()) {
    includeBuild(sharedCommon) {
        dependencySubstitution {
            substitute(module("com.fancia.backend.shared:common")).using(project(":"))
        }
    }
}
val sharedUser = file("../shared-user")
if (sharedUser.exists()) {
    includeBuild(sharedUser) {
        dependencySubstitution {
            substitute(module("com.fancia.backend.shared:user")).using(project(":"))
        }
    }
}
