package mongo4cats

import java.nio.ByteBuffer
import java.util.{Base64, UUID}

private[mongo4cats] object Uuid {

  def toBase64(uuid: UUID): String = {
    val base64 = Base64.getEncoder
    val bb = ByteBuffer.wrap(Array.ofDim[Byte](16))
    bb.putLong(uuid.getMostSignificantBits);
    bb.putLong(uuid.getLeastSignificantBits);
    base64.encodeToString(bb.array());
  }

  def fromBase64(base64Str: String): UUID = {
    val bytes = Base64.getDecoder.decode(base64Str)
    val bb = ByteBuffer.wrap(bytes)
    new UUID(bb.getLong(), bb.getLong());
  }
}
