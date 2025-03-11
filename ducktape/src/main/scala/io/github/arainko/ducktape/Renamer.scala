package io.github.arainko.ducktape

sealed trait Renamer {
  def toUpperCase: Renamer

  def toLowerCase: Renamer

  def rename(from: String, to: String): Renamer

  def replace(target: String, replacement: String): Renamer

  def regexReplace(pattern: String, replacement: String): Renamer
}
