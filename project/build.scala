import sbt._
import Keys._
//import org.scalatra.sbt._
//import org.scalatra.sbt.PluginKeys._
import com.mojolly.scalate.ScalatePlugin._
import ScalateKeys._
import com.earldouglas.xsbtwebplugin.WebPlugin.webSettings

import sbtassembly.Plugin._
import AssemblyKeys._

object JstestBuild extends Build {
  val Organization = "com.ipetruk.jstest"
  val Name = "jstest"
  val Version = "0.1.0-SNAPSHOT"
  val ScalaVersion = "2.10.2"
  val ScalatraVersion = "2.2.1"

  lazy val project = Project (
    "jstest",
    file("."),
    settings = Defaults.defaultSettings ++ scalateSettings++assemblySettings++ webSettings ++ net.virtualvoid.sbt.graph.Plugin.graphSettings ++ Seq(
      organization := Organization,
      name := Name,
      version := Version,
      scalaVersion := ScalaVersion,
      resolvers += Classpaths.typesafeReleases,
      resolvers += "Maven Search Hack" at "http://mirrors.ibiblio.org/maven2/",
      libraryDependencies ++= Seq(
        "org.scalatra" %% "scalatra" % ScalatraVersion,
        "org.scalatra" %% "scalatra-scalate" % ScalatraVersion,
        "org.scalatra" %% "scalatra-json" % ScalatraVersion,
        "org.json4s"   %% "json4s-jackson" % "3.2.4",
        "net.databinder.dispatch" %% "dispatch-core" % "0.11.0",
        "com.datastax.cassandra" % "cassandra-driver-core" % "1.0.3" exclude("org.apache.cassandra", "cassandra-all"),
//          exclude("org.slf4j", "log4j12") exclude("log4j", "log4j"),
        "org.apache.cassandra" % "cassandra-all" % "1.2.6" exclude("org.slf4j","slf4j-log4j12") exclude("log4j", "log4j"),
        "org.scalatra" %% "scalatra-specs2" % ScalatraVersion % "test",
        "ch.qos.logback" % "logback-classic" % "1.0.6" % "runtime",
        "org.eclipse.jetty" % "jetty-webapp" % "8.1.8.v20121106" % "container",
        "org.eclipse.jetty.orbit" % "javax.servlet" % "3.0.0.v201112011016" % "container;provided;test" artifacts (Artifact("javax.servlet", "jar", "jar"))
      ),
      scalateTemplateConfig in Compile <<= (sourceDirectory in Compile){ base =>
        Seq(
          TemplateConfig(
            base / "webapp" / "WEB-INF" / "templates",
            Seq.empty,  /* default imports should be added here */
            Seq(
              Binding("context", "_root_.org.scalatra.scalate.ScalatraRenderContext", importMembers = true, isImplicit = true)
            ),  /* add extra bindings here */
            Some("templates")
          )
        )
      }
    )
  )
}
