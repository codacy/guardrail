package dev.guardrail.sbt

import sbt._
import sbt.Keys._
import scoverage.ScoverageKeys
import complete.DefaultParsers._
import wartremover.WartRemover.autoImport._
import scalafix.sbt.ScalafixPlugin.autoImport._
import codacy.CodacySbt.autoImport.privateMvnPublish

object Build {
  def buildSampleProject(name: String, extraLibraryDependencies: Seq[sbt.librarymanagement.ModuleID]) =
    Project(s"sample-${name}", file(s"modules/sample-${name}"))
      .settings(commonSettings)
      .settings(codegenSettings)
      .settings(libraryDependencies += "org.scala-lang.modules" %% "scala-collection-compat" % "2.11.0")
      .settings(
        libraryDependencies ++= extraLibraryDependencies,
        Compile / unmanagedSourceDirectories += baseDirectory.value / "target" / "generated",
        publish / skip := true,
      )

  val excludedWarts = Set(Wart.DefaultArguments, Wart.Product, Wart.Serializable, Wart.Any, Wart.StringPlusAny)

  val codegenSettings = Seq(
    ScoverageKeys.coverageExcludedPackages := "<empty>;dev.guardrail.terms.*;dev.guardrail.protocol.terms.*",
    Compile / compile / wartremoverWarnings ++= Warts.unsafe.filterNot(w => excludedWarts.exists(_.clazz == w.clazz)),
  )

  def ifScalaVersion[A](minorPred: Int => Boolean = _ => true)(value: List[A]): Def.Initialize[Seq[A]] = Def.setting {
    scalaVersion.value.split('.') match {
      case Array("2", minor, bugfix) if minorPred(minor.toInt) => value
      case _                                                   => Nil
    }
  }

  val commonSettings = Seq(
    organization := "com.codacy",
    licenses += ("MIT", url("http://opensource.org/licenses/MIT")),

    crossScalaVersions := Seq("2.12.18", "2.13.18"),
    scalaVersion := "2.12.18",

    // early-semver was a mistake. We already have early-semver guaratees during CI, but including this in the publishing POM
    // ensures that independent 0.x versions are incompatible, even though we know they are.
    versionScheme := None,

    scalacOptions ++= Seq(
      "-Xfatal-warnings",
      "-Ydelambdafy:method",
      "-Yrangepos",
      "-release:17",
      // "-Ywarn-unused-import",  // TODO: Enable this! https://github.com/codacy/guardrail/pull/282
      "-feature",
      "-unchecked",
      "-deprecation",
      "-encoding",
      "utf8"
    ),
    javacOptions ++= Seq("--release", "17"),
    Test / scalacOptions -= "-Xfatal-warnings",
    Compile / console / scalacOptions -= "-Xfatal-warnings",
    Compile / consoleQuick / scalacOptions -= "-Xfatal-warnings",
    scalacOptions ++= ifScalaVersion(_ <= 11)(List("-Xexperimental")).value,
    scalacOptions ++= ifScalaVersion(_ == 12)(List("-Ypartial-unification")).value,
    Test / parallelExecution := true,
    ScoverageKeys.coverageScalacPluginVersion := {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, 12)) if scalaVersion.value == "2.12.16" => "2.3.0"
        case _                                                => "2.5.2"
      }
    },
    wartremoverCrossVersion := CrossVersion.binary,
    addCompilerPlugin("org.typelevel" % "kind-projector"  % "0.13.4" cross CrossVersion.full),
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
    addCompilerPlugin(scalafixSemanticdb),
  )

  def commonModule(moduleSegment: String) =
    baseModule(s"guardrail-${moduleSegment}", moduleSegment, file(s"modules/${moduleSegment}"))

  def baseModule(moduleName: String, moduleSegment: String, path: File): Project =
    Project(id=moduleName, base=path)
      .settings(commonSettings)
      .settings(privateMvnPublish)
      .settings(name := moduleName)
      .settings(codegenSettings)
      .settings(libraryDependencies ++= Dependencies.testDependencies)
      .settings(
        scalacOptions ++= List(
          "-language:higherKinds",
          "-Xlint:_,-missing-interpolator"
        ),
        description := "Principled code generation for Scala services from OpenAPI specifications",
        homepage := Some(url("https://github.com/codacy/guardrail")),
        scmInfo := Some(
          ScmInfo(
            url("https://github.com/codacy/guardrail"),
            "scm:git@github.com:codacy/guardrail.git"
          )
        ),
        developers := List(
          Developer(
            id = "blast_hardcheese",
            name = "Devon Stewart",
            email = "blast@hardchee.se",
            url = url("http://hardchee.se/")
          )
        )
      )
      .settings(
        scalacOptions ++= ifScalaVersion(_ <= 11)(List("-Xlint:-missing-interpolator,_")).value,
        scalacOptions ++= ifScalaVersion(_ >= 12)(List("-Xlint:-unused,-missing-interpolator,_")).value,
        scalacOptions ++= ifScalaVersion(_ == 12)(List("-Ypartial-unification", "-Ywarn-unused-import")).value,
        scalacOptions ++= ifScalaVersion(_ >= 13)(List("-Ywarn-unused:imports")).value,
      )

  implicit class ProjectSyntax(project: Project) {
    // Adding these to I _think_ work around https://github.com/sbt/sbt/issues/3733 ?
    // Seems like there's probably a better way to do this, but I don't know what it is
    // The intent is we should be able to use `dependsOn(core % Provided)` to compile
    // against the module's classes, but then emit <scope>provided</scope> in the published POM.
    //
    // Currently, it seems as though `dependsOn(core % Provided)` doesn't expose
    // classes from `core` to the depending module, which means even though we get the desired
    // scope in the pom, it's useless since we can't actually compile the project.
    def accumulateClasspath(other: Project): Project =
      project
        .settings(Compile / unmanagedClasspath := {
          val current = (Compile / unmanagedClasspath).value
          val fromOther = (other / Compile / fullClasspathAsJars).value
          (current ++ fromOther).distinct
        })
        .settings(Runtime / unmanagedClasspath := {
          val current = (Runtime / unmanagedClasspath).value
          val fromOther = (other / Runtime / fullClasspathAsJars).value
          (current ++ fromOther).distinct
        })
        .settings(Test / unmanagedClasspath := {
          val current = (Test / unmanagedClasspath).value
          val fromOther = (other / Test / fullClasspathAsJars).value
          (current ++ fromOther).distinct ++ (other / Test / exportedProductJars).value
        })
        .settings(Default / unmanagedClasspath := {
          val current = (Compile / unmanagedClasspath).value
          val fromOther = (other / Compile / fullClasspathAsJars).value
          (current ++ fromOther).distinct
        })

    def customDependsOn(moduleSegment: String, other: Project, useProvided: Boolean = false): Project =
      project
        .dependsOn(other)
        .accumulateClasspath(other)

    def providedDependsOn(moduleSegment: String, other: Project): Project =
      customDependsOn(moduleSegment, other, true)
  }
}
