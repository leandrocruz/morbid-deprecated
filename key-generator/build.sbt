import sbt.Keys._

scalaVersion := "2.12.4"
name         := "catitu-keys"
version      := "v1.0-SNAPSHOT"

libraryDependencies ++= Seq(
  "io.jsonwebtoken" % "jjwt-api" % "0.10.5",
  "io.jsonwebtoken" % "jjwt-impl" % "0.10.5"
)