// package io.github.arainko.ducktapetest.derivation

// import io.github.arainko.ducktape.*
// import scala.compiletime.testing.*
// import munit.FunSuite

// final case class Input(int: Int, string: String, list: List[Int], option: Option[Int])
// final case class Transformed(int: Int, string: String, list: List[Int], option: Option[Int])
// final case class TransformedGeneric[A](int: Int, string: String, list: List[Int], option: Option[A])

// final case class WrappedInt(value: Int)
// final case class WrappedString(value: String)
// final case class TransformedWithSubtransformations[A](
//   int: WrappedInt,
//   string: WrappedString,
//   list: List[WrappedInt],
//   option: Option[A]
// )

// class ViaSuite extends FunSuite {

//   test("via") {
//     def method(option: Option[Int], list: List[Int], string: String, int: Int) =
//       Transformed(int, string, list, option)

//     val value = Input(1, "a", List(1, 2, 3), Some(4))
//     val expected = Transformed(1, "a", List(1, 2, 3), Some(4))
//     val actual = value.via(method)
//     assertEquals(actual, expected)
//   }

//   test("via with type param") {
//     def method[A](option: Option[A], list: List[Int], string: String, int: Int) =
//       TransformedGeneric[A](int, string, list, option)

//     val value = Input(1, "a", List(1, 2, 3), Some(4))
//     val expected = TransformedGeneric(1, "a", List(1, 2, 3), Some(4))
//     val actual = value.via(method[Int])
//     assertEquals(actual, expected)
//   }

//   test("via with substransformations") {
//     def method[A](option: Option[A], list: List[WrappedInt], string: WrappedString, int: WrappedInt) =
//       TransformedWithSubtransformations[A](int, string, list, option)

//     val value = Input(1, "a", List(1, 2, 3), Some(4))
//     val expected = TransformedWithSubtransformations(
//       WrappedInt(1),
//       WrappedString("a"),
//       List(WrappedInt(1), WrappedInt(2), WrappedInt(3)),
//       Some(WrappedInt(4))
//     )
//     val actual = value.via(method[WrappedInt])

//     assertEquals(actual, expected)
//   }

//   test("via fails when the source doesn't have all the method arguments") {
//     val error = typeCheckErrors {
//       """
//       def method(option: Option[Int], list: List[Int], string: String, int: Int, extraField: String) = ???

//       val value = Input(1, "a", List(1, 2, 3), None)

//       value.via(method)
//       """
//     }.map(_.message).mkString

//     assertEquals(error, "No field named 'extraField' found in Input")
//   }

// }
