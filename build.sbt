name := "media-monkey"

version := "1.0"

lazy val `media-monkey` = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.7"

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"
resolvers += "openimaj" at "http://maven.openimaj.org"
resolvers += "billylieurance-net" at "http://www.billylieurance.net/maven2"

libraryDependencies ++= Seq(jdbc , cache , ws)

libraryDependencies += "org.apache.tika" % "tika-core" % "1.11"

libraryDependencies += "com.typesafe.play" %% "anorm" % "2.4.0"

libraryDependencies += "org.im4java" % "im4java" % "1.4.0"

libraryDependencies += "org.openimaj" % "core" % "1.3.6"
libraryDependencies += "org.openimaj" % "core-image" % "1.3.6"
libraryDependencies += "org.openimaj" % "faces" % "1.3.6"

libraryDependencies += "us.fatehi" % "pointlocation6709" % "4.1"

libraryDependencies += "commons-io" % "commons-io" % "2.5"

libraryDependencies += specs2 % Test

maintainer in Linux := "Tony McCrae <tony@eelpieconsulting.co.uk>"

packageSummary in Linux := "Media Monkey"

packageDescription := "Media handling service"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.2"

debianPackageDependencies in Debian ++= Seq("imagemagick", "libav-tools", "mediainfo", "openjdk-8-jdk", "libimage-exiftool-perl", "webp")

import com.typesafe.sbt.packager.archetypes.ServerLoader

serverLoading in Debian:= ServerLoader.Systemd

javaOptions in Universal ++= Seq(
  // -J params will be added as jvm parameters
  "-J-Xmx2048m"
)


enablePlugins(DockerPlugin)

import com.typesafe.sbt.packager.docker._

dockerBaseImage := "debian:jessie-backports"

dockerCommands ++= Seq(
  Cmd("USER", "root"),
  ExecCmd("RUN", "apt-get", "update"),
  ExecCmd("RUN", "apt-get", "upgrade", "-y"),
  ExecCmd("RUN", "apt-get", "install", "-t", "jessie-backports", "-y", "openjdk-8-jre"),
  ExecCmd("RUN", "apt-get", "install", "-y", "imagemagick", "libav-tools", "mediainfo", "libimage-exiftool-perl", "webp")
)
