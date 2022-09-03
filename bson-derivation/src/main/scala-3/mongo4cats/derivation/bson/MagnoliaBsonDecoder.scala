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
import mongo4cats.derivation.bson.BsonDecoder.slowInstance
import mongo4cats.client.MongoClient.asJava
import mongo4cats.derivation.bson.configured.Configuration
import org.bson.{BsonDocument, BsonReader, BsonValue}
import org.bson.AbstractBsonReader.State
import org.bson.codecs.DecoderContext
import org.bson.{AbstractBsonReader, BsonDocument, BsonDocumentReader, BsonNull, BsonReader, BsonType, BsonValue}

import java.util

private[bson] object MagnoliaBsonDecoder {

  private[bson] def join[A](caseClass: CaseClass[BsonDecoder, A])(implicit conf: Configuration): BsonDecoder[A] = {
    val paramArray = IArray.genericWrapArray(caseClass.params).toArray
    val nbParams   = paramArray.length

    val paramBsonKeyLookup: Map[String, String] =
      caseClass.params.map { p =>
        val bsonKeyAnnotation = p.annotations.collectFirst { case ann: BsonKey => ann }

        bsonKeyAnnotation match {
          case Some(ann) => p.label -> ann.value
          case None      => p.label -> conf.transformMemberNames(p.label)
        }
      }.toMap

    if (paramBsonKeyLookup.values.toList.distinct.length != nbParams) {
      throw new BsonDerivationError("Duplicate key detected after applying transformation function for case class parameters")
    }

    // Java HashMap for perfs (minimum allocations).
    val paramByBsonKeyJava = new util.HashMap[String, CaseClass.Param[BsonDecoder, A]](nbParams)

    paramArray.foreach(p =>
      paramByBsonKeyJava.put(
        paramBsonKeyLookup.getOrElse(
          p.label,
          throw new IllegalStateException(s"Looking up the parameter label '${p.label}' should always yield a value. This is a bug")
        ),
        p
      )
    )

    new BsonDecoder[A] {

      override def unsafeDecode(reader: AbstractBsonReader, decoderContext: DecoderContext): A = {
        val foundParamArray = new Array[Boolean](nbParams) // Init to false.
        val rawValuesArray  = new Array[Any](nbParams)

        var bsonKey: String = null
        reader.readStartDocument()
        while ({
          if (reader.getState == State.TYPE) reader.readBsonType()
          bsonKey = if (reader.getState == State.NAME) reader.readName() else null
          bsonKey != null
        }) {
          val param = paramByBsonKeyJava.get(bsonKey)
          if (param == null) reader.skipValue() // For extra field.
          else {
            val index = param.index
            foundParamArray(index) = true
            try rawValuesArray(index) = param.typeclass.unsafeDecode(reader, decoderContext)
            catch {
              case ex: WithErrorMessage => throw ex
              case ex: Throwable        => throw new Throwable(s"Error while decoding '${caseClass.typeInfo.full}.$bsonKey'", ex)
            }
          }
        }
        reader.readEndDocument()

        // For missing fields.
        var i = 0
        if (conf.useDefaults) {
          while (i < nbParams) {
            if (!foundParamArray(i)) {
              val missingParam = paramArray(i)
              val default      = missingParam.default
              rawValuesArray(i) =
                if (default.isDefined) default.get
                else // Some decoders (in particular, the default Option[T] decoder) do special things when a key is missing, so we give them a chance to do their thing here.
                  missingParam.typeclass.unsafeFromBsonValue(BsonNull.VALUE)
            }
            i += 1
          }
        } else {
          while (i < nbParams) {
            if (!foundParamArray(i)) rawValuesArray(i) = paramArray(i).typeclass.unsafeFromBsonValue(BsonNull.VALUE)
            i += 1
          }
        }

        val rawValuesSeq = new Seq[Any] {
          override def apply(i: Int): Any      = rawValuesArray(i)
          override def length: Int             = nbParams
          override def iterator: Iterator[Any] = rawValuesArray.iterator
        }
        caseClass.rawConstruct(rawValuesSeq)
      }
    }
  }

  private[bson] def split[A](
      sealedTrait: SealedTrait[BsonDecoder, A]
  )(implicit configuration: Configuration): BsonDecoder[A] = {
    val subTypes              = sealedTrait.subtypes.map(s => configuration.transformConstructorNames(s.typeInfo.short) -> s).toMap
    val knownSubTypes: String = subTypes.keys.toSeq.sorted.mkString(", ")
    val constructorLookup: util.Map[String, SealedTrait.Subtype[BsonDecoder, A, ?]] = asJava(subTypes)

    if (constructorLookup.size != sealedTrait.subtypes.length) {
      throw new BsonDerivationError("Duplicate key detected after applying transformation function for case class parameters")
    }

    val discriminatorOpt = configuration.discriminator
    if (discriminatorOpt.isDefined)
      new DiscriminatedDecoder[A](discriminatorOpt.get, constructorLookup, knownSubTypes)
    else
      new NonDiscriminatedDecoder[A](constructorLookup, knownSubTypes)
  }

  private[bson] class NonDiscriminatedDecoder[A](
      constructorLookup: util.Map[String, SealedTrait.Subtype[BsonDecoder, A, ?]],
      knownSubTypes: String
  ) extends BsonDecoder[A] {

    override def unsafeDecode(reader: AbstractBsonReader, decoderContext: DecoderContext): A = {
      reader.readStartDocument()
      val key = reader.readName()
      val theSubtype = {
        val sub = constructorLookup.get(key)
        if (sub == null) throw new Throwable(s"""|Can't decode coproduct type: couldn't find matching subtype.
              |BSON: $$doc
              |Key: $key
 |
              |Known subtypes: $knownSubTypes\n""".stripMargin)
        else sub
      }
      val result = theSubtype.typeclass.unsafeDecode(reader, decoderContext)
      reader.readEndDocument()
      result
    }
  }

  private[bson] class DiscriminatedDecoder[A](
      discriminator: String,
      constructorLookup: util.Map[String, SealedTrait.Subtype[BsonDecoder, A, ?]],
      knownSubTypes: String
  ) extends BsonDecoder[A] {

    override def unsafeDecode(reader: AbstractBsonReader, decoderContext: DecoderContext): A = {
      val mark                    = reader.getMark
      var constructorName: String = null
      var bsonKey: String         = null
      reader.readStartDocument()
      while ({
        if (reader.getState == State.TYPE) reader.readBsonType()
        bsonKey = if (reader.getState == State.NAME) reader.readName() else null
        constructorName == null || bsonKey != null
      })
        if (bsonKey == discriminator) constructorName = reader.readString()
        else if (reader.getState == State.VALUE) reader.skipValue()
        else throw new Throwable(s"Discriminator '${discriminator}' not found")
      mark.reset()

      val subType = constructorLookup.get(constructorName)
      if (subType == null)
        throw new Throwable(
          s"""|Can't decode coproduct type: constructor name "$constructorName" not found in known constructor names
              |BSON: $$doc
              |
              |Allowed discriminators: $knownSubTypes""".stripMargin
        )
      else subType.typeclass.unsafeDecode(reader, decoderContext)
    }
  }
}
