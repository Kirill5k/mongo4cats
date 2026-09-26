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

import mongo4cats.Uuid
import mongo4cats.bson.BsonError.Kind
import mongo4cats.bson.BsonPathSegment.{Field, Index}
import mongo4cats.bson.BsonValue._
import org.bson.types.Decimal128

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.{Base64, UUID}
import scala.util.control.NonFatal

/** Extended JSON v2 semantics shared by the JSON integrations. Legacy mappers deliberately remain separate. */
private[mongo4cats] object ExtendedJsonCodec {
  private val supportedTags = Set(
    "$oid",
    "$numberInt",
    "$numberLong",
    "$numberDouble",
    "$numberDecimal",
    "$date",
    "$binary",
    "$uuid",
    "$timestamp",
    "$regularExpression",
    "$minKey",
    "$maxKey",
    "$undefined"
  )
  private val unsupportedTags  = Set("$symbol", "$code", "$scope", "$dbPointer")
  private val reservedTags     = supportedTags ++ unsupportedTags
  private val integerPattern   = "-?(0|[1-9][0-9]*)"
  private val numberPattern    = "-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?"
  private val uint32Max        = 0xffffffffL
  private val relaxedDateLimit = 253402300800000L

  private def failure[A](kind: Kind, message: String): Either[BsonErrors, A] = Left(BsonErrors(BsonError(kind, message)))

  private def attempt[A](message: String, kind: Kind = Kind.InvalidValue)(value: => A): Either[BsonErrors, A] =
    try Right(value)
    catch {
      case NonFatal(error) => Left(BsonErrors(BsonError(kind, message, cause = Some(error))))
    }

  private def both[A, B](left: Either[BsonErrors, A], right: Either[BsonErrors, B]): Either[BsonErrors, (A, B)] =
    (left, right) match {
      case (Right(a), Right(b)) => Right((a, b))
      case (Left(a), Left(b))   => Left(a ++ b)
      case (Left(a), _)         => Left(a)
      case (_, Left(b))         => Left(b)
    }

  private def collect[A](values: Vector[Either[BsonErrors, A]]): Either[BsonErrors, Vector[A]] =
    values.foldLeft[Either[BsonErrors, Vector[A]]](Right(Vector.empty)) { (result, next) =>
      both(result, next).map { case (all, value) => all :+ value }
    }

  private def at[A](name: String)(value: Either[BsonErrors, A]): Either[BsonErrors, A] = value.left.map(_.prepend(Field(name)))

  /** Reject values for which writing BSON or Extended JSON would discard information. */
  def validate(value: BsonValue): Either[BsonErrors, Unit] = value match {
    case BDocument(document) =>
      collect(document.toList.toVector.map { case (key, child) =>
        val keyCheck =
          if (reservedTags(key)) failure[Unit](Kind.InvalidValue, "Document field conflicts with an Extended JSON type wrapper")
          else if (key.indexOf('\u0000') >= 0) failure[Unit](Kind.InvalidValue, "BSON field names cannot contain NUL")
          else Right(())
        at(key)(both(keyCheck, validate(child)).map(_ => ()))
      }).map(_ => ())
    case BArray(values) =>
      collect(values.toVector.zipWithIndex.map { case (child, index) => validate(child).left.map(_.prepend(Index(index))) }).map(_ => ())
    case BDateTime(instant) => validateInstant(instant).map(_ => ())
    case BDecimal(decimal)  => attempt("Value cannot be represented exactly as Decimal128")(new Decimal128(decimal.bigDecimal)).map(_ => ())
    case BTimestamp(seconds, _) =>
      if (seconds >= 0 && seconds <= uint32Max) Right(())
      else failure(Kind.InvalidValue, "Timestamp seconds must be an unsigned 32-bit integer")
    case BRegex(pattern, options) =>
      both(
        if (pattern.regex.indexOf('\u0000') < 0) Right(()) else failure[Unit](Kind.InvalidValue, "BSON regex patterns cannot contain NUL"),
        validateOptions(options)
      ).map(_ => ())
    case _ => Right(())
  }

  private def validateInstant(instant: Instant): Either[BsonErrors, Instant] =
    attempt("Date must fit signed 64-bit milliseconds without losing submillisecond precision") {
      require(instant.getNano % 1000000 == 0)
      val _ = instant.toEpochMilli
      instant
    }

  private def validateOptions(options: String): Either[BsonErrors, Unit] =
    if (options.forall("ilmsux".contains(_)) && options.distinct == options) Right(())
    else failure(Kind.InvalidValue, "Regex options must be distinct BSON option letters (i, l, m, s, u, x)")

  def fromBson[J](value: BsonValue, mode: BsonJsonMode)(implicit tree: JsonTree[J]): Either[BsonErrors, J] = {
    def validateNumbers(bson: BsonValue): Either[BsonErrors, Unit] = bson match {
      case BDouble(number) if java.lang.Double.doubleToRawLongBits(number) == Long.MinValue =>
        failure(Kind.InvalidValue, "This JSON AST cannot preserve negative zero in relaxed output; use canonical output")
      case BArray(values) =>
        collect(values.toVector.zipWithIndex.map { case (child, index) => validateNumbers(child).left.map(_.prepend(Index(index))) }).map(
          _ => ()
        )
      case BDocument(document) =>
        collect(document.toList.toVector.map { case (key, child) => at(key)(validateNumbers(child)) }).map(_ => ())
      case _ => Right(())
    }
    val numbers = if (mode == BsonJsonMode.Relaxed && !tree.preservesNegativeZero) validateNumbers(value) else Right(())
    both(validate(value), numbers).map(_ => encode(value, mode))
  }

  private def encode[J](value: BsonValue, mode: BsonJsonMode)(implicit tree: JsonTree[J]): J = {
    def wrapper(tag: String, payload: J): J     = tree.obj(Vector(tag -> payload))
    def numeric(tag: String, number: String): J =
      if (mode == BsonJsonMode.Canonical) wrapper(tag, tree.str(number)) else tree.num(number)
    def binary(bytes: Array[Byte], subtype: Byte): J = wrapper(
      "$binary",
      tree.obj(Vector("base64" -> tree.str(Base64.getEncoder.encodeToString(bytes)), "subType" -> tree.str(f"${subtype & 0xff}%02x")))
    )
    value match {
      case BNull                                 => tree.nul
      case BUndefined                            => wrapper("$undefined", tree.bool(true))
      case BMinKey                               => wrapper("$minKey", tree.num("1"))
      case BMaxKey                               => wrapper("$maxKey", tree.num("1"))
      case BBoolean(v)                           => tree.bool(v)
      case BString(v)                            => tree.str(v)
      case BInt32(v)                             => numeric("$numberInt", v.toString)
      case BInt64(v)                             => numeric("$numberLong", v.toString)
      case BDouble(v) if v.isNaN || v.isInfinity => wrapper("$numberDouble", tree.str(java.lang.Double.toString(v)))
      case BDouble(v)                            => numeric("$numberDouble", java.lang.Double.toString(v))
      case BDecimal(v)                           => wrapper("$numberDecimal", tree.str(new Decimal128(v.bigDecimal).toString))
      case BDateTime(v)                          =>
        val millis  = v.toEpochMilli
        val payload =
          if (mode == BsonJsonMode.Relaxed && millis >= 0 && millis < relaxedDateLimit) tree.str(v.toString)
          else wrapper("$numberLong", tree.str(millis.toString))
        wrapper("$date", payload)
      case BTimestamp(seconds, inc) =>
        wrapper(
          "$timestamp",
          tree.obj(Vector("t" -> tree.num(seconds.toString), "i" -> tree.num(java.lang.Integer.toUnsignedLong(inc).toString)))
        )
      case BObjectId(v)             => wrapper("$oid", tree.str(v.toHexString))
      case BBinary(bytes, subtype)  => binary(bytes, subtype)
      case BUuid(v)                 => binary(Uuid.toBinary(v), 4.toByte)
      case BRegex(pattern, options) =>
        wrapper("$regularExpression", tree.obj(Vector("pattern" -> tree.str(pattern.regex), "options" -> tree.str(options.sorted))))
      case BDocument(v) => tree.obj(v.toList.toVector.map { case (key, child) => key -> encode(child, mode) })
      case BArray(v)    => tree.arr(v.toVector.map(encode(_, mode)))
    }
  }

  def toBson[J](value: J)(implicit tree: JsonTree[J]): Either[BsonErrors, BsonValue] = {
    def actual(v: J): String =
      if (tree.isNull(v)) "Null"
      else if (tree.fields(v).isDefined) "Object"
      else if (tree.elements(v).isDefined) "Array"
      else if (tree.string(v).isDefined) "String"
      else if (tree.number(v).isDefined) "Number"
      else "Boolean"

    def mismatch[A](expected: String, v: J): Either[BsonErrors, A] =
      Left(
        BsonErrors(
          BsonError(Kind.TypeMismatch, s"Expected $expected but found ${actual(v)}", expected = Some(expected), actual = Some(actual(v)))
        )
      )

    def asString(v: J): Either[BsonErrors, String]              = tree.string(v).toRight(()).left.flatMap(_ => mismatch("String", v))
    def asObject(v: J): Either[BsonErrors, Vector[(String, J)]] = tree.fields(v).toRight(()).left.flatMap(_ => mismatch("Object", v))

    def required[A](fields: Vector[(String, J)], name: String)(read: J => Either[BsonErrors, A]): Either[BsonErrors, A] =
      at(name)(fields.find(_._1 == name) match {
        case Some((_, v)) => read(v)
        case None         => failure(Kind.MissingField, s"Missing required field $name")
      })

    def keys(fields: Vector[(String, J)], allowed: Set[String]): Either[BsonErrors, Unit] = {
      val duplicates = fields.groupBy(_._1).collect { case (key, entries) if entries.size > 1 => key }.toSet
      collect(fields.map(_._1).distinct.collect {
        case key if !allowed(key)   => at(key)(failure[Unit](Kind.InvalidValue, "Unexpected field in Extended JSON wrapper"))
        case key if duplicates(key) => at(key)(failure[Unit](Kind.InvalidValue, "Duplicate JSON field"))
      }).map(_ => ())
    }

    def objectPayload[A](v: J, allowed: Set[String])(read: Vector[(String, J)] => Either[BsonErrors, A]): Either[BsonErrors, A] =
      asObject(v).flatMap(fields => both(keys(fields, allowed), read(fields)).map(_._2))

    def signed(v: J, int32: Boolean): Either[BsonErrors, BsonValue] = asString(v).flatMap { s =>
      attempt(if (int32) "Expected a signed 32-bit integer string" else "Expected a signed 64-bit integer string") {
        require(s.matches(integerPattern))
        if (int32) BInt32(java.lang.Integer.parseInt(s)) else BInt64(java.lang.Long.parseLong(s))
      }
    }

    def uint(v: J): Either[BsonErrors, Long] = tree.number(v) match {
      case None    => mismatch("Number", v)
      case Some(s) =>
        attempt("Expected an unsigned 32-bit integer") {
          require(s.matches("0|[1-9][0-9]*"))
          val n = java.lang.Long.parseLong(s)
          require(n >= 0 && n <= uint32Max)
          n
        }
    }

    def double(s: String): Either[BsonErrors, Double] = attempt("Invalid BSON double") {
      require(s == "Infinity" || s == "-Infinity" || s == "NaN" || s.matches(numberPattern))
      val n = java.lang.Double.parseDouble(s)
      require(!n.isInfinity || s == "Infinity" || s == "-Infinity")
      n
    }

    def date(v: J): Either[BsonErrors, BsonValue] = tree.string(v) match {
      case Some(s) =>
        attempt("Invalid ISO instant or unrepresentable leap second") {
          val parsed = DateTimeFormatter.ISO_INSTANT.parse(s)
          require(!parsed.query(DateTimeFormatter.parsedLeapSecond()).booleanValue())
          Instant.from(parsed)
        }.flatMap(validateInstant).map(BDateTime.apply)
      case None =>
        objectPayload(v, Set("$numberLong")) { fields =>
          required(fields, "$numberLong")(j =>
            signed(j, int32 = false).map {
              case BInt64(millis) => BDateTime(Instant.ofEpochMilli(millis))
              case _              => throw new IllegalStateException("Expected Int64 from signed decoder")
            }
          )
        }
    }

    def readBinary(v: J): Either[BsonErrors, BsonValue] = objectPayload(v, Set("base64", "subType")) { fields =>
      val bytes = required(fields, "base64")(j =>
        asString(j).flatMap { s =>
          attempt("Invalid padded base64 binary payload") {
            val data = Base64.getDecoder.decode(s)
            require(Base64.getEncoder.encodeToString(data) == s)
            data
          }
        }
      )
      val subtype = required(fields, "subType")(j =>
        asString(j).flatMap { s =>
          attempt("Binary subtype must contain one or two hexadecimal digits") {
            require(s.matches("[0-9a-fA-F]{1,2}"))
            Integer.parseInt(s, 16).toByte
          }
        }
      )
      both(bytes, subtype).map { case (data, tag) =>
        // UUID is a convenience view; all other payloads retain their exact bytes and subtype.
        if (tag == 4 && data.length == 16) BUuid(new org.bson.BsonBinary(tag, data).asUuid()) else BBinary(data, tag)
      }
    }

    def regex(v: J): Either[BsonErrors, BsonValue] = objectPayload(v, Set("pattern", "options")) { fields =>
      val pattern = required(fields, "pattern")(j =>
        asString(j).flatMap { s =>
          if (s.indexOf('\u0000') >= 0) failure(Kind.InvalidValue, "BSON regex patterns cannot contain NUL")
          else attempt("Regex pattern cannot be represented by Scala Regex", Kind.UnsupportedType)(s.r)
        }
      )
      val options = required(fields, "options")(j => asString(j).flatMap(s => validateOptions(s).map(_ => s)))
      both(pattern, options).map { case (p, o) => BRegex(p, o.sorted) }
    }

    def readWrapper(tag: String, v: J): Either[BsonErrors, BsonValue] = tag match {
      case "$numberInt"     => signed(v, int32 = true)
      case "$numberLong"    => signed(v, int32 = false)
      case "$numberDouble"  => asString(v).flatMap(double).map(BDouble.apply)
      case "$numberDecimal" =>
        asString(v).flatMap(s =>
          attempt("Decimal128 value cannot be represented exactly by BigDecimal") {
            val decimal = Decimal128.parse(s)
            require(decimal.isFinite)
            BDecimal(BigDecimal(decimal.bigDecimalValue()))
          }
        )
      case "$oid"  => asString(v).flatMap(s => attempt("Invalid ObjectId hexadecimal string")(BObjectId(ObjectId(s))))
      case "$uuid" =>
        asString(v).flatMap(s =>
          attempt("Invalid UUID string") {
            require(s.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            BUuid(UUID.fromString(s))
          }
        )
      case "$date"              => date(v)
      case "$binary"            => readBinary(v)
      case "$regularExpression" => regex(v)
      case "$timestamp"         =>
        objectPayload(v, Set("t", "i")) { fields =>
          both(required(fields, "t")(uint), required(fields, "i")(uint)).map { case (t, i) => BTimestamp(t, i.toInt) }
        }
      case "$undefined" =>
        if (tree.boolean(v).contains(true)) Right(BUndefined) else failure(Kind.InvalidValue, "$undefined must be true")
      case "$minKey" | "$maxKey" =>
        if (tree.number(v).contains("1")) Right(if (tag == "$minKey") BMinKey else BMaxKey)
        else failure(Kind.InvalidValue, s"$tag must be the integer 1")
      case _ => failure(Kind.UnsupportedType, s"$tag is not supported by the current BSON value model")
    }

    def read(v: J): Either[BsonErrors, BsonValue] =
      if (tree.isNull(v)) Right(BNull)
      else
        tree.fields(v) match {
          case Some(fields) =>
            fields.find { case (name, _) => reservedTags(name) } match {
              case Some((tag, payload)) => both(keys(fields, Set(tag)), at(tag)(readWrapper(tag, payload))).map(_._2)
              case None                 =>
                val children = collect(fields.map { case (key, child) =>
                  val keyCheck =
                    if (key.indexOf('\u0000') >= 0) failure[Unit](Kind.InvalidValue, "BSON field names cannot contain NUL") else Right(())
                  at(key)(both(keyCheck, read(child)).map { case (_, decoded) => key -> decoded })
                })
                both(keys(fields, fields.map(_._1).toSet), children).map { case (_, entries) => BDocument(Document(entries)) }
            }
          case None =>
            tree.elements(v) match {
              case Some(values) =>
                collect(values.zipWithIndex.map { case (child, index) => read(child).left.map(_.prepend(Index(index))) }).map(BArray.apply)
              case None =>
                tree.string(v) match {
                  case Some(s) => Right(BString(s))
                  case None    =>
                    tree.boolean(v) match {
                      case Some(b) => Right(BBoolean(b))
                      case None    =>
                        tree.number(v) match {
                          case Some(s) if s.matches(integerPattern) =>
                            attempt("Invalid JSON integer")(BigInt(s)).flatMap { number =>
                              if (number.isValidInt) Right(BInt32(number.toInt))
                              else if (number.isValidLong) Right(BInt64(number.toLong))
                              else double(s).map(BDouble.apply)
                            }
                          case Some(s) => double(s).map(BDouble.apply)
                          case None    => failure(Kind.InvalidValue, "Unsupported JSON tree value")
                        }
                    }
                }
            }
        }
    read(value)
  }
}
