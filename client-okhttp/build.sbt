import sbt.Keys._

scalaVersion := "2.12.4"
organization := "morbid"
name         := "morbid-client-okhttp"
version      := "v1.0-SNAPSHOT"

resolvers ++= Seq(
  Resolver.sonatypeRepo("releases")
)

libraryDependencies ++= Seq(
  "org.slf4j"            % "slf4j-api"    % "1.7.26",
  "org.apache.commons"   % "commons-text" % "1.6"   ,
  "com.squareup.okhttp3" % "okhttp"       % "4.0.0"
)