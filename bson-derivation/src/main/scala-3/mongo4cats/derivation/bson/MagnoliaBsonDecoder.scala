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
import scala.collection.mutable

private[bson] object MagnoliaBsonDecoder {

  private[bson] def join[A](caseClass: CaseClass[BsonDecoder, A])(implicit conf: Configuration): BsonDecoder[A] = {
    val useDefaults = conf.useDefaults
    val params      = caseClass.params
    val nbParams    = params.length

    // Java HashMap for perfs (minimum allocations).
    val paramByBsonKeyJava = new util.HashMap[String, CaseClass.Param[BsonDecoder, A]](nbParams)

    var i = 0
    while (i < nbParams) {
      val p = params(i)

      var bsonKey: BsonKey = null
      val annIt            = p.annotations.iterator
      while (annIt.hasNext) {
        val ann = annIt.next()
        if (ann.isInstanceOf[BsonKey]) { bsonKey = ann.asInstanceOf[BsonKey] }
      }
      val bsonLabel = if (bsonKey eq null) conf.transformMemberNames(p.label) else bsonKey.value

      if (paramByBsonKeyJava.size() != i) {
        throw new BsonDerivationError("Duplicate key detected after applying transformation function for case class parameters")
      }
      paramByBsonKeyJava.put(bsonLabel, p)
      i += 1
    }

    new BsonDecoder[A] {

      override def unsafeDecode(reader: AbstractBsonReader, decoderContext: DecoderContext): A = {
        val foundParamArray = new Array[Boolean](nbParams) // Init to false.
        val rawValuesArray  = new Array[Any](nbParams)

        var bsonKey: String = null
        reader.readStartDocument()
        while ({
          if (reader.getState eq State.TYPE) reader.readBsonType()
          bsonKey = if (reader.getState eq State.NAME) reader.readName() else null
          bsonKey != null
        }) {
          val param = paramByBsonKeyJava.get(bsonKey)
          if (param eq null) reader.skipValue() // For extra field.
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
        if (useDefaults) {
          while (i < nbParams) {
            if (!foundParamArray(i)) {
              val missingParam = params(i)
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
            if (!foundParamArray(i)) rawValuesArray(i) = params(i).typeclass.unsafeFromBsonValue(BsonNull.VALUE)
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
  )(implicit conf: Configuration): BsonDecoder[A] = {
    val subs              = sealedTrait.subtypes
    val nbSubs            = subs.length
    val constructorLookup = new util.HashMap[String, SealedTrait.Subtype[BsonDecoder, A, ?]](nbSubs)

    var i = 0
    while (i < nbSubs) {
      val sub = subs(i)
      if (constructorLookup.size != i) {
        throw new BsonDerivationError("Duplicate key detected after applying transformation function for case class parameters")
      }
      constructorLookup.put(conf.transformConstructorNames(sub.typeInfo.short), sub)
      i += 1
    }

    conf.discriminator.fold[BsonDecoder[A]](
      new NonDiscriminatedDecoder[A](constructorLookup)
    )(d => //
      new DiscriminatedDecoder[A](d, constructorLookup)
    )
  }

  private[bson] class NonDiscriminatedDecoder[A](
      constructorLookup: util.Map[String, SealedTrait.Subtype[BsonDecoder, A, ?]]
  ) extends BsonDecoder[A] {

    override def unsafeDecode(reader: AbstractBsonReader, decoderContext: DecoderContext): A = {
      reader.readStartDocument()
      val key = reader.readName()
      val theSubtype = {
        val sub = constructorLookup.get(key)
        if (sub eq null) {
          val knownSubTypes = mutable.ListBuffer.empty[String]
          constructorLookup.keySet.forEach(knownSubTypes.append(_))

          throw new Throwable(s"""|Can't decode coproduct type: couldn't find matching subtype.
                                  |BSON: $$doc
                                  |Key: $key
                                  |
                                  |Known subtypes: ${knownSubTypes.sorted.mkString(", ")}\n""".stripMargin)
        } else sub
      }
      val result = theSubtype.typeclass.unsafeDecode(reader, decoderContext)
      reader.readEndDocument()
      result
    }
  }

  private[bson] class DiscriminatedDecoder[A](
      discriminator: String,
      constructorLookup: util.Map[String, SealedTrait.Subtype[BsonDecoder, A, ?]]
  ) extends BsonDecoder[A] {

    override def unsafeDecode(reader: AbstractBsonReader, decoderContext: DecoderContext): A = {
      val mark                    = reader.getMark
      var constructorName: String = null
      var bsonKey: String         = null
      reader.readStartDocument()
      while ({
        if (reader.getState eq State.TYPE) reader.readBsonType()
        bsonKey = if (reader.getState eq State.NAME) reader.readName() else null
        (constructorName eq null) || !(bsonKey eq null)
      })
        if (bsonKey == discriminator) constructorName = reader.readString()
        else if (reader.getState eq State.VALUE) reader.skipValue()
        else throw new Throwable(s"Discriminator '${discriminator}' not found")
      mark.reset()

      val subType = constructorLookup.get(constructorName)
      if (subType eq null) {
        val knownSubTypes = mutable.ListBuffer.empty[String]
        constructorLookup.keySet.forEach(knownSubTypes.append(_))

        throw new Throwable(
          s"""|Can't decode coproduct type: constructor name "$constructorName" not found in known constructor names
              |BSON: $$doc
              |
              |Allowed discriminators: ${knownSubTypes.sorted.mkString(", ")}""".stripMargin
        )
      } else subType.typeclass.unsafeDecode(reader, decoderContext)
    }
  }
}
