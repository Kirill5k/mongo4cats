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

package mongo4cats.bson

import java.time.Instant
import java.util.UUID

/** Shared format cases based on MongoDB's Extended JSON v2 table and BSON corpus boundary cases.
  * https://github.com/mongodb/specifications/tree/master/source/bson-corpus/tests Keep this fixture independent of either integration's
  * JSON AST.
  */
object ExtendedJsonFixtures {
  import BsonValue._

  val canonical: Vector[(String, BsonValue)] = Vector(
    "null"                                                               -> BNull,
    "true"                                                               -> BBoolean(true),
    "\"hello\""                                                          -> BString("hello"),
    """{"$numberInt":"-2147483648"}"""                                   -> BInt32(Int.MinValue),
    """{"$numberInt":"2147483647"}"""                                    -> BInt32(Int.MaxValue),
    """{"$numberLong":"-9223372036854775808"}"""                         -> BInt64(Long.MinValue),
    """{"$numberLong":"9223372036854775807"}"""                          -> BInt64(Long.MaxValue),
    """{"$numberLong":"1"}"""                                            -> BInt64(1L),
    """{"$numberDouble":"1.0"}"""                                        -> BDouble(1.0),
    """{"$numberDouble":"-0.0"}"""                                       -> BDouble(-0.0),
    """{"$numberDouble":"1.7976931348623157E308"}"""                     -> BDouble(Double.MaxValue),
    """{"$numberDouble":"4.9E-324"}"""                                   -> BDouble(java.lang.Double.MIN_VALUE),
    """{"$numberDouble":"NaN"}"""                                        -> BDouble(Double.NaN),
    """{"$numberDouble":"Infinity"}"""                                   -> BDouble(Double.PositiveInfinity),
    """{"$numberDouble":"-Infinity"}"""                                  -> BDouble(Double.NegativeInfinity),
    """{"$numberDecimal":"123.4500"}"""                                  -> BDecimal(BigDecimal("123.4500")),
    """{"$numberDecimal":"0E-6176"}"""                                   -> BDecimal(BigDecimal("0E-6176")),
    """{"$numberDecimal":"9.999999999999999999999999999999999E+6144"}""" -> BDecimal(
      BigDecimal("9.999999999999999999999999999999999E+6144")
    ),
    """{"$oid":"507f1f77bcf86cd799439011"}"""                              -> BObjectId(ObjectId("507f1f77bcf86cd799439011")),
    """{"$date":{"$numberLong":"-9223372036854775808"}}"""                 -> BDateTime(Instant.ofEpochMilli(Long.MinValue)),
    """{"$date":{"$numberLong":"9223372036854775807"}}"""                  -> BDateTime(Instant.ofEpochMilli(Long.MaxValue)),
    """{"$date":{"$numberLong":"0"}}"""                                    -> BDateTime(Instant.EPOCH),
    """{"$date":"2022-01-01T00:00:00.123Z"}"""                             -> BDateTime(Instant.parse("2022-01-01T00:00:00.123Z")),
    """{"$binary":{"base64":"AQID","subType":"80"}}"""                     -> BBinary(Array[Byte](1, 2, 3), 0x80.toByte),
    """{"$binary":{"base64":"","subType":"0"}}"""                          -> BBinary(Array.emptyByteArray, 0.toByte),
    """{"$binary":{"base64":"AQID","subType":"FF"}}"""                     -> BBinary(Array[Byte](1, 2, 3), 0xff.toByte),
    """{"$binary":{"base64":"AAAAAAAAAAAAAAAAAAAAAA==","subType":"04"}}""" -> BUuid(new UUID(0L, 0L)),
    """{"$uuid":"00000000-0000-0000-0000-000000000000"}"""                 -> BUuid(new UUID(0L, 0L)),
    """{"$regularExpression":{"pattern":"^a.*","options":"mi"}}"""         -> BRegex("^a.*".r, "im"),
    """{"$regularExpression":{"pattern":"","options":""}}"""               -> BRegex("".r, ""),
    """{"$timestamp":{"t":4294967295,"i":4294967295}}"""                   -> BTimestamp(0xffffffffL, -1),
    """{"$timestamp":{"t":0,"i":0}}"""                                     -> BTimestamp(0L, 0),
    """{"$undefined":true}"""                                              -> BUndefined,
    """{"$minKey":1}"""                                                    -> BMinKey,
    """{"$maxKey":1}"""                                                    -> BMaxKey,
    """{"a":[{"$numberLong":"1"},{"b":{"$undefined":true}}]}"""            -> BDocument(
      Document(
        "a" -> BArray(List(BInt64(1L), BDocument(Document("b" -> BUndefined))))
      )
    ),
    // Query operators remain ordinary document fields, as required by these upstream corpus cases:
    // https://github.com/mongodb/specifications/blob/master/source/bson-corpus/tests/regex.json
    """{"$regex":{"$regularExpression":{"pattern":"pattern","options":"ix"}}}""" -> BDocument(
      Document(
        "$regex" -> BRegex("pattern".r, "ix")
      )
    ),
    """{"$regex":{"$regularExpression":{"pattern":"pattern","options":""}},"$options":"ix"}""" -> BDocument(
      Document(
        "$regex"   -> BRegex("pattern".r, ""),
        "$options" -> BString("ix")
      )
    ),
    // https://github.com/mongodb/specifications/blob/master/source/bson-corpus/tests/binary.json
    """{"x":{"$type":"string"}}"""           -> BDocument(Document("x" -> BDocument(Document("$type" -> BString("string"))))),
    """{"x":{"$type":{"$numberInt":"2"}}}""" -> BDocument(Document("x" -> BDocument(Document("$type" -> BInt32(2))))),
    """{"$ref":"collection","$id":{"$oid":"507f1f77bcf86cd799439011"}}""" -> BDocument(
      Document(
        "$ref" -> BString("collection"),
        "$id"  -> BObjectId(ObjectId("507f1f77bcf86cd799439011"))
      )
    )
  )

