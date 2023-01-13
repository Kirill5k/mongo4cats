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

package mongo4cats

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.bson.BsonString
import io.circe.Decoder
import org.scalatest.EitherValues
import io.circe.DecodingFailure

class CirceSpec extends AnyWordSpec with Matchers with EitherValues {

  "circe conversions" should {
    "decode null as if it was Json.null" in {
      circe.implicits.circeDecoderToDecoder[Unit](Decoder.instance { c => 
        c.value.asNull.toRight(DecodingFailure("wasn't null!", Nil))
      }).apply(null) shouldBe Right(())
    }

    "not report the internal root tag in history when reporting errors" in {

      val deco = Decoder.instance(h => {
        h.get[String]("hek")(Decoder.failedWithMessage("Bad!"))
      })

      val res = circe.implicits.circeDecoderToDecoder[String](deco).apply(new BsonString("hek"))

      res.left.value.msg shouldBe "An error occured during decoding BsonValue BsonString{value='hek'}: DecodingFailure(Attempt to decode value on failed cursor, List(DownField(hek)))"

    }
  }

}
