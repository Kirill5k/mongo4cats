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
import mongo4cats.derivation.bson.BsonDecoder.instanceFromBsonValue
import mongo4cats.derivation.bson.configured.Configuration
import org.bson.{BsonDocument, BsonReader, BsonValue}
import org.bson.codecs.DecoderContext

private[bson] object MagnoliaBsonDecoder {

  private[bson] def join[A](
      caseClass: CaseClass[BsonDecoder, A]
  )(implicit configuration: Configuration): BsonDecoder[A] = {
    val paramBsonKeyLookup: Map[String, String] =
      caseClass.params.map { p =>
        val bsonKeyAnnotation = p.annotations.collectFirst { case ann: BsonKey => ann }

        bsonKeyAnnotation match {
          case Some(ann) => p.label -> ann.value
          case None      => p.label -> configuration.transformMemberNames(p.label)
        }
      }.toMap

    if (paramBsonKeyLookup.values.toList.distinct.length != caseClass.params.length) {
      throw new BsonDerivationError("Duplicate key detected after applying transformation function for case class parameters")
    }

    if (configuration.useDefaults) {
      instanceFromBsonValue { bson =>
        caseClass
          .construct { p =>
            val key: String = paramBsonKeyLookup.getOrElse(
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
                    p.typeclass.unsafeFromBsonValue(value)
                  )(x => x)
                } else {
                  p.typeclass.unsafeFromBsonValue(value)
                }

              case other => throw new Throwable(s"Not a BsonDocument: ${other}")
            }
          }
      }
    } else {
      instanceFromBsonValue {
        case doc: BsonDocument =>
          caseClass
            .construct(p =>
              p.typeclass
                .unsafeFromBsonValue(
                  doc
                    .get(
                      paramBsonKeyLookup.getOrElse(
                        p.label,
                        throw new IllegalStateException("Looking up a parameter label should always yield a value. This is a bug")
                      )
                    )
                )
            )
        case other =>
          throw new IllegalStateException(
            s"""|Not BsonDocument: $other
                    |Type: ${caseClass.typeInfo.full}""".stripMargin
          )
      }
    }
  }

  private[bson] def split[A](
      sealedTrait: SealedTrait[BsonDecoder, A]
  )(implicit configuration: Configuration): BsonDecoder[A] = {
    val constructorLookup: Map[String, SealedTrait.Subtype[BsonDecoder, A, _]] =
      sealedTrait.subtypes.map(s => configuration.transformConstructorNames(s.typeInfo.short) -> s).toMap

    if (constructorLookup.size != sealedTrait.subtypes.length) {
      throw new BsonDerivationError("Duplicate key detected after applying transformation function for case class parameters")
    }

    val knownSubTypes: String = constructorLookup.keys.toSeq.sorted.mkString(",")

    configuration.discriminator match {
      case Some(discriminator) =>
        instanceFromBsonValue {
          case doc: BsonDocument =>
            Either.catchNonFatal(doc.getString(discriminator)) match {
              case Right(constructorNameBsonString) =>
                val constructorName = constructorNameBsonString.getValue

                constructorLookup.get(constructorName) match {
                  case Some(subType) => subType.typeclass.unsafeFromBsonValue(doc)
                  case _ =>
                    throw new Throwable(
                      s"""|Can't decode coproduct type: constructor name "$constructorName" not found in known constructor names
                          |BSON: $doc
                          |
                          |Allowed discriminators: $knownSubTypes""".stripMargin
                    )
                }

              case Left(ex) =>
                throw new Throwable(
                  s"""|Can't decode coproduct type: couldn't find discriminator or is not of type String.
                      |discriminator key: $discriminator
                      |Exception: $ex
                      |
                      |BSON: $doc""".stripMargin
                )
            }

          case _ => throw new Throwable("Not a BsonDocument")
        }
      case _ =>
        instanceFromBsonValue {
          case doc: BsonDocument if doc.size() === 1 =>
            val key: String = doc.getFirstKey

            val theSubtype =
              constructorLookup.getOrElse(
                key,
                throw new Throwable(
                  s"""|Can't decode coproduct type: couldn't find matching subtype.
                      |BSON: $doc
                      |Key: $key
                      |
                      |Known subtypes: $knownSubTypes\n""".stripMargin
                )
              )

            theSubtype.typeclass.unsafeFromBsonValue(doc.get(key))

          case bson =>
            throw new Throwable(
              s"""|Can't decode coproduct type: zero or several keys were found, while coproduct type requires exactly one.
                  |BSON: ${bson},
                  |Keys: $${c.keys.map(_.mkString(","))}
                  |Known subtypes: $knownSubTypes\n""".stripMargin
            )
        }
    }
  }
}
