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

package mongo4cats.derivation.bson

import cats.syntax.all._
import magnolia1._
import mongo4cats.derivation.bson.BsonDecoder.Result
import mongo4cats.derivation.bson.configured.Configuration
import org.bson.{BsonDocument, BsonValue}

private[bson] object MagnoliaBsonDecoder {

  private[bson] def join[T](
      caseClass: CaseClass[BsonDecoder, T]
  )(implicit configuration: Configuration): BsonDecoder[T] = {
    val paramJsonKeyLookup: Map[String, String] =
      caseClass.parameters.map { p =>
        val jsonKeyAnnotation = p.annotations.collectFirst { case ann: BsonKey => ann }

        jsonKeyAnnotation match {
          case Some(ann) => p.label -> ann.value
          case None      => p.label -> configuration.transformMemberNames(p.label)
        }
      }.toMap

    // println(paramJsonKeyLookup)

    if (paramJsonKeyLookup.values.toList.distinct.length != caseClass.parameters.length) {
      throw new BsonDerivationError("Duplicate key detected after applying transformation function for case class parameters")
    }

    if (configuration.useDefaults) {
      new BsonDecoder[T] {
        override def apply(bson: BsonValue): Result[T] =
          caseClass
            .constructEither { p =>
              val key: String = paramJsonKeyLookup.getOrElse(
                p.label,
                throw new IllegalStateException("Looking up a parameter label should always yield a value. This is a bug")
              )

              bson match {
                case doc: BsonDocument =>
                  val value: BsonValue = doc.get(key)

                  if (value == null || value.isNull) {
                    p.default.fold(
                      // Some decoders (in particular, the default Option[T] decoder) do special things when a key is missing,
                      // so we give them a chance to do their thing here.
                      p.typeclass.apply(value)
                    )(x => x.asRight)
                  } else {
                    p.typeclass.apply(value)
                  }

                case other => new Throwable(s"Not a BsonDocument: ${other}").asLeft
              }
            }
            .leftMap(_.head)
      }
    } else {
      new BsonDecoder[T] {
        override def apply(bsonValue: BsonValue): Result[T] =
          bsonValue match {
            case doc: BsonDocument =>
              caseClass
                .constructEither(p =>
                  p.typeclass
                    .apply(
                      doc
                        .get(
                          paramJsonKeyLookup.getOrElse(
                            p.label,
                            throw new IllegalStateException("Looking up a parameter label should always yield a value. This is a bug")
                          )
                        )
                    )
                )
                .leftMap(_.head)
            case other => throw new IllegalStateException(s"""|Not BsonDocument: $other
                 |Type: ${caseClass.typeName.full}""".stripMargin)
          }
      }
    }
  }

  private[bson] def split[T](
      sealedTrait: SealedTrait[BsonDecoder, T]
  )(implicit configuration: Configuration): BsonDecoder[T] = {
    val constructorLookup: Map[String, Subtype[BsonDecoder, T]] =
      sealedTrait.subtypes.map(s => configuration.transformConstructorNames(s.typeName.short) -> s).toMap

    if (constructorLookup.size != sealedTrait.subtypes.length) {
      throw new BsonDerivationError("Duplicate key detected after applying transformation function for case class parameters")
    }

    configuration.discriminator match {
      case Some(discriminator) => new DiscriminatedDecoder[T](discriminator, constructorLookup)
      case None                => new NonDiscriminatedDecoder[T](constructorLookup)
    }
  }

  private[bson] class NonDiscriminatedDecoder[T](constructorLookup: Map[String, Subtype[BsonDecoder, T]]) extends BsonDecoder[T] {

    private val knownSubTypes: String = constructorLookup.keys.toSeq.sorted.mkString(",")

    override def apply(bson: BsonValue): Result[T] =
      bson match {
        case doc: BsonDocument if doc.keySet().size === 1 =>
          val key: String = doc.getFirstKey

          for {
            theSubtype <- Either.fromOption(
              constructorLookup.get(key),
              new Throwable(
                s"""|Can't decode coproduct type: couldn't find matching subtype.
                      |JSON: ${bson},
                      |Key: $key
                      |
                      |Known subtypes: $knownSubTypes\n""".stripMargin
              )
            )

            result <- theSubtype.typeclass(doc.get(key))
          } yield result

        case _ =>
          Left(
            new Throwable(
              s"""|Can't decode coproduct type: zero or several keys were found, while coproduct type requires exactly one.
                    |JSON: ${bson},
                    |Keys: $${c.keys.map(_.mkString(","))}
                    |Known subtypes: $knownSubTypes\n""".stripMargin
            )
          )
      }
  }

  private[bson] class DiscriminatedDecoder[T](discriminator: String, constructorLookup: Map[String, Subtype[BsonDecoder, T]])
      extends BsonDecoder[T] {

    val knownSubTypes: String = constructorLookup.keys.toSeq.sorted.mkString(",")

    override def apply(bson: BsonValue): Result[T] =
      bson match {
        case doc: BsonDocument =>
          Either.catchNonFatal(doc.getString(discriminator)) match {
            case Left(_) =>
              Left(new Throwable(s"""|Can't decode coproduct type: couldn't find discriminator or is not of type String.
                      |discriminator key: discriminator""".stripMargin))

            case Right(ctorName) =>
              constructorLookup.get(ctorName.toString) match {
                case Some(subType) => subType.typeclass.apply(doc)
                case None =>
                  Left(new Throwable(s"""|Can't decode coproduct type: constructor name not found in known constructor names
                          |BSON: ${doc}
                          |Allowed discriminators: $knownSubTypes""".stripMargin))
              }
          }

        case _ => Left(new Throwable("Not a BsonDocument"))
      }
  }
}
