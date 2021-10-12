package io.github.arainko.ducktape

import io.github.arainko.ducktape.*
import munit.*
import io.github.arainko.ducktape.SumCase.ProductSubcase

case class ProductCase(int: Int, str: String, list: List[Int])

enum SumCase:
  case SingletonSubcase1
  case SingletonSubcase2
  case ProductSubcase(subInt: Int, subStr: String, subList: List[Int])

enum AnotherSumCase:
  case SingletonSubcase
  case ProductSubcase(subStr: String)

class MySuite extends FunSuite {

  test("Product to Product should compile") {

    val _: TransformerBuilder[
      ProductCase,
      ProductSubcase,
      Field["int", Int] *: Field["str", String] *: Field["list", List[Int]] *: EmptyTuple,
      Field["subInt", Int] *: Field["subStr", String] *: Field["subList", List[Int]] *: EmptyTuple,
      Field["int", Int] *: Field["str", String] *: Field["list", List[Int]] *: EmptyTuple,
      Field["subInt", Int] *: Field["subStr", String] *: Field["subList", List[Int]] *: EmptyTuple,
    ] = TransformerBuilder.create[ProductCase, SumCase.ProductSubcase]

  }

  test("Sum to Product should compile") {
    val _: TransformerBuilder[
      SumCase,
      ProductCase,
      Case["SingletonSubcase1", SumCase.SingletonSubcase1.type, 0] *:
        Case["SingletonSubcase2", SumCase.SingletonSubcase2.type, 1] *:
        Case["ProductSubcase", ProductSubcase, 2] *:
        EmptyTuple,
      Field["int", Int] *: Field["str", String] *: Field["list", List[Int]] *: EmptyTuple,
      Case["SingletonSubcase1", SumCase.SingletonSubcase1.type, 0] *:
        Case["SingletonSubcase2", SumCase.SingletonSubcase2.type, 1] *:
        Case["ProductSubcase", ProductSubcase, 2] *:
        EmptyTuple,
      Field["int", Int] *: Field["str", String] *: Field["list", List[Int]] *: EmptyTuple
    ] = TransformerBuilder.create[SumCase, ProductCase]
  }

  test("Sum to Sum should compile") {
    val _: TransformerBuilder[
      AnotherSumCase,
      SumCase,
      Case["SingletonSubcase", AnotherSumCase.SingletonSubcase.type, 0] *:
        Case["ProductSubcase", AnotherSumCase.ProductSubcase, 1] *:
        EmptyTuple,
      Case["SingletonSubcase1", SumCase.SingletonSubcase1.type, 0] *:
        Case["SingletonSubcase2", SumCase.SingletonSubcase2.type, 1] *:
        Case["ProductSubcase", ProductSubcase, 2] *:
        EmptyTuple,
      Case["SingletonSubcase", AnotherSumCase.SingletonSubcase.type, 0] *:
        Case["ProductSubcase", AnotherSumCase.ProductSubcase, 1] *:
        EmptyTuple,
      Case["SingletonSubcase1", SumCase.SingletonSubcase1.type, 0] *:
        Case["SingletonSubcase2", SumCase.SingletonSubcase2.type, 1] *:
        Case["ProductSubcase", ProductSubcase, 2] *:
        EmptyTuple,
    ] = TransformerBuilder.create[AnotherSumCase, SumCase]
  }

  test("Product to Sum should not compile") {
    assertNotEquals(compileErrors("TransformerBuilder.create[ProductCase, SumCase]"), "")
  
  }
}
