package io.github.arainko.ducktapetest.model

final case class PrimitivePerson(
  name: String,
  age: Int,
  contact: PrimitiveContactInfo,
  hobbies: List[String],
  coolnessFactor: PrimitiveCoolnessFactor
)

final case class PrimitiveContactInfo(phoneNo: String, address: String)

enum PrimitiveCoolnessFactor:
  case Uncool
  case Cool
  case SomewhatCool

final case class ComplexPerson(
  name: Name,
  age: Age,
  contact: ComplexContactInfo,
  hobbies: Vector[Hobby],
  coolnessFactor: ComplexCoolnessFactor
)

final case class Name(value: String)

final case class Age(value: Int)

final case class ComplexContactInfo(phoneNo: PhoneNumber, address: Address)

final case class PhoneNumber(value: String)

final case class Address(value: String)

final case class Hobby(value: String)

enum ComplexCoolnessFactor:
  case Uncool
  case SomewhatCool
  case Cool
