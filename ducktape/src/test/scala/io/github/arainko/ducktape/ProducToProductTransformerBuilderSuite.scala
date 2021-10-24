package io.github.arainko.ducktape

import io.github.arainko.ducktape.*
import munit.*
import io.github.arainko.ducktape.internal.Derivation
import scala.deriving.Mirror

enum Enum1:
  case Sing1
  case Sing2
  case Sing3

enum Enum2:
  case Sing1
  case Sing2
  case Sing3
  case Sing4
  case Sing5

// should derive for Enum1 to Enum2
// should fail to derive for Enum2 to Enum1

case class CoolCase1(int: String, additional: Int)
case class CoolCase2(int: String)

case class Product1(int: Int, str: String, list: List[Int], prod: CoolCase1, coprod: Enum2)
case class Product3(int: Option[Int], str: String, list: Vector[Int], prod: Option[CoolCase2], coprod: Enum1)

case class Product2(
  optInt: Option[Int],
  str: String,
  list: List[Int],
  additional: Double,
  computed: Map[String, String]
)

case class Value(value: Int)

class MySuite extends FunSuite {

  // test("Product to Product should compile") {
  //   val prod1 = Product1(1, "prod1", List(1, 2, 3, 4))
  //   val transformed =
  //     TransformerBuilder
  //       .create[Product1, Product2]
  //       .withFieldComputed["computed"](from => Map(from.str -> from.str))
  //       .withFieldConst["additional"](5)
  //       .withFieldRenamed["int", "optInt"]
  //       .transformProdcutToProduct(prod1)

  //   println(transformed)
  // }

  // val cos = Derivation.summonSingleton[Enum2.Sing11.type]

  val costam = summon[Transformer[Value, Int]]
  val costamBack = summon[Transformer[Int, Value]]

  val enumTrans = summon[Transformer[Enum1, Enum2]]

  given Transformer[Enum2, Enum1] = TransformerBuilder
    .create[Enum2, Enum1]
    .withCaseInstance[Enum2.Sing4.type](_ => Enum1.Sing1)
    .withCaseInstance[Enum2.Sing5.type](_ => Enum1.Sing2)
    .build

  val trans = summon[Transformer[Product1, Product3]]

  test("trans test") {
    val prod1 = Product1(1, "prod1", List(1, 2, 3, 4), CoolCase1("COOL", 5), Enum2.Sing4)
    println(trans.transform(prod1))

    println(costam.transform(Value(5)))
    println(costamBack.transform(5))

    val t = enumTrans.transform(Enum1.Sing2)
    println(t)

    // println(enum2ToEnum1)
  }
}
