import sbt.Keys._

scalaVersion := "2.12.11"
name         := "morbid-backend"
version      := "v2.0"

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
  "org.typelevel"          %% "cats-core"                   % "1.6.0",
  "org.passay"             %  "passay"                      % "1.4.0",
  "org.apache.commons"     %  "commons-text"                % "1.6",
  "com.chuusai"            %% "shapeless"                   % "2.3.3",
  "com.typesafe.slick"     %% "slick"                       % "3.3.2",
  "com.typesafe.slick"     %% "slick-hikaricp"              % "3.3.2",
  "org.postgresql"         %  "postgresql"                  % "42.1.4",
  "xingu"                  %% "xingu-scala-play"            % "v1.1.2",
  "com.typesafe.akka"      %% "akka-testkit"                % "2.5.21" % Test,
  "org.scalatestplus.play" %% "scalatestplus-play"          % "3.1.0"  % Test,
  "org.scalamock"          %% "scalamock-scalatest-support" % "3.5.0"  % Test
)


lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  .disablePlugins(PlayLayoutPlugin)

topLevelDirectory    := None
executableScriptName := "run"
packageName in Universal := "package"
