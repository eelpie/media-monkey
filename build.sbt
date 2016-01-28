name := "media-monkey"

version := "1.0"

lazy val `untitled1` = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.7"

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"

libraryDependencies ++= Seq(jdbc , cache , ws)

libraryDependencies += "com.typesafe.play" %% "anorm" % "2.4.0"

libraryDependencies += "org.im4java" % "im4java" % "1.4.0"

libraryDependencies += specs2 % Test

unmanagedResourceDirectories in Test <+=  baseDirectory ( _ /"target/web/public/test" )  

maintainer in Linux := "Tony McCrae <tony@eelpieconsulting.co.uk>"

packageSummary in Linux := "Media Monkey"

packageDescription := "Media handling service"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.2"

debianPackageDependencies in Debian ++= Seq("imagemagick", "libav-tools", "mediainfo", "openjdk-8-jdk")

import com.typesafe.sbt.packager.archetypes.ServerLoader

serverLoading in Debian:= ServerLoader.Systemd
