package io.github.arainko.ducktapetest.model

final case class PrimitivePerson(
  name: String,
  age: Int,
  contact: PrimitiveContactInfo,
  hobbies: List[String],
  coolnessFactor: PrimitiveCoolnessFactor
)

final case class PrimitiveContactInfo(phoneNo: String, address: String)

enum PrimitiveCoolnessFactor {
  case Uncool
  case Cool
  case SomewhatCool
}

final case class ComplexPerson(
  name: Name,
  age: Age,
  contact: ComplexContactInfo,
  hobbies: Vector[Hobby],
  coolnessFactor: ComplexCoolnessFactor
)

final case class Name(value: String) extends AnyVal

final case class Age(value: Int) extends AnyVal

final case class ComplexContactInfo(phoneNo: PhoneNumber, address: Address)

final case class PhoneNumber(value: String) extends AnyVal

final case class Address(value: String) extends AnyVal

final case class Hobby(value: String) extends AnyVal

enum ComplexCoolnessFactor {
  case Uncool
  case SomewhatCool
  case Cool
}
