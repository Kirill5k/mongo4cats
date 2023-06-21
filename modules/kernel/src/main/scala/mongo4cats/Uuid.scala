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

import java.nio.ByteBuffer
import java.util.{Base64, UUID}

private[mongo4cats] object Uuid {

  def toBase64(uuid: UUID): String = {
    val bb = ByteBuffer.wrap(Array.ofDim[Byte](16))
    bb.putLong(uuid.getMostSignificantBits)
    bb.putLong(uuid.getLeastSignificantBits)
    Base64.getEncoder.encodeToString(bb.array())
  }

  def fromBase64(base64Str: String): UUID = {
    val bytes = Base64.getDecoder.decode(base64Str)
    val bb    = ByteBuffer.wrap(bytes)
    new UUID(bb.getLong(), bb.getLong())
  }
}
