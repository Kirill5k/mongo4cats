import sbt._

object Utils {

  def priorTo213(scalaVersion: String): Boolean =
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, minor)) if minor < 13 => true
      case _                              => false
    }

  def kindProjectorDependency(scalaVersion: String) =
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, 12)) | Some((2, 13)) => List(compilerPlugin(Dependencies.kindProjector cross CrossVersion.full))
      case _                             => Nil
    }

  def partialUnificationOption(scalaVersion: String) =
    if (priorTo213(scalaVersion)) List("-Ypartial-unification") else Nil
}
