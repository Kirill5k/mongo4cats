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

import cats.data.NonEmptyList
import cats.syntax.all._
import magnolia1._
import mongo4cats.client.MongoClient.asJava
import mongo4cats.derivation.bson.BsonDecoder.{debug, instanceFromBsonValue, instanceFromJavaDecoder, JavaDecoder, Result}
import mongo4cats.derivation.bson.configured.Configuration
import org.bson.AbstractBsonReader.State
import org.bson.codecs.DecoderContext
import org.bson.{AbstractBsonReader, BsonDocument, BsonReader, BsonType, BsonValue}

import java.util

private[bson] object MagnoliaBsonDecoder {

  private[bson] def join[A](
      caseClass: CaseClass[BsonDecoder, A]
  )(implicit configuration: Configuration): BsonDecoder[A] = {
    val paramBsonKeyLookup: Map[String, String] =
      caseClass.parameters.map { p =>
        val bsonKeyAnnotation = p.annotations.collectFirst { case ann: BsonKey => ann }

        bsonKeyAnnotation match {
          case Some(ann) => p.label -> ann.value
          case None      => p.label -> configuration.transformMemberNames(p.label)
        }
      }.toMap

    val nbParams = caseClass.parameters.length
    if (paramBsonKeyLookup.values.toList.distinct.length != nbParams) {
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
      val decoderByParam = caseClass.parameters.toList
        .groupByNel(p =>
          paramBsonKeyLookup.getOrElse(
            p.label,
            throw new IllegalStateException(s"Looking up the parameter label '${p.label}' should always yield a value. This is a bug")
          )
        )

      // For perfs.
      val decoderByParamJava: util.Map[String, NonEmptyList[Param[BsonDecoder, A]]] =
        new util.HashMap[String, NonEmptyList[Param[BsonDecoder, A]]]()
      decoderByParam.foreach { case (k, v) => decoderByParamJava.put(k, v) }

      instanceFromJavaDecoder(new JavaDecoder[A] {

        override def decode(reader: BsonReader, decoderContext: DecoderContext): A = {
          val abstractReader = reader.asInstanceOf[AbstractBsonReader]
          val rawValuesArray = new Array[Any](nbParams)
          val rawValuesSeq = new Seq[Any] {
            override def apply(i: Int): Any      = rawValuesArray(i)
            override val length: Int             = nbParams
            override def iterator: Iterator[Any] = rawValuesArray.iterator
          }

          var name: String = null
          reader.readStartDocument()
          while ({
            if (abstractReader.getState == State.TYPE) {
              reader.readBsonType()
            }
            name = if (abstractReader.getState == State.NAME) reader.readName() else null
            name != null
          }) {
            val decoder = decoderByParamJava.remove(name)
            if (decoder == null) reader.skipValue() // Extra field.
            else {
              val paramInfo = decoder.head
              try
                rawValuesArray(paramInfo.index) = paramInfo.typeclass.unsafeDecode(reader, decoderContext)
              catch {
                case ex: Throwable => throw new Throwable(s"Error while decoding '${caseClass.typeName.full}.$name'", ex)
              }
            }
          }
          reader.readEndDocument()

          // Set `None` for missing fields.
          decoderByParamJava.values().forEach(v => rawValuesArray(v.head.index) = None)

          caseClass.rawConstruct(rawValuesSeq)
        }
      })
    }
  }

  private[bson] def split[A](
      sealedTrait: SealedTrait[BsonDecoder, A]
  )(implicit configuration: Configuration): BsonDecoder[A] = {
    val constructorLookup: Map[String, Subtype[BsonDecoder, A]] =
      sealedTrait.subtypes.map(s => configuration.transformConstructorNames(s.typeName.short) -> s).toMap

    if (constructorLookup.size != sealedTrait.subtypes.length) {
      throw new BsonDerivationError("Duplicate key detected after applying transformation function for case class parameters")
    }

    configuration.discriminator match {
      case Some(discriminator) => new DiscriminatedDecoder[A](discriminator, constructorLookup)
      case _                   => new NonDiscriminatedDecoder[A](constructorLookup)
    }
  }

  private[bson] class NonDiscriminatedDecoder[A](constructorLookup: Map[String, Subtype[BsonDecoder, A]]) extends BsonDecoder[A] {

    private val knownSubTypes: String = constructorLookup.keys.toSeq.sorted.mkString(",")

    override def unsafeFromBsonValue(bson: BsonValue): A = ???

    override def unsafeDecode(reader: BsonReader, decoderContext: DecoderContext): A = {
      // debug(reader, "### unsafeDecode: ")
      reader.readStartDocument()
      val key = reader.readName()
      val theSubtype = constructorLookup.getOrElse(
        key,
        throw new Throwable(s"""|Can't decode coproduct type: couldn't find matching subtype.
                    |BSON: $$doc
                    |Key: $key
                    |
                    |Known subtypes: $knownSubTypes\n""".stripMargin)
      )
      val result = theSubtype.typeclass.unsafeDecode(reader, decoderContext)
      reader.readEndDocument()
      result
    }
  }

  private[bson] class DiscriminatedDecoder[A](
      discriminator: String,
      constructorLookup: Map[String, Subtype[BsonDecoder, A]]
  ) extends BsonDecoder[A] {

    val knownSubTypes: String = constructorLookup.keys.toSeq.sorted.mkString(",")

    override def unsafeFromBsonValue(bson: BsonValue): A =
      bson match {
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

    override def unsafeDecode(reader: BsonReader, decoderContext: DecoderContext): A =
      // debug(reader, s"unsafeDecode (discriminator: ${discriminator}): ")
      // TODO Write a true `unsafeDecode()` version.
      unsafeFromBsonValue(bsonValueCodecSingleton.decode(reader, decoderContext))
  }
}
