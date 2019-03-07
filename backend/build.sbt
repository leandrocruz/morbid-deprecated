import sbt.Keys._

scalaVersion := "2.12.4"
name         := "morbid-backend"
version      := "v1.0-SNAPSHOT"

PlayKeys.devSettings := Seq(
  "app.env"     -> "test",
  "app.shared"  -> "/opt/morbid/backend/shared",
  "play.server.http.port" -> "9004"
)

resolvers ++= Seq(
  Resolver.sonatypeRepo("releases")
)

libraryDependencies ++= Seq(
  jdbc,
  guice,
  ws,
  filters,
  "org.passay"         %  "passay"         % "1.4.0",
  "org.apache.commons" %  "commons-text"   % "1.6",
  "com.chuusai"        %% "shapeless"      % "2.3.3",
  "com.typesafe.slick" %% "slick"          % "3.2.1",
  "com.typesafe.slick" %% "slick-hikaricp" % "3.2.1",
  "org.postgresql"     %  "postgresql"     % "42.1.4",
  "org.scalatestplus.play" %% "scalatestplus-play"          % "3.1.0" % Test,
  "org.scalamock"          %% "scalamock-scalatest-support" % "3.5.0" % Test
)


lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  .disablePlugins(PlayLayoutPlugin)

topLevelDirectory    := None
executableScriptName := "run"
packageName in Universal := "package"