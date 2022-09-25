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
import mongo4cats.derivation.bson.BsonEncoder.{dummyRoot, fastInstance}
import mongo4cats.derivation.bson.configured.Configuration
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}
import org.bson.BsonWriter

private[bson] object MagnoliaBsonEncoder {

  private[bson] def join[A](caseClass: CaseClass[BsonEncoder, A])(implicit conf: Configuration): BsonEncoder[A] = {
    val params          = caseClass.parameters
    val nbParams        = params.length
    val paramLabelArray = Array.ofDim[String](nbParams)

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
      // TODO check label collision.

      paramLabelArray(i) = bsonLabel
      i += 1
    }

    new BsonDocumentEncoder[A] {
      override def unsafeBsonEncode(writer: BsonWriter, a: A, encoderContext: EncoderContext): Unit = {
        writer.writeStartDocument()
        unsafeFieldsBsonEncode(writer, a, encoderContext)
        writer.writeEndDocument()
      }

      override def unsafeFieldsBsonEncode(writer: BsonWriter, a: A, encoderContext: EncoderContext): Unit = {
        var i = 0
        while (i < nbParams) {
          val param = params(i)
          writer.writeName(paramLabelArray(i))
          param.typeclass.unsafeBsonEncode(writer, param.dereference(a), encoderContext)
          i += 1
        }
      }
    }
  }

  private[bson] def split[A](
      sealedTrait: SealedTrait[BsonEncoder, A]
  )(implicit conf: Configuration): BsonEncoder[A] = {
    val subs              = sealedTrait.subtypes
    val nbSubs            = subs.length
    val subTypeLabelArray = Array.ofDim[String](nbSubs)

    var i = 0
    while (i < nbSubs) {
      val subType = subs(i)
      subTypeLabelArray(i) = conf.transformConstructorNames(subType.typeName.short)
      // TODO check label collision.
      i += 1
    }

    val discriminator    = conf.discriminator.orNull
    val useDiscriminator = discriminator eq null

    new BsonDocumentEncoder[A] {
      override def unsafeFieldsBsonEncode(writer: BsonWriter, a: A, encoderContext: EncoderContext): Unit =
        sealedTrait.split(a) { subType =>
          val constructorName = subTypeLabelArray(subType.index)
          val typeclass       = subType.typeclass

          // Note: Here we handle the edge case where a subtype of a sealed trait has a custom encoder which does not encode
          // encode into a JSON object and thus we cannot insert the discriminator key. In this case we fallback
          // to the non-discriminator case for this subtype. This is same as the behavior of circe-generic-extras
          if (useDiscriminator) {
            writer.writeName(constructorName)
            typeclass.unsafeBsonEncode(writer, subType.cast(a), encoderContext)
          } else {
            val isBsonDocumentEncoder = typeclass.isInstanceOf[BsonDocumentEncoder[_]]

            if (isBsonDocumentEncoder) {
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
}
