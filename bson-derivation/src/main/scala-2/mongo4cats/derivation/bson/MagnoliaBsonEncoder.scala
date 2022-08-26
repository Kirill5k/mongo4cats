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

import magnolia1._
import mongo4cats.derivation.bson.BsonEncoder.{dummyRoot, instanceFromJavaCodec}
import mongo4cats.derivation.bson.configured.Configuration
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}
import org.bson.{BsonDocument, BsonDocumentWriter, BsonReader, BsonString, BsonValue, BsonWriter}

private[bson] object MagnoliaBsonEncoder {

  private[bson] def join[A](caseClass: CaseClass[BsonEncoder, A])(implicit config: Configuration): BsonEncoder[A] = {
    val paramArray = caseClass.parameters.toArray
    val nbParams   = paramArray.length

    val paramLabelArray = paramArray.map { p =>
      val jsonKeyAnnotation = p.annotations.collectFirst { case ann: BsonKey => ann }

      jsonKeyAnnotation match {
        case Some(ann) => ann.value
        case _         => config.transformMemberNames(p.label)
      }
    }

    if (paramLabelArray.distinct.length != caseClass.parameters.length) {
      throw new BsonDerivationError(
        "Duplicate key detected after applying transformation function for case class parameters"
      )
    }

    new BsonDocumentEncoder[A] {

      override def unsafeBsonEncode(_writer: BsonWriter, a: A, encoderContext: EncoderContext): Unit = {
        val writer = config.mayOptimizeWriter(_writer)

        writer.writeStartDocument()
        unsafeFieldsBsonEncode(writer, a, encoderContext)
        writer.writeEndDocument()
      }

      override def unsafeFieldsBsonEncode(_writer: BsonWriter, a: A, encoderContext: EncoderContext): Unit = {
        val writer = config.mayOptimizeWriter(_writer)

        //if (config.yoloMode) println(s"unsafeFieldsBsonEncode(): ${a}")

        var i = 0
        while (i < nbParams) {
          val param = paramArray(i)
          writer.writeName(paramLabelArray(i))
          param.typeclass.unsafeBsonEncode(writer, param.dereference(a), encoderContext)
          i += 1
        }
      }
    }
  }

  private[bson] def split[A](
      sealedTrait: SealedTrait[BsonEncoder, A]
  )(implicit config: Configuration): BsonEncoder[A] = {
    {
      val origTypeNames = sealedTrait.subtypes.map(_.typeName.short)
      val transformed   = origTypeNames.map(config.transformConstructorNames).distinct
      if (transformed.length != origTypeNames.length) {
        throw new BsonDerivationError("Duplicate key detected after applying transformation function for sealed trait child classes")
      }
    }

    val subTypeLabelArray = sealedTrait.subtypes.map { subType =>
      config.transformConstructorNames(subType.typeName.short)
    }.toArray

    val discriminatorOpt = config.discriminator

    new BsonDocumentEncoder[A] {
      override def unsafeFieldsBsonEncode(_writer: BsonWriter, a: A, encoderContext: EncoderContext): Unit =
        sealedTrait.split(a) { subType =>
          val constructorName       = subTypeLabelArray(subType.index)
          val typeclass             = subType.typeclass
          val isBsonDocumentEncoder = typeclass.isInstanceOf[BsonDocumentEncoder[_]]
          val writer                = config.mayOptimizeWriter(_writer)

          // Note: Here we handle the edge case where a subtype of a sealed trait has a custom encoder which does not encode
          // encode into a JSON object and thus we cannot insert the discriminator key. In this case we fallback
          // to the non-discriminator case for this subtype. This is same as the behavior of circe-generic-extras
          if (discriminatorOpt.isDefined && isBsonDocumentEncoder) {
            val discriminator = discriminatorOpt.get
            writer.writeName(discriminator)
            writer.writeString(constructorName)
            typeclass.asInstanceOf[BsonDocumentEncoder[A]].unsafeFieldsBsonEncode(writer, a, encoderContext)
          } else {
            writer.writeName(constructorName)
            typeclass.unsafeBsonEncode(writer, subType.cast(a), encoderContext)
          }
        }
    }
  }
}