  val invalid: Vector[String] = Vector(
    """{"$numberInt":"bad","$numberInt":"42"}""",
    """{"nested":{"a":1,"a":2}}""",
    """{"$numberInt":"2147483648"}""",
    """{"$numberInt":42}""",
    """{"$numberLong":"9223372036854775808"}""",
    """{"$numberLong":"1.0"}""",
    """{"$numberDouble":"0x1.0p0"}""",
    """{"$numberDouble":"1e400"}""",
    """{"$numberDouble":"nan"}""",
    """{"$numberDecimal":"NaN"}""",
    """{"$numberDecimal":"Infinity"}""",
    """{"$numberDecimal":"-0"}""",
    """{"$numberDecimal":"1E+6145"}""",
    """{"$oid":"invalid"}""",
    """{"$oid":"507f1f77bcf86cd799439011","extra":1}""",
    """{"$uuid":"1-1-1-1-1"}""",
    """{"$binary":{"base64":"!","subType":"100"}}""",
    """{"$binary":{"base64":"AA"}}""",
    """{"$binary":{"base64":"AA==","subType":"00","extra":true}}""",
    """{"$date":{"$numberLong":"9223372036854775808"}}""",
    """{"$date":"2022-01-01T00:00:00.000000001Z"}""",
    """{"$date":"2016-12-31T23:59:60Z"}""",
    """{"$date":0}""",
    """{"$date":"2022-01-01"}""",
    """{"$timestamp":{"t":4294967296,"i":-1}}""",
    """{"$timestamp":{"t":1.5,"i":0}}""",
    """{"$timestamp":{"t":1e0,"i":0}}""",
    """{"$regularExpression":{"pattern":"[","options":"i"}}""",
    """{"$regularExpression":{"pattern":"a","options":"z"}}""",
    """{"$undefined":false}""",
    """{"$minKey":0}""",
    """{"$maxKey":"1"}""",
    """{"$code":"return 42"}""",
    """{"$symbol":"x"}""",
    """{"$dbPointer":{"$ref":"ns","$id":{"$oid":"507f1f77bcf86cd799439011"}}}"""
  )
}
