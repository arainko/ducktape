package io.github.arainko.ducktape.examples

import io.github.arainko.ducktape.*
import scala.compiletime.*

final case class Person(firstName: String, lastName: String, age: Int)

final case class PersonButMoreFields(firstName: String, lastName: String, age: Int, socialSecurityNo: String)

@main def productConfigExamples = {
  val person = Person("Jerry", "Smith", 20)

  // 1. Set a constant to a specific field
  val withConstant =
    person
      .into[PersonButMoreFields]
      .transform(Field.const(_.socialSecurityNo, "CONSTANT-SSN"))

// 2. Compute the value for a specific field by applying a function
  val withComputed =
    person
      .into[PersonButMoreFields]
      .transform(Field.computed(_.socialSecurityNo, p => s"${p.firstName}-COMPUTED-SSN"))

// 3. Use a different field in its place - 'rename' it
  val withRename =
    codeOf {
      person
        .into[PersonButMoreFields]
        .transform(Field.renamed(_.socialSecurityNo, _.firstName))
    }

  println(withConstant)
  println(withComputed)
  println(withRename)
}
