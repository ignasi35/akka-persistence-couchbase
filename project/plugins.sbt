addSbtPlugin("de.heikoseeberger" % "sbt-header" % "5.2.0") // for maintenance of copyright file header
addSbtPlugin("org.scalameta"     % "sbt-scalafmt"       % "2.2.1")
addSbtPlugin("com.lightbend.sbt" % "sbt-java-formatter" % "0.4.4")

// whitesource for tracking licenses and vulnerabilities in dependencies
addSbtPlugin("com.lightbend" % "sbt-whitesource" % "0.1.18")

// for releasing
addSbtPlugin("com.dwijnand" % "sbt-dynver" % "4.0.0")
addSbtPlugin("org.foundweekends" % "sbt-bintray" % "0.5.5")

addSbtPlugin("com.typesafe.sbt" % "sbt-multi-jvm" % "0.4.0")

// docs
addSbtPlugin("com.lightbend.paradox" % "sbt-paradox-dependencies" % "0.2")
addSbtPlugin("com.lightbend.paradox" % "sbt-paradox-project-info" % "1.1.3")
addSbtPlugin("com.lightbend.akka" % "sbt-paradox-akka" % "0.26")
addSbtPlugin("com.lightbend.sbt" % "sbt-publish-rsync" % "0.1")
addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "1.4.0")
// patched version of sbt-dependency-graph
// depend directly on the patched version see https://github.com/akka/alpakka/issues/1388
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.9.2+10-148ba0ff")

// patched version of sbt-dependency-graph for sbt-paradox-dependencies
resolvers += Resolver.bintrayIvyRepo("2m", "sbt-plugins")
