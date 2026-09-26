/*
 * Copyright 2020 Kirill5k
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mongo4cats.zio.json

import mongo4cats.bson._
import zio.json.ast.Json

/** Preserve number spelling after the native parser has validated the entire input. */
private[json] object ZioJsonLexical {
  final private case class LexicalJson(value: Json, children: Vector[LexicalJson] = Vector.empty, token: Option[String] = None)

  implicit private val tree: JsonTree[LexicalJson] = new JsonTree[LexicalJson] {
    def fields(value: LexicalJson): Option[Vector[(String, LexicalJson)]] =
      value.value.asObject.map(_.fields.map(_._1).toVector.zip(value.children))
    def elements(value: LexicalJson): Option[Vector[LexicalJson]] = value.value.asArray.map(_ => value.children)
    def string(value: LexicalJson): Option[String]                = value.value.asString
    def number(value: LexicalJson): Option[String]                = value.token
    def boolean(value: LexicalJson): Option[Boolean]              = value.value.asBoolean
    def isNull(value: LexicalJson): Boolean                       = value.value.asNull.nonEmpty
    def obj(fields: Vector[(String, LexicalJson)]): LexicalJson   =
      LexicalJson(Json.Obj(fields.map { case (name, value) => name -> value.value }: _*), fields.map(_._2))
    def arr(elements: Vector[LexicalJson]): LexicalJson = LexicalJson(Json.Arr(elements.map(_.value): _*), elements)
    def str(value: String): LexicalJson                 = LexicalJson(Json.Str(value))
    def num(value: String): LexicalJson                 = LexicalJson(Json.Num(new java.math.BigDecimal(value)), token = Some(value))
    def bool(value: Boolean): LexicalJson               = LexicalJson(Json.Bool(value))
    def nul: LexicalJson                                = LexicalJson(Json.Null)
  }

  def toBson(input: String, validated: Json): Either[BsonErrors, BsonValue] = {
    val tokens                           = numberTokens(input).iterator
    def attach(value: Json): LexicalJson = value match {
      case Json.Obj(fields)   => LexicalJson(value, fields.map { case (_, child) => attach(child) }.toVector)
      case Json.Arr(elements) => LexicalJson(value, elements.map(attach).toVector)
      case _: Json.Num        => LexicalJson(value, token = Some(tokens.next()))
      case _                  => LexicalJson(value)
    }

    ExtendedJsonCodec.toBson(attach(validated))
  }

  // This only locates tokens in already validated JSON. Quoted content, including
  // escaped quotes and backslashes, must not consume a numeric node's token.
  private def numberTokens(input: String): Vector[String] = {
    val result                          = Vector.newBuilder[String]
    var index                           = 0
    def digit(char: Char): Boolean      = char >= '0' && char <= '9'
    def numberChar(char: Char): Boolean = digit(char) || char == '-' || char == '+' || char == '.' || char == 'e' || char == 'E'

    while (index < input.length) {
      val char = input.charAt(index)
      if (char == '"') {
        index += 1
        while (index < input.length && input.charAt(index) != '"')
          if (input.charAt(index) == '\\') index += 2 else index += 1
        index += 1
      } else if (char == '-' || digit(char)) {
        val start = index
        index += 1
        while (index < input.length && numberChar(input.charAt(index))) index += 1
        result += input.substring(start, index)
      } else index += 1
    }
    result.result()
  }
}
