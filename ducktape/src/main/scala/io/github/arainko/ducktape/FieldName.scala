package io.github.arainko.ducktape

trait FieldName:
  def uppercase: FieldName
  def lowercase: FieldName


object FieldName {
  // prints 'lowercase, uppercase, uppercase, uppercase'
  FunctionExpressionMatching
    .printOutUsedMethods(_.lowercase.uppercase.lowercase)
}
