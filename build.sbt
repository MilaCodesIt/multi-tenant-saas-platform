ThisBuild / scalaVersion := "3.3.1"
ThisBuild / organization := "com.multitenant"
ThisBuild / version := "0.1.0-SNAPSHOT"

val zioVersion = "2.0.19"
val zioHttpVersion = "3.0.0-RC4"
val zioJsonVersion = "0.6.2"
val quillVersion = "4.8.0"
val cirisVersion = "3.5.0"
val refinedVersion = "0.11.0"
val flywayVersion = "9.22.3"
val testcontainersVersion = "0.41.0"

lazy val commonSettings = Seq(
  scalacOptions ++= Seq(
    "-Xmax-inlines", "64",
    "-Wunused:all",
    "-deprecation",
    "-feature",
    "-unchecked"
  ),
  testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
)

lazy val root = (project in file("."))
  .aggregate(core, config, database, api, auth, billing, features, observability)
  .settings(
    name := "multitenant-saas",
    commonSettings
  )

lazy val core = (project in file("modules/core"))
  .settings(
    name := "core",
    commonSettings,
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % zioVersion,
      "dev.zio" %% "zio-json" % zioJsonVersion,
      "dev.zio" %% "zio-prelude" % "1.0.0-RC21",
      "dev.zio" %% "zio-test" % zioVersion % Test,
      "dev.zio" %% "zio-test-sbt" % zioVersion % Test,
      "dev.zio" %% "zio-test-magnolia" % zioVersion % Test
    )
  )

lazy val config = (project in file("modules/config"))
  .dependsOn(core)
  .settings(
    name := "config",
    commonSettings,
    libraryDependencies ++= Seq(
      "is.cir" %% "ciris" % cirisVersion,
      "is.cir" %% "ciris-refined" % cirisVersion,
      "eu.timepit" %% "refined" % refinedVersion
    )
  )

lazy val database = (project in file("modules/database"))
  .dependsOn(core, config)
  .settings(
    name := "database",
    commonSettings,
    libraryDependencies ++= Seq(
      "io.getquill" %% "quill-jdbc-zio" % quillVersion,
      "org.postgresql" % "postgresql" % "42.6.0",
      "org.flywaydb" % "flyway-core" % flywayVersion,
      "com.dimafeng" %% "testcontainers-scala-postgresql" % testcontainersVersion % Test
    )
  )

lazy val api = (project in file("modules/api"))
  .dependsOn(core, config, database, auth, features)
  .settings(
    name := "api",
    commonSettings,
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-http" % zioHttpVersion
    )
  )

lazy val auth = (project in file("modules/auth"))
  .dependsOn(core, config)
  .settings(
    name := "auth",
    commonSettings,
    libraryDependencies ++= Seq(
      "com.github.jwt-scala" %% "jwt-zio-json" % "9.4.4",
      "dev.profunktor" %% "redis4cats-effects" % "1.5.2"
    )
  )

lazy val billing = (project in file("modules/billing"))
  .dependsOn(core, config, database)
  .settings(
    name := "billing",
    commonSettings,
    libraryDependencies ++= Seq(
      "com.stripe" % "stripe-java" % "24.3.0"
    )
  )

lazy val features = (project in file("modules/features"))
  .dependsOn(core, config)
  .settings(
    name := "features",
    commonSettings,
    libraryDependencies ++= Seq(
      "dev.profunktor" %% "redis4cats-effects" % "1.5.2"
    )
  )

lazy val observability = (project in file("modules/observability"))
  .dependsOn(core, config)
  .settings(
    name := "observability",
    commonSettings,
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-opentelemetry" % "3.0.0-RC21",
      "io.opentelemetry" % "opentelemetry-exporter-otlp" % "1.32.0",
      "io.opentelemetry" % "opentelemetry-sdk" % "1.32.0",
      "dev.zio" %% "zio-metrics-connectors" % "2.3.1"
    )
  )

lazy val it = (project in file("it"))
  .dependsOn(api, database, auth, billing)
  .settings(
    name := "integration-tests",
    commonSettings,
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-test" % zioVersion,
      "dev.zio" %% "zio-test-sbt" % zioVersion,
      "com.dimafeng" %% "testcontainers-scala-postgresql" % testcontainersVersion,
      "com.dimafeng" %% "testcontainers-scala-core" % testcontainersVersion
    )
  )

